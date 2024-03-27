package io.github.uharaqo.epoque.dsl

import io.github.uharaqo.epoque.api.EventHandler
import io.github.uharaqo.epoque.api.EventType
import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.annotation.AnnotationTarget.FUNCTION
import kotlin.annotation.AnnotationTarget.PROPERTY
import kotlin.annotation.AnnotationTarget.TYPE
import kotlin.annotation.AnnotationTarget.VALUE_PARAMETER

@DslMarker
@Target(CLASS, TYPE, FUNCTION, PROPERTY)
annotation class EventHandlersDslMarker

@JournalDslMarker
@EventHandlersDslMarker
abstract class EventHandlersDsl<S, E : Any> {
  inline fun <reified CE : E> onEvent(noinline block: EventHandlerDsl<S, CE>.() -> Unit) {
    val type = EventType.of<CE>()
    onEvent(type, block)
  }

  abstract fun <CE : E> onEvent(type: EventType, block: EventHandlerDsl<S, CE>.() -> Unit)
}

@DslMarker
@Target(CLASS, TYPE, FUNCTION, PROPERTY, VALUE_PARAMETER)
annotation class EventHandlerDslMarker

@EventHandlersDslMarker
@EventHandlerDslMarker
abstract class EventHandlerDsl<S, E> {
  @EventHandlerDslMarker
  abstract fun handle(eventHandler: EventHandler<S, E>)
}
