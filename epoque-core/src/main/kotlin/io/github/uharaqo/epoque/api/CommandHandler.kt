package io.github.uharaqo.epoque.api

import arrow.core.Either
import arrow.core.flatMap
import io.github.uharaqo.epoque.api.EpoqueException.CommandHandlerFailure
import io.github.uharaqo.epoque.api.EpoqueException.UnexpectedCommand

interface CommandHandler<C, S, E : Any> {
  fun handle(command: C, summary: S): Either<CommandHandlerFailure, List<E>>
}

interface CommandProcessor {
  suspend fun process(input: CommandInput): Either<EpoqueException, CommandOutput>
}

interface CommandRouter : CommandProcessor {
  operator fun get(input: CommandInput): Either<UnexpectedCommand, CommandProcessor>

  override suspend fun process(input: CommandInput): Either<EpoqueException, CommandOutput> =
    get(input).flatMap { it.process(input) }
}
