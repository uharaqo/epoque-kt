package io.github.uharaqo.epoque.api

import arrow.core.Either
import arrow.core.right
import io.github.uharaqo.epoque.api.EpoqueException.CommandHandlerFailure
import io.github.uharaqo.epoque.api.EpoqueException.EventLoadFailure
import io.github.uharaqo.epoque.api.EpoqueException.EventWriteFailure
import io.github.uharaqo.epoque.api.EpoqueException.SummaryAggregationFailure
import io.github.uharaqo.epoque.impl.CommandCodecRegistryBuilder
import io.github.uharaqo.epoque.impl.EventCodecRegistryBuilder
import io.github.uharaqo.epoque.serialization.SerializedJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.serialization.Serializable

abstract class TestEnvironment {
  val journalKey = JournalKey(JournalGroupId("foo"), JournalId("bar"))
  val eventType = EventType(TestEvent.ResourceCreated::class.qualifiedName!!)
  val dummyEvent1 = SerializedEvent(SerializedJson("""{"id": 1}"""))
  val dummyEvent2 = SerializedEvent(SerializedJson("""{"id": 1}"""))

  val dummyEventCodecRegistry =
    EventCodecRegistryBuilder<TestEvent>()
      .register<TestEvent.ResourceCreated>()
      .build()

  val dummySummaryGenerator = object : SummaryGenerator<MockSummary> {
    override val emptySummary: MockSummary = MockSummary()

    override fun generateSummary(
      prevSummary: MockSummary,
      event: SerializedEvent,
    ): Either<SummaryAggregationFailure, MockSummary> = (prevSummary + event).right()
  }

  val dummyRecords = listOf(
    VersionedEvent(Version(1), eventType, dummyEvent1),
    VersionedEvent(Version(2), eventType, dummyEvent2),
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
    ): Either<CommandHandlerFailure, List<TestEvent>> =
      listOf(TestEvent.ResourceCreated("1"), TestEvent.ResourceCreated("2")).right()
  }

  val dummyCommandCodecRegistry =
    CommandCodecRegistryBuilder<TestCommand>()
      .register<TestCommand.Create>()
      .build()

  sealed interface TestCommand {
    @Serializable
    data class Create(val name: String) : TestCommand
  }

  sealed interface TestEvent {
    @Serializable
    data class ResourceCreated(val name: String) : TestEvent
  }

  data class MockSummary(val list: List<SerializedEvent> = emptyList())

  operator fun MockSummary.plus(event: SerializedEvent): MockSummary =
    this.copy(list = list + event)
}
