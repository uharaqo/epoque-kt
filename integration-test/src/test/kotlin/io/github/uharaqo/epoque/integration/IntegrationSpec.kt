package io.github.uharaqo.epoque.integration

import io.github.uharaqo.epoque.Epoque
import io.github.uharaqo.epoque.api.EpoqueException
import io.github.uharaqo.epoque.builder.ProjectionRegistry
import io.github.uharaqo.epoque.integration.TestEnvironment.RequestId
import io.github.uharaqo.epoque.integration.epoque.project.CreateProject
import io.github.uharaqo.epoque.integration.epoque.project.PROJECT_COMMANDS
import io.github.uharaqo.epoque.integration.epoque.project.PROJECT_JOURNAL
import io.github.uharaqo.epoque.integration.epoque.project.PROJECT_PROJECTIONS
import io.github.uharaqo.epoque.integration.epoque.project.Project
import io.github.uharaqo.epoque.integration.epoque.project.ProjectCreated
import io.github.uharaqo.epoque.integration.epoque.task.CreateTask
import io.github.uharaqo.epoque.integration.epoque.task.EndTask
import io.github.uharaqo.epoque.integration.epoque.task.StartTask
import io.github.uharaqo.epoque.integration.epoque.task.TASK_COMMANDS
import io.github.uharaqo.epoque.integration.epoque.task.TASK_JOURNAL
import io.github.uharaqo.epoque.integration.epoque.task.TASK_PROJECTIONS
import io.github.uharaqo.epoque.integration.epoque.task.Task
import io.github.uharaqo.epoque.integration.epoque.task.TaskCreated
import io.github.uharaqo.epoque.integration.epoque.task.TaskEnded
import io.github.uharaqo.epoque.integration.epoque.task.TaskStarted
import io.github.uharaqo.epoque.test.EpoqueTest
import io.github.uharaqo.epoque.test.impl.DebugLogger
import io.github.uharaqo.epoque.test.impl.newH2JooqContext
import io.github.uharaqo.epoque.test.impl.newTestEnvironment
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.throwable.shouldHaveMessage
import org.jooq.impl.DSL.table

class IntegrationSpec : StringSpec(
  {
    val startedAt = 123L
    val endedAt = 456L

    initTables(Epoque.newH2JooqContext())

    val callbackHandler = DebugLogger() +
      ProjectionRegistry.from(listOf(PROJECT_PROJECTIONS, TASK_PROJECTIONS)).toCallbackHandler()
    val environment = Epoque.newTestEnvironment(callbackHandler = callbackHandler)
    val tester = EpoqueTest.newTester(environment, PROJECT_COMMANDS, TASK_COMMANDS)

    val meta = mutableMapOf("RequestId" to RequestId("123"))

    val expectedTaskRecords = """Foo,TaskX,""
My First Task,My First Task,Project1
"""

    "Register project" {
      tester.forJournal(PROJECT_JOURNAL) {
        command(projectId1, CreateProject(project1), meta) {
          events shouldBe listOf(ProjectCreated(project1))
          summary shouldBe Project
        }
      }
      Epoque.newH2JooqContext().apply {
        selectFrom(table("task")).fetch().formatCSV(false) shouldBe "Foo,TaskX,\"\"\n"
      }
    }
    "Register project again and fail" {
      tester.forJournal(PROJECT_JOURNAL) {
        shouldThrow<EpoqueException> {
          command(projectId1, CreateProject(project1), meta) {
          }
        }.cause!! shouldHaveMessage "COMMAND_REJECTED: Project already exists"
      }
    }
    "Register task" {
      tester.forJournal(TASK_JOURNAL) {
        command(taskId1, CreateTask(task1, startedAt, project1, setOf("tag1", "tag2"))) {
          events shouldBe listOf(TaskCreated(task1, startedAt, project1, setOf("tag1", "tag2")))
          summary shouldBe Task.Default(false)
        }
      }
      Epoque.newH2JooqContext().apply {
        selectFrom(table("task")).fetch().formatCSV(false) shouldBe expectedTaskRecords
      }
    }
    "Start task" {
      tester.forJournal(TASK_JOURNAL) {
        command(taskId1, StartTask(startedAt)) {
          events shouldBe listOf(TaskStarted(startedAt))
          summary shouldBe Task.Default(true)
        }
      }
      Epoque.newH2JooqContext().apply {
        selectFrom(table("task")).fetch().formatCSV(false) shouldBe expectedTaskRecords
      }
    }
    "Start task again" {
      tester.forJournal(TASK_JOURNAL) {
        command(taskId1, StartTask(startedAt)) {
          events shouldBe emptyList()
          summary shouldBe Task.Default(true)
        }
      }
      Epoque.newH2JooqContext().apply {
        selectFrom(table("task")).fetch().formatCSV(false) shouldBe expectedTaskRecords
      }
    }
    "End task" {
      tester.forJournal(TASK_JOURNAL) {
        command(taskId1, EndTask(endedAt)) {
          events shouldBe listOf(TaskEnded(endedAt))
          summary shouldBe Task.Default(false)
        }
      }
      Epoque.newH2JooqContext().apply {
        selectFrom(table("task")).fetch().formatCSV(false) shouldBe expectedTaskRecords
      }
    }
    "End task again" {
      tester.forJournal(TASK_JOURNAL) {
        command(taskId1, EndTask(endedAt)) {
          events shouldBe emptyList()
          summary shouldBe Task.Default(false)
        }
      }
      Epoque.newH2JooqContext().apply {
        selectFrom(table("task")).fetch().formatCSV(false) shouldBe expectedTaskRecords
      }
    }

    "Projection Result" {
      fun Map<String, Any?>.fixKeys() = mapKeys { (k, _) -> k.uppercase() }
      Epoque.newH2JooqContext().apply {
        println(selectFrom(table("task")).fetch())
      }

      Epoque.newH2JooqContext().apply {
        selectFrom(table("project")).fetch().intoMaps() shouldBe listOf(
          mapOf("id" to project1, "name" to project1).fixKeys(),
        )
        selectFrom(table("task")).fetch().intoMaps() shouldBe listOf(
          mapOf("id" to "Foo", "name" to "TaskX", "project_id" to null).fixKeys(),
          mapOf("id" to task1, "name" to task1, "project_id" to project1).fixKeys(),
        )
        selectFrom(table("task_entry")).fetch().intoMaps() shouldBe listOf(
          mapOf(
            "task_id" to task1,
            "seq" to 1,
            "started_at" to startedAt,
            "ended_at" to endedAt,
          ).fixKeys(),
        )
      }
    }
  },
) {
  companion object : TestEnvironment()
}
