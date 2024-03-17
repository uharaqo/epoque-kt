package io.github.uharaqo.epoque.impl

import arrow.core.getOrElse
import arrow.core.right
import io.github.uharaqo.epoque.api.EventType
import io.github.uharaqo.epoque.api.EventWritable
import io.github.uharaqo.epoque.api.EventWriter
import io.github.uharaqo.epoque.api.JournalGroupId
import io.github.uharaqo.epoque.api.JournalId
import io.github.uharaqo.epoque.api.JournalKey
import io.github.uharaqo.epoque.api.SerializedEvent
import io.github.uharaqo.epoque.api.TransactionContext
import io.github.uharaqo.epoque.api.Version
import io.github.uharaqo.epoque.api.VersionedEvent
import io.github.uharaqo.epoque.impl.TestEnvironment.TestEvent
import io.github.uharaqo.epoque.impl.TestEnvironment.TestEvent.ResourceCreated
import io.github.uharaqo.epoque.serialization.JsonCodec
import io.github.uharaqo.epoque.serialization.SerializedJson
import io.github.uharaqo.epoque.serialization.toEventCodec
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot

class EventWritableSpec : StringSpec(
  {
    val resourceCreated = ResourceCreated("foo")

    "json event codec works as expected" {
      val codec = JsonCodec.of<ResourceCreated>().toEventCodec()
      val serialized = codec.serialize(resourceCreated).getOrElse { throw it }
      val deserialized = codec.deserialize(serialized).getOrElse { throw it }

      deserialized shouldBe resourceCreated
    }

    "EventCodecRegistry and EventWriter work as EventWritable" {
      val eventWriter = mockk<EventWriter>()
      val tx = mockk<TransactionContext>()

      val eventWritable = object : EventWritable<TestEvent> {
        override val eventWriter = eventWriter
        override val eventCodecRegistry = dummyEventCodecRegistry
      }

      val writtenEvents = slot<List<VersionedEvent>>()
      coEvery { eventWriter.write(any(), capture(writtenEvents), any()) } returns Unit.right()

      eventWritable.write(
        journalKey = JournalKey(JournalGroupId.of<TestEvent>(), JournalId("bar")),
        currentVersion = Version.ZERO,
        events = listOf(resourceCreated, resourceCreated),
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
