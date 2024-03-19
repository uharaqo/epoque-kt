package io.github.uharaqo.epoque.api

interface CallbackHandler {
  /** Starting a transaction. */
  suspend fun beforeBegin(context: CommandContext)

  /** Started a transaction. Executing a command. */
  suspend fun afterBegin(context: CommandContext)

  /** Committing events. */
  suspend fun beforeCommit(context: CommandContext, output: CommandOutput)

  /** Committed events. */
  suspend fun afterCommit(context: CommandContext, output: CommandOutput)

  /** Rolled back transactions due to an error. */
  suspend fun afterRollback(context: CommandContext, error: Throwable)
}
