package io.github.uharaqo.epoque.api

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.right
import io.github.uharaqo.epoque.api.EpoqueException.EventLoadFailure
import io.github.uharaqo.epoque.api.TestEnvironment.MockSummary
import io.kotest.assertions.throwables.shouldThrowMessage
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class SummaryLoadableSpec : StringSpec(
  {
    "EventLoader and SummaryGenerator work as SummaryLoadable" {
      val summary =
        newSummaryLoadable().loadSummary(
          journalKey = journalKey,
          tx = mockk(),
        ).getOrElse { throw it }

      summary.version shouldBe Version(2)
      summary.summary.list shouldBe listOf(dummyEvent1, dummyEvent2)
    }

    "SummaryLoadable works with cached summary" {
      val cachedSummary = VersionedSummary(Version(1), MockSummary(listOf(dummyEvent1)))
      val summary =
        newSummaryLoadable().loadSummary(
          journalKey = journalKey,
          tx = mockk(),
          cachedSummary = cachedSummary,
        ).getOrElse { throw it }

      summary.version shouldBe Version(2)
      summary.summary.list shouldBe listOf(dummyEvent1, dummyEvent2)
    }

    "SummaryLoadable fails on version mismatch" {
      val dummyEventLoader = object : EventLoader {
        override fun queryById(
          journalKey: JournalKey,
          prevVersion: Version,
          tx: TransactionContext,
        ): Either<EventLoadFailure, Flow<VersionedEvent>> =
          flowOf(VersionedEvent(Version(2), eventType, dummyEvent2)).right()
      }

      shouldThrowMessage("Event version mismatch. prev: 0, received: 2: $eventType") {
        newSummaryLoadable(dummyEventLoader).loadSummary(
          journalKey = journalKey,
          tx = mockk(),
          cachedSummary = null,
        ).getOrElse { throw it }
      }
    }
  },
) {
  companion object : TestEnvironment() {
    fun newSummaryLoadable(eventLoader: EventLoader = dummyEventLoader) =
      object : SummaryLoadable<MockSummary> {
        override val eventLoader = eventLoader
        override val summaryGenerator = dummySummaryGenerator
      }
  }
}
