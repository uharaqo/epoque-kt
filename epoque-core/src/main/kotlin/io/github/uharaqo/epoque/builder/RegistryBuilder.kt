package io.github.uharaqo.epoque.builder

import io.github.uharaqo.epoque.api.EpoqueException
import io.github.uharaqo.epoque.api.Registry
import io.github.uharaqo.epoque.impl.DefaultRegistry
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

class RegistryBuilder<K : Any, V : Any>(
  private val map: MutableMap<K, V> = mutableMapOf(),
) {
  private val built = AtomicBoolean(false)

  operator fun set(key: K, value: V): Unit = synchronized(this) {
    check(!built.get()) { "Registry is already built" }
    checkNotNull(map.computeIfAbsent(key) { value }) { "The key: $key is already registered" }
  }

  fun build(onError: (K) -> EpoqueException): Registry<K, V> = synchronized(this) {
    built.set(true)
    DefaultRegistry(map, onError)
  }

  fun buildIntoMap(): Map<K, V> = synchronized(this) {
    built.set(true)
    Collections.unmodifiableMap(map)
  }

  fun plus(other: RegistryBuilder<K, V>): RegistryBuilder<K, V> =
    mutableMapOf<K, V>().also {
      it.putAll(buildIntoMap())
      it.putAll(other.buildIntoMap())
    }.let(::RegistryBuilder)
}
