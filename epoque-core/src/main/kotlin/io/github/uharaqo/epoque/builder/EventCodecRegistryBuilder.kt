package io.github.uharaqo.epoque.builder

import io.github.uharaqo.epoque.api.EventCodec
import io.github.uharaqo.epoque.api.EventCodecRegistry
import io.github.uharaqo.epoque.api.EventType
import io.github.uharaqo.epoque.serialization.JsonCodec
import io.github.uharaqo.epoque.serialization.toEventCodec

class DefaultEventCodecRegistry(
  private val registry: Registry<EventType, EventCodec<*>>,
) : EventCodecRegistry {
  @Suppress("UNCHECKED_CAST")
  override fun <E> get(eventType: EventType): EventCodec<E>? =
    registry.get(eventType)?.let { it as? EventCodec<E> }
}

class EventCodecRegistryBuilder<E : Any> {
  val registry = DefaultRegistryBuilder<EventType, EventCodec<*>>()

  inline fun <reified E2 : E> register() = this.also {
    registry.register(EventType.of<E2>(), JsonCodec.of<E2>().toEventCodec())
  }

  fun build(): EventCodecRegistry = DefaultEventCodecRegistry(registry.build())
}
