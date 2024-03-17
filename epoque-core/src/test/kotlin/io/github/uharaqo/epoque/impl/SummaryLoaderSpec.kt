package io.github.uharaqo.epoque.impl

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.right
import io.github.uharaqo.epoque.api.CanLoadSummary
import io.github.uharaqo.epoque.api.EpoqueException.EventLoadFailure
import io.github.uharaqo.epoque.api.EventLoader
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
          journalKey = dummyJournalKey,
          tx = mockk(),
        ).getOrElse { throw it }

      summary.version shouldBe Version(2)
      summary.summary.list shouldBe listOf(serializedEvent1, serializedEvent2)
    }

    "SummaryLoadable works with cached summary" {
      val cachedSummary = VersionedSummary(Version(1), MockSummary(listOf(serializedEvent1)))
      val summary =
        newSummaryLoader().loadSummary(
          journalKey = dummyJournalKey,
          tx = mockk(),
          cachedSummary = cachedSummary,
        ).getOrElse { throw it }

      summary.version shouldBe Version(2)
      summary.summary.list shouldBe listOf(serializedEvent1, serializedEvent2)
    }

    "SummaryLoadable fails on version mismatch" {
      val dummyEventLoader = object : EventLoader {
        override fun queryById(
          journalKey: JournalKey,
          prevVersion: Version,
          tx: TransactionContext,
        ): Either<EventLoadFailure, Flow<VersionedEvent>> =
          flowOf(VersionedEvent(Version(2), resourceCreatedEventType, serializedEvent2)).right()
      }

      shouldThrowMessage("Event version mismatch. prev: 0, received: 2: $resourceCreatedEventType") {
        newSummaryLoader(dummyEventLoader).loadSummary(
          journalKey = dummyJournalKey,
          tx = mockk(),
          cachedSummary = null,
        ).getOrElse { throw it }
      }
    }
  },
) {
  companion object : TestEnvironment() {
    fun newSummaryLoader(eventLoader: EventLoader = dummyEventLoader): CanLoadSummary<MockSummary> =
      object : CanLoadSummary<MockSummary> {
        override val eventLoader = eventLoader
        override val eventHandlerExecutor = dummyEventHandlerExecutor
      }
  }
}
