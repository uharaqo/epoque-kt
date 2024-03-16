package io.github.uharaqo.epoque.builder

import io.github.uharaqo.epoque.api.Registry

class DefaultRegistry<K : Any, V : Any>(private val map: Map<K, V>) : Registry<K, V> {
  override fun find(key: K): V? = map[key]

  override fun toMap(): Map<K, V> = map
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

  override fun build(): Registry<K, V> =
    DefaultRegistry(map)
}
