package io.github.uharaqo.epoque.impl

import io.github.uharaqo.epoque.api.Metadata

// TODO: merge this with the EpoqueContext?
@JvmInline
value class DefaultMetadata private constructor(
  private val map: Map<Metadata.Key<*>, Any> = mapOf(),
) : Metadata {
  override fun isEmpty(): Boolean = map.isEmpty()

  @Suppress("UNCHECKED_CAST")
  override fun <T : Any> get(key: Metadata.Key<T>): T? = map[key]?.let { it as T }

  override operator fun plus(other: Metadata): Metadata =
    when {
      this.isEmpty() -> other
      other.isEmpty() -> this
      else ->
        if (other is DefaultMetadata) {
          DefaultMetadata(
            buildMap {
              putAll(this@DefaultMetadata.map)
              putAll(other.map)
            },
          )
        } else {
          throw IllegalArgumentException("Not supported yet. this: ${this::class}, other: ${other::class}")
        }
    }

  override fun toString(): String = map.toString()

  companion object {
    operator fun <T : Any> invoke(key: Metadata.Key<T>, value: T) =
      DefaultMetadata(mapOf(key to value))

    operator fun invoke(vararg kvs: Pair<Metadata.Key<*>, Any>) = DefaultMetadata(mapOf(*kvs))
  }
}
