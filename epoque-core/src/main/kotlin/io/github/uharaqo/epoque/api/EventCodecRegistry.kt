package io.github.uharaqo.epoque.api

interface EventCodecRegistry {
  fun <E> find(eventType: EventType): EventCodec<E>?
}
