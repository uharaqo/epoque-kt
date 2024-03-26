package io.github.uharaqo.epoque.dsl

import io.github.uharaqo.epoque.api.CommandExecutorOptions
import io.github.uharaqo.epoque.api.CommandHandlerOutput
import io.github.uharaqo.epoque.api.CommandType
import io.github.uharaqo.epoque.api.EpoqueException.Cause.COMMAND_REJECTED
import io.github.uharaqo.epoque.api.JournalId
import io.github.uharaqo.epoque.api.JournalKey
import io.github.uharaqo.epoque.api.TransactionContext
import io.github.uharaqo.epoque.api.WriteOption
import java.util.concurrent.atomic.AtomicReference
import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.annotation.AnnotationTarget.FUNCTION
import kotlin.annotation.AnnotationTarget.PROPERTY
import kotlin.annotation.AnnotationTarget.TYPE
import kotlin.annotation.AnnotationTarget.VALUE_PARAMETER

@DslMarker
@Target(CLASS, FUNCTION, PROPERTY, TYPE, VALUE_PARAMETER)
annotation class CommandHandlersDslMarker

@CommandHandlersDslMarker
abstract class CommandHandlersDsl<C : Any, S, E : Any> {
  inline fun <reified CC : C> onCommand(
    writeOption: WriteOption? = null,
    noinline block: @CommandHandlersDslMarker CommandHandlerDsl<CC, S, E>.() -> Unit,
  ) {
    val type = CommandType.of<CC>()
    onCommand(writeOption, type, block)
  }

  abstract fun <CC : C> onCommand(
    writeOption: WriteOption? = null,
    type: CommandType,
    block: @CommandHandlersDslMarker CommandHandlerDsl<CC, S, E>.() -> Unit,
  )
}

@DslMarker
@Target(CLASS, FUNCTION, PROPERTY, TYPE, VALUE_PARAMETER)
annotation class CommandHandlerDslMarker

@CommandHandlerDslMarker
abstract class CommandHandlerDsl<C, S, E> {
  abstract fun <X> prepare(block: suspend () -> X): PreparedCommandHandlerDsl<C, S, E, X>
  abstract fun handle(block: suspend CommandHandlerRuntimeEnvironment<C, S, E>.(C, S) -> Unit)
  abstract fun project(block: @CommandHandlerDslMarker suspend TransactionContext.() -> Unit)
  abstract fun notify(block: @CommandHandlerDslMarker suspend NotificationContext.() -> Unit)
}

abstract class PreparedCommandHandlerDsl<C, S, E, X> {
  abstract infix fun handle(block: suspend CommandHandlerRuntimeEnvironment<C, S, E>.(C, S, X) -> Unit)
}

class PreparedCommandHandlerBuilder<C, S, E, X>(
  private val x: AtomicReference<X>,
  private val callback: (suspend CommandHandlerRuntimeEnvironment<C, S, E>.(C, S) -> Unit) -> Unit,
) : PreparedCommandHandlerDsl<C, S, E, X>() {
  override fun handle(block: suspend CommandHandlerRuntimeEnvironment<C, S, E>.(C, S, X) -> Unit) {
    callback { c, s -> block(c, s, x.get()) }
  }
}

interface NotificationContext

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

  suspend fun exists(journal: EpoqueJournal<*, *, *>, id: String?): Boolean =
    exists(id?.let { journal.keyFor(it) })

  suspend fun exists(key: JournalKey?): Boolean

  // TODO
  fun complete(): CommandHandlerOutput<E>
}
