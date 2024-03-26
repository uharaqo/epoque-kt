package io.github.uharaqo.epoque.builder

import io.github.uharaqo.epoque.api.CommandHandler
import io.github.uharaqo.epoque.api.CommandProcessor
import io.github.uharaqo.epoque.api.CommandRouter
import io.github.uharaqo.epoque.api.DataCodec
import io.github.uharaqo.epoque.api.DataCodecFactory
import io.github.uharaqo.epoque.api.EpoqueEnvironment
import io.github.uharaqo.epoque.api.Journal
import io.github.uharaqo.epoque.api.codecFor
import io.github.uharaqo.epoque.impl.DefaultCommandRouterFactoryBuilder
import io.github.uharaqo.epoque.impl.fromFactories

fun List<CommandRouterFactory>.toRouter(environment: EpoqueEnvironment): CommandRouter =
  CommandRouter.fromFactories(environment, this)

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
    noinline handle: suspend EpoqueRuntimeEnvironment<CC, S, E>.(c: CC, s: S) -> Unit,
  ) {
    commandHandlerFor(codecFactory.codecFor<CC>(), handle)
  }

  inline fun <reified CC : C, reified X> commandHandlerFor(
    noinline prepare: suspend (c: CC) -> X,
    noinline handle: suspend EpoqueRuntimeEnvironment<CC, S, E>.(c: CC, s: S, x: X?) -> Unit,
  ) {
    commandHandlerFor(codecFactory.codecFor<CC>(), prepare, handle)
  }

  abstract fun <CC : C> commandHandlerFor(
    codec: DataCodec<CC>,
    handle: suspend EpoqueRuntimeEnvironment<CC, S, E>.(c: CC, s: S) -> Unit,
  )

  abstract fun <CC : C, X> commandHandlerFor(
    codec: DataCodec<CC>,
    prepare: suspend (c: CC) -> X?,
    handle: suspend EpoqueRuntimeEnvironment<CC, S, E>.(c: CC, s: S, x: X?) -> Unit,
  )

  abstract fun build(): CommandRouterFactory
  abstract fun register(codec: DataCodec<C>, commandHandler: CommandHandler<C, S, E>)
}
