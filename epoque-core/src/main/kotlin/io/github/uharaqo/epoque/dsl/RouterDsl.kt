package io.github.uharaqo.epoque.dsl

import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.annotation.AnnotationTarget.FUNCTION
import kotlin.annotation.AnnotationTarget.PROPERTY
import kotlin.annotation.AnnotationTarget.TYPE

@DslMarker
@Target(CLASS, TYPE, FUNCTION, PROPERTY)
annotation class EpoqueDslMarker

@EpoqueDslMarker
abstract class EpoqueDsl {
  abstract fun environment(block: @EpoqueDslMarker EnvironmentDsl.() -> Unit)
}
