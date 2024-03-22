package io.github.uharaqo.epoque.api

data class ProjectionEvent<E>(
  val key: JournalKey,
  val version: Version,
  val event: E,
)

interface Projection<E> {
  val eventCodec: EventCodec<E>

  suspend fun process(event: ProjectionEvent<E>, tx: TransactionContext)
}
