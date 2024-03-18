package io.github.uharaqo.epoque.impl

import io.github.uharaqo.epoque.api.CanComputeNextSummary
import io.github.uharaqo.epoque.api.EventCodecRegistry
import io.github.uharaqo.epoque.api.EventHandlerExecutor
import io.github.uharaqo.epoque.api.EventHandlerRegistry
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

class DefaultEventHandlerExecutor<S>(
  val journal: Journal<S, *>,
) : EventHandlerExecutor<S> by journal
