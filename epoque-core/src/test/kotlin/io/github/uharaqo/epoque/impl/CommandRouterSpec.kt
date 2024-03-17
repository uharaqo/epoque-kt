package io.github.uharaqo.epoque.impl

import arrow.core.right
import io.github.uharaqo.epoque.api.CommandInput
import io.github.uharaqo.epoque.api.CommandOutput
import io.github.uharaqo.epoque.api.CommandType
import io.github.uharaqo.epoque.api.JournalId
import io.github.uharaqo.epoque.api.SerializedCommand
import io.github.uharaqo.epoque.api.toCommandCodec
import io.github.uharaqo.epoque.impl.TestEnvironment.TestCommand
import io.github.uharaqo.epoque.serialization.JsonCodec
import io.github.uharaqo.epoque.serialization.SerializedJson
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.StringSpec
import io.mockk.coEvery
import io.mockk.mockk

class CommandRouterSpec : StringSpec(
  {
    "command deserializer and processor are found and executed properly" {
      // given
      val commandExecutor = mockk<CommandExecutor<TestCommand.Create, *, *>>()
      val commandCodec = JsonCodec.of<TestCommand.Create>().toCommandCodec()
      val processor = TypedCommandProcessor(commandCodec, commandExecutor)
      val commandRouter =
        DefaultCommandRouter(mapOf(CommandType.of<TestCommand.Create>() to processor))

      coEvery { commandExecutor.execute(any(), any()) } returns CommandOutput(dummyRecords).right()

      // when
      val input = CommandInput(
        id = JournalId("JID_1"),
        type = CommandType.of<TestCommand.Create>(),
        payload = SerializedCommand(SerializedJson("""{"name":"Foo"}""")),
      )
      val out = commandRouter.process(input)

      // then
      out shouldBeRight CommandOutput(dummyRecords)
    }
  },
) {
  companion object : TestEnvironment()
}
