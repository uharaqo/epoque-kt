package io.github.uharaqo.epoque.impl

import io.github.uharaqo.epoque.api.DataCodec
import io.github.uharaqo.epoque.api.DataCodecFactory
import io.github.uharaqo.epoque.api.EpoqueException
import io.github.uharaqo.epoque.api.EventCodec
import io.github.uharaqo.epoque.api.EventCodecRegistry
import io.github.uharaqo.epoque.api.EventType
import io.github.uharaqo.epoque.api.codecFor
import io.github.uharaqo.epoque.builder.RegistryBuilder
import io.github.uharaqo.epoque.dsl.toEventCodec

class EventCodecRegistryBuilder<E : Any>(val codecFactory: DataCodecFactory) {
  private val registry = RegistryBuilder<EventType, EventCodec<*>>()

  /** [CE]: Concrete type of the event */
  inline fun <reified CE : E> register(): EventCodecRegistryBuilder<E> = this.also {
    @Suppress("UNCHECKED_CAST")
    register(codecFactory.codecFor<CE>() as DataCodec<E>)
  }

  fun register(codec: DataCodec<E>): EventCodecRegistryBuilder<E> =
    this.also { registry[EventType.of(codec.type)] = codec.toEventCodec() }

  fun build(): EventCodecRegistry = EventCodecRegistry(
    registry.build { EpoqueException.Cause.EVENT_NOT_SUPPORTED.toException(message = it.toString()) },
  )
}
