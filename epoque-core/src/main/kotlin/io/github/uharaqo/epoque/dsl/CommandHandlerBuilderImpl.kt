package io.github.uharaqo.epoque.dsl

import arrow.core.flatMap
import arrow.core.getOrElse
import io.github.uharaqo.epoque.api.CallbackHandler
import io.github.uharaqo.epoque.api.CommandCodec
import io.github.uharaqo.epoque.api.CommandCodecRegistry
import io.github.uharaqo.epoque.api.CommandContext
import io.github.uharaqo.epoque.api.CommandExecutorOptions
import io.github.uharaqo.epoque.api.CommandHandler
import io.github.uharaqo.epoque.api.CommandHandlerOutput
import io.github.uharaqo.epoque.api.CommandInput
import io.github.uharaqo.epoque.api.CommandOutput
import io.github.uharaqo.epoque.api.CommandProcessor
import io.github.uharaqo.epoque.api.CommandProcessorRegistry
import io.github.uharaqo.epoque.api.CommandRouter
import io.github.uharaqo.epoque.api.CommandType
import io.github.uharaqo.epoque.api.DataCodecFactory
import io.github.uharaqo.epoque.api.EpoqueEnvironment
import io.github.uharaqo.epoque.api.EpoqueException.Cause.COMMAND_NOT_SUPPORTED
import io.github.uharaqo.epoque.api.EpoqueException.Cause.COMMAND_PREPARATION_FAILURE
import io.github.uharaqo.epoque.api.EventCodecRegistry
import io.github.uharaqo.epoque.api.EventHandlerRegistry
import io.github.uharaqo.epoque.api.Journal
import io.github.uharaqo.epoque.api.JournalChecker
import io.github.uharaqo.epoque.api.JournalId
import io.github.uharaqo.epoque.api.JournalKey
import io.github.uharaqo.epoque.api.TransactionContext
import io.github.uharaqo.epoque.api.WriteOption
import io.github.uharaqo.epoque.api.asOutputMetadata
import io.github.uharaqo.epoque.impl.CommandExecutor
import io.github.uharaqo.epoque.impl.CommandExecutorFactory
import io.github.uharaqo.epoque.impl.DefaultRegistry
import io.github.uharaqo.epoque.impl.toCommandCodec
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference

data class CommandHandlerEntry<C, S, E>(
  val codec: CommandCodec<C>,
  val writeOption: WriteOption,
  val prepare: (suspend () -> Any?)?,
  val handler: suspend CommandHandlerRuntimeEnvironment<C, S, E>.(C, S) -> Unit,
  val projections: List<suspend TransactionContext.() -> Unit>,
  val notifications: List<suspend NotificationContext.() -> Unit>,
)

class CommandHandlersBuilder<C : Any, S, E : Any>(
  private val defaultWriteOption: WriteOption,
) : CommandHandlersDsl<C, S, E>() {

  private val handlers = mutableMapOf<CommandType, CommandHandlerBuilder<C, S, E>>()

  override fun <CC : C> onCommand(
    writeOption: WriteOption?,
    type: CommandType,
    block: @CommandHandlersDslMarker (CommandHandlerDsl<CC, S, E>.() -> Unit),
  ) {
    @Suppress("UNCHECKED_CAST")
    handlers += type to
      CommandHandlerBuilder<CC, S, E>(type, writeOption)
      .apply(block) as CommandHandlerBuilder<C, S, E>
  }

  fun build(codecFactory: DataCodecFactory): Map<CommandType, CommandHandlerEntry<C, S, E>> =
    handlers.mapValues { it.value.build(codecFactory, defaultWriteOption) }
}

class CommandHandlerBuilder<C, S, E>(
  private val type: CommandType,
  private val writeOption: WriteOption?,
) : CommandHandlerDsl<C, S, E>() {
  private lateinit var handler: suspend CommandHandlerRuntimeEnvironment<C, S, E>.(C, S) -> Unit
  private val projections = mutableListOf<suspend TransactionContext.() -> Unit>()
  private val notifications = mutableListOf<suspend NotificationContext.() -> Unit>()
  private var prepare: (suspend () -> Any?)? = null

  override fun handle(block: suspend CommandHandlerRuntimeEnvironment<C, S, E>.(C, S) -> Unit) {
    handler = block
  }

  override fun <X> prepare(block: suspend () -> X): PreparedCommandHandlerDsl<C, S, E, X> {
    val x = AtomicReference<X>()
    prepare = suspend { x.set(block()) }
    return PreparedCommandHandlerBuilder(x) { handler = it }
  }

  override fun project(block: suspend TransactionContext.() -> Unit) {
    projections += block
  }

  override fun notify(block: suspend NotificationContext.() -> Unit) {
    notifications += block
  }

  fun build(
    codecFactory: DataCodecFactory,
    defaultWriteOption: WriteOption,
  ): CommandHandlerEntry<C, S, E> =
    @Suppress("UNCHECKED_CAST")
    CommandHandlerEntry(
      codecFactory.create(type.unwrap).toCommandCodec() as CommandCodec<C>,
      writeOption ?: defaultWriteOption,
      prepare,
      handler,
      projections,
      notifications,
    )
}

