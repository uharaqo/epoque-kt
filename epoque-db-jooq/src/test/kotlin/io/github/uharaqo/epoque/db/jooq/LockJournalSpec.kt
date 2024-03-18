package io.github.uharaqo.epoque.db.jooq

import io.github.uharaqo.epoque.api.EpoqueException
import io.github.uharaqo.epoque.api.EpoqueException.EventWriteConflict
import io.github.uharaqo.epoque.api.EventStore
import io.github.uharaqo.epoque.api.EventType
import io.github.uharaqo.epoque.api.JournalGroupId
import io.github.uharaqo.epoque.api.JournalId
import io.github.uharaqo.epoque.api.JournalKey
import io.github.uharaqo.epoque.api.LockOption
import io.github.uharaqo.epoque.api.SerializedEvent
import io.github.uharaqo.epoque.api.Version
import io.github.uharaqo.epoque.api.VersionedEvent
import io.github.uharaqo.epoque.serialization.SerializedJson
import io.kotest.assertions.arrow.core.rethrow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.common.runBlocking
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.throwable.shouldHaveMessage
import java.sql.DriverManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import org.jooq.DSLContext
import org.jooq.JSONB
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory

class LockJournalSpec : StringSpec(
  {
    val ctx = newDslContext().also {
      autoClose(it.configuration().connectionProvider().acquire()!!)
    }
    val store = ctx.toEventStore()

    beforeEach {
      with(tableDefinition) {
        ctx.createTableQuery().execute()
      }
    }

    afterEach {
      ctx.truncate(tableDefinition.EVENT).execute()
    }

    "Simple Read-Write" {
      val insertedAndFetched =
        runBlocking {
          store.startDefaultTransaction { tx ->
            store.writeEvents(key1, listOf(event1, event2), tx)

            store.queryById(key1, Version(0), tx).rethrow().toList()
          }.rethrow()
        }

      insertedAndFetched shouldBe listOf(event1, event2)
    }

    "Duplicated requests are rejected" {
      shouldThrow<EventWriteConflict> {
        withConcurrentConnections { store1, store2 ->
          with(store1) {
            startTransactionAndLock(key1, LockOption.DEFAULT) { tx ->
              writeEvents(key1, listOf(event1, event2), tx).rethrow()
            }.rethrow()
          }
          with(store2) {
            startTransactionAndLock(key1, LockOption.DEFAULT) { tx ->
              writeEvents(key1, listOf(event1, event2), tx).rethrow()
            }.rethrow()
          }
        }
      } shouldHaveMessage "Failed due to conflict: $key1"
    }

    "LOCK_JOURNAL blocks concurrent inserts (without version 1 record)" {
      val t2 = 500L
      val t3 = 1000L
      withContext(Dispatchers.IO) {
        withConcurrentConnections { store1, store2 ->
          val list = mutableListOf<String>()

          // take a lock, sleep, and write succeeds
          val job1 = async {
            val log = LoggerFactory.getLogger("JOB1")
            log.info("Starting @ T1").also { list += "Job1 Starting" }

            with(store1) {
              log.info("Taking a lock @ T1").also { list += "Job1 Locking" }
              startTransactionAndLock(key1, LockOption.LOCK_JOURNAL) { tx ->
                log.info("Took a lock. Sleeping @ T2").also { list += "Job1 Sleeping" }
                delay(t3)
                log.info("Writing @T4").also { list += "Job1 Writing" }
                writeEvents(key1, listOf(event1), tx).rethrow()
              }.rethrow()
            }
          }

          // sleep a bit to yield, wait for JOB1 to release the lock, then write fails
          val job2 = async {
            val log = LoggerFactory.getLogger("JOB2")
            log.info("Starting @ T1").also { list += "Job2 Sleeping" }
            delay(t2)

            with(store2) {
              log.info("Taking a lock @ T3 <- blocked by the job1's lock for a while")
                .also { list += "Job2 Locking" }
              startTransactionAndLock(key1, LockOption.LOCK_JOURNAL) { tx ->
                log.info("Writing @ T5 <- gets called once the job1 release the lock")
                  .also { list += "Job2 Writing" }
                shouldThrow<EpoqueException> {
                  writeEvents(key1, listOf(event1), tx).rethrow()
                } shouldHaveMessage "Failed due to conflict: $key1"
              }.rethrow()
            }
          }

          job1.join().also { list += "Job1 Done" }
          LoggerFactory.getLogger(this.javaClass).info("JOB1 done @ T5")
          job2.join().also { list += "Job2 Done" }
          LoggerFactory.getLogger(this.javaClass).info("JOB2 done @ T6")

          list shouldBe listOf(
            "Job1 Starting", "Job1 Locking", "Job2 Sleeping", // T1: Job1 <- Job2
            "Job1 Sleeping", // T2: Job1 -> Job2
            "Job2 Locking", // T3: Job2 -> Job1
            "Job1 Writing", "Job1 Done", // T4, T5: Job1 -> Job2
            "Job2 Writing", "Job2 Done", // T5, T6: Job2
          )
        }
      }
    }
  },
) {
  companion object {
    val tableDefinition = TableDefinition()

    val key1 = JournalKey(JournalGroupId("G1"), JournalId("J1"))
    val key2 = JournalKey(JournalGroupId("G2"), JournalId("J2"))
    val event1 = VersionedEvent(
      Version(1),
      EventType("foo"),
      SerializedEvent(SerializedJson("{}")),
    )
    val event2 = VersionedEvent(
      Version(2),
      EventType("foo"),
      SerializedEvent(SerializedJson("{}")),
    )

    private suspend fun withConcurrentConnections(f: suspend (EventStore, EventStore) -> Unit) {
      val ctx1 = newDslContext()
      val ctx2 = newDslContext()
      val store1 = ctx1.toEventStore()
      val store2 = ctx2.toEventStore()
      try {
        f(store1, store2)
      } finally {
        ctx1.configuration().connectionProvider().acquire()!!.close()
        ctx2.configuration().connectionProvider().acquire()!!.close()
      }
    }

    private fun DSLContext.toEventStore(): JooqEventStore<JSONB> =
      JooqEventStore(this, H2JooqQueries(tableDefinition))

    private fun newDslContext(): DSLContext =
      DSL.using(
        DriverManager.getConnection(
          "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=TRUE",
          "sa",
          "",
        ).also { it.autoCommit = false },
        SQLDialect.H2,
      )
  }
}
