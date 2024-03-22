package io.github.uharaqo.epoque.api

interface CallbackHandler {
  /** Starting a transaction. */
  suspend fun beforeBegin(context: CommandContext) {}

  /** Started a transaction. Executing a command. */
  suspend fun afterBegin(context: CommandContext) {}

  /** Committing events. */
  suspend fun beforeCommit(output: CommandOutput) {}

  /** Committed events. */
  suspend fun afterCommit(output: CommandOutput) {}

  /** Rolled back transactions due to an error. */
  suspend fun afterRollback(context: CommandContext, error: Throwable) {}

  operator fun plus(other: CallbackHandler) =
    object : CallbackHandler {
      override suspend fun beforeBegin(context: CommandContext) {
        this@CallbackHandler.beforeBegin(context)
        other.beforeBegin(context)
      }

      override suspend fun afterBegin(context: CommandContext) {
        this@CallbackHandler.afterBegin(context)
        other.afterBegin(context)
      }

      override suspend fun beforeCommit(output: CommandOutput) {
        this@CallbackHandler.beforeCommit(output)
        other.beforeCommit(output)
      }

      override suspend fun afterCommit(output: CommandOutput) {
        this@CallbackHandler.afterCommit(output)
        other.afterCommit(output)
      }

      override suspend fun afterRollback(context: CommandContext, error: Throwable) {
        this@CallbackHandler.afterRollback(context, error)
        other.afterRollback(context, error)
      }
    }
}
