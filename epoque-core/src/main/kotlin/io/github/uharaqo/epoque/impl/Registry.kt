package io.github.uharaqo.epoque.impl

import arrow.core.left
import arrow.core.right
import io.github.uharaqo.epoque.api.EpoqueException
import io.github.uharaqo.epoque.api.Failable
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

interface Registry<K, V> {
  fun find(key: K): Failable<V>

  companion object
}

fun <K : Any, V : Any> Registry.Companion.builder(
  onError: (K) -> EpoqueException,
): RegistryBuilder<K, V> = RegistryBuilder(onError)

class RegistryBuilder<K : Any, V : Any>(
  private val onError: (K) -> EpoqueException,
) {
  private val built = AtomicBoolean(false)
  private val map = mutableMapOf<K, V>()

  operator fun set(key: K, value: V): Unit = synchronized(this) {
    check(!built.get()) { "Registry is already built" }
    checkNotNull(map.computeIfAbsent(key) { value }) { "The key: $key is already registered" }
  }

  fun build(): Registry<K, V> = synchronized(this) {
    built.set(true)
    DefaultRegistry(map, onError)
  }

  private inner class DefaultRegistry(
    map: Map<K, V>,
    private val onError: (K) -> EpoqueException,
  ) : Registry<K, V> {
    private val map = Collections.unmodifiableMap(map)

    override fun find(key: K): Failable<V> = (map[key]?.right()) ?: (onError(key).left())
  }
}
