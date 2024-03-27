package io.github.uharaqo.epoque.api

interface SummaryCache {
  operator fun <S> get(id: SummaryId): VersionedSummary<S>?
  operator fun <S> set(id: SummaryId, summary: VersionedSummary<S>)
}
