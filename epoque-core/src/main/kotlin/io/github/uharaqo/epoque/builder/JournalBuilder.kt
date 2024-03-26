package io.github.uharaqo.epoque.builder

import io.github.uharaqo.epoque.api.DataCodec
import io.github.uharaqo.epoque.api.DataCodecFactory
import io.github.uharaqo.epoque.api.EventHandler
import io.github.uharaqo.epoque.api.Journal
import io.github.uharaqo.epoque.api.JournalGroupId
import io.github.uharaqo.epoque.api.codecFor
import io.github.uharaqo.epoque.impl2.DefaultJournalBuilder

abstract class JournalBuilder<S, E : Any> {
  abstract val codecFactory: DataCodecFactory

  /** [CE]: Concrete type of the event */
  inline fun <reified CE : E> JournalBuilder<S, E>.eventHandlerFor(
    noinline eventHandler: (s: S, e: CE) -> S,
  ): JournalBuilder<S, E> =
    @Suppress("UNCHECKED_CAST")
    eventHandlerFor(
      codec = codecFactory.codecFor<CE>() as DataCodec<E>,
      handler = EventHandler { s: S, e: CE -> eventHandler(s, e) } as EventHandler<S, E>,
    )

  abstract fun eventHandlerFor(
    codec: DataCodec<E>,
    handler: EventHandler<S, E>,
  ): JournalBuilder<S, E>

  abstract fun build(): Journal<S, E>

  companion object {
    fun <S, E : Any> create(
      journalGroupId: JournalGroupId,
      emptySummary: S,
      codecFactory: DataCodecFactory,
      block: JournalBuilder<S, E>.() -> Unit,
    ): Journal<S, E> =
      DefaultJournalBuilder<S, E>(journalGroupId, emptySummary, codecFactory).apply(block).build()
  }
}
