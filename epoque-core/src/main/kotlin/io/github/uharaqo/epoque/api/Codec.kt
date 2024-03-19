package io.github.uharaqo.epoque.api

import arrow.core.Either
import io.github.uharaqo.epoque.api.EpoqueException.Cause.COMMAND_DESERIALIZATION_FAILURE
import io.github.uharaqo.epoque.api.EpoqueException.Cause.COMMAND_SERIALIZATION_FAILURE
import io.github.uharaqo.epoque.api.EpoqueException.Cause.EVENT_DESERIALIZATION_FAILURE
import io.github.uharaqo.epoque.api.EpoqueException.Cause.EVENT_SERIALIZATION_FAILURE
import io.github.uharaqo.epoque.impl.Registry

interface SerializedData {
  fun toText(): String
  fun toByteArray(): ByteArray
}

interface DataSerializer<V, W> {
  val type: String
  fun serialize(value: V): Either<Throwable, W>
}

interface DataDeserializer<V, W> {
  val type: String
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

class EventCodec<E>(
  override val type: String,
  private val codec: DataCodec<E, SerializedData>,
) : DataCodec<E, SerializedEvent>, EventSerializer<E>, EventDeserializer<E> {
  override fun serialize(value: E): Failable<SerializedEvent> =
    codec.serialize(value).map(::SerializedEvent)
      .mapLeft { EVENT_SERIALIZATION_FAILURE.toException(type, it) }

  override fun deserialize(serialized: SerializedEvent): Failable<E> =
    codec.deserialize(serialized.unwrap)
      .mapLeft { EVENT_DESERIALIZATION_FAILURE.toException(type, it) }
}

fun <E> DataCodec<E, SerializedData>.toEventCodec(): EventCodec<E> = EventCodec(type, this)

@JvmInline
value class EventCodecRegistry<E>(
  private val registry: Registry<EventType, EventCodec<E>>,
) : Registry<EventType, EventCodec<E>> by registry, EpoqueContextValue {
  object Key : EpoqueContextKey<EventCodecRegistry<*>>
}

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

class CommandCodec<C>(
  override val type: String,
  private val codec: DataCodec<C, SerializedData>,
) : DataCodec<C, SerializedCommand>,
  CommandSerializer<C>,
  CommandDeserializer<C>,
  EpoqueContextValue {

  override fun serialize(value: C): Failable<SerializedCommand> =
    codec.serialize(value).map(::SerializedCommand)
      .mapLeft { COMMAND_SERIALIZATION_FAILURE.toException(type, it) }

  override fun deserialize(serialized: SerializedCommand): Failable<C> =
    codec.deserialize(serialized.unwrap)
      .mapLeft { COMMAND_DESERIALIZATION_FAILURE.toException(type, it) }

  object Key : EpoqueContextKey<CommandCodec<*>>
}

fun <C> DataCodec<C, SerializedData>.toCommandCodec(): CommandCodec<C> = CommandCodec(type, this)

@JvmInline
value class CommandCodecRegistry<C>(
  private val registry: Registry<CommandType, CommandCodec<C>>,
) : Registry<CommandType, CommandCodec<C>> by registry
