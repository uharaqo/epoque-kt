package io.github.uharaqo.epoque.dsl

import io.github.uharaqo.epoque.Epoque
import io.github.uharaqo.epoque.api.CommandType
import io.github.uharaqo.epoque.api.DataCodecFactory
import io.github.uharaqo.epoque.api.EventType
import io.github.uharaqo.epoque.api.JournalGroupId
import io.github.uharaqo.epoque.api.JournalId
import io.github.uharaqo.epoque.api.JournalKey
import io.github.uharaqo.epoque.api.WriteOption
import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.annotation.AnnotationTarget.FUNCTION
import kotlin.annotation.AnnotationTarget.PROPERTY
import kotlin.annotation.AnnotationTarget.TYPE
import kotlin.annotation.AnnotationTarget.VALUE_PARAMETER

data class EpoqueJournal<C, S, E>(
  val journalGroupId: JournalGroupId,
  val emptySummary: S,
  val eventHandlers: Map<EventType, EventHandlerEntry<S, E>>,
  val commandHandlers: Map<CommandType, CommandHandlerEntry<C, S, E>>,
)

fun EpoqueJournal<*, *, *>.keyFor(id: String): JournalKey =
  JournalKey(journalGroupId, JournalId(id))

inline fun <C : Any, S, reified E : Any> Epoque.journalFor(
  codecFactory: DataCodecFactory,
  noinline block: JournalDsl<C, S, E>.() -> Unit,
): EpoqueJournal<C, S, E> =
  journalFor(JournalGroupId.of<E>(), codecFactory, block)

fun <C : Any, S, E : Any> Epoque.journalFor(
  journalGroupId: JournalGroupId,
  codecFactory: DataCodecFactory,
  block: JournalDsl<C, S, E>.() -> Unit,
): EpoqueJournal<C, S, E> =
  JournalBuilder<C, S, E>(journalGroupId).apply(block).build(codecFactory)

@DslMarker
@Target(CLASS, FUNCTION, PROPERTY, TYPE, VALUE_PARAMETER)
annotation class JournalDslMarker

@JournalDslMarker
interface JournalDsl<C : Any, S, E : Any> {

  fun commands(
    defaultWriteOption: WriteOption = WriteOption.DEFAULT,
    block: @JournalDslMarker CommandHandlersDsl<C, S, E>.() -> Unit,
  )

  fun events(
    emptySummary: S,
    defaultCacheOption: CacheOption = CacheOption.DEFAULT,
    block: @JournalDslMarker EventHandlersDsl<S, E>.() -> Unit,
  )
}
