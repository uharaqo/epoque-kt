package io.github.uharaqo.epoque.dsl

import io.github.uharaqo.epoque.api.Journal

@EpoqueDslMarker
class EpoqueBuilder(private val journals: List<Journal<*, *, *>>) : EpoqueDsl() {
  private var environmentBuilder: EnvironmentBuilder? = null

  override fun environment(block: EnvironmentDsl.() -> Unit) {
    this.environmentBuilder = EnvironmentBuilder().apply(block)
  }

  fun build(): DefaultCommandRouter {
    val environment = environmentBuilder.shouldBeDefined("environment").build()

    @Suppress("UNCHECKED_CAST")
    val commandCodecRegistry = (journals as List<Journal<Any, Any?, Any>>).toCommandCodecRegistry()
    val commandProcessors = journals.toCommandProcessorRegistry(commandCodecRegistry, environment)

    val eventHandlers = journals.toEventHandlerRegistry()
    val eventCodecRegistry = journals.toEventCodecRegistry()

    return DefaultCommandRouter(
      environment,
      eventHandlers,
      commandProcessors,
      commandCodecRegistry,
      eventCodecRegistry,
    )
  }
}
