package io.github.uharaqo.epoque.serialization

import arrow.core.Either
import io.github.uharaqo.epoque.api.EventCodec
import io.github.uharaqo.epoque.api.SerializedData
import io.github.uharaqo.epoque.api.SerializedEvent
import kotlinx.serialization.KSerializer
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.serializer

@JvmInline
value class SerializedJson(val value: String) : SerializedData {
  override fun toString(): String = value
}

class JsonCodec<T>(private val serializer: KSerializer<T>) {

  fun serialize(v: T): Either<Throwable, SerializedJson> = Either.catch {
    kotlinx.serialization.json.Json.encodeToString(serializer, v).let(::SerializedJson)
  }

  fun deserialize(json: SerializedData): Either<Throwable, T> = Either.catch {
    require(json is SerializedJson) { "Unexpected format: ${json::class.java.canonicalName}" }

    kotlinx.serialization.json.Json.decodeFromString(serializer, json.value)
  }

  companion object {
    inline fun <reified T> serializer(): KSerializer<T> = EmptySerializersModule().serializer<T>()
    inline fun <reified T> of(): JsonCodec<T> = JsonCodec(serializer<T>())
  }
}

@JvmInline
value class JsonEventCodec<E>(private val codec: JsonCodec<E>) : EventCodec<E> {
  override fun serialize(value: E): Either<Throwable, SerializedEvent> =
    codec.serialize(value).map { SerializedEvent(it) }

  override fun deserialize(serialized: SerializedEvent): Either<Throwable, E> =
    codec.deserialize(serialized.unwrap)
}

fun <T> JsonCodec<T>.toEventCodec(): EventCodec<T> = JsonEventCodec(this)
