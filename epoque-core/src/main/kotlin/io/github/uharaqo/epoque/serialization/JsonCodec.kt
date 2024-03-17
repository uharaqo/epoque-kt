package io.github.uharaqo.epoque.serialization

import arrow.core.Either
import io.github.uharaqo.epoque.api.DataCodec
import io.github.uharaqo.epoque.api.SerializedData
import kotlinx.serialization.KSerializer
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.serializer

@JvmInline
value class SerializedJson(private val unwrap: String) : SerializedData {
  override fun toText(): String = unwrap

  override fun toString(): String = toText()
}

class JsonCodec<T>(private val serializer: KSerializer<T>) : DataCodec<T, SerializedData> {

  override fun serialize(value: T): Either<Throwable, SerializedJson> = Either.catch {
    kotlinx.serialization.json.Json.encodeToString(serializer, value).let(::SerializedJson)
  }

  override fun deserialize(serialized: SerializedData): Either<Throwable, T> = Either.catch {
    kotlinx.serialization.json.Json.decodeFromString(serializer, serialized.toText())
  }

  companion object {
    inline fun <reified T> serializer(): KSerializer<T> = EmptySerializersModule().serializer<T>()
    inline fun <reified T> of(): JsonCodec<T> = JsonCodec(serializer<T>())
  }
}