fun List<EpoqueJournal<*, *, *>>.toCommandCodecRegistry(): CommandCodecRegistry =
  this.asSequence()
    .flatMap { it.commandHandlers.entries }
    .associate { (type, h) -> type to h.codec }
    .let { map -> DefaultRegistry(map) { COMMAND_NOT_SUPPORTED.toException(message = it.toString()) } }
    .let(::CommandCodecRegistry)

fun List<EpoqueJournal<*, *, *>>.toCommandProcessorRegistry(
  commandCodecs: CommandCodecRegistry,
  eventCodecs: EventCodecRegistry,
  eventHandlers: EventHandlerRegistry<Any?, Any>,
  environment: EpoqueEnvironment,
): CommandProcessorRegistry =
  this.asSequence()
    .flatMap { j ->
      @Suppress("UNCHECKED_CAST")
      val journal =
        (j as EpoqueJournal<*, Any?, Any>).toJournal(eventCodecs, eventHandlers)

      j.commandHandlers.entries.asSequence().map { (type, entry) ->
        type to newCommandProcessor(environment, journal, commandCodecs, entry)
      }
    }
    .toMap()
    .let { map -> DefaultRegistry(map) { COMMAND_NOT_SUPPORTED.toException(message = it.toString()) } }
    .let { CommandProcessorRegistry(it) }

object DefaultNotificationContext : NotificationContext

private fun <C, S, E> newCommandProcessor(
  environment: EpoqueEnvironment,
  journal: Journal<S, E>,
  commandCodecs: CommandCodecRegistry,
  entry: CommandHandlerEntry<C, S, E>,
): CommandProcessor =
  DefaultCommandExecutorFactory(entry, journal, commandCodecs, environment)

class DefaultCommandExecutorFactory<C, S, E>(
  private val entry: CommandHandlerEntry<C, S, E>,
  private val journal: Journal<S, E>,
  private val commandCodecs: CommandCodecRegistry,
  private val environment: EpoqueEnvironment,
) : CommandExecutorFactory<C, S, E> {

  override suspend fun create(): CommandExecutor<C, S, E> {
    val env = Env(entry, commandCodecs, environment.eventReader)
    val callbackHandler = (environment.callbackHandler ?: CallbackHandler.EMPTY) + env

    return CommandExecutor(
      journalGroupId = journal.journalGroupId,
      commandDecoder = entry.codec,
      commandHandler = env,
      callbackHandler = callbackHandler,
      eventCodecRegistry = journal.eventCodecRegistry,
      eventHandlerExecutor = journal,
      eventReader = environment.eventReader,
      eventWriter = environment.eventWriter,
      transactionStarter = environment.transactionStarter,
      defaultCommandExecutorOptions = environment.defaultCommandExecutorOptions,
    )
  }
}

class Env<C, S, E>(
  private val entry: CommandHandlerEntry<C, S, E>,
  private val commandCodecRegistry: CommandCodecRegistry,
  private val journalChecker: JournalChecker,
) : CommandHandler<C, S, E>, CommandHandlerRuntimeEnvironment<C, S, E>, CallbackHandler {

  private val events = ConcurrentLinkedQueue<E>()
  private val metadata = ConcurrentHashMap<Any, Any>()
  private val chainedCommands = ConcurrentLinkedQueue<CommandInput>()

  override suspend fun handle(c: C, s: S): CommandHandlerOutput<E> {
    entry.handler(this, c, s)
    return complete()
  }

  override fun chain(
    id: JournalId,
    command: Any,
    options: CommandExecutorOptions,
    metadata: Map<Any, Any>,
  ) {
    val type = CommandType.of(command::class.java)
    val serialized =
      commandCodecRegistry.find<Any>(type).flatMap { it.encode(command) }.getOrElse { throw it }
    chainedCommands += CommandInput(id, type, serialized, metadata, options)
  }

  override fun emit(events: List<E>, metadata: Map<Any, Any>) {
    this.events += events
    this.metadata += metadata
  }

  override suspend fun exists(key: JournalKey?): Boolean =
    if (key == null) {
      false
    } else {
      journalChecker.journalExists(key, TransactionContext.get()!!).getOrElse { throw it }
    }

  override fun complete(): CommandHandlerOutput<E> =
    CommandHandlerOutput(events.toList(), metadata.asOutputMetadata())

  override suspend fun beforeBegin(context: CommandContext) {
    entry.prepare?.let {
      try {
        it.invoke()
      } catch (e: Exception) {
        throw COMMAND_PREPARATION_FAILURE.toException(e)
      }
    }
  }

  override suspend fun beforeCommit(output: CommandOutput) {
//    TODO: projections

    chainedCommands.forEach { command ->
      val commandRouter = CommandRouter.get()!!
      // TODO: result handling
      commandRouter.process(command).getOrElse { throw it }
    }
  }

  override suspend fun afterCommit(output: CommandOutput) {
    // TODO: exception should be caught?
    entry.notifications.forEach { it.invoke(DefaultNotificationContext) }
  }
}
