package io.github.uharaqo.epoque.integration

import io.github.uharaqo.epoque.Epoque
import io.github.uharaqo.epoque.api.EpoqueException
import io.github.uharaqo.epoque.dsl.ProjectionRegistry
import io.github.uharaqo.epoque.dsl.routerFor
import io.github.uharaqo.epoque.integration.epoque.project.CreateProject
import io.github.uharaqo.epoque.integration.epoque.project.PROJECT_JOURNAL
import io.github.uharaqo.epoque.integration.epoque.project.PROJECT_PROJECTIONS
import io.github.uharaqo.epoque.integration.epoque.project.Project
import io.github.uharaqo.epoque.integration.epoque.project.ProjectCreated
import io.github.uharaqo.epoque.integration.epoque.task.CreateTask
import io.github.uharaqo.epoque.integration.epoque.task.EndTask
import io.github.uharaqo.epoque.integration.epoque.task.StartTask
import io.github.uharaqo.epoque.integration.epoque.task.TASK_JOURNAL
import io.github.uharaqo.epoque.integration.epoque.task.TASK_PROJECTIONS
import io.github.uharaqo.epoque.integration.epoque.task.Task
import io.github.uharaqo.epoque.integration.epoque.task.TaskCreated
import io.github.uharaqo.epoque.integration.epoque.task.TaskEnded
import io.github.uharaqo.epoque.integration.epoque.task.TaskStarted
import io.github.uharaqo.epoque.test.impl.DebugLogger
import io.github.uharaqo.epoque.test.impl.newH2EventStore
import io.github.uharaqo.epoque.test.impl.newH2JooqContext
import io.github.uharaqo.epoque.test.impl.newTestEnvironment
import io.github.uharaqo.epoque.test.impl.newTester
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.throwable.shouldHaveMessage
import org.jooq.impl.DSL

class IntegrationSpec : StringSpec(
  {
    val startedAt = 123L
    val endedAt = 456L

    initTables(Epoque.newH2JooqContext())

    val callbackHandler = DebugLogger() +
      ProjectionRegistry.from(listOf(PROJECT_PROJECTIONS, TASK_PROJECTIONS)).toCallbackHandler()
    val environment = Epoque.newTestEnvironment(globalCallbackHandler = callbackHandler)
    val tester = Epoque.newTester(ROUTER, environment)

    val meta = mutableMapOf("RequestId" to TestEnvironment.RequestId("123"))

    val projectTester = tester.forJournal(PROJECT_JOURNAL)
    val taskTester = tester.forJournal(TASK_JOURNAL)

    "Register project" {
      projectTester.command(projectId1, CreateProject(project1), meta) {
        events shouldBe listOf(ProjectCreated(project1))
        summary shouldBe Project
      }
    }
    "Register project again and fail" {
      shouldThrow<EpoqueException> {
        projectTester.command(projectId1, CreateProject(project1), meta) {
        }
      }.cause!! shouldHaveMessage "COMMAND_REJECTED: Project already exists"
    }
    "Register task" {
      taskTester.command(taskId1, CreateTask(task1, startedAt, project1, setOf("tag1", "tag2"))) {
        events shouldBe listOf(TaskCreated(task1, startedAt, project1, setOf("tag1", "tag2")))
        summary shouldBe Task.Default(false)
      }
    }
    "Start task" {
      taskTester.command(taskId1, StartTask(startedAt)) {
        events shouldBe listOf(TaskStarted(startedAt))
        summary shouldBe Task.Default(true)
      }
    }
    "Start task again" {
      taskTester.command(taskId1, StartTask(startedAt)) {
        events shouldBe emptyList()
        summary shouldBe Task.Default(true)
      }
    }
    "End task" {
      taskTester.command(taskId1, EndTask(endedAt)) {
        events shouldBe listOf(TaskEnded(endedAt))
        summary shouldBe Task.Default(false)
      }
    }
    "End task again" {
      taskTester.command(taskId1, EndTask(endedAt)) {
        events shouldBe emptyList()
        summary shouldBe Task.Default(false)
      }
    }

    "Projection Result" {
      fun Map<String, Any?>.fixKeys() = mapKeys { (k, _) -> k.uppercase() }

      Epoque.newH2JooqContext().apply {
        selectFrom(DSL.table("project")).fetch()
          .intoMaps() shouldBe listOf(
          mapOf("id" to project1, "name" to project1).fixKeys(),
        )
        selectFrom(DSL.table("task")).fetch()
          .intoMaps() shouldBe listOf(
          mapOf("id" to "Foo", "name" to "TaskX", "project_id" to null).fixKeys(),
          mapOf("id" to task1, "name" to task1, "project_id" to project1).fixKeys(),
        )
        selectFrom(DSL.table("task_entry")).fetch()
          .intoMaps() shouldBe listOf(
          mapOf("task_id" to task1, "seq" to 1, "started_at" to startedAt, "ended_at" to endedAt)
            .fixKeys(),
        )
      }
    }
  },
) {
  companion object : TestEnvironment() {
    val ROUTER = Epoque.routerFor(PROJECT_JOURNAL, TASK_JOURNAL) {
      environment {
        eventStore = Epoque.newH2EventStore()
        globalCallbackHandler =
          DebugLogger() +
            ProjectionRegistry.from(listOf(PROJECT_PROJECTIONS, TASK_PROJECTIONS))
              .toCallbackHandler()
      }
    }
  }
}
