package io.github.uharaqo.epoque.dsl

import io.github.uharaqo.epoque.api.CommandExecutorOptions
import io.github.uharaqo.epoque.api.CommandType
import io.github.uharaqo.epoque.api.EpoqueException.Cause.COMMAND_REJECTED
import io.github.uharaqo.epoque.api.Journal
import io.github.uharaqo.epoque.api.JournalId
import io.github.uharaqo.epoque.api.JournalKey
import io.github.uharaqo.epoque.api.TransactionContext
import io.github.uharaqo.epoque.api.WriteOption
import io.github.uharaqo.epoque.api.keyFor
import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.annotation.AnnotationTarget.FUNCTION
import kotlin.annotation.AnnotationTarget.PROPERTY
import kotlin.annotation.AnnotationTarget.TYPE

@DslMarker
@Target(CLASS, TYPE, FUNCTION, PROPERTY)
annotation class CommandHandlersDslMarker

@JournalDslMarker
@CommandHandlersDslMarker
abstract class CommandHandlersDsl<C : Any, S, E : Any> {
  var defaultWriteOption = WriteOption.DEFAULT

  inline fun <reified CC : C> onCommand(noinline block: CommandHandlerDsl<CC, S, E>.() -> Unit) {
    onCommand(CommandType.of<CC>(), block)
  }

  abstract fun <CC : C> onCommand(
    type: CommandType,
    block: CommandHandlerDsl<CC, S, E>.() -> Unit,
  )
}

@DslMarker
@Target(CLASS, TYPE)
annotation class CommandHandlerDslMarker

@CommandHandlersDslMarker
@CommandHandlerDslMarker
abstract class CommandHandlerDsl<C, S, E> {
  var writeOption: WriteOption? = null

  abstract fun <X> prepare(block: suspend () -> X): PreparedCommandHandlerDsl<C, S, E, X>
  abstract fun handle(block: suspend CommandHandlerRuntimeEnvironment<C, S, E>.(C, S) -> Unit)
  abstract fun project(block: @CommandHandlerDslMarker suspend TransactionContext.() -> Unit)
  abstract fun notify(block: @CommandHandlerDslMarker suspend NotificationContext.() -> Unit)
}

abstract class PreparedCommandHandlerDsl<C, S, E, X> {
  abstract infix fun handle(block: suspend CommandHandlerRuntimeEnvironment<C, S, E>.(C, S, X) -> Unit)
}

@CommandHandlerDslMarker
interface CommandHandlerRuntimeEnvironment<C, S, E> {
  fun emit(event: E) = emit(listOf(event))
  fun emit(vararg events: E) = emit(events.toList())
  fun emit(events: List<E>, metadata: Map<Any, Any> = emptyMap())

  fun reject(message: String, t: Throwable? = null): Nothing =
    throw COMMAND_REJECTED.toException(t, message)

  fun chain(
    id: String,
    command: Any,
    options: CommandExecutorOptions = CommandExecutorOptions.DEFAULT,
    metadata: Map<Any, Any> = emptyMap(),
  ) = chain(JournalId(id), command, options, metadata)

  fun chain(
    id: JournalId,
    command: Any,
    options: CommandExecutorOptions = CommandExecutorOptions.DEFAULT,
    metadata: Map<Any, Any> = emptyMap(),
  )

  suspend fun exists(journal: Journal<*, *, *>, id: String?): Boolean =
    exists(id?.let { journal.keyFor(it) })

  suspend fun exists(key: JournalKey?): Boolean
}
