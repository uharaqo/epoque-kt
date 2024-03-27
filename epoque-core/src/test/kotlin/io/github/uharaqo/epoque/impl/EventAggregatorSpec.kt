package io.github.uharaqo.epoque.impl

import io.github.uharaqo.epoque.api.Version
import io.github.uharaqo.epoque.api.VersionedSummary
import io.kotest.assertions.arrow.core.rethrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class EventAggregatorSpec : StringSpec(
  {
    val expected = VersionedSummary(Version(2), TestEnvironment.TestSummary.Default(dummyEvents))

    "Load summary without cache" {
      val versionedSummary =
        TEST_JOURNAL.aggregateEvents(dummyVersionedEvents, null).rethrow()

      versionedSummary shouldBe expected
    }

    "Load with outdated cache" {
      val ver1 =
        VersionedSummary(
          Version(1),
          TestEnvironment.TestSummary.Default(dummyEvents.take(1)) as TestEnvironment.TestSummary,
        )
      val versionedSummary =
        TEST_JOURNAL.aggregateEvents(dummyVersionedEvents.drop(1), ver1).rethrow()

      versionedSummary shouldBe expected
    }

    "Load with latest cache, no more event to load" {
      val ver2 =
        VersionedSummary(
          Version(2),
          TestEnvironment.TestSummary.Default(dummyEvents) as TestEnvironment.TestSummary,
        )
      val versionedSummary =
        TEST_JOURNAL.aggregateEvents(emptyList(), ver2).rethrow()

      versionedSummary shouldBe expected
    }
  },
) {
  companion object : TestEnvironment()
}
