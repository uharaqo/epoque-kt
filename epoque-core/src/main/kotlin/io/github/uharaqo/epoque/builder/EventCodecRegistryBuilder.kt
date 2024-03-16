package io.github.uharaqo.epoque.builder

import io.github.uharaqo.epoque.api.EventCodec
import io.github.uharaqo.epoque.api.EventCodecRegistry
import io.github.uharaqo.epoque.api.EventType
import io.github.uharaqo.epoque.api.Registry
import io.github.uharaqo.epoque.serialization.JsonCodec
import io.github.uharaqo.epoque.serialization.JsonEventCodec

class DefaultEventCodecRegistry(
  private val registry: Registry<EventType, EventCodec<*>>,
) : EventCodecRegistry {
  @Suppress("UNCHECKED_CAST")
  override fun <E> find(eventType: EventType): EventCodec<E>? =
    registry.find(eventType)?.let { it as? EventCodec<E> }
}

class EventCodecRegistryBuilder<E : Any> {
  val registry = DefaultRegistryBuilder<EventType, EventCodec<*>>()

  inline fun <reified E2 : E> register() = this.also {
    registry.register(EventType.of<E2>(), JsonEventCodec(JsonCodec.of<E2>()))
  }

  fun build(): EventCodecRegistry = DefaultEventCodecRegistry(registry.build())
}
