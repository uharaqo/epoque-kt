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
    val eventHandlers = (journals as List<Journal<Any, Any?, Any>>).toEventHandlerRegistry()
    val eventCodecs = journals.toEventCodecRegistry()
    val commandCodecs = journals.toCommandCodecRegistry()
    val commandProcessors = journals.toCommandProcessorRegistry(commandCodecs, environment)

    return DefaultCommandRouter(
      environment,
      eventCodecs,
      eventHandlers,
      commandCodecs,
      commandProcessors,
    )
  }
}
