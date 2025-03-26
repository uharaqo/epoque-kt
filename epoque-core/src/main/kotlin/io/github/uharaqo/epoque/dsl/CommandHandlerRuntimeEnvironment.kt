package io.github.uharaqo.epoque.dsl

import arrow.core.flatMap
import arrow.core.getOrElse
import io.github.uharaqo.epoque.api.CallbackHandler
import io.github.uharaqo.epoque.api.CommandCodecRegistry
import io.github.uharaqo.epoque.api.CommandContext
import io.github.uharaqo.epoque.api.CommandExecutorOptions
import io.github.uharaqo.epoque.api.CommandHandler
import io.github.uharaqo.epoque.api.CommandHandlerOutput
import io.github.uharaqo.epoque.api.CommandInput
import io.github.uharaqo.epoque.api.CommandOutput
import io.github.uharaqo.epoque.api.CommandRouter
import io.github.uharaqo.epoque.api.CommandType
import io.github.uharaqo.epoque.api.EpoqueEnvironment
import io.github.uharaqo.epoque.api.EpoqueException.Cause.CHAINED_COMMAND_FAILURE
import io.github.uharaqo.epoque.api.EpoqueException.Cause.COMMAND_PREPARATION_FAILURE
import io.github.uharaqo.epoque.api.EpoqueException.Cause.NOTIFICATION_FAILURE
import io.github.uharaqo.epoque.api.EpoqueException.Cause.PROJECTION_FAILURE
import io.github.uharaqo.epoque.api.Journal
import io.github.uharaqo.epoque.api.JournalChecker
import io.github.uharaqo.epoque.api.JournalId
import io.github.uharaqo.epoque.api.JournalKey
import io.github.uharaqo.epoque.api.TransactionContext
import io.github.uharaqo.epoque.api.asOutputMetadata
import io.github.uharaqo.epoque.dsl.CommandExecutor.Companion.CHAINED_COMMAND_OUTPUTS_METADATA_KEY
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

class DefaultCommandHandlerRuntimeEnvironment<C, S, E>(
  commandHandlerSetup: CommandHandlerSetup<C, S, E>,
  private val commandCodecRegistry: CommandCodecRegistry,
  private val journalChecker: JournalChecker,
) : CommandHandler<C, S, E>, CommandHandlerRuntimeEnvironment<C, S, E>, CallbackHandler {

  private val handler = commandHandlerSetup.handler
  private val prepare = commandHandlerSetup.prepare
  private val projections = commandHandlerSetup.projections
  private val notifications = commandHandlerSetup.notifications

  private val events = ConcurrentLinkedQueue<E>()
  private val metadata = ConcurrentHashMap<Any, Any>()
  private val chainedCommands = ConcurrentLinkedQueue<CommandInput>()

  override suspend fun handle(c: C, s: S): CommandHandlerOutput<E> {
    handler(this, c, s)
    return complete()
  }

  override fun chain(
    id: JournalId,
    command: Any,
    options: CommandExecutorOptions,
    metadata: Map<out Any, Any>,
  ) {
    val type = CommandType.of(command::class.java)
    val serialized =
      commandCodecRegistry.find<Any>(type).flatMap { it.encode(command) }.getOrElse { throw it }
    chain(CommandInput(id, type, serialized, metadata, options))
  }

  override fun chain(input: CommandInput) {
    chainedCommands += input
  }

  override fun emit(events: List<E>, metadata: Map<Any, Any>) {
    this.events += events
    this.metadata += metadata
  }

  override suspend fun exists(key: JournalKey?): Boolean {
    if (key == null) return false
    return journalChecker.journalExists(key, TransactionContext.get()!!).getOrElse { throw it }
  }

  fun complete(): CommandHandlerOutput<E> =
    CommandHandlerOutput(events.toList(), metadata.asOutputMetadata())

  override suspend fun beforeBegin(context: CommandContext) {
    prepare?.let {
      try {
        it.invoke()
      } catch (e: Exception) {
        throw COMMAND_PREPARATION_FAILURE.toException(e)
      }
    }
  }

  override suspend fun beforeCommit(output: CommandOutput) {
    projections.forEach {
      try {
        it.invoke(TransactionContext.get()!!)
      } catch (e: Exception) {
        throw PROJECTION_FAILURE.toException(e)
      }
    }

    chainedCommands
      .map { input ->
        CommandRouter.get()!!.process(input)
          .getOrElse { e -> throw CHAINED_COMMAND_FAILURE.toException(e, input.type.toString()) }
      }
      .let { metadata.put(CHAINED_COMMAND_OUTPUTS_METADATA_KEY, it) }
  }

  override suspend fun afterCommit(output: CommandOutput) {
    notifications.forEach {
      try {
        it.invoke(DefaultNotificationContext)
      } catch (e: Exception) {
        throw NOTIFICATION_FAILURE.toException(e)
      }
    }
  }
}

object DefaultNotificationContext : NotificationContext

class DefaultCommandExecutorFactory<C, S, E>(
  private val commandHandlerSetup: CommandHandlerSetup<C, S, E>,
  private val journal: Journal<*, S, E>,
  private val commandCodecs: CommandCodecRegistry,
  private val environment: EpoqueEnvironment,
) : CommandExecutorFactory<C, S, E> {

  override fun create(): CommandExecutor<C, S, E> {
    val env =
      DefaultCommandHandlerRuntimeEnvironment(
        commandHandlerSetup,
        commandCodecs,
        environment.eventReader,
      )
    val callbackHandler = (environment.globalCallbackHandler ?: CallbackHandler.EMPTY) + env

    return CommandExecutor(
      journalGroupId = journal.journalGroupId,
      summaryType = journal.summaryType,
      commandDecoder = commandHandlerSetup.codec,
      commandHandler = env,
      callbackHandler = callbackHandler,
      eventCodecRegistry = journal.eventCodecRegistry,
      eventAggregator = journal,
      eventReader = environment.eventReader,
      eventWriter = environment.eventWriter,
      transactionStarter = environment.transactionStarter,
      cache = journal.summaryCache ?: environment.globalCache,
      defaultCommandExecutorOptions = environment.defaultCommandExecutorOptions,
    )
  }
}
