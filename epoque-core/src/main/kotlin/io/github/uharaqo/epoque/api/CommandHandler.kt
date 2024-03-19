package io.github.uharaqo.epoque.api

import arrow.core.flatMap
import io.github.uharaqo.epoque.impl.Registry

interface CommandHandler<C, S, E : Any> {
  fun handle(command: C, summary: S): Failable<List<E>>
}

interface CommandProcessor {
  suspend fun process(input: CommandInput): Failable<CommandOutput>
}

@JvmInline
value class CommandProcessorRegistry(
  private val registry: Registry<CommandType, CommandProcessor>,
) : Registry<CommandType, CommandProcessor> by registry

interface CommandRouter : CommandProcessor {
  val commandProcessorRegistry: CommandProcessorRegistry

  override suspend fun process(input: CommandInput): Failable<CommandOutput> =
    commandProcessorRegistry.find(input.type).flatMap { it.process(input) }
}
