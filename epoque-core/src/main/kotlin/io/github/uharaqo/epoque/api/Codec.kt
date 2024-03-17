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

interface EventCodecRegistry {
  operator fun <E> get(eventType: EventType): EventCodec<E>?
}

fun interface CommandSerializer<C> {
  fun serialize(value: C): Either<Throwable, SerializedCommand>
}

fun interface CommandDeserializer<C> {
  fun deserialize(serialized: SerializedCommand): Either<Throwable, C>
}

interface CommandCodec<C> : CommandSerializer<C>, CommandDeserializer<C>

interface CommandCodecRegistry {
  operator fun <C> get(commandType: CommandType): CommandCodec<C>?
}
