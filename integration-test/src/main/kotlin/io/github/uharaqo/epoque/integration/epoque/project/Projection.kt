package io.github.uharaqo.epoque.integration.epoque.project

import io.github.uharaqo.epoque.db.jooq.EpoqueJooq
import org.jooq.impl.DSL.table

val PROJECT_PROJECTIONS = EpoqueJooq.projectionFor(PROJECT_JOURNAL) {
  projectionFor<ProjectCreated> { (k, v, e) ->
    ctx
      .insertInto(table("project"))
      .set(mapOf("id" to k.id.unwrap, "name" to e.name))
      .execute()
      .takeIf { it == 1 }
      ?: error("Failed to insert project: $e")
  }
}
