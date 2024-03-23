package io.github.uharaqo.epoque.builder

import io.github.uharaqo.epoque.api.CommandProcessor
import io.github.uharaqo.epoque.api.CommandRouter
import io.github.uharaqo.epoque.api.DataCodec
import io.github.uharaqo.epoque.api.EpoqueContextKey
import io.github.uharaqo.epoque.api.EpoqueEnvironment
import io.github.uharaqo.epoque.api.Journal
import io.github.uharaqo.epoque.impl.CommandHandlerBuilder
import io.github.uharaqo.epoque.impl.CommandHandlerFactory
import io.github.uharaqo.epoque.impl.DefaultCommandRouterFactoryBuilder

fun interface CommandProcessorFactory {
  fun create(environment: EpoqueEnvironment): CommandProcessor
}

fun interface CommandRouterFactory {
  fun create(environment: EpoqueEnvironment): CommandRouter

  companion object {
    fun <C : Any, S, E : Any> create(
      journal: Journal<S, E>,
      dataCodecFactory: DataCodecFactory,
      block: CommandRouterFactoryBuilder<C, S, E>.() -> Unit,
    ): CommandRouterFactory =
      DefaultCommandRouterFactoryBuilder<C, S, E>(journal, dataCodecFactory).also(block).build()
  }
}

abstract class CommandRouterFactoryBuilder<C : Any, S, E : Any> {
  abstract val codecFactory: DataCodecFactory

  /** [CC]: Concrete type of the command */
  inline fun <reified CC : C> commandHandlerFor(
    noinline handle: suspend CommandHandlerBuilder<CC, S, E>.(c: CC, s: S) -> Unit,
  ) {
    commandHandlerFor(codecFactory.codecFor<CC>(), handle)
  }

  abstract fun <CC : C> commandHandlerFor(
    codec: DataCodec<CC>,
    handle: suspend CommandHandlerBuilder<CC, S, E>.(c: CC, s: S) -> Unit,
  )

  abstract fun register(
    codec: DataCodec<C>,
    commandHandlerFactory: CommandHandlerFactory<C, S, E>,
  )

  abstract fun build(): CommandRouterFactory
}

object DeserializedCommand : EpoqueContextKey<Any>
