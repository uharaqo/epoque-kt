package io.github.uharaqo.epoque.db.jooq

import arrow.core.Either
import arrow.core.left
import io.github.uharaqo.epoque.api.EpoqueException.EventWriteConflict
import io.github.uharaqo.epoque.api.EventType
import io.github.uharaqo.epoque.api.JournalKey
import io.github.uharaqo.epoque.api.SerializedData
import io.github.uharaqo.epoque.api.SerializedEvent
import io.github.uharaqo.epoque.api.Version
import io.github.uharaqo.epoque.api.VersionedEvent
import io.github.uharaqo.epoque.serialization.SerializedJson
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
  private val def: TableDefinition,
) : JooqQueries<JSONB> {
  override fun JSONB.toSerializedData(): SerializedData = SerializedJson(this.data())

  override fun SerializedData.toFieldValue(): JSONB = JSONB.jsonb(this.toText())

  override fun selectById(
    ctx: DSLContext,
    key: JournalKey,
    prevVersion: Version,
  ): Flow<VersionedEvent> =
    with(def) {
      ctx.select(this.VERSION, this.TYPE, this.CONTENT)
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
            type = EventType(it.value2()!!),
            event = SerializedEvent(it.value3()!!.toSerializedData()),
          )
        }
    }

  override suspend fun writeEvents(ctx: DSLContext, key: JournalKey, events: List<VersionedEvent>) =
    with(def) {
      ctx.transactionCoroutine {
        val tx = it.dsl()
        val records =
          events.asSequence().map { e ->
            row(
              key.groupId.unwrap,
              key.id.unwrap,
              e.version.unwrap,
              e.type.unwrap,
              e.event.unwrap.toFieldValue(),
            )
          }.toList()

        val cnt =
          tx.insertInto(this.EVENT)
            .columns(this.GROUP, this.ID, this.VERSION, this.TYPE, this.CONTENT)
            .valuesOfRows(records)
            .awaitFirstOrNull() ?: 0

        if (cnt != records.size) {
          throw EventWriteConflict("Failed to write events due to conflict")
        }
      }
    }

  /** Prevent the next record to be written through another connection */
  override suspend fun lockNextEvent(ctx: DSLContext, key: JournalKey): Either<EventWriteConflict, Unit> =
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
      .map { if (!it) EventWriteConflict("Failed to acquire lock").left() }
      .mapLeft { EventWriteConflict("Failed to write event due to conflict: $key", it) }

  /** Insert an empty event to acquire the lock for the row to be written */
  private suspend fun DSLContext.insertDummyEventForLock(
    key: JournalKey,
    version: Long,
  ): Boolean =
    with(def) {
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
    with(def) {
      deleteFrom(EVENT)
        .where(GROUP.eq(key.groupId.unwrap), ID.eq(key.id.unwrap), VERSION.eq(version))
        .awaitFirstOrNull() == 1
    }

  private suspend fun DSLContext.lockEarliestEvent(key: JournalKey): Boolean =
    with(def) {
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
