package io.github.uharaqo.epoque.dsl

import io.github.uharaqo.epoque.api.SummaryCache
import io.github.uharaqo.epoque.api.SummaryId
import io.github.uharaqo.epoque.api.VersionedSummary
import java.util.concurrent.ConcurrentHashMap

class MapSummaryCache(
  private val cache: MutableMap<SummaryId, VersionedSummary<*>> = ConcurrentHashMap(),
) : SummaryCache {
  @Suppress("UNCHECKED_CAST")
  override fun <S> get(id: SummaryId): VersionedSummary<S>? = cache[id] as VersionedSummary<S>?

  override fun <S> set(id: SummaryId, summary: VersionedSummary<S>) {
    cache[id] = summary
  }

  override fun toString(): String = cache.toString()
}
