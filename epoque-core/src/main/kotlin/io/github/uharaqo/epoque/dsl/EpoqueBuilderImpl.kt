package io.github.uharaqo.epoque.dsl

import io.github.uharaqo.epoque.api.EpoqueEnvironment

@EpoqueDslMarker
class EpoqueBuilder(private val journals: List<EpoqueJournal<*, *, *>>) : EpoqueDsl() {
  private lateinit var environmentBuilder: EnvironmentBuilder

  override fun environment(block: EnvironmentDsl.() -> Unit) {
    this.environmentBuilder = EnvironmentBuilder().apply(block)
  }

  fun build(): EpoqueCommandRouter {
    val environment = environmentBuilder.build()
    val eventCodecs = journals.toEventCodecRegistry()
    val eventHandlers = journals.toEventHandlerRegistry()
    val commandCodecs = journals.toCommandCodecRegistry()
    val commandProcessors =
      journals.toCommandProcessorRegistry(commandCodecs, eventCodecs, eventHandlers, environment)

    return EpoqueCommandRouter(
      environment,
      eventCodecs,
      eventHandlers,
      commandCodecs,
      commandProcessors,
    )
  }
}

@EnvironmentDslMarker
class EnvironmentBuilder : EnvironmentDsl() {
  fun build(): EpoqueEnvironment = EpoqueEnvironment(
    eventReader ?: eventStore!!,
    eventWriter ?: eventStore!!,
    transactionStarter ?: eventStore!!,
    defaultCommandExecutorOptions,
    callbackHandler,
  )
}
