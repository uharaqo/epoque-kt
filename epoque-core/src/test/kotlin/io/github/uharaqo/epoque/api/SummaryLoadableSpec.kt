package io.github.uharaqo.epoque.api

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.right
import io.github.uharaqo.epoque.TestEvent.ResourceCreated
import io.github.uharaqo.epoque.api.EpoqueException.EventLoadFailure
import io.github.uharaqo.epoque.api.EpoqueException.SummaryAggregationFailure
import io.github.uharaqo.epoque.serialization.SerializedJson
import io.kotest.assertions.throwables.shouldThrowMessage
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flowOf

class SummaryLoadableSpec : StringSpec(
  {
    "EventLoader and SummaryGenerator work as SummaryLoadable" {
      val summary =
        Env.newSummaryLoadable().loadSummary(
          journalKey = Env.journalKey,
          tx = mockk(),
        ).getOrElse { throw it }

      summary.version shouldBe Version(2)
      summary.summary.list shouldBe listOf(Env.dummyEvent, Env.dummyEvent)
    }

    "SummaryLoadable works with cached summary" {
      val cachedSummary = VersionedSummary(Version(1), Env.MockSummary(listOf(Env.dummyEvent)))
      val summary =
        Env.newSummaryLoadable().loadSummary(
          journalKey = Env.journalKey,
          tx = mockk(),
          cachedSummary = cachedSummary,
        ).getOrElse { throw it }

      summary.version shouldBe Version(2)
      summary.summary.list shouldBe listOf(Env.dummyEvent, Env.dummyEvent)
    }

    "SummaryLoadable fails on version mismatch" {
      val dummyEventLoader = object : EventLoader {
        override fun queryById(
          journalKey: JournalKey,
          prevVersion: Version,
          tx: TransactionContext,
        ): Either<EventLoadFailure, Flow<VersionedEvent>> =
          flowOf(VersionedEvent(Version(2), Env.eventType, Env.dummyEvent)).right()
      }

      shouldThrowMessage("Event version mismatch. prev: 0, received: 2: ${Env.eventType}") {
        Env.newSummaryLoadable(dummyEventLoader).loadSummary(
          journalKey = Env.journalKey,
          tx = mockk(),
          cachedSummary = null,
        ).getOrElse { throw it }
      }
    }
  },
)

private object Env {

  val journalKey = JournalKey(JournalGroupId("foo"), JournalId("bar"))
  val eventType = EventType(ResourceCreated::class.qualifiedName!!)
  val dummyEvent = SerializedEvent(SerializedJson("{}"))

  data class MockSummary(val list: List<SerializedEvent> = emptyList())

  operator fun MockSummary.plus(event: SerializedEvent): MockSummary =
    this.copy(list = list + event)

  val dummyRecords = listOf(
    VersionedEvent(Version(1), eventType, dummyEvent),
    VersionedEvent(Version(2), eventType, dummyEvent),
  )

  val eventLoader = object : EventLoader {
    override fun queryById(
      journalKey: JournalKey,
      prevVersion: Version,
      tx: TransactionContext,
    ): Either<EventLoadFailure, Flow<VersionedEvent>> =
      dummyRecords.asSequence().drop(prevVersion.unwrap.toInt()).asFlow().right()
  }

  val summaryGenerator = object : SummaryGenerator<MockSummary> {
    override val emptySummary: MockSummary = MockSummary()

    override fun generateSummary(
      prevSummary: MockSummary,
      event: SerializedEvent,
    ): Either<SummaryAggregationFailure, MockSummary> = (prevSummary + event).right()
  }

  fun newSummaryLoadable(eventLoader: EventLoader = Env.eventLoader) =
    object : SummaryLoadable<MockSummary> {
      override val eventLoader = eventLoader
      override val summaryGenerator = Env.summaryGenerator
    }
}
