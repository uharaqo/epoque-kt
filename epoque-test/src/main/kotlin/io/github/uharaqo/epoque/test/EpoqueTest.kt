package io.github.uharaqo.epoque.test

import io.github.uharaqo.epoque.api.CommandExecutorOptions
import io.github.uharaqo.epoque.api.CommandRouter
import io.github.uharaqo.epoque.api.EpoqueEnvironment
import io.github.uharaqo.epoque.api.EventStore
import io.github.uharaqo.epoque.api.LockOption.LOCK_JOURNAL
import io.github.uharaqo.epoque.db.jooq.H2JooqQueries
import io.github.uharaqo.epoque.db.jooq.JooqEventStore
import io.github.uharaqo.epoque.db.jooq.TableDefinition
import io.github.uharaqo.epoque.impl.CommandRouterFactory
import io.github.uharaqo.epoque.impl.DefaultCommandRouter
import io.github.uharaqo.epoque.impl.fromFactories
import io.github.uharaqo.epoque.test.api.Tester
import io.github.uharaqo.epoque.test.impl.DebugLogger
import io.github.uharaqo.epoque.test.impl.DefaultTester
import java.sql.DriverManager
import org.jooq.DSLContext
import org.jooq.JSONB
import org.jooq.SQLDialect
import org.jooq.impl.DSL

object EpoqueTest {
  fun newH2JooqContext(): DSLContext =
    DriverManager.getConnection(
      "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=TRUE",
      "sa",
      "",
    )
      .also { it.autoCommit = false }
      .also { Runtime.getRuntime().addShutdownHook(Thread { it.close() }) }
      .let { DSL.using(it, SQLDialect.H2) }

  fun newH2EventStore() = newH2JooqContext().toH2EventStore()

  fun DSLContext.toH2EventStore(tableDefinition: TableDefinition = TableDefinition()): JooqEventStore<JSONB> =
    JooqEventStore(this, H2JooqQueries(tableDefinition))

  fun newEnvironment(
    eventStore: EventStore = newH2EventStore(),
    options: CommandExecutorOptions = CommandExecutorOptions(
      timeoutMillis = 30000,
      lockOption = LOCK_JOURNAL,
    ),
  ): EpoqueEnvironment =
    EpoqueEnvironment(eventStore, eventStore, eventStore, options, DebugLogger())

  fun newTester(
    environment: EpoqueEnvironment,
    vararg commandRouterFactories: CommandRouterFactory,
  ): Tester {
    val router = CommandRouter.fromFactories(environment, commandRouterFactories.toList())

    return DefaultTester(router as DefaultCommandRouter, environment)
  }
}
