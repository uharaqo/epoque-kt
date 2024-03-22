package io.github.uharaqo.epoque.integration

import io.github.uharaqo.epoque.db.jooq.TableDefinition
import io.github.uharaqo.epoque.integration.TestEnvironment.RequestId
import io.github.uharaqo.epoque.integration.epoque.project.CreateProject
import io.github.uharaqo.epoque.integration.epoque.project.PROJECT_COMMANDS
import io.github.uharaqo.epoque.integration.epoque.project.PROJECT_JOURNAL
import io.github.uharaqo.epoque.integration.epoque.project.Project
import io.github.uharaqo.epoque.integration.epoque.project.ProjectCreated
import io.github.uharaqo.epoque.integration.epoque.task.CreateTask
import io.github.uharaqo.epoque.integration.epoque.task.EndTask
import io.github.uharaqo.epoque.integration.epoque.task.StartTask
import io.github.uharaqo.epoque.integration.epoque.task.TASK_COMMANDS
import io.github.uharaqo.epoque.integration.epoque.task.TASK_JOURNAL
import io.github.uharaqo.epoque.integration.epoque.task.Task
import io.github.uharaqo.epoque.integration.epoque.task.TaskCreated
import io.github.uharaqo.epoque.integration.epoque.task.TaskEnded
import io.github.uharaqo.epoque.integration.epoque.task.TaskStarted
import io.github.uharaqo.epoque.test.EpoqueTest
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class IntegrationSpec : StringSpec(
  {
    val startedAt = 123L
    val endedAt = 456L

    EpoqueTest.newH2JooqContext().also { ctx ->
      with(TableDefinition()) {
        ctx.createTableQuery().execute()
      }
    }
    val tester = EpoqueTest.newTester(EpoqueTest.newEnvironment(), PROJECT_COMMANDS, TASK_COMMANDS)

    val meta = mutableMapOf("RequestId" to RequestId("123"))

    "Register project" {
      tester.forJournal(PROJECT_JOURNAL) {
        command(projectId1, CreateProject(project1), meta) {
          events shouldBe listOf(ProjectCreated(project1))
          summary shouldBe Project.Default
        }
      }
    }
    "Register task" {
      tester.forJournal(TASK_JOURNAL) {
        command(taskId1, CreateTask(task1, startedAt, project1, setOf("tag1", "tag2"))) {
          events shouldBe listOf(TaskCreated(task1, startedAt, project1, setOf("tag1", "tag2")))
          summary shouldBe Task.Default(false)
        }
      }
    }
    "Start task" {
      tester.forJournal(TASK_JOURNAL) {
        command(taskId1, StartTask(startedAt)) {
          events shouldBe listOf(TaskStarted(startedAt))
          summary shouldBe Task.Default(true)
        }
      }
    }
    "Start task again" {
      tester.forJournal(TASK_JOURNAL) {
        command(taskId1, StartTask(startedAt)) {
          events shouldBe emptyList()
          summary shouldBe Task.Default(true)
        }
      }
    }
    "End task" {
      tester.forJournal(TASK_JOURNAL) {
        command(taskId1, EndTask(endedAt)) {
          events shouldBe listOf(TaskEnded(endedAt))
          summary shouldBe Task.Default(false)
        }
      }
    }
    "End task again" {
      tester.forJournal(TASK_JOURNAL) {
        command(taskId1, EndTask(endedAt)) {
          events shouldBe emptyList()
          summary shouldBe Task.Default(false)
        }
      }
    }

    "Projection Result" {
//      ctx.selectFrom(DSL.table("project")).fetch().intoMaps() shouldBe
//        listOf(
//          mapOf("id" to project1, "name" to project1),
// //                    mapOf("id" to "ProjectX", "name" to "Chained Project"),
//        )
//      ctx.selectFrom(DSL.table("task")).fetch().intoMaps() shouldBe listOf(
//        mapOf("id" to task1, "name" to task1, "project_id" to project1),
//      )
//      ctx.selectFrom(DSL.table("task_entry")).fetch().intoMaps() shouldBe listOf(
//        mapOf(
//          "task_id" to task1,
//          "seq" to ULong.valueOf(1),
//          "started_at" to startedAt,
//          "ended_at" to endedAt,
//        ),
//      )
    }
  },
) {
  companion object : TestEnvironment() {
  }
}
