package io.github.uharaqo.epoque.impl

import io.github.uharaqo.epoque.api.CanExecuteCommandHandler
import io.github.uharaqo.epoque.api.CanProcessCommand
import io.github.uharaqo.epoque.api.CommandCodec
import io.github.uharaqo.epoque.api.CommandExecutorOptions
import io.github.uharaqo.epoque.api.CommandHandler
import io.github.uharaqo.epoque.api.CommandProcessor
import io.github.uharaqo.epoque.api.CommandProcessorRegistry
import io.github.uharaqo.epoque.api.CommandRouter
import io.github.uharaqo.epoque.api.CommandType
import io.github.uharaqo.epoque.api.EpoqueException.UnexpectedCommand
import io.github.uharaqo.epoque.api.EventCodecRegistry
import io.github.uharaqo.epoque.api.EventHandlerExecutor
import io.github.uharaqo.epoque.api.EventLoader
import io.github.uharaqo.epoque.api.EventWriter
import io.github.uharaqo.epoque.api.JournalGroupId
import io.github.uharaqo.epoque.api.TransactionStarter

class CommandRouterBuilder {
  private val registry =
    Registry.builder<CommandType, CommandProcessor, UnexpectedCommand> { UnexpectedCommand("CommandProcessor not found: $it") }

  inline fun <reified C : Any> processorFor(commandProcessor: CommandProcessor): CommandRouterBuilder =
    register(CommandType.of<C>(), commandProcessor)

  fun register(commandType: CommandType, commandProcessor: CommandProcessor): CommandRouterBuilder =
    this.also { registry[commandType] = commandProcessor }

  fun build(): CommandRouter = DefaultCommandRouter(CommandProcessorRegistry(registry.build()))

  private inner class DefaultCommandRouter(
    override val commandProcessorRegistry: CommandProcessorRegistry,
  ) : CommandRouter
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
  override val defaultCommandExecutorOptions: CommandExecutorOptions?,
) : CanExecuteCommandHandler<C, S, E>, TransactionStarter by transactionStarter
