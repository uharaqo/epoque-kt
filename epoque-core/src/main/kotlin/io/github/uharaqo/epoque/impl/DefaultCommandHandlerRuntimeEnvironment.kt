package io.github.uharaqo.epoque.impl

import arrow.core.flatMap
import arrow.core.getOrElse
import io.github.uharaqo.epoque.api.CommandContext
import io.github.uharaqo.epoque.api.CommandExecutorOptions
import io.github.uharaqo.epoque.api.CommandHandler
import io.github.uharaqo.epoque.api.CommandHandlerOutput
import io.github.uharaqo.epoque.api.CommandInput
import io.github.uharaqo.epoque.api.CommandOutput
import io.github.uharaqo.epoque.api.CommandRouter
import io.github.uharaqo.epoque.api.CommandType
import io.github.uharaqo.epoque.api.EpoqueContext
import io.github.uharaqo.epoque.api.EpoqueContextKey
import io.github.uharaqo.epoque.api.EpoqueEnvironment
import io.github.uharaqo.epoque.api.EpoqueException
import io.github.uharaqo.epoque.api.Failable
import io.github.uharaqo.epoque.api.JournalChecker
import io.github.uharaqo.epoque.api.JournalId
import io.github.uharaqo.epoque.api.JournalKey
import io.github.uharaqo.epoque.api.TransactionContext
import io.github.uharaqo.epoque.api.asOutputMetadata
import io.github.uharaqo.epoque.builder.CommandHandlerOutputCollector
import io.github.uharaqo.epoque.builder.CommandHandlerRuntimeEnvironment
import io.github.uharaqo.epoque.builder.CommandHandlerRuntimeEnvironmentFactory
import io.github.uharaqo.epoque.builder.CommandHandlerRuntimeEnvironmentFactoryFactory
import io.github.uharaqo.epoque.builder.CommandHandlerSideEffectHandler
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

interface PreparedCommandHandler<C, S, E> : CommandHandler<C, S, E> {
  val prepare: suspend (c: C) -> Any?
}

object DeserializedCommand : EpoqueContextKey<Any>

class DefaultCommandHandlerRuntimeEnvironmentFactoryFactory :
  CommandHandlerRuntimeEnvironmentFactoryFactory {

  override fun create(
    commandRouter: CommandRouter,
    environment: EpoqueEnvironment,
  ): CommandHandlerRuntimeEnvironmentFactory<Any, Any?, Any> {
    return DefaultCommandHandlerRuntimeEnvironmentFactory(commandRouter, environment.eventReader)
  }
}

class DefaultCommandHandlerRuntimeEnvironmentFactory(
  private val commandRouter: CommandRouter,
  private val journalChecker: JournalChecker,
) : CommandHandlerRuntimeEnvironmentFactory<Any, Any?, Any>, CommandRouter by commandRouter {

  override suspend fun create(): CommandHandlerRuntimeEnvironment<Any, Any?, Any> {
    val outputCollector = DefaultCommandHandlerOutputCollector<Any>()
    val sideEffectHandler = DefaultCommandHandlerSideEffectHandler(journalChecker, commandRouter)
    return DefaultCommandHandlerRuntimeEnvironment(outputCollector, sideEffectHandler)
  }

  override suspend fun process(input: CommandInput): Failable<CommandOutput> {
    val workflow = create()
    return EpoqueContext.with({ put(CommandHandlerRuntimeEnvironment, workflow) }) {
      commandRouter.process(input)
    }
  }
}

class DefaultCommandHandlerRuntimeEnvironment(
  private val outputCollector: CommandHandlerOutputCollector<Any>,
  private val sideEffectHandler: CommandHandlerSideEffectHandler,
) :
  CommandHandlerRuntimeEnvironment<Any, Any?, Any>,
  CommandHandlerOutputCollector<Any> by outputCollector,
  CommandHandlerSideEffectHandler by sideEffectHandler {

  private lateinit var x: Optional<Any>

  override val preparedParam: Any?
    get() = x.orElse(null)

  override suspend fun beforeBegin(context: CommandContext) {
    @Suppress("UNCHECKED_CAST")
    val commandHandler = CommandHandler.get()?.let { it as CommandHandler<Any?, *, *> }
    x =
      if (commandHandler !is PreparedCommandHandler<Any?, *, *>) {
        Optional.empty()
      } else {
        try {
          Optional.ofNullable(commandHandler.prepare.invoke(DeserializedCommand.get()))
        } catch (e: Exception) {
          throw EpoqueException.Cause.COMMAND_PREPARATION_FAILURE.toException(e)
        }
      }
  }

  override suspend fun beforeCommit(output: CommandOutput) {
    sideEffectHandler.chainedCommands.forEach { command ->
      println("---")
      println("TODO: execute this: $command")
      println("---")
    }
  }

  override suspend fun afterCommit(output: CommandOutput) {
    // TODO: exception should be caught?
    sideEffectHandler.notifications.forEach { it.invoke() }
  }
}

/** Created for each command request to collect outputs */
class DefaultCommandHandlerOutputCollector<E> : CommandHandlerOutputCollector<E> {
  private val events = ConcurrentLinkedQueue<E>()
  private var metadata = mutableMapOf<Any, Any>()

  override fun emit(events: List<E>, metadata: Map<Any, Any>) {
    this.events += events
    this.metadata += metadata
  }

  override fun complete(): CommandHandlerOutput<E> {
    return CommandHandlerOutput(events.toList(), metadata.asOutputMetadata())
  }
}

class DefaultCommandHandlerSideEffectHandler(
  private val journalChecker: JournalChecker,
  private val router: CommandRouter,
) : CommandHandlerSideEffectHandler {
  private val _chainedCommands = ConcurrentLinkedQueue<CommandInput>()
  private val _notifications = ConcurrentLinkedQueue<suspend () -> Unit>()

  override val chainedCommands: List<CommandInput>
    get() = _chainedCommands.toList()
  override val notifications: List<suspend () -> Unit>
    get() = _notifications.toList()

  override suspend fun exists(key: JournalKey): Boolean =
    journalChecker.journalExists(key, TransactionContext.get()!!).getOrElse { throw it }

  override fun notify(block: suspend () -> Unit) {
    _notifications += block
  }

  override fun chain(
    id: JournalId,
    command: Any,
    options: CommandExecutorOptions,
    metadata: Map<Any, Any>,
  ) {
    val type = CommandType.of(command::class.java)
    val serialized =
      router.commandCodecRegistry.find<Any>(type).flatMap { it.encode(command) }
        .getOrElse { throw it }
    _chainedCommands += CommandInput(id, type, serialized, metadata, options)
  }
}
