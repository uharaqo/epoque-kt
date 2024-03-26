package io.github.uharaqo.epoque.dsl

import io.github.uharaqo.epoque.Epoque
import io.github.uharaqo.epoque.api.CallbackHandler
import io.github.uharaqo.epoque.api.CommandCodecRegistry
import io.github.uharaqo.epoque.api.CommandExecutorOptions
import io.github.uharaqo.epoque.api.CommandInput
import io.github.uharaqo.epoque.api.CommandOutput
import io.github.uharaqo.epoque.api.CommandProcessorRegistry
import io.github.uharaqo.epoque.api.CommandRouter
import io.github.uharaqo.epoque.api.EpoqueContext
import io.github.uharaqo.epoque.api.EpoqueEnvironment
import io.github.uharaqo.epoque.api.EventCodecRegistry
import io.github.uharaqo.epoque.api.EventHandlerRegistry
import io.github.uharaqo.epoque.api.EventReader
import io.github.uharaqo.epoque.api.EventStore
import io.github.uharaqo.epoque.api.EventWriter
import io.github.uharaqo.epoque.api.Failable
import io.github.uharaqo.epoque.api.TransactionStarter
import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.annotation.AnnotationTarget.FUNCTION
import kotlin.annotation.AnnotationTarget.PROPERTY
import kotlin.annotation.AnnotationTarget.TYPE
import kotlin.annotation.AnnotationTarget.VALUE_PARAMETER

data class EpoqueCommandRouter(
  val environment: EpoqueEnvironment,
  val eventCodecs: EventCodecRegistry,
  val eventHandlers: EventHandlerRegistry<*, *>,
  override val commandCodecRegistry: CommandCodecRegistry,
  override val commandProcessorRegistry: CommandProcessorRegistry,
) : CommandRouter {
  override suspend fun process(input: CommandInput): Failable<CommandOutput> =
    EpoqueContext.with({ put(CommandRouter, this@EpoqueCommandRouter) }) {
      super.process(input)
    }

  fun <C, S, E> toJournal(j: EpoqueJournal<C, S, E>) =
    @Suppress("UNCHECKED_CAST")
    j.toJournal(eventCodecs, eventHandlers as EventHandlerRegistry<S, E>)
}

fun Epoque.routerFor(
  vararg journals: EpoqueJournal<*, *, *>,
  block: EpoqueDsl.() -> Unit,
): EpoqueCommandRouter =
  Epoque.routerFor(journals.toList(), block)

fun Epoque.routerFor(
  journals: List<EpoqueJournal<*, *, *>>,
  block: EpoqueDsl.() -> Unit,
): EpoqueCommandRouter =
  EpoqueBuilder(journals).apply(block).build()

@DslMarker
@Target(CLASS, FUNCTION, PROPERTY, TYPE, VALUE_PARAMETER)
annotation class EpoqueDslMarker

@EpoqueDslMarker
abstract class EpoqueDsl {
  abstract fun environment(block: @EpoqueDslMarker EnvironmentDsl.() -> Unit)
}

@DslMarker
@Target(CLASS, FUNCTION, PROPERTY, TYPE, VALUE_PARAMETER)
annotation class EnvironmentDslMarker

@EnvironmentDslMarker
abstract class EnvironmentDsl {
  var eventReader: EventReader? = null
  var eventWriter: EventWriter? = null
  var transactionStarter: TransactionStarter? = null
  var eventStore: EventStore? = null
  var defaultCommandExecutorOptions: CommandExecutorOptions? = null
  var callbackHandler: CallbackHandler? = null
}
