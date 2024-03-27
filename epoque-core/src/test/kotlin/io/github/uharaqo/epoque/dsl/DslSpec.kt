package io.github.uharaqo.epoque.dsl

import io.github.uharaqo.epoque.Epoque
import io.github.uharaqo.epoque.api.CommandInput
import io.github.uharaqo.epoque.api.SerializedEvent
import io.github.uharaqo.epoque.api.SummaryId
import io.github.uharaqo.epoque.api.SummaryType
import io.github.uharaqo.epoque.api.Version
import io.github.uharaqo.epoque.api.VersionedSummary
import io.github.uharaqo.epoque.codec.JsonCodecFactory
import io.github.uharaqo.epoque.codec.SerializedJson
import io.github.uharaqo.epoque.impl.TestEnvironment
import io.github.uharaqo.epoque.impl.TestEnvironment.TestCommand
import io.github.uharaqo.epoque.impl.TestEnvironment.TestCommand.Create
import io.github.uharaqo.epoque.impl.TestEnvironment.TestCommand.EmptyCommand
import io.github.uharaqo.epoque.impl.TestEnvironment.TestEvent
import io.github.uharaqo.epoque.impl.TestEnvironment.TestEvent.ResourceCreated
import io.github.uharaqo.epoque.impl.TestEnvironment.TestSummary
import io.kotest.assertions.arrow.core.rethrow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.throwable.shouldHaveMessage

class DslSpec : StringSpec(
  {
    "'events' must be defined" {
      shouldThrow<IllegalStateException> {
        Epoque.journalFor<TestCommand, TestSummary, TestEvent>(JsonCodecFactory()) {
        }
      } shouldHaveMessage "'events' must be defined"
    }

    "'commands' must be defined" {
      shouldThrow<IllegalStateException> {
        Epoque.journalFor<TestCommand, TestSummary, TestEvent>(JsonCodecFactory()) {
          events(TestSummary.Empty) {
          }
        }
      } shouldHaveMessage "'commands' must be defined"
    }

    "Empty but it's valid" {
      Epoque.journalFor<TestCommand, TestSummary, TestEvent>(JsonCodecFactory()) {
        events(TestSummary.Empty) {
        }
        commands {
        }
      }
    }

    "'events.onEvent.handle' must be defined" {
      shouldThrow<IllegalStateException> {
        Epoque.journalFor<TestCommand, TestSummary, TestEvent>(JsonCodecFactory()) {
          events(TestSummary.Empty) {
            onEvent<ResourceCreated> {
            }
          }
          commands {
          }
        }
      } shouldHaveMessage "'events.onEvent.handle' must be defined"
    }

    "'commands.onCommand.handle' must be defined" {
      shouldThrow<IllegalStateException> {
        Epoque.journalFor<TestCommand, TestSummary, TestEvent>(JsonCodecFactory()) {
          events(TestSummary.Empty) {
            onEvent<ResourceCreated> {
              handle { s, e -> s }
            }
          }
          commands {
            onCommand<Create> { }
          }
        }
      } shouldHaveMessage "'commands.onCommand.handle' must be defined"
    }

    "'environment' must be defined" {
      shouldThrow<IllegalStateException> {
        Epoque.routerFor(
          Epoque.journalFor<TestCommand, TestSummary, TestEvent>(JsonCodecFactory()) {
            events(TestSummary.Empty) {
              onEvent<ResourceCreated> {
                handle { s, e -> s }
              }
            }
            commands {
              onCommand<Create> {
                handle { s, e -> }
              }
            }
          },
        ) {
          // empty
        }
      } shouldHaveMessage "'environment' must be defined"
    }

    "Minimum setup" {
      val (eventWriter, capturedOutput) = newMockWriter()

      val router = Epoque.routerFor(
        Epoque.journalFor<TestCommand, TestSummary, TestEvent>(JsonCodecFactory()) {
          events(TestSummary.Empty) {
            onEvent<ResourceCreated> {
              handle { s, e -> s }
            }
          }
          commands {
            onCommand<Create> {
              handle { s, e -> emit(dummyEvents) }
            }
          }
        },
      ) { environment { eventStore = dummyEventStore(eventWriter) } }

      val out = router.process(input).rethrow()
      out.events shouldBe dummyOutputEvents
      capturedOutput.captured.events shouldBe dummyOutputEvents
    }

    "Full features" {
      val (eventWriter, capturedOutput) = newMockWriter()
      val recorded = mutableListOf<String>()
      fun record(s: String) {
        println("[[[$s]]]")
        recorded += s
      }

      val cache = MapSummaryCache()

      val router = Epoque.routerFor(
        Epoque.journalFor<TestCommand, TestSummary, TestEvent>(JsonCodecFactory()) {
          summaryCache = cache

          events(TestSummary.Empty) {
            onEvent<ResourceCreated> {
              handle { s, e -> s }
            }
          }
          commands {
            onCommand<EmptyCommand> {
              handle { c, s ->
                record("Chained")
              }
            }
            onCommand<Create> {
              prepare {
                record("Prepared")
                "Prepared Param"
              } handle { s, e, x ->
                record("Handled")
                exists(dummyJournalKey)
                emit(ResourceCreated(x))
                chain(dummyJournalKey.id, EmptyCommand)
              }

              project { record("Projected") }

              notify { record("Notified") }
            }
          }
        },
      ) {
        environment {
          eventStore = dummyEventStore(eventWriter)
          globalCallbackHandler = loggingCallbackHandler
        }
      }

      val out = router.process(input).rethrow()

      out.events.first() shouldBe dummyOutputEvents.first()
        .copy(event = SerializedEvent(SerializedJson("""{"name":"Prepared Param"}""")))

      recorded shouldBe listOf("Prepared", "Handled", "Projected", "Chained", "Notified")
      cache.get<TestSummary>(SummaryId(SummaryType.of<TestSummary>(), dummyJournalKey)) shouldBe
        VersionedSummary(Version(2), TestSummary.Empty)
    }
  },
) {
  companion object : TestEnvironment() {
    val input = CommandInput(dummyJournalKey.id, dummyCommandType, serializedCommand)
  }
}
