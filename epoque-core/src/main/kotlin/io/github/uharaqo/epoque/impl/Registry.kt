package io.github.uharaqo.epoque.impl

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import io.github.uharaqo.epoque.api.EpoqueException
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

interface Registry<K, V, ERR : EpoqueException> {
  fun find(key: K): Either<ERR, V>

  companion object
}

fun <K : Any, V : Any, ERR : EpoqueException> Registry.Companion.builder(
  onError: (K) -> ERR,
): RegistryBuilder<K, V, ERR> = RegistryBuilder(onError)

class RegistryBuilder<K : Any, V : Any, ERR : EpoqueException>(
  private val onError: (K) -> ERR,
) {
  private val built = AtomicBoolean(false)
  private val map = mutableMapOf<K, V>()

  operator fun set(key: K, value: V): Unit = synchronized(this) {
    check(!built.get()) { "Registry is already built" }
    checkNotNull(map.computeIfAbsent(key) { value }) { "The key: $key is already registered" }
  }

  fun build(): Registry<K, V, ERR> = synchronized(this) {
    built.set(true)
    DefaultRegistry(map, onError)
  }

  private inner class DefaultRegistry(
    map: Map<K, V>,
    private val onError: (K) -> ERR,
  ) : Registry<K, V, ERR> {
    private val map = Collections.unmodifiableMap(map)

    override fun find(key: K): Either<ERR, V> = either {
      ensureNotNull(map[key]) { onError(key) }
    }
  }
}
