package io.github.uharaqo.epoque.api

data class ProjectionEvent(
  val key: JournalKey,
  val event: VersionedEvent,
)

fun CommandOutput.toProjectionEvents(): List<ProjectionEvent> =
  events.map { event -> ProjectionEvent(context.key, event) }

interface Projection {
  suspend fun process(event: ProjectionEvent, tx: TransactionContext)
}
