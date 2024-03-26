package io.github.uharaqo.epoque.impl

import arrow.core.right
import io.github.uharaqo.epoque.api.CanSerializeEvents
import io.github.uharaqo.epoque.api.CanWriteEvents
import io.github.uharaqo.epoque.api.CommandOutput
import io.github.uharaqo.epoque.api.EventCodecRegistry
import io.github.uharaqo.epoque.api.EventWriter
import io.github.uharaqo.epoque.api.SerializedEvent
import io.github.uharaqo.epoque.api.TransactionContext
import io.github.uharaqo.epoque.api.Version
import io.github.uharaqo.epoque.impl.TestEnvironment.TestEvent
import io.github.uharaqo.epoque.impl.TestEnvironment.TestEvent.ResourceCreated
import io.kotest.assertions.arrow.core.rethrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot

class EventWriterSpec : StringSpec(
  {
    val event1 = dummyEvents[0]

    "JsonEventCodec works as expected" {
      val codec = dummyEventCodecRegistry.find<ResourceCreated>(dummyEventType).rethrow()
      val serialized = codec.encode(event1).rethrow()
      val deserialized = codec.decode(serialized).rethrow()

      deserialized shouldBe event1
    }

    "EventCodecRegistry hides concrete classes as expected" {
      // codec registry is for the sealed interface: TestEvent
      val builder = EventCodecRegistryBuilder<TestEvent>(jsonCodecFactory)
      // register a concrete class: ResourceCreated
      val sut: EventCodecRegistry = builder.register<ResourceCreated>().build()

      // codec is also for the sealed interface
      val codec = sut.find<ResourceCreated>(dummyEventType).rethrow()

      // concrete classes are not visible on signatures
      val serialized: SerializedEvent = codec.encode(event1).rethrow()
      val deserialized: TestEvent = codec.decode(serialized).rethrow()

      // but concrete classes are actually used
      deserialized::class shouldBe ResourceCreated::class
      deserialized shouldBe event1
    }

    "EventCodecRegistry and EventWriter work as EventWritable" {
      // given
      val eventWriter = mockk<EventWriter>()
      val tx = mockk<TransactionContext>()

      val canSerializeEvents = object : CanSerializeEvents<TestEvent> {
        override val eventCodecRegistry = dummyEventCodecRegistry
      }
      val canWriteEvents = object : CanWriteEvents<TestEvent> {
        override val eventWriter = eventWriter
      }
      val output = slot<CommandOutput>()
      coEvery { eventWriter.writeEvents(capture(output), any()) } returns Unit.right()

      // when
      val versionedEvents = canSerializeEvents.serializeEvents(Version.ZERO, dummyEvents).rethrow()

      canWriteEvents.eventWriter.writeEvents(
        output = CommandOutput(versionedEvents, dummyOutputMetadata, dummyCommandContext),
        tx = tx,
      )

      // then
      output.captured.events shouldBe dummyRecords
    }
  },
) {
  companion object : TestEnvironment()
}
