package io.github.uharaqo.epoque.impl

interface Registry<K : Any, V : Any> {
  operator fun get(key: K): V?

  fun toMap(): Map<K, V>
}

@JvmInline
value class DefaultRegistry<K : Any, V : Any>(private val map: Map<K, V>) : Registry<K, V> {
  override operator fun get(key: K): V? = map[key]

  override fun toMap(): Map<K, V> = map

  operator fun plus(other: DefaultRegistry<K, V>): DefaultRegistry<K, V> =
    DefaultRegistry(this.toMap() + other.toMap())
}

interface RegistryBuilder<K : Any, V : Any> {
  fun register(key: K, value: V)

  fun build(): Registry<K, V>
}

class DefaultRegistryBuilder<K : Any, V : Any> : RegistryBuilder<K, V> {
  private val map = mutableMapOf<K, V>()

  override fun register(key: K, value: V) {
    map[key] = value
  }

  override fun build(): Registry<K, V> = DefaultRegistry(map)
}
