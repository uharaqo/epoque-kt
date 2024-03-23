package io.github.uharaqo.epoque.db.jooq

import io.github.uharaqo.epoque.db.jooq.h2.H2JooqQueries
import java.sql.DriverManager
import org.jooq.DSLContext
import org.jooq.JSONB
import org.jooq.SQLDialect
import org.jooq.impl.DSL

object JooqUtil {
  fun DSLContext.newH2EventStore() = newH2DslContext().toEventStore()

  fun newH2DslContext(): DSLContext =
    DSL.using(
      DriverManager.getConnection(
        "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=TRUE",
        "sa",
        "",
      ).also { it.autoCommit = false },
      SQLDialect.H2,
    )

  fun DSLContext.toEventStore(): JooqEventStore<JSONB> =
    JooqEventStore(this, H2JooqQueries(LockJournalSpec.tableDefinition))
}
