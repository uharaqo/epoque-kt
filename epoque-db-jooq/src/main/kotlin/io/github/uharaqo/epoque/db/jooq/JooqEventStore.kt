package io.github.uharaqo.epoque.db.jooq

import arrow.core.Either
import arrow.core.getOrElse
import io.github.uharaqo.epoque.api.CommandOutput
import io.github.uharaqo.epoque.api.EpoqueContext
import io.github.uharaqo.epoque.api.EpoqueException
import io.github.uharaqo.epoque.api.EpoqueException.Cause.EVENT_READ_FAILURE
import io.github.uharaqo.epoque.api.EpoqueException.Cause.EVENT_WRITE_CONFLICT
import io.github.uharaqo.epoque.api.EpoqueException.Cause.EVENT_WRITE_FAILURE
import io.github.uharaqo.epoque.api.EventStore
import io.github.uharaqo.epoque.api.Failable
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
    key: JournalKey,
    prevVersion: Version,
    tx: TransactionContext,
  ): Failable<Flow<VersionedEvent>> = Either.catch {
    tx.asJooqBlocking {
      queries.selectById(ctx, key, prevVersion)
    }
  }.mapLeft { EVENT_READ_FAILURE.toException(it) }

  override suspend fun journalExists(
    key: JournalKey,
    tx: TransactionContext,
  ): Failable<Boolean> = Either.catch {
    tx.asJooq {
      queries.journalExists(ctx, key)
    }
  }.mapLeft { EVENT_READ_FAILURE.toException(it) }

  override suspend fun writeEvents(
    output: CommandOutput,
    tx: TransactionContext,
  ): Failable<Unit> = Either.catch {
    tx.asJooq {
      queries.writeEvents(ctx, output.context.key, output.events)
    }
  }.mapLeft { it.toEpoqueException() }

  private suspend fun getTransactionContext() = TransactionContext.Key.get()

  private suspend fun <T> runTransaction(
    ctx: DSLContext,
    lockOption: LockOption,
    lockedKeys: Set<JournalKey>,
    block: suspend (tx: TransactionContext) -> T,
    beforeExecute: suspend (DSLContext) -> Unit = {},
  ): T {
    // explicitly propagate this context because current context has Job related contexts
    val epoqueContext = coroutineContext[EpoqueContext.Key] ?: EpoqueContext.create()

    return ctx.transactionCoroutine(Dispatchers.IO) { conf ->
      val tx = JooqTransactionContext(conf.dsl(), lockOption, lockedKeys)
      epoqueContext.withContext(TransactionContext.Key, tx) {
        // explicitly throw exception to roll back the transaction
        tx.asJooq { beforeExecute(this.ctx) }
        block(tx)
      }
    }
  }

  override suspend fun <T> startDefaultTransaction(block: suspend (tx: TransactionContext) -> T): Failable<T> =
    Either.catch {
      getTransactionContext()
        ?.let { block(it) }
        ?: runTransaction(originalContext, LockOption.DEFAULT, emptySet(), block)
    }.mapLeft { it.toEpoqueException() }

  override suspend fun <T> startTransactionAndLock(
    key: JournalKey,
    lockOption: LockOption,
    block: suspend (tx: TransactionContext) -> T,
  ): Failable<T> =
    when (lockOption) {
      LockOption.DEFAULT -> startDefaultTransaction(block)

      LockOption.LOCK_JOURNAL -> Either.catch {
        val prevTx = getTransactionContext()
        val locked = prevTx?.asJooq { lockedKeys } ?: emptySet()
        if (key in locked) return@catch block(prevTx!!)

        val prevCtx = prevTx?.asJooq { ctx } ?: originalContext

        runTransaction(prevCtx, lockOption, locked + key, block) { ctx ->
          queries.lockNextEvent(ctx, key).getOrElse { throw it }
        }
      }.mapLeft { it.toEpoqueException() }
    }

  private fun Throwable.toEpoqueException(): EpoqueException =
    when (this) {
      is IntegrityConstraintViolationException -> EVENT_WRITE_CONFLICT.toException(this)
      is EpoqueException -> this
      else -> EVENT_WRITE_FAILURE.toException(this)
    }
}
