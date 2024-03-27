package io.github.uharaqo.epoque.dsl

import io.github.uharaqo.epoque.api.CommandCodec
import io.github.uharaqo.epoque.api.CommandCodecRegistry
import io.github.uharaqo.epoque.api.CommandInput
import io.github.uharaqo.epoque.api.CommandOutput
import io.github.uharaqo.epoque.api.CommandProcessorRegistry
import io.github.uharaqo.epoque.api.CommandRouter
import io.github.uharaqo.epoque.api.EpoqueContext
import io.github.uharaqo.epoque.api.EpoqueEnvironment
import io.github.uharaqo.epoque.api.EventCodec
import io.github.uharaqo.epoque.api.EventCodecRegistry
import io.github.uharaqo.epoque.api.EventHandler
import io.github.uharaqo.epoque.api.EventHandlerRegistry
import io.github.uharaqo.epoque.api.Failable
import io.github.uharaqo.epoque.api.TransactionContext
import io.github.uharaqo.epoque.api.WriteOption

data class DefaultCommandRouter(
  val environment: EpoqueEnvironment,
  val eventCodecs: EventCodecRegistry,
  val eventHandlers: EventHandlerRegistry<*, *>,
  override val commandCodecRegistry: CommandCodecRegistry,
  override val commandProcessorRegistry: CommandProcessorRegistry,
) : CommandRouter {
  override suspend fun process(input: CommandInput): Failable<CommandOutput> =
    EpoqueContext.with({ put(CommandRouter, this@DefaultCommandRouter) }) {
      super.process(input)
    }
}

data class EventHandlerSetup<S, E>(
  val codec: EventCodec<E>,
  val handler: EventHandler<S, E>,
)

data class CommandHandlerSetup<C, S, E>(
  val codec: CommandCodec<C>,
  val writeOption: WriteOption, // TODO
  val prepare: (suspend () -> Any?)?,
  val handler: suspend CommandHandlerRuntimeEnvironment<C, S, E>.(C, S) -> Unit,
  val projections: List<suspend TransactionContext.() -> Unit>,
  val notifications: List<suspend NotificationContext.() -> Unit>,
)

interface NotificationContext
