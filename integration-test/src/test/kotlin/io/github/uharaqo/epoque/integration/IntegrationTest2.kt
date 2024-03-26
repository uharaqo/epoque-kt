package io.github.uharaqo.epoque.integration

import io.github.uharaqo.epoque.Epoque
import io.github.uharaqo.epoque.api.EpoqueException
import io.github.uharaqo.epoque.api.WriteOption
import io.github.uharaqo.epoque.builder.ProjectionRegistry
import io.github.uharaqo.epoque.codec.KotlinxJsonCodecFactory
import io.github.uharaqo.epoque.dsl.CacheOption
import io.github.uharaqo.epoque.dsl.journalFor
import io.github.uharaqo.epoque.dsl.routerFor
import io.github.uharaqo.epoque.integration.epoque.project.CreateProject
import io.github.uharaqo.epoque.integration.epoque.project.PROJECT_PROJECTIONS
import io.github.uharaqo.epoque.integration.epoque.project.Project
import io.github.uharaqo.epoque.integration.epoque.project.ProjectCommand
import io.github.uharaqo.epoque.integration.epoque.project.ProjectCreated
import io.github.uharaqo.epoque.integration.epoque.project.ProjectEvent
import io.github.uharaqo.epoque.integration.epoque.project.toProjectCreated
import io.github.uharaqo.epoque.integration.epoque.task.CreateTask
import io.github.uharaqo.epoque.integration.epoque.task.EndTask
import io.github.uharaqo.epoque.integration.epoque.task.StartTask
import io.github.uharaqo.epoque.integration.epoque.task.TASK_PROJECTIONS
import io.github.uharaqo.epoque.integration.epoque.task.Task
import io.github.uharaqo.epoque.integration.epoque.task.TaskCommand
import io.github.uharaqo.epoque.integration.epoque.task.TaskCreated
import io.github.uharaqo.epoque.integration.epoque.task.TaskEnded
import io.github.uharaqo.epoque.integration.epoque.task.TaskEvent
import io.github.uharaqo.epoque.integration.epoque.task.TaskStarted
import io.github.uharaqo.epoque.integration.epoque.task.toTaskCreated
import io.github.uharaqo.epoque.integration.epoque.task.toTaskEnded
import io.github.uharaqo.epoque.integration.epoque.task.toTaskStarted
import io.github.uharaqo.epoque.test.impl.DebugLogger
import io.github.uharaqo.epoque.test.impl.newH2EventStore
import io.github.uharaqo.epoque.test.impl.newH2JooqContext
import io.github.uharaqo.epoque.test.impl.newTestEnvironment
import io.github.uharaqo.epoque.test.impl.newTester
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.throwable.shouldHaveMessage
import org.jooq.impl.DSL.table

class IntegrationTest2 : StringSpec(
  {
    val startedAt = 123L
    val endedAt = 456L

    initTables(Epoque.newH2JooqContext())

    val callbackHandler = DebugLogger() +
      ProjectionRegistry.from(listOf(PROJECT_PROJECTIONS, TASK_PROJECTIONS)).toCallbackHandler()
    val environment = Epoque.newTestEnvironment(callbackHandler = callbackHandler)
    val tester = Epoque.newTester(ROUTER, environment)

    val meta = mutableMapOf("RequestId" to TestEnvironment.RequestId("123"))

    val projectTester = tester.forJournal(ROUTER.toJournal(PROJECT_JOURNAL))
    val taskTester = tester.forJournal(ROUTER.toJournal(TASK_JOURNAL))

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
        selectFrom(table("project")).fetch()
          .intoMaps() shouldBe listOf(
          mapOf("id" to project1, "name" to project1).fixKeys(),
        )
        selectFrom(table("task")).fetch()
          .intoMaps() shouldBe listOf(
          mapOf("id" to "Foo", "name" to "TaskX", "project_id" to null).fixKeys(),
          mapOf("id" to task1, "name" to task1, "project_id" to project1).fixKeys(),
        )
        selectFrom(table("task_entry")).fetch()
          .intoMaps() shouldBe listOf(
          mapOf("task_id" to task1, "seq" to 1, "started_at" to startedAt, "ended_at" to endedAt)
            .fixKeys(),
        )
      }
    }
  },
) {
  companion object : TestEnvironment()
}

val codecFactory = KotlinxJsonCodecFactory()

val PROJECT_JOURNAL = Epoque.journalFor<ProjectCommand, Project?, ProjectEvent>(codecFactory) {

  commands(defaultWriteOption = WriteOption.JOURNAL_LOCK) {
    onCommand<CreateProject> {
      prepare {
        "PREPARED"
      } handle { c, s, x ->
        println("Prepared: $x")
        if (s != null) reject("Project already exists")
        chain("Foo", CreateTask("TaskX"))
        emit(c.toProjectCreated())
      }

      project {
        println("PROJECTION")
      }

      notify {
        println("NOTIFICATION SENT")
      }
    }
  }

  events(null, defaultCacheOption = CacheOption.DEFAULT) {
    onEvent<ProjectCreated> {
      ignoreUnknownEvents()

      cache {
      }

      handle { s, e ->
        check(s == null) { "Project already exists" }
        Project
      }
    }
  }
}

val TASK_JOURNAL = Epoque.journalFor<TaskCommand, Task, TaskEvent>(codecFactory) {

  commands(defaultWriteOption = WriteOption.JOURNAL_LOCK) {
    onCommand<CreateTask> {
      handle { c, s ->
        if (s !is Task.Empty) reject("Already created")
        if (c.project != null && !exists(PROJECT_JOURNAL, c.project)) reject("Project Not Found")

        emit(c.toTaskCreated())
      }
    }
    onCommand<StartTask> {
      handle { c, s ->
        if (s !is Task.Default) reject("Not found")
        if (!s.started) emit(c.toTaskStarted())
      }
    }
    onCommand<EndTask> {
      handle { c, s ->
        if (s !is Task.Default) reject("Not found")
        if (s.started) emit(c.toTaskEnded())
      }
    }
  }

  events(Task.Empty, defaultCacheOption = CacheOption.DEFAULT) {
    onEvent<TaskCreated> {
      handle { s, e ->
        check(s is Task.Empty) { "Already created" }
        Task.Default(false)
      }
    }
    onEvent<TaskStarted> {
      handle { s, e ->
        check(s !is Task.Empty)
        Task.Default(true)
      }
    }
    onEvent<TaskEnded> {
      handle { s, e ->
        check(s !is Task.Empty)
        Task.Default(false)
      }
    }
  }
}

val ROUTER = Epoque.routerFor(PROJECT_JOURNAL, TASK_JOURNAL) {
  environment {
    eventStore = Epoque.newH2EventStore()
    callbackHandler =
      DebugLogger() +
        ProjectionRegistry.from(listOf(PROJECT_PROJECTIONS, TASK_PROJECTIONS)).toCallbackHandler()
  }
}
