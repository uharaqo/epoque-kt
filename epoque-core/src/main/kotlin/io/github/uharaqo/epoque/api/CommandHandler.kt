package io.github.uharaqo.epoque.api

import arrow.core.Either
import arrow.core.left
import io.github.uharaqo.epoque.api.EpoqueException.CommandHandlerFailure
import io.github.uharaqo.epoque.api.EpoqueException.CommandRouterFailure

interface CommandHandler<C, S, E : Any> {
  fun handle(command: C, summary: S): Either<CommandHandlerFailure, List<E>>
}

interface CommandProcessor {
  suspend fun process(input: CommandInput): Either<EpoqueException, CommandOutput>
}

interface CommandRouter : CommandProcessor {
  operator fun get(input: CommandInput): CommandProcessor?

  override suspend fun process(input: CommandInput): Either<EpoqueException, CommandOutput> =
    get(input)?.process(input)
      ?: CommandRouterFailure("Unknown command type: ${input.type}").left()
}
