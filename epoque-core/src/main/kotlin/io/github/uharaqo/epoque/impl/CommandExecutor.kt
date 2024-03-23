package io.github.uharaqo.epoque.impl

import io.github.uharaqo.epoque.api.CallbackHandler
import io.github.uharaqo.epoque.api.CanExecuteCommandHandler
import io.github.uharaqo.epoque.api.CanProcessCommand
import io.github.uharaqo.epoque.api.CommandDecoder
import io.github.uharaqo.epoque.api.CommandExecutorOptions
import io.github.uharaqo.epoque.api.CommandHandler
import io.github.uharaqo.epoque.api.EpoqueEnvironment
import io.github.uharaqo.epoque.api.EventCodecRegistry
import io.github.uharaqo.epoque.api.EventHandlerExecutor
import io.github.uharaqo.epoque.api.EventReader
import io.github.uharaqo.epoque.api.EventWriter
import io.github.uharaqo.epoque.api.Journal
import io.github.uharaqo.epoque.api.JournalGroupId
import io.github.uharaqo.epoque.api.TransactionStarter

fun interface CommandHandlerFactory<C, S, E : Any> {
  fun create(environment: EpoqueEnvironment): CommandHandler<C, S, E>
}

class CommandExecutor<C, S, E : Any>(
  override val journalGroupId: JournalGroupId,
  override val commandHandler: CommandHandler<C, S, E>,
  override val eventCodecRegistry: EventCodecRegistry,
  override val eventHandlerExecutor: EventHandlerExecutor<S>,
  override val eventReader: EventReader,
  override val eventWriter: EventWriter,
  transactionStarter: TransactionStarter,
  override val defaultCommandExecutorOptions: CommandExecutorOptions?,
  override val callbackHandler: CallbackHandler?,
) : CanExecuteCommandHandler<C, S, E>, TransactionStarter by transactionStarter {

  companion object {
    fun <C : Any, S, E : Any> create(
      journal: Journal<S, E>,
      commandHandlerFactory: CommandHandlerFactory<C, S, E>,
      environment: EpoqueEnvironment,
    ): CommandExecutor<C, S, E> =
      CommandExecutor(
        journalGroupId = journal.journalGroupId,
        commandHandler = commandHandlerFactory.create(environment),
        eventCodecRegistry = journal.eventCodecRegistry,
        eventHandlerExecutor = journal.toEventHandlerExecutor(),
        eventReader = environment.eventReader,
        eventWriter = environment.eventWriter,
        transactionStarter = environment.transactionStarter,
        defaultCommandExecutorOptions = environment.defaultCommandExecutorOptions,
        callbackHandler = environment.callbackHandler,
      )
  }
}

class TypedCommandProcessor<C>(
  override val commandDecoder: CommandDecoder<C>,
  override val executor: CanExecuteCommandHandler<C, *, *>,
) : CanProcessCommand<C>
