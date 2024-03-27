package io.github.uharaqo.epoque.integration.epoque.project

import io.github.uharaqo.epoque.Epoque
import io.github.uharaqo.epoque.api.WriteOption
import io.github.uharaqo.epoque.codec.JsonCodecFactory
import io.github.uharaqo.epoque.dsl.journalFor
import io.github.uharaqo.epoque.integration.epoque.task.CreateTask

// Simple nullable summary without any subtypes
data object Project

val PROJECT_JOURNAL = Epoque.journalFor<ProjectCommand, Project?, ProjectEvent>(JsonCodecFactory()) {
  summaryCache = null

  commands {
    defaultWriteOption = WriteOption.DEFAULT

    onCommand<CreateProject> {
      writeOption = WriteOption.JOURNAL_LOCK
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

  events(null) {
    onEvent<ProjectCreated> {
      handle { s, e ->
        check(s == null) { "Project already exists" }
        Project
      }
    }
  }
}
