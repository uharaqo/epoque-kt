package io.github.uharaqo.epoque.impl

import arrow.core.right
import io.github.uharaqo.epoque.Epoque
import io.github.uharaqo.epoque.api.CanAggregateEvents
import io.github.uharaqo.epoque.api.CommandInput
import io.github.uharaqo.epoque.api.CommandOutput
import io.github.uharaqo.epoque.api.EventWriter
import io.github.uharaqo.epoque.api.Version
import io.github.uharaqo.epoque.api.VersionedSummary
import io.github.uharaqo.epoque.impl.TestEnvironment.TestCommand
import io.github.uharaqo.epoque.impl.TestEnvironment.TestEvent
import io.github.uharaqo.epoque.impl.TestEnvironment.TestSummary
import io.github.uharaqo.epoque.impl2.DefaultEpoqueRuntimeEnvironmentFactory
import io.github.uharaqo.epoque.impl2.routerFor
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
      val canAggregateEvents = object : CanAggregateEvents<TestSummary> {
        override val eventHandlerExecutor = dummyJournal
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
      val environment = dummyEnvironment.copy(eventWriter = eventWriter)

      val codec = dummyCommandCodecRegistry.find(dummyCommandType).rethrow()
      val processor =
        Epoque
          .routerFor<TestCommand, TestSummary, TestEvent>(TEST_JOURNAL, jsonCodecFactory) {
            commandHandlerFor<TestCommand.Create> { c, s ->
              emit(dummyEvents)
            }
          }
          .create(environment)
          .let { DefaultEpoqueRuntimeEnvironmentFactory(it, mockk()) }

      // when
      val command = TestCommand.Create("Integration")
      val serialized = codec.encode(command).rethrow()
      val output =
        processor.process(CommandInput(dummyJournalKey.id, dummyCommandType, serialized)).rethrow()

      // then
      output.events shouldBe slot.captured.events
      output.events shouldBe dummyOutputEvents
    }
  },
) {
  companion object : TestEnvironment()
}
