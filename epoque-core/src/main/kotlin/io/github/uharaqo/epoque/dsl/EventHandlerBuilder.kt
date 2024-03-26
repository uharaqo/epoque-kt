package io.github.uharaqo.epoque.dsl

import io.github.uharaqo.epoque.api.EpoqueException.Cause.EVENT_NOT_SUPPORTED
import io.github.uharaqo.epoque.api.EventCodec
import io.github.uharaqo.epoque.api.EventCodecRegistry
import io.github.uharaqo.epoque.api.EventHandler
import io.github.uharaqo.epoque.api.EventHandlerRegistry
import io.github.uharaqo.epoque.api.EventType
import io.github.uharaqo.epoque.api.Journal
import io.github.uharaqo.epoque.impl.DefaultRegistry
import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.annotation.AnnotationTarget.FUNCTION
import kotlin.annotation.AnnotationTarget.PROPERTY
import kotlin.annotation.AnnotationTarget.TYPE
import kotlin.annotation.AnnotationTarget.VALUE_PARAMETER

data class EventHandlerEntry<S, E>(
  val ignoreUnknownEvents: Boolean, // TODO
  val codec: EventCodec<E>,
  val cacheOption: CacheOption,
  val handler: (S, E) -> S,
)

data class CacheOption(val enabled: Boolean) {
  companion object {
    val DEFAULT = CacheOption(false)
  }
}

fun <S, E> EpoqueJournal<*, S, E>.toJournal(
  eventCodecs: EventCodecRegistry,
  eventHandlers: EventHandlerRegistry<S, E>,
): Journal<S, E> =
  Journal(journalGroupId, emptySummary, eventHandlers, eventCodecs)

fun List<EpoqueJournal<*, *, *>>.toEventCodecRegistry(): EventCodecRegistry =
  this.asSequence()
    .flatMap { it.eventHandlers.entries }
    .map { (type, h) -> type to h.codec }
    .toMap()
    .let { map -> DefaultRegistry(map) { EVENT_NOT_SUPPORTED.toException(message = it.toString()) } }
    .let(::EventCodecRegistry)

fun List<EpoqueJournal<*, *, *>>.toEventHandlerRegistry(): EventHandlerRegistry<Any?, Any> =
  this.asSequence()
    .flatMap { j ->
      j.eventHandlers.entries.map { (type, h) ->
        @Suppress("UNCHECKED_CAST")
        type to h.toEventHandler() as EventHandler<Any?, Any>
      }
    }
    .toMap()
    .let { map -> DefaultRegistry(map) { EVENT_NOT_SUPPORTED.toException(message = it.toString()) } }
    .let { EventHandlerRegistry(it) }

@DslMarker
@Target(CLASS, FUNCTION, PROPERTY, TYPE, VALUE_PARAMETER)
annotation class EventHandlersDslMarker

@EventHandlersDslMarker
abstract class EventHandlersDsl<S, E : Any> {
  inline fun <reified CE : E> onEvent(
    noinline block: @EventHandlersDslMarker EventHandlerDsl<S, CE>.() -> Unit,
  ) {
    val type = EventType.of<CE>()
    onEvent(type, block)
  }

  abstract fun <CE : E> onEvent(
    type: EventType,
    block: @EventHandlersDslMarker EventHandlerDsl<S, CE>.() -> Unit,
  )
}

@DslMarker
@Target(CLASS, FUNCTION, PROPERTY, TYPE, VALUE_PARAMETER)
annotation class EventHandlerDslMarker

@EventHandlerDslMarker
abstract class EventHandlerDsl<S, E> {

  abstract fun ignoreUnknownEvents()
  abstract fun cache(block: CacheOptionDsl.() -> Unit)
  abstract fun handle(block: (S, E) -> S)
}

@EventHandlerDslMarker
interface CacheOptionDsl {
  fun build(defaultCacheOption: CacheOption): CacheOption
}
