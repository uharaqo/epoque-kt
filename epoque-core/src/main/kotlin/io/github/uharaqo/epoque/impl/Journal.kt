package io.github.uharaqo.epoque.impl

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import io.github.uharaqo.epoque.api.CanComputeNextSummary
import io.github.uharaqo.epoque.api.EpoqueException.UnexpectedEvent
import io.github.uharaqo.epoque.api.EventCodecRegistry
import io.github.uharaqo.epoque.api.EventHandler
import io.github.uharaqo.epoque.api.EventHandlerExecutor
import io.github.uharaqo.epoque.api.EventHandlerRegistry
import io.github.uharaqo.epoque.api.EventType
import io.github.uharaqo.epoque.api.JournalGroupId
import kotlin.reflect.KClass

class Journal<S, E : Any>(
  val journalGroupId: JournalGroupId,
  val eventClass: KClass<E>,
  override val emptySummary: S,
  override val eventHandlerRegistry: EventHandlerRegistry<S, E>,
  override val eventCodecRegistry: EventCodecRegistry<E>,
) : CanComputeNextSummary<S, E> {
  override fun toString(): String = "Journal for $journalGroupId [$eventClass]"
}

class DefaultEventHandlerRegistry<S, E>(
  private val eventHandlerRegistry: Registry<EventType, EventHandler<S, E>>,
) : EventHandlerRegistry<S, E> {

  override fun find(eventType: EventType): Either<UnexpectedEvent, EventHandler<S, E>> =
    either {
      ensureNotNull(eventHandlerRegistry[eventType]) { UnexpectedEvent("Unexpected event: $eventType") }
    }
}

class DefaultEventHandlerExecutor<S>(
  val journal: Journal<S, *>,
) : EventHandlerExecutor<S> by journal
