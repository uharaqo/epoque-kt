package io.github.uharaqo.epoque.api

import kotlinx.coroutines.flow.Flow

interface TransactionContext : EpoqueContextValue {
  val lockOption: LockOption
  val lockedKeys: Set<JournalKey>

  object Key : EpoqueContextKey<TransactionContext>
}

/** Controls how command handlers are executed for each [JournalKey] */
enum class LockOption {
  /** Command handlers for the same [JournalKey] can run in parallel but may fail due to conflict on event write */
  DEFAULT,

  /** Ensures that only a single command handler can be processed at a time by locking the [JournalKey] */
  LOCK_JOURNAL,
}

interface EventWriter {
  suspend fun writeEvents(
    output: CommandOutput,
    tx: TransactionContext,
  ): Failable<Unit>
}

interface EventLoader {
  fun queryById(
    key: JournalKey,
    prevVersion: Version,
    tx: TransactionContext,
  ): Failable<Flow<VersionedEvent>>
}

interface TransactionStarter {
  suspend fun <T> startTransactionAndLock(
    key: JournalKey,
    lockOption: LockOption,
    block: suspend (tx: TransactionContext) -> T,
  ): Failable<T>

  suspend fun <T> startDefaultTransaction(block: suspend (tx: TransactionContext) -> T): Failable<T>
}

interface EventStore : EventLoader, EventWriter, TransactionStarter
