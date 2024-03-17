package io.github.uharaqo.epoque.impl

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.right
import io.github.uharaqo.epoque.api.CommandHandler
import io.github.uharaqo.epoque.api.EpoqueException
import io.github.uharaqo.epoque.api.EpoqueException.CommandHandlerFailure
import io.github.uharaqo.epoque.api.EpoqueException.EventHandlerFailure
import io.github.uharaqo.epoque.api.EpoqueException.EventLoadFailure
import io.github.uharaqo.epoque.api.EpoqueException.EventWriteFailure
import io.github.uharaqo.epoque.api.EpoqueException.SummaryAggregationFailure
import io.github.uharaqo.epoque.api.EventHandler
import io.github.uharaqo.epoque.api.EventHandlerExecutor
import io.github.uharaqo.epoque.api.EventLoader
import io.github.uharaqo.epoque.api.EventType
import io.github.uharaqo.epoque.api.EventWriter
import io.github.uharaqo.epoque.api.JournalGroupId
import io.github.uharaqo.epoque.api.JournalId
import io.github.uharaqo.epoque.api.JournalKey
import io.github.uharaqo.epoque.api.SerializedEvent
import io.github.uharaqo.epoque.api.TransactionContext
import io.github.uharaqo.epoque.api.TransactionStarter
import io.github.uharaqo.epoque.api.Version
import io.github.uharaqo.epoque.api.VersionedEvent
import io.github.uharaqo.epoque.serialization.SerializedJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.serialization.Serializable

abstract class TestEnvironment {
  val dummyJournalKey = JournalKey(JournalGroupId("foo"), JournalId("bar"))
  val resourceCreatedEventType = EventType(TestEvent.ResourceCreated::class.qualifiedName!!)
  val serializedEvent1 = SerializedEvent(SerializedJson("""{"name":"1"}"""))
  val serializedEvent2 = SerializedEvent(SerializedJson("""{"name":"2"}"""))

  val dummyEventCodecRegistry =
    EventCodecRegistryBuilder<TestEvent>().register<TestEvent.ResourceCreated>().build()

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
    VersionedEvent(Version(1), resourceCreatedEventType, serializedEvent1),
    VersionedEvent(Version(2), resourceCreatedEventType, serializedEvent2),
  )

  val dummyEventLoader = object : EventLoader {
    override fun queryById(
      journalKey: JournalKey,
      prevVersion: Version,
      tx: TransactionContext,
    ): Either<EventLoadFailure, Flow<VersionedEvent>> =
      dummyRecords.asSequence().drop(prevVersion.unwrap.toInt()).asFlow().right()
  }

  val dummyEventWriter = object : EventWriter {
    override suspend fun write(
      journalKey: JournalKey,
      events: List<VersionedEvent>,
      tx: TransactionContext,
    ): Either<EventWriteFailure, Unit> = Unit.right()
  }

  val dummyTransactionContext = object : TransactionContext {}

  val dummyTransactionStarter = object : TransactionStarter {
    override suspend fun <T> startTransactionAndLock(
      journalKey: JournalKey,
      block: suspend (tx: TransactionContext) -> T,
    ): Either<EpoqueException, T> = block(dummyTransactionContext).right()
  }

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

  val dummyEventHandlerRegistry =
    DefaultEventHandlerRegistry(
      DefaultRegistryBuilder<EventType, EventHandler<TestSummary, TestEvent>>().apply {
        register(resourceCreatedEventType, dummyEventHandler)
      }.build(),
    )

  val dummyJournal = Journal(
    dummyJournalKey.groupId,
    TestEvent::class,
    TestSummary.Empty,
    dummyEventHandlerRegistry,
    dummyEventCodecRegistry,
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

  fun dummyCommandExecutor(eventWriter: EventWriter) =
    CommandExecutor(
      dummyJournalKey.groupId,
      dummyCommandHandler,
      dummyEventCodecRegistry,
      dummyEventHandlerExecutor,
      dummyEventLoader,
      eventWriter,
      dummyTransactionStarter,
    )
}
