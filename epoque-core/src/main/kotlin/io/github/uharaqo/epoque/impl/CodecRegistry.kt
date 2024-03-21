package io.github.uharaqo.epoque.impl

import arrow.core.raise.catch
import arrow.core.raise.either
import io.github.uharaqo.epoque.api.CommandCodec
import io.github.uharaqo.epoque.api.DataCodec
import io.github.uharaqo.epoque.api.DataCodecFactory
import io.github.uharaqo.epoque.api.EpoqueException.Cause
import io.github.uharaqo.epoque.api.EpoqueException.Cause.COMMAND_DECODING_FAILURE
import io.github.uharaqo.epoque.api.EpoqueException.Cause.COMMAND_ENCODING_FAILURE
import io.github.uharaqo.epoque.api.EpoqueException.Cause.COMMAND_NOT_SUPPORTED
import io.github.uharaqo.epoque.api.EpoqueException.Cause.EVENT_DECODING_FAILURE
import io.github.uharaqo.epoque.api.EpoqueException.Cause.EVENT_ENCODING_FAILURE
import io.github.uharaqo.epoque.api.EpoqueException.Cause.EVENT_NOT_SUPPORTED
import io.github.uharaqo.epoque.api.EventCodec
import io.github.uharaqo.epoque.api.EventCodecRegistry
import io.github.uharaqo.epoque.api.EventType
import io.github.uharaqo.epoque.api.Failable
import io.github.uharaqo.epoque.api.SerializedCommand
import io.github.uharaqo.epoque.api.SerializedData
import io.github.uharaqo.epoque.api.SerializedEvent
import io.github.uharaqo.epoque.api.codecFor

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
    registry.build { EVENT_NOT_SUPPORTED.toException(message = it.toString()) },
  )
}

fun <E : Any> DataCodec<E>.toEventCodec(): EventCodec<E> =
  DataCodecAdapter(
    this,
    ::SerializedEvent,
    SerializedEvent::unwrap,
    EVENT_NOT_SUPPORTED,
    EVENT_ENCODING_FAILURE,
    EVENT_DECODING_FAILURE,
  )
    .let { EventCodec(it::encode, it::decode) }

fun <C : Any> DataCodec<C>.toCommandCodec(): CommandCodec<C> =
  DataCodecAdapter(
    this,
    ::SerializedCommand,
    SerializedCommand::unwrap,
    COMMAND_NOT_SUPPORTED,
    COMMAND_ENCODING_FAILURE,
    COMMAND_DECODING_FAILURE,
  )
    .let { CommandCodec(it::encode, it::decode) }

class DataCodecAdapter<V : Any, W>(
  private val codec: DataCodec<V>,
  private val wrapper: (SerializedData) -> W,
  private val unwrapper: (W) -> SerializedData,
  private val notSupported: Cause,
  private val encodingFailure: Cause,
  private val decodingFailure: Cause,
) {
  fun encode(v: V): Failable<W> = either {
    if (!codec.type.isInstance(v)) {
      raise(notSupported.toException(message = v::class.java.canonicalName ?: v.toString()))
    }

    catch({ codec.encode(v).let(wrapper) }) {
      raise(encodingFailure.toException(it, codec.type.toString()))
    }
  }

  fun decode(serialized: W): Failable<V> = either {
    catch({ codec.decode(unwrapper(serialized)) }) {
      raise(decodingFailure.toException(it, codec.type.toString()))
    }
      .also { v ->
        if (!codec.type.isInstance(v)) {
          raise(notSupported.toException(message = v::class.java.canonicalName ?: v.toString()))
        }
      }
  }
}
