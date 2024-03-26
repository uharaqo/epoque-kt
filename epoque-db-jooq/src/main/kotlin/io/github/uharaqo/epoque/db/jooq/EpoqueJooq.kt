package io.github.uharaqo.epoque.db.jooq

import io.github.uharaqo.epoque.Epoque
import io.github.uharaqo.epoque.api.Journal
import io.github.uharaqo.epoque.builder.ProjectionRegistry

fun <S, E : Any> Epoque.projectionFor(
  journal: Journal<S, E>,
  block: JooqProjectionBuilder<S, E>.() -> Unit,
): ProjectionRegistry =
  JooqProjectionBuilder(journal).apply(block).build()
