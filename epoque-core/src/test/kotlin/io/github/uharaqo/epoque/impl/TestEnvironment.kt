package io.github.uharaqo.epoque.impl

import arrow.core.right
import io.github.uharaqo.epoque.Epoque
import io.github.uharaqo.epoque.api.CallbackHandler
import io.github.uharaqo.epoque.api.CommandCodec
import io.github.uharaqo.epoque.api.CommandContext
import io.github.uharaqo.epoque.api.CommandExecutorOptions
import io.github.uharaqo.epoque.api.CommandHandler
import io.github.uharaqo.epoque.api.CommandHandlerOutput
import io.github.uharaqo.epoque.api.CommandOutput
import io.github.uharaqo.epoque.api.CommandType
import io.github.uharaqo.epoque.api.EpoqueEnvironment
import io.github.uharaqo.epoque.api.EventHandler
import io.github.uharaqo.epoque.api.EventHandlerExecutor
import io.github.uharaqo.epoque.api.EventHandlerRegistry
import io.github.uharaqo.epoque.api.EventReader
import io.github.uharaqo.epoque.api.EventStore
import io.github.uharaqo.epoque.api.EventType
import io.github.uharaqo.epoque.api.EventWriter
import io.github.uharaqo.epoque.api.Failable
import io.github.uharaqo.epoque.api.InputMetadata
import io.github.uharaqo.epoque.api.Journal
import io.github.uharaqo.epoque.api.JournalGroupId
import io.github.uharaqo.epoque.api.JournalId
import io.github.uharaqo.epoque.api.JournalKey
import io.github.uharaqo.epoque.api.OutputMetadata
import io.github.uharaqo.epoque.api.SerializedCommand
import io.github.uharaqo.epoque.api.SerializedEvent
import io.github.uharaqo.epoque.api.TransactionContext
import io.github.uharaqo.epoque.api.TransactionStarter
import io.github.uharaqo.epoque.api.Version
import io.github.uharaqo.epoque.api.VersionedEvent
import io.github.uharaqo.epoque.api.WriteOption
import io.github.uharaqo.epoque.builder.EpoqueRuntimeEnvironmentFactoryFactory
import io.github.uharaqo.epoque.builder.EventCodecRegistryBuilder
import io.github.uharaqo.epoque.builder.RegistryBuilder
import io.github.uharaqo.epoque.builder.toCommandCodec
import io.github.uharaqo.epoque.serialization.JsonCodec
import io.github.uharaqo.epoque.serialization.JsonCodecFactory
import io.github.uharaqo.epoque.serialization.SerializedJson
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.time.Instant

abstract class TestEnvironment {
  val jsonCodecFactory = JsonCodecFactory()

  val epoqueBuilder = Epoque.journalFor<TestEvent>(jsonCodecFactory)

  val TEST_JOURNAL = epoqueBuilder.summaryFor<TestSummary>(TestSummary.Empty) {
    eventHandlerFor<TestEvent.ResourceCreated> { s, e ->
      if (s is TestSummary.Default) {
        TestSummary.Default(s.list + e)
      } else {
        TestSummary.Default(listOf(e))
      }
    }
  }

  val TEST_COMMANDS = epoqueBuilder.with(TEST_JOURNAL).routerFor<TestCommand> {
    commandHandlerFor<TestCommand.Create> { c, s ->
      emit(dummyEvents)
    }
  }

  val dummyJournalKey = JournalKey(JournalGroupId.of<TestEvent>(), JournalId("bar"))
  val serializedEvent1 = SerializedEvent(SerializedJson("""{"name":"1"}"""))
  val serializedEvent2 = SerializedEvent(SerializedJson("""{"name":"2"}"""))
  val serializedCommand = SerializedCommand(SerializedJson("""{"name": "foo"}"""))

  val dummyEventCodecRegistry =
    EventCodecRegistryBuilder<TestEvent>(jsonCodecFactory).register<TestEvent.ResourceCreated>()
      .build()

