package io.github.uharaqo.epoque.db.jooq

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.raise.catch
import arrow.core.raise.either
import io.github.uharaqo.epoque.api.EpoqueContext
import io.github.uharaqo.epoque.api.EpoqueException
import io.github.uharaqo.epoque.api.EpoqueException.EventLoadFailure
import io.github.uharaqo.epoque.api.EventStore
import io.github.uharaqo.epoque.api.JournalKey
import io.github.uharaqo.epoque.api.LockOption
import io.github.uharaqo.epoque.api.TransactionContext
import io.github.uharaqo.epoque.api.Version
import io.github.uharaqo.epoque.api.VersionedEvent
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import org.jooq.DSLContext
import org.jooq.exception.IntegrityConstraintViolationException
import org.jooq.kotlin.coroutines.transactionCoroutine

class JooqEventStore<D>(
  private val originalContext: DSLContext,
  private val queries: JooqQueries<D>,
) : EventStore {
  override fun queryById(
    journalKey: JournalKey,
    prevVersion: Version,
    tx: TransactionContext,
  ): Either<EventLoadFailure, Flow<VersionedEvent>> = either {
    catch(
      {
        tx.asJooqBlocking {
          with(queries) { ctx.selectById(journalKey, prevVersion) }
        }
      },
    ) { raise(EventLoadFailure("Failed to load events: $journalKey", it)) }
  }

  override suspend fun writeEvents(
    journalKey: JournalKey,
    events: List<VersionedEvent>,
    tx: TransactionContext,
  ): Either<EpoqueException, Unit> = either {
    catch(
      {
        tx.asJooq {
          with(queries) { ctx.writeEvents(journalKey, events) }
        }
      },
    ) { raise(it.toEpoqueException(": $journalKey")) }
  }

  private suspend fun getTransactionContext() = TransactionContext.Key.get()

  private suspend fun <T> runTransaction(
    ctx: DSLContext,
    lockOption: LockOption,
    lockedKeys: Set<JournalKey>,
    block: suspend (tx: TransactionContext) -> T,
    beforeExecute: suspend (DSLContext) -> Unit = {},
  ): T {
    // explicitly propagate this context because current context has Job related contexts
    val epoqueContext = coroutineContext[EpoqueContext.Key] ?: EpoqueContext()

    return ctx.transactionCoroutine(Dispatchers.IO) { conf ->
      val tx = JooqTransactionContext(conf.dsl(), lockOption, lockedKeys)
      epoqueContext.withContext(TransactionContext.Key, tx) {
        // explicitly throw exception to roll back the transaction
        tx.asJooq { beforeExecute(this.ctx) }
        block(tx)
      }
    }
  }

  override suspend fun <T> startDefaultTransaction(block: suspend (tx: TransactionContext) -> T): Either<EpoqueException, T> =
    Either.catch {
      getTransactionContext()
        ?.let { block(it) }
        ?: runTransaction(originalContext, LockOption.DEFAULT, emptySet(), block)
    }.mapLeft { it.toEpoqueException() }

  override suspend fun <T> startTransactionAndLock(
    key: JournalKey,
    lockOption: LockOption,
    block: suspend (tx: TransactionContext) -> T,
  ): Either<EpoqueException, T> =
    when (lockOption) {
      LockOption.DEFAULT -> startDefaultTransaction(block)

      LockOption.LOCK_JOURNAL -> Either.catch {
        val prevTx = getTransactionContext()
        val locked = prevTx?.asJooq { lockedKeys } ?: emptySet()
        if (key in locked) return@catch block(prevTx!!)

        val prevCtx = prevTx?.asJooq { ctx } ?: originalContext

        runTransaction(prevCtx, lockOption, locked + key, block) { ctx ->
          with(queries) {
            ctx.lockNextEvent(key).getOrElse { throw it }
          }
        }
      }.mapLeft { it.toEpoqueException(": $key") }
    }

  private fun Throwable.toEpoqueException(trailer: String = ""): EpoqueException =
    when (this) {
      is IntegrityConstraintViolationException ->
        EpoqueException.EventWriteConflict("Failed due to conflict$trailer", this)

      is EpoqueException -> this
      else -> EpoqueException.EventWriteFailure("Database access error$trailer", this)
    }
}
