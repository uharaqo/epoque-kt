package io.github.uharaqo.epoque.api

interface Registry<K : Any, V : Any> {
  fun find(key: K): V?
  fun toMap(): Map<K, V>
}
