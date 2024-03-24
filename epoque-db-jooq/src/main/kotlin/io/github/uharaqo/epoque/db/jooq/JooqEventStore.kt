package io.github.uharaqo.epoque.db.jooq

import arrow.core.Either
import arrow.core.getOrElse
import io.github.uharaqo.epoque.api.CommandContext
import io.github.uharaqo.epoque.api.CommandExecutorOptions
import io.github.uharaqo.epoque.api.CommandOutput
import io.github.uharaqo.epoque.api.EpoqueContext
import io.github.uharaqo.epoque.api.EpoqueException
import io.github.uharaqo.epoque.api.EpoqueException.Cause.EVENT_READ_FAILURE
import io.github.uharaqo.epoque.api.EpoqueException.Cause.EVENT_WRITE_CONFLICT
import io.github.uharaqo.epoque.api.EpoqueException.Cause.EVENT_WRITE_FAILURE
import io.github.uharaqo.epoque.api.EventStore
import io.github.uharaqo.epoque.api.Failable
import io.github.uharaqo.epoque.api.JournalKey
import io.github.uharaqo.epoque.api.TransactionContext
import io.github.uharaqo.epoque.api.Version
import io.github.uharaqo.epoque.api.VersionedEvent
import io.github.uharaqo.epoque.api.WriteOption
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withTimeout
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

  private suspend fun getTransactionContext() = TransactionContext.get()

  private suspend fun <T> runTransaction(
    ctx: DSLContext,
    writeOption: WriteOption,
    lockedKeys: Set<JournalKey>,
    block: suspend (tx: TransactionContext) -> T,
    beforeExecute: suspend (DSLContext) -> Unit = {},
  ): T {
    // jOOQ does not work with a coroutineContext that contains a Job
    val coroutineCtx = coroutineContext.minusKey(Job)

    return ctx.transactionCoroutine(coroutineCtx + Dispatchers.IO) { conf ->
      // setting the timeout again because the TimeoutCoroutine was removed as a Job
      withTimeout(getRemainingMillisToTimeout()) {
        val tx = JooqTransactionContext(conf.dsl(), writeOption, lockedKeys)
        EpoqueContext.with({ put(TransactionContext, tx) }) {
          // explicitly throw exception to roll back the transaction
          tx.asJooq { beforeExecute(this.ctx) }
          block(tx)
        }
      }
    }
  }

  private suspend fun getRemainingMillisToTimeout(): Long =
    CommandContext.get()
      ?.let { context ->
        val timeoutMillis = context.options.timeoutMillis
        val elapsedMillis = System.currentTimeMillis() - context.receivedTime.toEpochMilli()
        timeoutMillis - elapsedMillis
      }
      ?: CommandExecutorOptions.DEFAULT.timeoutMillis

  override suspend fun <T> startDefaultTransaction(block: suspend (tx: TransactionContext) -> T): Failable<T> =
    Either.catch {
      getTransactionContext()
        ?.let { block(it) }
        ?: runTransaction(originalContext, WriteOption.DEFAULT, emptySet(), block)
    }.mapLeft { it.toEpoqueException() }

  override suspend fun <T> startTransactionAndLock(
    key: JournalKey,
    writeOption: WriteOption,
    block: suspend (tx: TransactionContext) -> T,
  ): Failable<T> =
    when (writeOption) {
      WriteOption.DEFAULT -> startDefaultTransaction(block)

      WriteOption.LOCK_JOURNAL -> Either.catch {
        val prevTx = getTransactionContext()
        val locked = prevTx?.asJooq { lockedKeys } ?: emptySet()
        if (key in locked) return@catch block(prevTx!!)

        val prevCtx = prevTx?.asJooq { ctx } ?: originalContext

        runTransaction(prevCtx, writeOption, locked + key, block) { ctx ->
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
