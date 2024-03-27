package io.github.uharaqo.epoque.api

import io.github.uharaqo.epoque.TestEnvironment
import io.github.uharaqo.epoque.TestEnvironment.TestEvent
import io.github.uharaqo.epoque.TestEnvironment.TestEvent.ResourceCreated
import io.github.uharaqo.epoque.api.EpoqueException.Cause.COMMAND_NOT_SUPPORTED
import io.github.uharaqo.epoque.api.EpoqueException.Cause.EVENT_NOT_SUPPORTED
import io.kotest.assertions.arrow.core.rethrow
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk

class CodecSpec : StringSpec(
  {
    val event1 = dummyEvents[0]
    val eventCodec = TEST_JOURNAL.eventCodecRegistry

    "JsonEventCodec works as expected" {
      val codec = eventCodec.find<ResourceCreated>(dummyEventType).rethrow()
      val serialized = codec.encode(event1).rethrow()
      val deserialized = codec.decode(serialized).rethrow()

      deserialized shouldBe event1
    }

    "EventCodec not found" {
      val result = eventCodec.find<Long>(EventType.of<Long>())

      result shouldBeLeft EVENT_NOT_SUPPORTED.toException(message = "java.lang.Long")
    }

    "CommandCodec not found" {
      val result = TEST_JOURNAL.commandCodecRegistry.find<Long>(CommandType.of<Long>())

      result shouldBeLeft COMMAND_NOT_SUPPORTED.toException(message = "java.lang.Long")
    }

    "EventCodecRegistry hides concrete classes" {
      // codec is also for the sealed interface
      val codec: EventCodec<ResourceCreated> =
        eventCodec.find<ResourceCreated>(dummyEventType).rethrow()

      // concrete classes are not visible on signatures
      val serialized: SerializedEvent = codec.encode(event1).rethrow()
      val deserialized: TestEvent = codec.decode(serialized).rethrow()

      // but concrete classes are actually used
      deserialized::class shouldBe ResourceCreated::class // concrete class
      deserialized shouldBe event1 // concrete instance
    }

    "EventCodecRegistry and EventWriter work as CanWriteEvents" {
      // given
      val (eventWriter, output) = newMockWriter()
      val tx = mockk<TransactionContext>()

      val canWriteEvents = object : CanWriteEvents<TestEvent> {
        override val eventCodecRegistry: EventCodecRegistry = eventCodec
        override val eventWriter = eventWriter
      }

      // when
      canWriteEvents.eventWriter.writeEvents(
        CommandOutput(dummyVersionedEvents, dummyOutputMetadata, dummyCommandContext),
        tx,
      )

      // then
      output.captured.events shouldBe dummyVersionedEvents
    }
  },
) {
  companion object : TestEnvironment()
}
