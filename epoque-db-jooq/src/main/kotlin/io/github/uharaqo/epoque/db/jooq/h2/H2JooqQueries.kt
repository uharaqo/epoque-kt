package io.github.uharaqo.epoque.db.jooq.h2

import arrow.core.Either
import arrow.core.left
import io.github.uharaqo.epoque.api.EpoqueException.Cause.EVENT_WRITE_CONFLICT
import io.github.uharaqo.epoque.api.EventType
import io.github.uharaqo.epoque.api.Failable
import io.github.uharaqo.epoque.api.JournalKey
import io.github.uharaqo.epoque.api.SerializedData
import io.github.uharaqo.epoque.api.SerializedEvent
import io.github.uharaqo.epoque.api.Version
import io.github.uharaqo.epoque.api.VersionedEvent
import io.github.uharaqo.epoque.codec.SerializedJson
import io.github.uharaqo.epoque.db.jooq.JooqQueries
import io.github.uharaqo.epoque.db.jooq.TableDefinition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.jooq.DSLContext
import org.jooq.JSONB
import org.jooq.impl.DSL.inline
import org.jooq.impl.DSL.row
import org.jooq.kotlin.coroutines.transactionCoroutine

class H2JooqQueries(
  private val tableDefinition: TableDefinition,
) : JooqQueries<JSONB> {
  override fun JSONB.toSerializedData(): SerializedData = SerializedJson(this.data())

  override fun SerializedData.toFieldValue(): JSONB = JSONB.jsonb(this.toText())

  override fun selectById(
    ctx: DSLContext,
    key: JournalKey,
    prevVersion: Version,
  ): Flow<VersionedEvent> =
    with(tableDefinition) {
      ctx.select(VERSION, TYPE, CONTENT)
        .from(EVENT)
        .where(
          GROUP.eq(key.groupId.unwrap),
          ID.eq(key.id.unwrap),
          VERSION.gt(prevVersion.unwrap),
        )
        .orderBy(VERSION.asc())
        .asFlow()
        .map {
          VersionedEvent(
            version = Version(it.value1()!!),
            type = EventType.of(Class.forName(it.value2())),
            event = SerializedEvent(it.value3()!!.toSerializedData()),
          )
        }
    }

  override suspend fun journalExists(ctx: DSLContext, key: JournalKey): Boolean =
    with(tableDefinition) {
      ctx.select(inline(1))
        .from(EVENT)
        .where(GROUP.eq(key.groupId.unwrap), ID.eq(key.id.unwrap))
        .limit(1)
        .awaitFirstOrNull()
        ?.value1() != null
    }

  override suspend fun writeEvents(ctx: DSLContext, key: JournalKey, events: List<VersionedEvent>) =
    with(tableDefinition) {
      ctx.transactionCoroutine {
        val tx = it.dsl()
        val records =
          events.asSequence().map { e ->
            row(
              key.groupId.unwrap,
              key.id.unwrap,
              e.version.unwrap,
              e.type.toString(),
              e.event.unwrap.toFieldValue(),
            )
          }.toList()

        val cnt =
          tx.insertInto(EVENT)
            .columns(GROUP, ID, VERSION, TYPE, CONTENT)
            .valuesOfRows(records)
            .awaitFirstOrNull() ?: 0

        if (cnt != records.size) {
          throw EVENT_WRITE_CONFLICT.toException()
        }
      }
    }

  /** Prevent the next record to be written through another connection */
  override suspend fun lockNextEvent(ctx: DSLContext, key: JournalKey): Failable<Unit> =
    Either.catch {
      when {
        // try locking the record with the earliest version
        ctx.lockEarliestEvent(key) -> true

        else -> when {
          // if not found, insert a dummy record with the earliest version to prevent concurrent processing
          ctx.insertDummyEventForLock(key, version = 1L) &&
            // no need to keep the record
            ctx.deleteDummyEventForLock(key, version = 1L) -> true

          else -> false
        }
      }
    }
      .map { if (!it) EVENT_WRITE_CONFLICT.toException().left() }
      .mapLeft { EVENT_WRITE_CONFLICT.toException(it) }

  /** Insert an empty event to acquire the lock for the row to be written */
  private suspend fun DSLContext.insertDummyEventForLock(
    key: JournalKey,
    version: Long,
  ): Boolean =
    with(tableDefinition) {
      try {
        insertInto(EVENT)
          .columns(GROUP, ID, VERSION, TYPE, CONTENT)
          .values(key.groupId.unwrap, key.id.unwrap, version, "(LOCK)", JSONB.jsonb(null))
          //        .onConflictDoNothing() // not supported in H2
          .awaitFirstOrNull() == 1
      } catch (ignored: Exception) {
        false
      }
    }

  /** Remove the dummy record. The transaction holds the row lock */
  private suspend fun DSLContext.deleteDummyEventForLock(
    key: JournalKey,
    version: Long,
  ): Boolean =
    with(tableDefinition) {
      deleteFrom(EVENT)
        .where(GROUP.eq(key.groupId.unwrap), ID.eq(key.id.unwrap), VERSION.eq(version))
        .awaitFirstOrNull() == 1
    }

  private suspend fun DSLContext.lockEarliestEvent(key: JournalKey): Boolean =
    with(tableDefinition) {
      select(inline(1))
        .from(EVENT)
        .where(GROUP.eq(key.groupId.unwrap), ID.eq(key.id.unwrap))
        .orderBy(VERSION.asc())
        .limit(1)
        .forUpdate()
        .noWait()
        .awaitFirstOrNull() != null
    }
}
