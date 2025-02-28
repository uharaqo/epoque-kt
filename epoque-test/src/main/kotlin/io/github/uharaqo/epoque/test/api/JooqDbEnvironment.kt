package io.github.uharaqo.epoque.test.api

import io.github.uharaqo.epoque.Epoque
import io.github.uharaqo.epoque.api.EventStore
import io.github.uharaqo.epoque.db.jooq.TableDefinition
import io.github.uharaqo.epoque.db.jooq.h2.H2JooqQueries
import io.github.uharaqo.epoque.db.jooq.toEventStore
import io.github.uharaqo.epoque.test.impl.newH2JooqContext
import org.jooq.DSLContext

interface JooqDbEnvironment {
  val dsl: DSLContext
  val eventStore: EventStore
}

class H2JooqDbEnvironment : JooqDbEnvironment {
  override val dsl = Epoque.newH2JooqContext()
  override val eventStore = H2JooqQueries(TableDefinition()).toEventStore(dsl)
}
