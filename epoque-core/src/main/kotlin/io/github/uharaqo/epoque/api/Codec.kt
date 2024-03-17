package io.github.uharaqo.epoque.api

import arrow.core.Either
import io.github.uharaqo.epoque.api.EpoqueException.UnexpectedCommand
import io.github.uharaqo.epoque.api.EpoqueException.UnexpectedEvent

interface SerializedData {
  fun toText(): String
}

interface DataSerializer<V, W> {
  fun serialize(value: V): Either<Throwable, W>
}

interface DataDeserializer<V, W> {
  fun deserialize(serialized: W): Either<Throwable, V>
}

interface DataCodec<V, W> : DataSerializer<V, W>, DataDeserializer<V, W>

@JvmInline
value class SerializedEvent(val unwrap: SerializedData) : SerializedData by unwrap {
  override fun toString(): String = unwrap.toString()
}

@JvmInline
value class EventType(private val unwrap: String) {
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
  override fun serialize(value: E): Either<Throwable, SerializedEvent> =
    codec.serialize(value).map(::SerializedEvent)

  override fun deserialize(serialized: SerializedEvent): Either<Throwable, E> =
    codec.deserialize(serialized)
}

fun <E> DataCodec<E, SerializedData>.toEventCodec(): EventCodec<E> = EventCodec(this)

fun interface EventCodecRegistry<E> {
  fun find(eventType: EventType): Either<UnexpectedEvent, EventCodec<E>>
}

@JvmInline
value class SerializedCommand(val unwrap: SerializedData) : SerializedData by unwrap {
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
  override fun serialize(value: C): Either<Throwable, SerializedCommand> =
    codec.serialize(value).map(::SerializedCommand)

  override fun deserialize(serialized: SerializedCommand): Either<Throwable, C> =
    codec.deserialize(serialized)
}

fun <C> DataCodec<C, SerializedData>.toCommandCodec(): CommandCodec<C> = CommandCodec(this)

fun interface CommandCodecRegistry<C> {
  fun find(commandType: CommandType): Either<UnexpectedCommand, CommandCodec<C>>
}
