package io.github.uharaqo.epoque.impl

import arrow.core.getOrElse
import arrow.core.right
import io.github.uharaqo.epoque.api.CanLoadSummary
import io.github.uharaqo.epoque.api.EventReader
import io.github.uharaqo.epoque.api.Failable
import io.github.uharaqo.epoque.api.JournalKey
import io.github.uharaqo.epoque.api.TransactionContext
import io.github.uharaqo.epoque.api.Version
import io.github.uharaqo.epoque.api.VersionedEvent
import io.github.uharaqo.epoque.api.VersionedSummary
import io.github.uharaqo.epoque.impl.TestEnvironment.MockSummary
import io.kotest.assertions.throwables.shouldThrowMessage
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class SummaryLoaderSpec : StringSpec(
  {
    "EventLoader and SummaryGenerator work as SummaryLoadable" {
      val summary =
        newSummaryLoader().loadSummary(
          key = dummyJournalKey,
          tx = mockk(),
        ).getOrElse { throw it }

      summary.version shouldBe Version(2)
      summary.summary.list shouldBe listOf(serializedEvent1, serializedEvent2)
    }

    "SummaryLoadable works with cached summary" {
      val cachedSummary = VersionedSummary(Version(1), MockSummary(listOf(serializedEvent1)))
      val summary =
        newSummaryLoader().loadSummary(
          key = dummyJournalKey,
          tx = mockk(),
          cachedSummary = cachedSummary,
        ).getOrElse { throw it }

      summary.version shouldBe Version(2)
      summary.summary.list shouldBe listOf(serializedEvent1, serializedEvent2)
    }

    "SummaryLoadable fails on version mismatch" {
      val dummyEventReader = object : EventReader {
        override fun queryById(
          key: JournalKey,
          prevVersion: Version,
          tx: TransactionContext,
        ): Failable<Flow<VersionedEvent>> =
          flowOf(VersionedEvent(Version(2), dummyEventType, serializedEvent2)).right()

        override suspend fun journalExists(
          key: JournalKey,
          tx: TransactionContext,
        ): Failable<Boolean> {
          TODO("Not yet implemented")
        }
      }

      shouldThrowMessage("SUMMARY_AGGREGATION_FAILURE: Event version mismatch. prev: 0, received: 2: $dummyEventType") {
        newSummaryLoader(dummyEventReader).loadSummary(
          key = dummyJournalKey,
          tx = mockk(),
          cachedSummary = null,
        ).getOrElse { throw it }
      }
    }
  },
) {
  companion object : TestEnvironment() {
    fun newSummaryLoader(eventReader: EventReader = dummyEventReader): CanLoadSummary<MockSummary> =
      object : CanLoadSummary<MockSummary> {
        override val eventReader = eventReader
        override val eventHandlerExecutor = dummyEventHandlerExecutor
      }
  }
}
