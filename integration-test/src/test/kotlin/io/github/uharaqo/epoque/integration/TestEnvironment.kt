package io.github.uharaqo.epoque.integration

import io.github.uharaqo.epoque.api.JournalId
import io.github.uharaqo.epoque.db.jooq.TableDefinition
import org.jooq.DSLContext

abstract class TestEnvironment {
  val projectId1 = JournalId("Project1")
  val project1 = projectId1.unwrap
  val taskId1 = JournalId("My First Task")
  val task1 = taskId1.unwrap

  @JvmInline
  value class RequestId(val value: String)

  fun initTables(ctx: DSLContext) {
    with(TableDefinition()) {
      ctx.createTableQuery().execute()
      ctx.execute(
        """
          |CREATE TABLE IF NOT EXISTS project (
          |  id VARCHAR(255) NOT NULL,
          |  name TEXT NOT NULL,
          |  PRIMARY KEY (id)
          |);
          |
          |CREATE TABLE IF NOT EXISTS task (
          |  id VARCHAR(255) NOT NULL,
          |  name TEXT NOT NULL,
          |  project_id TEXT NOT NULL REFERENCES project(id),
          |  PRIMARY KEY (id)
          |);
          |
          |CREATE TABLE IF NOT EXISTS task_entry (
          |  task_id VARCHAR(255) NOT NULL REFERENCES task(id),
          |  seq SERIAL NOT NULL,
          |  started_at BIGINT NOT NULL,
          |  ended_at BIGINT,
          |  PRIMARY KEY (task_id, seq)
          |);
          |
        """.trimMargin(),
      )
    }
  }
}
