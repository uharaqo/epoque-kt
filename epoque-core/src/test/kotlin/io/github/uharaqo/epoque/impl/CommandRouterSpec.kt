package io.github.uharaqo.epoque.impl

import io.github.uharaqo.epoque.api.CommandInput
import io.github.uharaqo.epoque.api.CommandType
import io.github.uharaqo.epoque.api.EpoqueException.Cause.COMMAND_NOT_SUPPORTED
import io.kotest.assertions.arrow.core.rethrow
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class CommandRouterSpec : StringSpec(
  {
    // given
    val (eventWriter, capturedOutput) = newMockWriter()
    val input = CommandInput(dummyJournalKey.id, dummyCommandType, serializedCommand)

    val router = newTestRouter(eventWriter).commandProcessorRegistry

    "Command decoder and processor are found and executed properly in a Workflow" {
      // when
      val output = router.find(dummyCommandType).rethrow().process(
        input,
      ).rethrow()

      // then
      output.events shouldBe capturedOutput.captured.events
      output.events shouldBe dummyOutputEvents
    }

    "Command not supported" {
      // when
      val output = router.find(CommandType.of<Long>())

      // then
      output shouldBeLeft COMMAND_NOT_SUPPORTED.toException(message = "java.lang.Long")
    }
  },
) {
  companion object : TestEnvironment()
}
