package io.github.uharaqo.epoque.dsl

import io.github.uharaqo.epoque.api.CommandCodec
import io.github.uharaqo.epoque.api.CommandType
import io.github.uharaqo.epoque.api.DataCodecFactory
import io.github.uharaqo.epoque.api.TransactionContext
import io.github.uharaqo.epoque.api.WriteOption
import java.util.concurrent.atomic.AtomicReference

class CommandHandlersBuilder<C : Any, S, E : Any> : CommandHandlersDsl<C, S, E>() {
  private val handlers = mutableMapOf<CommandType, CommandHandlerBuilder<C, S, E>>()

  override fun <CC : C> onCommand(
    type: CommandType,
    block: @CommandHandlersDslMarker (CommandHandlerDsl<CC, S, E>.() -> Unit),
  ) {
    @Suppress("UNCHECKED_CAST")
    handlers += type to
      CommandHandlerBuilder<CC, S, E>(type).apply(block) as CommandHandlerBuilder<C, S, E>
  }

  fun build(codecFactory: DataCodecFactory): Map<CommandType, CommandHandlerSetup<C, S, E>> =
    handlers.mapValues { it.value.build(codecFactory, defaultWriteOption) }
}

class CommandHandlerBuilder<C, S, E>(
  private val type: CommandType,
) : CommandHandlerDsl<C, S, E>() {
  private var prepare: (suspend () -> Any?)? = null
  private var handler: (suspend CommandHandlerRuntimeEnvironment<C, S, E>.(C, S) -> Unit)? = null
  private val projections = mutableListOf<suspend TransactionContext.() -> Unit>()
  private val notifications = mutableListOf<suspend NotificationContext.() -> Unit>()

  override fun handle(block: suspend CommandHandlerRuntimeEnvironment<C, S, E>.(C, S) -> Unit) {
    handler.shouldBeNull("commands.onCommand.handle")
    handler = block
  }

  override fun <X> prepare(block: suspend () -> X): PreparedCommandHandlerDsl<C, S, E, X> {
    val preparedParam = AtomicReference<X>()
    prepare = suspend { preparedParam.set(block()) }
    return PreparedCommandHandlerBuilder(preparedParam) { handler = it }
  }

  override fun project(block: suspend TransactionContext.() -> Unit) {
    projections += block
  }

  override fun notify(block: suspend NotificationContext.() -> Unit) {
    notifications += block
  }

  fun build(
    codecFactory: DataCodecFactory,
    defaultWriteOption: WriteOption,
  ): CommandHandlerSetup<C, S, E> =
    @Suppress("UNCHECKED_CAST")
    CommandHandlerSetup(
      codecFactory.create(type.unwrap).toCommandCodec() as CommandCodec<C>,
      writeOption ?: defaultWriteOption,
      prepare,
      handler.shouldBeDefined("commands.onCommand.handle"),
      projections,
      notifications,
    )
}

class PreparedCommandHandlerBuilder<C, S, E, X>(
  private val preparedParam: AtomicReference<X>,
  private val callback: (suspend CommandHandlerRuntimeEnvironment<C, S, E>.(C, S) -> Unit) -> Unit,
) : PreparedCommandHandlerDsl<C, S, E, X>() {
  override fun handle(block: suspend CommandHandlerRuntimeEnvironment<C, S, E>.(C, S, X) -> Unit) {
    callback { c, s -> block(c, s, preparedParam.get()) }
  }
}
