package io.github.uharaqo.epoque.impl

import io.github.uharaqo.epoque.Epoque
import io.github.uharaqo.epoque.api.CommandInput
import io.github.uharaqo.epoque.impl.TestEnvironment.TestCommand
import io.github.uharaqo.epoque.impl.TestEnvironment.TestEvent
import io.github.uharaqo.epoque.impl.TestEnvironment.TestSummary
import io.kotest.assertions.arrow.core.rethrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk

class CommandRouterSpec : StringSpec(
  {
    "Command decoder and processor are found and executed properly in a Workflow" {
      // given
      val commandRouter =
        Epoque
          .routerFor<TestCommand, TestSummary, TestEvent>(TEST_JOURNAL, jsonCodecFactory) {
            commandHandlerFor<TestCommand.Create> { command, summary ->
              emit(dummyEvents)
            }
          }
          .create(dummyEnvironment)
          .let { DefaultCommandHandlerRuntimeEnvironmentFactory(it, mockk()) }

      // when
      val input = CommandInput(
        id = dummyJournalKey.id,
        type = dummyCommandType,
        payload = serializedCommand,
      )
      val output = commandRouter.process(input).rethrow()

      // then
      output.events shouldBe dummyOutputEvents
      output.metadata shouldBe dummyOutputMetadata
      output.context.copy(receivedTime = dummyReceivedTime) shouldBe dummyCommandContext
    }
  },
) {
  companion object : TestEnvironment()
}