  val dummyEventType = EventType.of<TestEvent.ResourceCreated>()
  val dummyEventHandler = EventHandler<TestSummary, TestEvent> { summary, event ->
    if (summary !is TestSummary.Default) {
      TestSummary.Default(listOf(event))
    } else {
      summary.copy(summary.list + event)
    }
  }

  val dummyEventHandlerExecutor = object : EventHandlerExecutor<MockSummary> {
    override val emptySummary: MockSummary = MockSummary()

    override fun computeNextSummary(
      prevSummary: MockSummary,
      eventType: EventType,
      event: SerializedEvent,
    ): Failable<MockSummary> = (prevSummary + event).right()
  }

  val dummyEvents = listOf(TestEvent.ResourceCreated("1"), TestEvent.ResourceCreated("2"))
  val dummyOutputMetadata = OutputMetadata.EMPTY
  val dummyCommandHandlerOutput = CommandHandlerOutput(dummyEvents, dummyOutputMetadata)
  val dummyRecords = listOf(
    VersionedEvent(Version(1), dummyEventType, serializedEvent1),
    VersionedEvent(Version(2), dummyEventType, serializedEvent2),
  )
  val dummyOutputEvents = listOf(
    VersionedEvent(Version(3), dummyEventType, serializedEvent1),
    VersionedEvent(Version(4), dummyEventType, serializedEvent2),
  )

  val dummyEventReader = object : EventReader {
    override fun queryById(
      key: JournalKey,
      prevVersion: Version,
      tx: TransactionContext,
    ): Failable<Flow<VersionedEvent>> =
      dummyRecords.asSequence().drop(prevVersion.unwrap.toInt()).asFlow().right()

    override suspend fun journalExists(key: JournalKey, tx: TransactionContext): Failable<Boolean> =
      false.right() // TODO
  }

  val dummyEventWriter = object : EventWriter {
    override suspend fun writeEvents(
      output: CommandOutput,
      tx: TransactionContext,
    ): Failable<Unit> = Unit.right()
  }

  val dummyTransactionContext = object : TransactionContext {
    override val writeOption: WriteOption = WriteOption.DEFAULT
    override val lockedKeys: Set<JournalKey> = emptySet()
  }

  val dummyTransactionStarter = object : TransactionStarter {
    override suspend fun <T> startTransactionAndLock(
      key: JournalKey,
      writeOption: WriteOption,
      block: suspend (tx: TransactionContext) -> T,
    ): Failable<T> = block(dummyTransactionContext).right()

    override suspend fun <T> startDefaultTransaction(block: suspend (tx: TransactionContext) -> T): Failable<T> =
      block(dummyTransactionContext).right()
  }

  val dummyCommandType = CommandType.of<TestCommand.Create>()
  val dummyCommandDecoder = JsonCodec.of<TestCommand.Create>().toCommandCodec()
  val dummyCommandHandler =
    CommandHandler<TestCommand, MockSummary, TestEvent> { c, s ->
      @Suppress("UNCHECKED_CAST")
      dummyCommandHandlerOutput as CommandHandlerOutput<TestEvent>
    }

  val dummyCommandCodecRegistry =
    RegistryBuilder<CommandType, CommandCodec<TestCommand>>().apply {
      val codec = JsonCodec.of<TestCommand.Create>().toCommandCodec()
      @Suppress("UNCHECKED_CAST")
      set(dummyCommandType, codec as CommandCodec<TestCommand>)
    }.build { error(it) }

  val dummyEventHandlerRegistry =
    EventHandlerRegistry(
      RegistryBuilder<EventType, EventHandler<TestSummary, TestEvent>>().apply {
        set(dummyEventType, dummyEventHandler)
      }.build { error(it) },
    )

  val dummyJournal: Journal<TestSummary, TestEvent> = Journal(
    dummyJournalKey.groupId,
    TestSummary.Empty as TestSummary,
    dummyEventHandlerRegistry,
    dummyEventCodecRegistry,
  )

  val dummyReceivedTime = Instant.now()
  val dummyCommandContext = CommandContext(
    dummyJournalKey,
    dummyCommandType,
    serializedCommand,
    InputMetadata.EMPTY,
    CommandExecutorOptions(),
    dummyReceivedTime,
  )

