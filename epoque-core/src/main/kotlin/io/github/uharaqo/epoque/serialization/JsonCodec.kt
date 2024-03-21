package io.github.uharaqo.epoque.serialization

import io.github.uharaqo.epoque.api.DataCodec
import io.github.uharaqo.epoque.api.DataCodecFactory
import io.github.uharaqo.epoque.api.SerializedData
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.serializer

@JvmInline
value class SerializedJson(private val unwrap: String) : SerializedData {
  override fun toText(): String = unwrap
  override fun toByteArray(): ByteArray = toText().toByteArray()
  override fun toString(): String = toText()
}

class JsonCodec<T>(
  override val type: Class<T>,
  private val serializer: KSerializer<T>,
) : DataCodec<T> {

  override fun encode(value: T): SerializedJson =
    kotlinx.serialization.json.Json.encodeToString(serializer, value).let(::SerializedJson)

  override fun decode(serialized: SerializedData): T =
    kotlinx.serialization.json.Json.decodeFromString(serializer, serialized.toText())

  companion object {
    inline fun <reified T> serializer(): KSerializer<T> = EmptySerializersModule().serializer<T>()
    inline fun <reified T> of(): JsonCodec<T> = JsonCodec(T::class.java, serializer<T>())
  }
}

class JsonCodecFactory : DataCodecFactory {
  @InternalSerializationApi
  override fun <V : Any> create(type: Class<V>): DataCodec<V> =
    JsonCodec(type, type.kotlin.serializer())
}
