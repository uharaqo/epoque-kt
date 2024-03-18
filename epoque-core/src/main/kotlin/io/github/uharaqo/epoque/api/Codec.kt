package io.github.uharaqo.epoque.api

import arrow.core.Either
import io.github.uharaqo.epoque.api.EpoqueException.CommandDeserializationFailure
import io.github.uharaqo.epoque.api.EpoqueException.CommandSerializationFailure
import io.github.uharaqo.epoque.api.EpoqueException.EventDeserializationFailure
import io.github.uharaqo.epoque.api.EpoqueException.EventSerializationFailure
import io.github.uharaqo.epoque.api.EpoqueException.UnexpectedCommand
import io.github.uharaqo.epoque.api.EpoqueException.UnexpectedEvent
import io.github.uharaqo.epoque.impl.Registry

interface SerializedData {
  fun toText(): String
  fun toByteArray(): ByteArray
}

interface DataSerializer<V, W> {
  fun serialize(value: V): Either<Throwable, W>
}

interface DataDeserializer<V, W> {
  fun deserialize(serialized: W): Either<Throwable, V>
}

interface DataCodec<V, W> : DataSerializer<V, W>, DataDeserializer<V, W>

@JvmInline
value class SerializedEvent(val unwrap: SerializedData) {
  override fun toString(): String = unwrap.toString()
}

@JvmInline
value class EventType(val unwrap: String) {
  override fun toString(): String = unwrap

  companion object {
    fun <E : Any> of(clazz: Class<E>): EventType = EventType(clazz.canonicalName!!)
    inline fun <reified E : Any> of(): EventType = of(E::class.java)
  }
}

interface EventSerializer<E> : DataSerializer<E, SerializedEvent>
interface EventDeserializer<E> : DataDeserializer<E, SerializedEvent>

@JvmInline
value class EventCodec<E>(
  private val codec: DataCodec<E, SerializedData>,
) : DataCodec<E, SerializedEvent>, EventSerializer<E>, EventDeserializer<E> {
  override fun serialize(value: E): Either<EventSerializationFailure, SerializedEvent> =
    codec.serialize(value).map(::SerializedEvent)
      .mapLeft { EventSerializationFailure("Failed to serialize event", it) }

  override fun deserialize(serialized: SerializedEvent): Either<EventDeserializationFailure, E> =
    codec.deserialize(serialized.unwrap)
      .mapLeft { EventDeserializationFailure("Failed to deserialize event", it) }
}

fun <E> DataCodec<E, SerializedData>.toEventCodec(): EventCodec<E> = EventCodec(this)

@JvmInline
value class EventCodecRegistry<E>(
  private val registry: Registry<EventType, EventCodec<E>, UnexpectedEvent>,
) : Registry<EventType, EventCodec<E>, UnexpectedEvent> by registry

@JvmInline
value class SerializedCommand(val unwrap: SerializedData) {
  override fun toString(): String = unwrap.toString()
}

@JvmInline
value class CommandType(private val unwrap: String) {
  override fun toString(): String = unwrap

  companion object {
    fun <C : Any> of(clazz: Class<C>): CommandType = CommandType(clazz.canonicalName!!)
    inline fun <reified E : Any> of(): CommandType = of(E::class.java)
  }
}

interface CommandSerializer<E> : DataSerializer<E, SerializedCommand>
interface CommandDeserializer<E> : DataDeserializer<E, SerializedCommand>

@JvmInline
value class CommandCodec<C>(
  private val codec: DataCodec<C, SerializedData>,
) : DataCodec<C, SerializedCommand>, CommandSerializer<C>, CommandDeserializer<C> {
  override fun serialize(value: C): Either<CommandSerializationFailure, SerializedCommand> =
    codec.serialize(value).map(::SerializedCommand)
      .mapLeft { CommandSerializationFailure("Failed to serialize command", it) }

  override fun deserialize(serialized: SerializedCommand): Either<CommandDeserializationFailure, C> =
    codec.deserialize(serialized.unwrap)
      .mapLeft { CommandDeserializationFailure("Failed to deserialize command", it) }
}

fun <C> DataCodec<C, SerializedData>.toCommandCodec(): CommandCodec<C> = CommandCodec(this)

@JvmInline
value class CommandCodecRegistry<C>(
  private val registry: Registry<CommandType, CommandCodec<C>, UnexpectedCommand>,
) : Registry<CommandType, CommandCodec<C>, UnexpectedCommand> by registry
