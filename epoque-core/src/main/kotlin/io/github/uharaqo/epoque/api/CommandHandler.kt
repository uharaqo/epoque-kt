package io.github.uharaqo.epoque.api

import arrow.core.flatMap

fun interface CommandHandler<C, S, E : Any> {
  suspend fun handle(c: C, s: S): CommandHandlerOutput<E>
}

fun interface CommandProcessor {
  suspend fun process(input: CommandInput): Failable<CommandOutput>
}

@JvmInline
value class CommandProcessorRegistry(
  private val registry: Registry<CommandType, CommandProcessor>,
) : Registry<CommandType, CommandProcessor> by registry

interface CommandRouter : CommandProcessor {
  val commandCodecRegistry: CommandCodecRegistry
  val commandProcessorRegistry: CommandProcessorRegistry

  override suspend fun process(input: CommandInput): Failable<CommandOutput> =
    EpoqueContext.with({ putIfAbsent(CommandRouter) { this@CommandRouter } }) {
      commandProcessorRegistry.find(input.type).flatMap { it.process(input) }
    }

  companion object : EpoqueContextKey<CommandRouter>
}
