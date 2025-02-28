package io.github.uharaqo.epoque.db.jooq.mysql

import io.github.uharaqo.epoque.Epoque
import io.github.uharaqo.epoque.api.EventStore
import io.github.uharaqo.epoque.db.jooq.JooqEventStore
import io.github.uharaqo.epoque.db.jooq.TableDefinition
import java.sql.DriverManager
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.conf.RenderQuotedNames
import org.jooq.conf.Settings
import org.jooq.impl.DSL

fun Epoque.newMysqlJooqContext(): DSLContext =
  // start testcontainers and connect to it
  DriverManager.getConnection("jdbc:tc:mysql:8.0.36:///test", "test", "test")
    .also { it.autoCommit = false }
    .also { Runtime.getRuntime().addShutdownHook(Thread { it.close() }) }
    .let { conn ->
      DSL.using(
        conn,
        SQLDialect.MYSQL,
        Settings().withRenderQuotedNames(RenderQuotedNames.ALWAYS),
      )
    }

fun TableDefinition.toMysqlEventStore(dsl: DSLContext): EventStore =
  JooqEventStore(dsl, MysqlJooqQueries(this))
