package io.github.uharaqo.epoque.api

import arrow.core.Either

interface SerializedData

fun interface EventSerializer<E> {
  fun serialize(value: E): Either<Throwable, SerializedEvent>
}

fun interface EventDeserializer<E> {
  fun deserialize(serialized: SerializedEvent): Either<Throwable, E>
}

interface EventCodec<E> : EventSerializer<E>, EventDeserializer<E>
