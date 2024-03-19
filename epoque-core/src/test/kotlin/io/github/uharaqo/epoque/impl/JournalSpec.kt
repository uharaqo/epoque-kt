package io.github.uharaqo.epoque.impl

import arrow.core.right
import io.github.uharaqo.epoque.api.CanAggregateEvents
import io.github.uharaqo.epoque.api.CommandInput
import io.github.uharaqo.epoque.api.CommandOutput
import io.github.uharaqo.epoque.api.CommandProcessor
import io.github.uharaqo.epoque.api.EventWriter
import io.github.uharaqo.epoque.api.Version
import io.github.uharaqo.epoque.api.VersionedEvent
import io.github.uharaqo.epoque.api.VersionedSummary
import io.github.uharaqo.epoque.api.toCommandCodec
import io.github.uharaqo.epoque.impl.TestEnvironment.TestCommand
import io.github.uharaqo.epoque.impl.TestEnvironment.TestSummary
import io.github.uharaqo.epoque.serialization.JsonCodec
import io.kotest.assertions.arrow.core.rethrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot

class JournalSpec : StringSpec(
  {
    "CanAggregateEvents works as expected" {
      // given
      val executor = DefaultEventHandlerExecutor(dummyJournal)
      val canAggregateEvents = object : CanAggregateEvents<TestSummary> {
        override val eventHandlerExecutor = executor
      }

      // when
      val result = canAggregateEvents.aggregateEvents(dummyRecords, null).rethrow()

      // then
      result shouldBe VersionedSummary(Version(2), TestSummary.Default(dummyEvents))
    }

    "Basic components work together as expected" {
      // given
      val eventWriter = mockk<EventWriter>()
      val slot = slot<CommandOutput>()
      coEvery { eventWriter.writeEvents(capture(slot), any()) } returns Unit.right()

      val commandExecutor = dummyCommandExecutor(eventWriter)
      val commandType = dummyCommandType
      val commandCodec = JsonCodec.of<TestCommand.Create>().toCommandCodec()
      val testProcessor = TypedCommandProcessor(commandCodec, commandExecutor)
      val processor: CommandProcessor =
        CommandRouterBuilder().processorFor<TestCommand.Create>(testProcessor).build()

      // when
      val command = TestCommand.Create("Integration")
      val serialized = commandCodec.serialize(command).rethrow()
      val output =
        processor.process(CommandInput(dummyJournalKey.id, commandType, serialized)).rethrow()

      // then
      output.events shouldBe slot.captured.events
      output.events shouldBe listOf(
        VersionedEvent(Version(3), dummyEventType, serializedEvent1),
        VersionedEvent(Version(4), dummyEventType, serializedEvent2),
      )
    }
  },
) {
  companion object : TestEnvironment()
}
