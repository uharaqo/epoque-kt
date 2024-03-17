package io.github.uharaqo.epoque.impl

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import io.github.uharaqo.epoque.api.CanExecuteCommandHandler
import io.github.uharaqo.epoque.api.CanProcessCommand
import io.github.uharaqo.epoque.api.CommandCodec
import io.github.uharaqo.epoque.api.CommandHandler
import io.github.uharaqo.epoque.api.CommandInput
import io.github.uharaqo.epoque.api.CommandProcessor
import io.github.uharaqo.epoque.api.CommandRouter
import io.github.uharaqo.epoque.api.CommandType
import io.github.uharaqo.epoque.api.EpoqueException.UnexpectedCommand
import io.github.uharaqo.epoque.api.EventCodecRegistry
import io.github.uharaqo.epoque.api.EventHandlerExecutor
import io.github.uharaqo.epoque.api.EventLoader
import io.github.uharaqo.epoque.api.EventWriter
import io.github.uharaqo.epoque.api.JournalGroupId
import io.github.uharaqo.epoque.api.TransactionStarter

class DefaultCommandRouter(
  private val map: Map<CommandType, CommandProcessor>,
) : CommandRouter {
  override fun get(input: CommandInput): Either<UnexpectedCommand, CommandProcessor> = either {
    ensureNotNull(map[input.type]) {
      UnexpectedCommand("Unknown command type: ${input.type}")
    }
  }

  fun plus(other: DefaultCommandRouter): DefaultCommandRouter =
    DefaultCommandRouter(this.map + other.map)
}

class TypedCommandProcessor<C>(
  override val commandCodec: CommandCodec<C>,
  override val canExecuteCommandHandler: CanExecuteCommandHandler<C, *, *>,
) : CanProcessCommand<C>

class CommandExecutor<C, S, E : Any>(
  override val journalGroupId: JournalGroupId,
  override val commandHandler: CommandHandler<C, S, E>,
  override val eventCodecRegistry: EventCodecRegistry<E>,
  override val eventHandlerExecutor: EventHandlerExecutor<S>,
  override val eventLoader: EventLoader,
  override val eventWriter: EventWriter,
  transactionStarter: TransactionStarter,
) : CanExecuteCommandHandler<C, S, E>, TransactionStarter by transactionStarter
