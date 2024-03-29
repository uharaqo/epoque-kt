package io.github.uharaqo.epoque.api

import arrow.core.raise.either
import io.github.uharaqo.epoque.api.EpoqueException.Cause.COMMAND_NOT_SUPPORTED
import io.github.uharaqo.epoque.api.EpoqueException.Cause.EVENT_NOT_SUPPORTED

interface SerializedData {
  fun toText(): String
  fun toByteArray(): ByteArray
}

interface DataEncoder<in V> {
  val type: Class<@UnsafeVariance V>
  fun encode(value: V): SerializedData
}

interface DataDecoder<out V> {
  val type: Class<@UnsafeVariance V>
  fun decode(serialized: SerializedData): V
}

interface DataCodec<V> : DataEncoder<V>, DataDecoder<V>

interface DataCodecFactory {
  fun <V : Any> create(type: Class<V>): DataCodec<V>
}

@JvmInline
value class SerializedEvent(val unwrap: SerializedData) {
  override fun toString(): String = unwrap.toString()
}

@JvmInline
value class EventType private constructor(val unwrap: Class<*>) {
  override fun toString(): String = unwrap.canonicalName!!

  companion object {
    fun <E : Any> of(clazz: Class<E>): EventType = EventType(clazz)
    inline fun <reified E : Any> of(): EventType = of(E::class.java)
  }
}

fun interface EventEncoder<in E> {
  fun encode(value: E): Failable<SerializedEvent>
}

fun interface EventDecoder<out E> {
  fun decode(serialized: SerializedEvent): Failable<E>
}

data class EventCodec<E>(
  val encoder: EventEncoder<E>,
  val decoder: EventDecoder<E>,
) : EventEncoder<E> by encoder, EventDecoder<E> by decoder

@JvmInline
value class EventCodecRegistry(
  val registry: Map<EventType, EventCodec<*>>,
) {
  fun <E> find(type: EventType): Failable<EventCodec<E>> = either {
    @Suppress("UNCHECKED_CAST")
    registry[type]?.let { it as EventCodec<E> }
      ?: raise(EVENT_NOT_SUPPORTED.toException(message = type.toString()))
  }
}

@JvmInline
value class SerializedCommand(val unwrap: SerializedData) {
  override fun toString(): String = unwrap.toString()
}

@JvmInline
value class CommandType private constructor(val unwrap: Class<*>) {
  override fun toString(): String = unwrap.canonicalName!!

  companion object {
    fun <C : Any> of(clazz: Class<C>): CommandType = CommandType(clazz)
    inline fun <reified C : Any> of(): CommandType = of(C::class.java)
  }
}

fun interface CommandEncoder<in C> {
  fun encode(value: C): Failable<SerializedCommand>
}

fun interface CommandDecoder<out C> {
  fun decode(serialized: SerializedCommand): Failable<C>
}

data class CommandCodec<C>(
  val encoder: CommandEncoder<C>,
  val decoder: CommandDecoder<C>,
) : CommandEncoder<C> by encoder, CommandDecoder<C> by decoder

@JvmInline
value class CommandCodecRegistry(
  val registry: Map<CommandType, CommandCodec<*>>,
) {
  fun <E> find(type: CommandType): Failable<CommandCodec<E>> = either {
    @Suppress("UNCHECKED_CAST")
    registry[type]?.let { it as CommandCodec<E> }
      ?: raise(COMMAND_NOT_SUPPORTED.toException(message = type.toString()))
  }
}
