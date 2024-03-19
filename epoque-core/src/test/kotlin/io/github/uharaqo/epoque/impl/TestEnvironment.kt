package io.github.uharaqo.epoque.impl

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.right
import io.github.uharaqo.epoque.api.CommandContext
import io.github.uharaqo.epoque.api.CommandExecutorOptions
import io.github.uharaqo.epoque.api.CommandHandler
import io.github.uharaqo.epoque.api.CommandOutput
import io.github.uharaqo.epoque.api.CommandType
import io.github.uharaqo.epoque.api.EpoqueException
import io.github.uharaqo.epoque.api.EpoqueException.CommandHandlerFailure
import io.github.uharaqo.epoque.api.EpoqueException.EventHandlerFailure
import io.github.uharaqo.epoque.api.EpoqueException.EventLoadFailure
import io.github.uharaqo.epoque.api.EpoqueException.EventWriteFailure
import io.github.uharaqo.epoque.api.EpoqueException.SummaryAggregationFailure
import io.github.uharaqo.epoque.api.EpoqueException.UnexpectedEvent
import io.github.uharaqo.epoque.api.EventHandler
import io.github.uharaqo.epoque.api.EventHandlerExecutor
import io.github.uharaqo.epoque.api.EventHandlerRegistry
import io.github.uharaqo.epoque.api.EventLoader
import io.github.uharaqo.epoque.api.EventStore
import io.github.uharaqo.epoque.api.EventType
import io.github.uharaqo.epoque.api.EventWriter
import io.github.uharaqo.epoque.api.JournalGroupId
import io.github.uharaqo.epoque.api.JournalId
import io.github.uharaqo.epoque.api.JournalKey
import io.github.uharaqo.epoque.api.LockOption
import io.github.uharaqo.epoque.api.SerializedCommand
import io.github.uharaqo.epoque.api.SerializedEvent
import io.github.uharaqo.epoque.api.TransactionContext
import io.github.uharaqo.epoque.api.TransactionStarter
import io.github.uharaqo.epoque.api.Version
import io.github.uharaqo.epoque.api.VersionedEvent
import io.github.uharaqo.epoque.serialization.SerializedJson
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.serialization.Serializable

abstract class TestEnvironment {
  val dummyJournalKey = JournalKey(JournalGroupId("foo"), JournalId("bar"))
  val serializedEvent1 = SerializedEvent(SerializedJson("""{"name":"1"}"""))
  val serializedEvent2 = SerializedEvent(SerializedJson("""{"name":"2"}"""))
  val serializedCommand = SerializedCommand(SerializedJson("""{"name": "foo"}"""))

  val dummyEventCodecRegistry =
    EventCodecRegistryBuilder<TestEvent>().register<TestEvent.ResourceCreated>().build()

  val dummyEventType = EventType.of<TestEvent.ResourceCreated>()
  val dummyEventHandler = object : EventHandler<TestSummary, TestEvent> {
    override fun handle(
      summary: TestSummary,
      event: TestEvent,
    ): Either<EventHandlerFailure, TestSummary> = either {
      if (summary !is TestSummary.Default) {
        TestSummary.Default(listOf(event))
      } else {
        summary.copy(summary.list + event)
      }
    }
  }

  val dummyEventHandlerExecutor = object : EventHandlerExecutor<MockSummary> {
    override val emptySummary: MockSummary = MockSummary()

    override fun computeNextSummary(
      prevSummary: MockSummary,
      eventType: EventType,
      event: SerializedEvent,
    ): Either<SummaryAggregationFailure, MockSummary> = (prevSummary + event).right()
  }

  val dummyEvents = listOf(TestEvent.ResourceCreated("1"), TestEvent.ResourceCreated("2"))
  val dummyRecords = listOf(
    VersionedEvent(Version(1), dummyEventType, serializedEvent1),
    VersionedEvent(Version(2), dummyEventType, serializedEvent2),
  )

  val dummyEventLoader = object : EventLoader {
    override fun queryById(
      key: JournalKey,
      prevVersion: Version,
      tx: TransactionContext,
    ): Either<EventLoadFailure, Flow<VersionedEvent>> =
      dummyRecords.asSequence().drop(prevVersion.unwrap.toInt()).asFlow().right()
  }