  val dummyCallbackHandler = object : CallbackHandler {
    val log = LoggerFactory.getLogger("TestCallbackHandler")
    override suspend fun beforeBegin(context: CommandContext) {
      log.info("> BeforeBegin: $context")
    }

    override suspend fun afterBegin(context: CommandContext) {
      log.info("> AfterBegin: $context")
    }

    override suspend fun beforeCommit(output: CommandOutput) {
      log.info("> BeforeCommit: $output")
    }

    override suspend fun afterCommit(output: CommandOutput) {
      log.info("> AfterCommit: $output")
    }

    override suspend fun afterRollback(context: CommandContext, error: Throwable) {
      log.error("> AfterRollback: $context", error)
    }
  }

  sealed interface TestCommand {
    @Serializable
    data class Create(val name: String) : TestCommand
  }

  sealed interface TestEvent {
    @Serializable
    data class ResourceCreated(val name: String) : TestEvent
  }

  sealed interface TestSummary {
    data object Empty : TestSummary
    data class Default(val list: List<TestEvent>) : TestSummary
  }

  data class MockSummary(val list: List<SerializedEvent> = emptyList())

  operator fun MockSummary.plus(event: SerializedEvent): MockSummary =
    this.copy(list = list + event)

  fun dummyEventStore(eventWriter: EventWriter? = null) = object : EventStore {
    override fun queryById(
      key: JournalKey,
      prevVersion: Version,
      tx: TransactionContext,
    ): Failable<Flow<VersionedEvent>> =
      dummyEventReader.queryById(key, prevVersion, tx)

    override suspend fun journalExists(key: JournalKey, tx: TransactionContext): Failable<Boolean> =
      dummyEventReader.journalExists(key, tx)

    override suspend fun writeEvents(
      output: CommandOutput,
      tx: TransactionContext,
    ): Failable<Unit> = (eventWriter ?: dummyEventWriter).writeEvents(output, tx)

    override suspend fun <T> startTransactionAndLock(
      key: JournalKey,
      writeOption: WriteOption,
      block: suspend (tx: TransactionContext) -> T,
    ): Failable<T> =
      dummyTransactionStarter.startTransactionAndLock(key, writeOption, block)

    override suspend fun <T> startDefaultTransaction(block: suspend (tx: TransactionContext) -> T): Failable<T> =
      dummyTransactionStarter.startDefaultTransaction(block)
  }

  fun mockCommandExecutor(
    commandOutput: CommandOutput,
  ): CommandExecutor<TestCommand, MockSummary, TestEvent> {
    val mock = mockk<CommandExecutor<TestCommand, MockSummary, TestEvent>>()
    every { mock.journalGroupId.unwrap } returns CommandRouterSpec.dummyJournalKey.groupId.unwrap
    every { mock.eventCodecRegistry } returns CommandRouterSpec.dummyEventCodecRegistry
    coEvery { mock.execute(any(), any(), any(), any()) } returns commandOutput.right()
    return mock
  }

  val dummyEnvironment = EpoqueEnvironment(
    dummyEventReader,
    dummyEventWriter,
    dummyTransactionStarter,
    CommandExecutorOptions(),
    dummyCallbackHandler,
    EpoqueRuntimeEnvironmentFactoryFactory.create(),
  )

  @Suppress("UNCHECKED_CAST")
  fun dummyCommandExecutor(
    eventWriter: EventWriter? = null,
  ): CommandExecutor<TestCommand, MockSummary, TestEvent> {
    return CommandExecutor(
      dummyJournalKey.groupId,
      dummyCommandDecoder,
      dummyCommandHandler,
      dummyEventCodecRegistry,
      dummyEventHandlerExecutor,
      dummyEnvironment.eventReader,
      eventWriter ?: dummyEnvironment.eventWriter,
      dummyEnvironment.transactionStarter,
      dummyEnvironment.defaultCommandExecutorOptions,
      dummyEnvironment.callbackHandler ?: CallbackHandler.EMPTY,
    )
  }
}
