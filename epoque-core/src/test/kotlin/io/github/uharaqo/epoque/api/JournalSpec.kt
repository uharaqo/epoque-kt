package io.github.uharaqo.epoque.api

import io.github.uharaqo.epoque.TestEnvironment
import io.github.uharaqo.epoque.TestEnvironment.TestSummary
import io.kotest.assertions.arrow.core.rethrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class JournalSpec : StringSpec(
  {
    "CanAggregateEvents works as expected" {
      // given
      val canAggregateEvents = TEST_JOURNAL

      // when
      val result = canAggregateEvents.aggregateEvents(dummyVersionedEvents, null).rethrow()

      // then
      result shouldBe VersionedSummary(Version(2), TestSummary.Default(dummyEvents))
    }
  },
) {
  companion object : TestEnvironment()
}