  val dummyEventWriter = object : EventWriter {
    override suspend fun writeEvents(
      output: CommandOutput,
      tx: TransactionContext,
    ): Either<EventWriteFailure, Unit> = Unit.right()
  }

  val dummyTransactionContext = object : TransactionContext {
    override val lockOption: LockOption = LockOption.DEFAULT
    override val lockedKeys: Set<JournalKey> = emptySet()
  }

  val dummyTransactionStarter = object : TransactionStarter {
    override suspend fun <T> startTransactionAndLock(
      key: JournalKey,
      lockOption: LockOption,
      block: suspend (tx: TransactionContext) -> T,
    ): Either<EpoqueException, T> = block(dummyTransactionContext).right()

    override suspend fun <T> startDefaultTransaction(block: suspend (tx: TransactionContext) -> T): Either<EpoqueException, T> =
      block(dummyTransactionContext).right()
  }

  val dummyCommandType = CommandType.of<TestCommand.Create>()
  val dummyCommandHandler = object : CommandHandler<TestCommand, MockSummary, TestEvent> {
    override fun handle(
      command: TestCommand,
      summary: MockSummary,
    ): Either<CommandHandlerFailure, List<TestEvent>> = dummyEvents.right()
  }

  val dummyCommandCodecRegistry =
    CommandCodecRegistryBuilder<TestCommand>()
      .register<TestCommand.Create>()
      .build()

  val dummyEventHandlerRegistry =
    EventHandlerRegistry(
      Registry.builder<EventType, EventHandler<TestSummary, TestEvent>, UnexpectedEvent> {
        UnexpectedEvent("Unexpected event: $it")
      }.apply {
        set(dummyEventType, dummyEventHandler)
      }.build(),
    )

  val dummyJournal = Journal(
    dummyJournalKey.groupId,
    TestEvent::class,
    TestSummary.Empty,
    dummyEventHandlerRegistry,
    dummyEventCodecRegistry,
  )

  val dummyCommandContext = CommandContext(
    dummyJournalKey,
    dummyCommandType,
    serializedCommand,
    CommandExecutorOptions(),
  )

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
    ): Either<EventLoadFailure, Flow<VersionedEvent>> =
      dummyEventLoader.queryById(key, prevVersion, tx)

    override suspend fun writeEvents(
      output: CommandOutput,
      tx: TransactionContext,
    ): Either<EpoqueException, Unit> = (eventWriter ?: dummyEventWriter).writeEvents(output, tx)

    override suspend fun <T> startTransactionAndLock(
      key: JournalKey,
      lockOption: LockOption,
      block: suspend (tx: TransactionContext) -> T,
    ): Either<EpoqueException, T> =
      dummyTransactionStarter.startTransactionAndLock(key, lockOption, block)

    override suspend fun <T> startDefaultTransaction(block: suspend (tx: TransactionContext) -> T): Either<EpoqueException, T> =
      dummyTransactionStarter.startDefaultTransaction(block)
  }

  fun mockCommandExecutor(
    commandOutput: CommandOutput,
  ): CommandExecutor<TestCommand.Create, MockSummary, TestEvent> {
    val mock = mockk<CommandExecutor<TestCommand.Create, MockSummary, TestEvent>>()
    every { mock.journalGroupId.unwrap } returns CommandRouterSpec.dummyJournalKey.groupId.unwrap
    every { mock.defaultCommandExecutorOptions } returns CommandExecutorOptions()
    every { mock.eventCodecRegistry } returns CommandRouterSpec.dummyEventCodecRegistry
    coEvery { mock.execute(any(), any()) } returns commandOutput.right()
    return mock
  }

  fun dummyCommandExecutor(
    eventWriter: EventWriter? = null,
    defaultCommandExecutorOptions: CommandExecutorOptions? = null,
  ) =
    CommandExecutor(
      dummyJournalKey.groupId,
      dummyCommandHandler,
      dummyEventCodecRegistry,
      dummyEventHandlerExecutor,
      dummyEventStore(eventWriter),
      defaultCommandExecutorOptions,
    ) as CommandExecutor<TestCommand.Create, MockSummary, TestEvent>
}
