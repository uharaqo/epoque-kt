package io.github.uharaqo.epoque.dsl

import io.github.uharaqo.epoque.api.CallbackHandler
import io.github.uharaqo.epoque.api.CommandExecutorOptions
import io.github.uharaqo.epoque.api.EventReader
import io.github.uharaqo.epoque.api.EventStore
import io.github.uharaqo.epoque.api.EventWriter
import io.github.uharaqo.epoque.api.SummaryCache
import io.github.uharaqo.epoque.api.TransactionStarter
import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.annotation.AnnotationTarget.FUNCTION
import kotlin.annotation.AnnotationTarget.PROPERTY
import kotlin.annotation.AnnotationTarget.TYPE

@DslMarker
@Target(CLASS, TYPE, FUNCTION, PROPERTY)
annotation class EnvironmentDslMarker

@EnvironmentDslMarker
abstract class EnvironmentDsl {
  var eventReader: EventReader? = null
  var eventWriter: EventWriter? = null
  var transactionStarter: TransactionStarter? = null
  var eventStore: EventStore? = null
  var defaultCommandExecutorOptions: CommandExecutorOptions? = null
  var globalCallbackHandler: CallbackHandler? = null
  var globalCache: SummaryCache? = null
}
