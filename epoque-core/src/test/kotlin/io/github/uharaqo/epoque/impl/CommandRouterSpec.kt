package io.github.uharaqo.epoque.impl

import io.github.uharaqo.epoque.api.CommandInput
import io.github.uharaqo.epoque.api.CommandOutput
import io.github.uharaqo.epoque.api.toCommandCodec
import io.github.uharaqo.epoque.impl.TestEnvironment.TestCommand
import io.github.uharaqo.epoque.serialization.JsonCodec
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.StringSpec

class CommandRouterSpec : StringSpec(
  {
    "command deserializer and processor are found and executed properly" {
      // given
      val commandOutput = CommandOutput(dummyRecords, dummyCommandContext)
      val commandExecutor = mockCommandExecutor(commandOutput)
      val commandCodec = JsonCodec.of<TestCommand.Create>().toCommandCodec()
      val processor = TypedCommandProcessor(commandCodec, commandExecutor)
      val commandRouter = CommandRouterBuilder().processorFor<TestCommand.Create>(processor).build()

      // when
      val input = CommandInput(
        id = dummyJournalKey.id,
        type = dummyCommandType,
        payload = serializedCommand,
      )
      val output = commandRouter.process(input)

      // then
      output shouldBeRight commandOutput
    }
  },
) {
  companion object : TestEnvironment()
}
