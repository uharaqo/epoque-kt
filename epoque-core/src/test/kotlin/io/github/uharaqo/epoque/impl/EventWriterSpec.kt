package io.github.uharaqo.epoque.impl

import arrow.core.right
import io.github.uharaqo.epoque.api.CanWriteEvents
import io.github.uharaqo.epoque.api.EventCodec
import io.github.uharaqo.epoque.api.EventCodecRegistry
import io.github.uharaqo.epoque.api.EventType
import io.github.uharaqo.epoque.api.EventWriter
import io.github.uharaqo.epoque.api.JournalGroupId
import io.github.uharaqo.epoque.api.JournalId
import io.github.uharaqo.epoque.api.JournalKey
import io.github.uharaqo.epoque.api.SerializedEvent
import io.github.uharaqo.epoque.api.TransactionContext
import io.github.uharaqo.epoque.api.Version
import io.github.uharaqo.epoque.api.VersionedEvent
import io.github.uharaqo.epoque.api.toEventCodec
import io.github.uharaqo.epoque.impl.TestEnvironment.TestEvent
import io.github.uharaqo.epoque.impl.TestEnvironment.TestEvent.ResourceCreated
import io.github.uharaqo.epoque.serialization.JsonCodec
import io.github.uharaqo.epoque.serialization.SerializedJson
import io.kotest.assertions.arrow.core.rethrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot

class EventWriterSpec : StringSpec(
  {
    val event1 = ResourceCreated("foo")

    "JsonEventCodec works as expected" {
      val codec = JsonCodec.of<ResourceCreated>().toEventCodec()
      val serialized = codec.serialize(event1).rethrow()
      val deserialized = codec.deserialize(serialized).rethrow()

      deserialized shouldBe event1
    }

    "EventCodecRegistry hides concrete classes as expected" {
      // codec registry is for the sealed interface: TestEvent
      val builder = EventCodecRegistryBuilder<TestEvent>()
      // register a concrete class: ResourceCreated
      val sut: EventCodecRegistry<TestEvent> = builder.register<ResourceCreated>().build()

      // codec is also for the sealed interface
      val codec: EventCodec<TestEvent> = sut.find(EventType.of<ResourceCreated>()).rethrow()

      // concrete classes are not visible on signatures
      val serialized: SerializedEvent = codec.serialize(event1).rethrow()
      val deserialized: TestEvent = codec.deserialize(serialized).rethrow()

      // but concrete classes are actually used
      deserialized::class shouldBe ResourceCreated::class
      deserialized shouldBe event1
    }

    "EventCodecRegistry and EventWriter work as EventWritable" {
      val eventWriter = mockk<EventWriter>()
      val tx = mockk<TransactionContext>()

      val canWriteEvents = object : CanWriteEvents<TestEvent> {
        override val eventWriter = eventWriter
        override val eventCodecRegistry = dummyEventCodecRegistry
      }

      val writtenEvents = slot<List<VersionedEvent>>()
      coEvery { eventWriter.write(any(), capture(writtenEvents), any()) } returns Unit.right()

      canWriteEvents.writeEvents(
        journalKey = JournalKey(JournalGroupId.of<TestEvent>(), JournalId("bar")),
        currentVersion = Version.ZERO,
        events = listOf(event1, event1),
        tx = tx,
      )

      val expected =
        VersionedEvent(
          version = Version.ZERO,
          type = EventType(ResourceCreated::class.qualifiedName!!),
          event = SerializedEvent(SerializedJson("""{"name":"foo"}""")),
        )
          .let { listOf(it.copy(version = Version(1)), it.copy(version = Version(2))) }

      writtenEvents.captured shouldBe expected
    }
  },
) {
  companion object : TestEnvironment()
}
