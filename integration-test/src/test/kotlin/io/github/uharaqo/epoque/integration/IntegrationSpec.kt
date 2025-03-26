package io.github.uharaqo.epoque.integration

import io.github.uharaqo.epoque.Epoque
import io.github.uharaqo.epoque.api.EpoqueException
import io.github.uharaqo.epoque.api.JournalId
import io.github.uharaqo.epoque.dsl.ProjectionRegistry
import io.github.uharaqo.epoque.dsl.routerFor
import io.github.uharaqo.epoque.integration.epoque.project.CreateProject
import io.github.uharaqo.epoque.integration.epoque.project.PROJECT_JOURNAL
import io.github.uharaqo.epoque.integration.epoque.project.PROJECT_PROJECTIONS
import io.github.uharaqo.epoque.integration.epoque.project.Project
import io.github.uharaqo.epoque.integration.epoque.project.ProjectCreated
import io.github.uharaqo.epoque.integration.epoque.task.CreateAndStartTask
import io.github.uharaqo.epoque.integration.epoque.task.CreateTask
import io.github.uharaqo.epoque.integration.epoque.task.EndTask
import io.github.uharaqo.epoque.integration.epoque.task.StartTask
import io.github.uharaqo.epoque.integration.epoque.task.TASK_JOURNAL
import io.github.uharaqo.epoque.integration.epoque.task.TASK_PROJECTIONS
import io.github.uharaqo.epoque.integration.epoque.task.Task
import io.github.uharaqo.epoque.integration.epoque.task.TaskCreated
import io.github.uharaqo.epoque.integration.epoque.task.TaskEnded
import io.github.uharaqo.epoque.integration.epoque.task.TaskStarted
import io.github.uharaqo.epoque.test.api.H2JooqDbEnvironment
import io.github.uharaqo.epoque.test.impl.DebugLogger
import io.github.uharaqo.epoque.test.impl.newTestEnvironment
import io.github.uharaqo.epoque.test.impl.newTester
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.throwable.shouldHaveMessage
import org.jooq.impl.DSL

class IntegrationSpec : StringSpec(
  {
    initReadModels(dsl)

    val router = Epoque.routerFor(PROJECT_JOURNAL, TASK_JOURNAL) {
      environment {
        eventStore = Companion.eventStore
        globalCallbackHandler =
          DebugLogger() +
            ProjectionRegistry.from(listOf(PROJECT_PROJECTIONS, TASK_PROJECTIONS))
              .toCallbackHandler()
      }
    }

    val callbackHandler = DebugLogger() +
      ProjectionRegistry.from(listOf(PROJECT_PROJECTIONS, TASK_PROJECTIONS)).toCallbackHandler()
    val environment =
      Epoque.newTestEnvironment(Companion, globalCallbackHandler = callbackHandler)
    val tester = Epoque.newTester(router, environment)

    val meta = mutableMapOf("RequestId" to TestEnvironment.RequestId("123"))

    val projectTester = tester.forJournal(PROJECT_JOURNAL)
    val taskTester = tester.forJournal(TASK_JOURNAL)

    "Register project" {
      projectTester.command(projectId1, CreateProject(project1), meta) {
        events.size shouldBe 2
        events[0] shouldBe ProjectCreated(project1)
        (events[1] as? TaskCreated)?.name shouldBe "TaskX"
        summary shouldBe Project
      }
    }

    "Register project again and fail" {
      shouldThrow<EpoqueException> {
        projectTester.command(projectId1, CreateProject(project1), meta) {}
      } shouldHaveMessage "COMMAND_REJECTED: Project already exists"
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
      dsl.apply {
        selectFrom(DSL.table("project")).fetch().intoMaps() shouldBe listOf(
          mapOf("id" to project1, "name" to project1),
        )
        selectFrom(DSL.table("task")).fetch().intoMaps() shouldBe listOf(
          mapOf("id" to "Foo", "name" to "TaskX", "project_id" to null),
          mapOf("id" to task1, "name" to task1, "project_id" to project1),
        )
        selectFrom(DSL.table("task_entry")).fetch().intoMaps() shouldBe listOf(
          mapOf(
            "task_id" to task1,
            "seq" to 1, // for mysql, use org.jooq.types.ULong.valueOf(1)
            "started_at" to startedAt,
            "ended_at" to endedAt,
          ),
        )
      }
    }

    "Create and start task (batch)" {
      val taskId2 = JournalId("My Second Task")
      val task2 = taskId2.unwrap
      taskTester.command(
        taskId2,
        CreateAndStartTask(
          CreateTask(task2, startedAt, project1, setOf("tag1", "tag2")),
          StartTask(startedAt),
        ),
      ) {
        events shouldBe listOf(
          TaskCreated(task2, startedAt, project1, setOf("tag1", "tag2")),
          TaskStarted(startedAt),
        )
        summary shouldBe Task.Default(true)
      }
    }
  },
) {
  companion object : TestEnvironment(H2JooqDbEnvironment())
//  companion object : TestEnvironment(MySqlDbEnvironment())
}
