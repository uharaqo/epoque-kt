package io.github.uharaqo.epoque.impl

import arrow.core.right
import io.github.uharaqo.epoque.Epoque
import io.github.uharaqo.epoque.api.CallbackHandler
import io.github.uharaqo.epoque.api.CommandContext
import io.github.uharaqo.epoque.api.CommandExecutorOptions
import io.github.uharaqo.epoque.api.CommandOutput
import io.github.uharaqo.epoque.api.CommandType
import io.github.uharaqo.epoque.api.EpoqueContext
import io.github.uharaqo.epoque.api.EventReader
import io.github.uharaqo.epoque.api.EventStore
import io.github.uharaqo.epoque.api.EventType
import io.github.uharaqo.epoque.api.EventWriter
import io.github.uharaqo.epoque.api.Failable
import io.github.uharaqo.epoque.api.InputMetadata
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
import io.github.uharaqo.epoque.codec.JsonCodecFactory
import io.github.uharaqo.epoque.codec.SerializedJson
import io.github.uharaqo.epoque.dsl.journalFor
import io.github.uharaqo.epoque.dsl.routerFor
import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.serialization.Serializable

abstract class TestEnvironment {
  sealed interface TestCommand {
    @Serializable
    data class Create(val name: String) : TestCommand

    @Serializable
    data object EmptyCommand : TestCommand
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

  val serializedEvent1 = SerializedEvent(SerializedJson("""{"name":"1"}"""))
  val serializedEvent2 = SerializedEvent(SerializedJson("""{"name":"2"}"""))
  val serializedCommand = SerializedCommand(SerializedJson("""{"name": "foo"}"""))

  val dummyJournalKey = JournalKey(JournalGroupId.of<TestEvent>(), JournalId("bar"))
  val dummyEventType = EventType.of<TestEvent.ResourceCreated>()
  val dummyEvents = listOf(TestEvent.ResourceCreated("1"), TestEvent.ResourceCreated("2"))
  val dummyOutputMetadata = OutputMetadata.EMPTY
  val dummyVersionedEvents = listOf(
    VersionedEvent(Version(1), dummyEventType, serializedEvent1),
    VersionedEvent(Version(2), dummyEventType, serializedEvent2),
  )
  val dummyOutputEvents = listOf(
    VersionedEvent(Version(3), dummyEventType, serializedEvent1),
    VersionedEvent(Version(4), dummyEventType, serializedEvent2),
  )
  val dummyTransactionContext = object : TransactionContext {
    override val writeOption: WriteOption = WriteOption.DEFAULT
    override val lockedKeys: Set<JournalKey> = emptySet()
  }
  val dummyReceivedTime = Instant.now()
  val dummyCommandType = CommandType.of<TestCommand.Create>()
  val dummyCommandContext = CommandContext(
    dummyJournalKey,
    dummyCommandType,
    serializedCommand,
    InputMetadata.EMPTY,
    CommandExecutorOptions(),
    dummyReceivedTime,
  )

  val dummyEventReader = object : EventReader {
    override fun queryById(
      key: JournalKey,
      prevVersion: Version,
      tx: TransactionContext,
    ): Failable<Flow<VersionedEvent>> =
      dummyVersionedEvents.asSequence().drop(prevVersion.unwrap.toInt()).asFlow().right()

    override suspend fun journalExists(key: JournalKey, tx: TransactionContext): Failable<Boolean> =
      true.right()
  }

  val dummyEventWriter = object : EventWriter {
    override suspend fun writeEvents(
      output: CommandOutput,
      tx: TransactionContext,
    ): Failable<Unit> = Unit.right()
  }

  fun newMockWriter(): Pair<EventWriter, CapturingSlot<CommandOutput>> {
    val mockWriter = mockk<EventWriter>()
    val output = slot<CommandOutput>()
    coEvery { mockWriter.writeEvents(capture(output), any()) } returns Unit.right()
    return (mockWriter to output)
  }

  val dummyTransactionStarter = object : TransactionStarter {
    override suspend fun <T> startTransactionAndLock(
      key: JournalKey,
      writeOption: WriteOption,
      block: suspend (tx: TransactionContext) -> T,
    ): Failable<T> =
      EpoqueContext.with({ put(TransactionContext, dummyTransactionContext) }) {
        block(dummyTransactionContext).right()
      }

    override suspend fun <T> startDefaultTransaction(block: suspend (tx: TransactionContext) -> T): Failable<T> =
      block(dummyTransactionContext).right()
  }

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

  val jsonCodecFactory = JsonCodecFactory()

  val TEST_JOURNAL = Epoque.journalFor<TestCommand, TestSummary, TestEvent>(jsonCodecFactory) {
    commands {
      onCommand<TestCommand.Create> {
        handle { c, s ->
          emit(dummyEvents)
        }
      }
    }

    events(TestSummary.Empty) {
      onEvent<TestEvent.ResourceCreated> {
        handle { s, e ->
          if (s is TestSummary.Default) {
            TestSummary.Default(s.list + e)
          } else {
            TestSummary.Default(listOf(e))
          }
        }
      }
    }
  }

  fun newTestRouter(eventWriter: EventWriter? = null) =
    Epoque.routerFor(TEST_JOURNAL) {
      environment {
        this.eventWriter = eventWriter
        eventStore = dummyEventStore()
        globalCallbackHandler = loggingCallbackHandler
      }
    }

  val loggingCallbackHandler = object : CallbackHandler {
    override suspend fun beforeBegin(context: CommandContext) {
      println("--- BeforeBegin: $context ---")
    }

    override suspend fun afterBegin(context: CommandContext) {
      println("--- AfterBegin: $context ---")
    }

    override suspend fun beforeCommit(output: CommandOutput) {
      println("--- BeforeCommit: $output ---")
    }

    override suspend fun afterCommit(output: CommandOutput) {
      println("--- AfterCommit: $output ---")
    }

    override suspend fun afterRollback(context: CommandContext, error: Throwable) {
      error.printStackTrace()
      println("--- AfterRollback: $context ---")
    }
  }
}
