package io.github.uharaqo.epoque.integration.epoque.task

import io.github.uharaqo.epoque.db.jooq.EpoqueJooq
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.table

val TASK_PROJECTIONS = EpoqueJooq.projectionFor(TASK_JOURNAL) {
  projectionFor<TaskCreated> { (k, v, e) ->
    ctx
      .insertInto(table("task"))
      .set(
        mapOf(
          "id" to k.id.unwrap,
          "name" to e.name,
          "project_id" to e.project,
        ),
      )
      .execute()
      .takeIf { it == 1 }
      ?: error("Failed to insert task: $e")
  }
  projectionFor<TaskStarted> { (k, v, e) ->
    ctx
      .insertInto(table("task_entry"))
      .set(
        mapOf(
          "task_id" to k.id.unwrap,
          "started_at" to e.startedAt,
        ),
      )
      .execute()
      .takeIf { it == 1 }
      ?: error("Failed to insert task: $e")
  }
  projectionFor<TaskEnded> { (k, v, e) ->
    ctx
      .update(table("task_entry"))
      .set(mapOf("ended_at" to e.endedAt))
      .where(field("task_id").eq(k.id.unwrap))
      .execute()
      .takeIf { it == 1 }
      ?: error("Failed to insert task: $e")
  }
}
