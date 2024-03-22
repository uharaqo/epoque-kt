package io.github.uharaqo.epoque.db.jooq

import io.github.uharaqo.epoque.api.Journal
import io.github.uharaqo.epoque.impl.ProjectionRegistry

object EpoqueJooq {
  fun <S, E : Any> projectionFor(
    journal: Journal<S, E>,
    block: JooqProjectionBuilder<S, E>.() -> Unit,
  ): ProjectionRegistry =
    JooqProjectionBuilder(journal).apply(block).build()
}
