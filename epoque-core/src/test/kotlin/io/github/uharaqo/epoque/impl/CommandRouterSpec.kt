package io.github.uharaqo.epoque.impl

import io.github.uharaqo.epoque.Epoque
import io.github.uharaqo.epoque.api.CommandInput
import io.github.uharaqo.epoque.api.CommandOutput
import io.github.uharaqo.epoque.impl.TestEnvironment.TestCommand
import io.github.uharaqo.epoque.impl.TestEnvironment.TestEvent
import io.github.uharaqo.epoque.impl.TestEnvironment.TestSummary
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.StringSpec

class CommandRouterSpec : StringSpec(
  {
    "command decoder and processor are found and executed properly" {
      // given
      val commandRouter =
        Epoque
          .routerFor<TestCommand, TestSummary, TestEvent>(TEST_JOURNAL, jsonCodecFactory) {
            commandHandlerFor<TestCommand.Create> { command, summary ->
              emit(dummyEvents)
            }
          }
          .create(dummyEnvironment)

      // when
      val input = CommandInput(
        id = dummyJournalKey.id,
        type = dummyCommandType,
        payload = serializedCommand,
      )
      val output = commandRouter.process(input)

      // then
      output shouldBeRight CommandOutput(
        dummyOutputEvents,
        dummyOutputMetadata,
        dummyCommandContext,
      )
    }
  },
) {
  companion object : TestEnvironment()
}
