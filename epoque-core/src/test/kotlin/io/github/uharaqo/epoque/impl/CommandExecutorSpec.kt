package io.github.uharaqo.epoque.impl

import arrow.core.getOrElse
import arrow.core.right
import io.github.uharaqo.epoque.api.CommandOutput
import io.github.uharaqo.epoque.api.EventType
import io.github.uharaqo.epoque.api.EventWriter
import io.github.uharaqo.epoque.api.SerializedEvent
import io.github.uharaqo.epoque.api.Version
import io.github.uharaqo.epoque.api.VersionedEvent
import io.github.uharaqo.epoque.impl.TestEnvironment.TestCommand.Create
import io.github.uharaqo.epoque.impl.TestEnvironment.TestEvent.ResourceCreated
import io.github.uharaqo.epoque.serialization.SerializedJson
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot

class CommandExecutorSpec : StringSpec(
  {
    "events generated by a command are written and returned properly" {
      // given
      val eventWriter = mockk<EventWriter>()
      val slot = slot<List<VersionedEvent>>()
      coEvery { eventWriter.write(any(), capture(slot), any()) } returns Unit.right()

      val dummyCommandExecutor = dummyCommandExecutor(eventWriter = eventWriter)

      // when
      val out = dummyCommandExecutor.execute(journalKey.id, Create("Foo"))

      // then: returned == written
      out shouldBeRight CommandOutput(slot.captured)

      // then
      out.getOrElse { throw it }.events shouldBe listOf(
        VersionedEvent(
          Version(3),
          EventType(ResourceCreated::class.qualifiedName!!),
          SerializedEvent(SerializedJson("""{"name":"1"}""")),
        ),
        VersionedEvent(
          Version(4),
          EventType(ResourceCreated::class.qualifiedName!!),
          SerializedEvent(SerializedJson("""{"name":"2"}""")),
        ),
      )
    }
  },
) {
  companion object : TestEnvironment() {
    fun dummyCommandExecutor(eventWriter: EventWriter) =
      CommandExecutor(
        journalKey.groupId,
        dummyCommandHandler,
        dummyEventCodecRegistry,
        dummyEventHandlerExecutor,
        dummyEventLoader,
        eventWriter,
        dummyTransactionStarter,
      )
  }
}
