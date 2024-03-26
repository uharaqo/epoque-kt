package io.github.uharaqo.epoque.impl

import arrow.core.raise.either
import io.github.uharaqo.epoque.api.EpoqueException
import io.github.uharaqo.epoque.api.Failable
import io.github.uharaqo.epoque.api.Registry
import java.util.*

class DefaultRegistry<K : Any, V : Any>(
  map: Map<K, V>,
  private val onError: (K) -> EpoqueException,
) : Registry<K, V> {
  private val map = Collections.unmodifiableMap(map)

  override fun find(key: K): Failable<V> = either { map[key] ?: raise(onError(key)) }

  override fun toMap(): Map<K, V> = map
}
