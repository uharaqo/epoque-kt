package io.github.uharaqo.epoque.impl

import arrow.core.left
import arrow.core.right
import io.github.uharaqo.epoque.api.EpoqueException
import io.github.uharaqo.epoque.api.Failable
import io.github.uharaqo.epoque.api.Registry
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

class DefaultRegistry<K : Any, V : Any>(
  map: Map<K, V>,
  private val onError: (K) -> EpoqueException,
) : Registry<K, V> {
  private val map = Collections.unmodifiableMap(map)

  override fun find(key: K): Failable<V> = (map[key]?.right()) ?: (onError(key).left())

  fun toBuilder() = RegistryBuilder<K, V>(map)
  override fun toMap(): Map<K, V> = map
}
