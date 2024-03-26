package io.github.uharaqo.epoque.api

import kotlinx.coroutines.flow.Flow

interface TransactionContext {
  val writeOption: WriteOption
  val lockedKeys: Set<JournalKey>

  companion object : EpoqueContextKey<TransactionContext>
}

/** Controls how command handlers are executed for each [JournalKey] */
enum class WriteOption {
  /** Command handlers for the same [JournalKey] can run in parallel but may fail due to conflict on event write */
  DEFAULT,

  /** Ensures that only a single command handler can be processed at a time by reserving event
   * writes for a [JournalKey] */
  JOURNAL_LOCK,
}

fun interface EventWriter {
  suspend fun writeEvents(output: CommandOutput, tx: TransactionContext): Failable<Unit>
}

fun interface JournalChecker {
  suspend fun journalExists(key: JournalKey, tx: TransactionContext): Failable<Boolean>
}

interface EventReader : JournalChecker {
  fun queryById(
    key: JournalKey,
    prevVersion: Version,
    tx: TransactionContext,
  ): Failable<Flow<VersionedEvent>>
}

interface TransactionStarter {
  suspend fun <T> startTransactionAndLock(
    key: JournalKey,
    writeOption: WriteOption,
    block: suspend (tx: TransactionContext) -> T,
  ): Failable<T>

  suspend fun <T> startDefaultTransaction(block: suspend (tx: TransactionContext) -> T): Failable<T>
}

interface EventStore : EventReader, EventWriter, TransactionStarter
