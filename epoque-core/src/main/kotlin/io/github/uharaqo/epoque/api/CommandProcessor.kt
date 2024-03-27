package io.github.uharaqo.epoque.api

import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import io.github.uharaqo.epoque.api.EpoqueException.Cause.COMMAND_NOT_SUPPORTED

fun interface CommandProcessor {
  suspend fun process(input: CommandInput): Failable<CommandOutput>
}

@JvmInline
value class CommandProcessorRegistry(
  val registry: Map<CommandType, CommandProcessor>,
) {
  fun find(key: CommandType): Failable<CommandProcessor> =
    registry[key]?.right() ?: COMMAND_NOT_SUPPORTED.toException(message = key.toString()).left()
}

interface CommandRouter : CommandProcessor {
  val commandCodecRegistry: CommandCodecRegistry
  val commandProcessorRegistry: CommandProcessorRegistry

  override suspend fun process(input: CommandInput): Failable<CommandOutput> =
    commandProcessorRegistry.find(input.type).flatMap { it.process(input) }

  companion object : EpoqueContextKey<CommandRouter>
}
