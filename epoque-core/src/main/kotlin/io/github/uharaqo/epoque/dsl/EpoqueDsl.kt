package io.github.uharaqo.epoque.dsl

import io.github.uharaqo.epoque.api.SummaryCache
import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.annotation.AnnotationTarget.FUNCTION
import kotlin.annotation.AnnotationTarget.PROPERTY
import kotlin.annotation.AnnotationTarget.TYPE

@DslMarker
@Target(CLASS, TYPE, FUNCTION, PROPERTY)
annotation class JournalDslMarker

@JournalDslMarker
abstract class JournalDsl<C : Any, S, E : Any> {
  var summaryCache: SummaryCache? = null

  abstract fun commands(block: CommandHandlersDsl<C, S, E>.() -> Unit)

  abstract fun events(emptySummary: S, block: EventHandlersDsl<S, E>.() -> Unit)
}
