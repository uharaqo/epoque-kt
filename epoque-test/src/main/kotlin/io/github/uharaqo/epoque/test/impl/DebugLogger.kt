package io.github.uharaqo.epoque.test.impl

import io.github.uharaqo.epoque.api.CallbackHandler
import io.github.uharaqo.epoque.api.CommandContext
import io.github.uharaqo.epoque.api.CommandOutput
import org.slf4j.LoggerFactory

class DebugLogger : CallbackHandler {
  private val logger = LoggerFactory.getLogger(DebugLogger::class.java)

  override suspend fun beforeBegin(context: CommandContext) {
    if (logger.isDebugEnabled) logger.debug("> BeforeBegin: $context")
  }

  override suspend fun afterBegin(context: CommandContext) {
    if (logger.isDebugEnabled) logger.debug("> AfterBegin")
  }

  override suspend fun beforeCommit(context: CommandContext, output: CommandOutput) {
    if (logger.isDebugEnabled) logger.debug("> BeforeCommit: $output")
  }

  override suspend fun afterCommit(context: CommandContext, output: CommandOutput) {
    if (logger.isDebugEnabled) logger.debug("> AfterCommit")
  }

  override suspend fun afterRollback(context: CommandContext, error: Throwable) {
    logger.error("> AfterRollback", error)
  }
}
