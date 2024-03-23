package io.github.uharaqo.epoque.impl

import arrow.core.raise.Raise
import io.github.uharaqo.epoque.api.CommandHandler
import io.github.uharaqo.epoque.api.CommandHandlerOutput
import io.github.uharaqo.epoque.api.EpoqueContextKey
import io.github.uharaqo.epoque.api.EpoqueException.Cause.COMMAND_REJECTED
import io.github.uharaqo.epoque.api.asOutputMetadata
import java.util.concurrent.ConcurrentLinkedQueue

interface CommandHandlerBuilder<C, S, E> : CommandHandlerSideEffects, Raise<Throwable> {

  fun emit(event: E) = emit(listOf(event))
  fun emit(vararg events: E) = emit(events.toList())
  fun emit(events: List<E>, metadata: Map<Any, Any> = emptyMap())

  fun reject(message: String, t: Throwable? = null): Nothing =
    throw COMMAND_REJECTED.toException(t, message)

  override fun raise(r: Throwable): Nothing = throw r
}

fun interface CommandHandlerRuntimeEnvironmentFactory<C, S, E : Any> {
  suspend fun create(): DefaultCommandHandlerRuntime<C, S, E>
}

class DefaultCommandHandler<C : Any, S, E : Any>(
  private val impl: suspend CommandHandlerBuilder<C, S, E>.(C, S) -> Unit,
  private val runtimeEnvFactory: CommandHandlerRuntimeEnvironmentFactory<C, S, E>,
) : CommandHandler<C, S, E> {
  override suspend fun handle(c: C, s: S): CommandHandlerOutput<E> =
    runtimeEnvFactory.create().apply { impl(c, s) }.complete()
}

class DefaultCommandHandlerRuntime<C, S, E>(
  private val runtimeEnvironment: CommandHandlerRuntimeEnvironment,
) : CommandHandlerBuilder<C, S, E>, CommandHandlerSideEffects by runtimeEnvironment {
  private val events = ConcurrentLinkedQueue<E>()
  private var metadata = mutableMapOf<Any, Any>()

  override fun emit(events: List<E>, metadata: Map<Any, Any>) {
    this.events += events
    this.metadata += metadata
  }

  fun complete(): CommandHandlerOutput<E> {
    return CommandHandlerOutput(events.toList(), metadata.asOutputMetadata())
  }
}
