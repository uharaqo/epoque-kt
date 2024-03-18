package io.github.uharaqo.epoque.api

import arrow.core.Either
import arrow.core.flatMap
import io.github.uharaqo.epoque.api.EpoqueException.CommandHandlerFailure
import io.github.uharaqo.epoque.api.EpoqueException.UnexpectedCommand
import io.github.uharaqo.epoque.impl.Registry

interface CommandHandler<C, S, E : Any> {
  fun handle(command: C, summary: S): Either<CommandHandlerFailure, List<E>>
}

interface CommandProcessor {
  suspend fun process(input: CommandInput): Either<EpoqueException, CommandOutput>
}

@JvmInline
value class CommandProcessorRegistry(
  private val registry: Registry<CommandType, CommandProcessor, UnexpectedCommand>,
) : Registry<CommandType, CommandProcessor, UnexpectedCommand> by registry

interface CommandRouter : CommandProcessor {
  val commandProcessorRegistry: CommandProcessorRegistry

  override suspend fun process(input: CommandInput): Either<EpoqueException, CommandOutput> =
    commandProcessorRegistry.find(input.type).flatMap { it.process(input) }
}
