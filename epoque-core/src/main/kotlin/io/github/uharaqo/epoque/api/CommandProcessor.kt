package io.github.uharaqo.epoque.api

import arrow.core.left
import arrow.core.raise.either
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
  val commandProcessorRegistry: CommandProcessorRegistry
  val commandCodecRegistry: CommandCodecRegistry
  val eventCodecRegistry: EventCodecRegistry

  override suspend fun process(input: CommandInput): Failable<CommandOutput> = either {
    val processor = commandProcessorRegistry.find(input.type).bind()
    EpoqueContext.with(
      {
        put(CommandRouter, this@CommandRouter)
        put(CommandInput, input)
      },
    ) {
      processor.process(input).bind()
    }
  }

  companion object : EpoqueContextKey<CommandRouter>
}
