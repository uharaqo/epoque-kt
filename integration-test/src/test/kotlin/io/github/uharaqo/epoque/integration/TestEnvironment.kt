package io.github.uharaqo.epoque.integration

import io.github.uharaqo.epoque.api.JournalId
import io.github.uharaqo.epoque.api.Metadata

abstract class TestEnvironment {
  val projectId1 = JournalId("Project1")
  val project1 = projectId1.unwrap
  val taskId1 = JournalId("My First Task")
  val task1 = taskId1.unwrap

  @JvmInline
  value class RequestId(val value: String) {
    companion object {
      val Key = Metadata.Key.of<RequestId>()
    }
  }
}
