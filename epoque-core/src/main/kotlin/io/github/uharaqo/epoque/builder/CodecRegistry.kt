package io.github.uharaqo.epoque.builder

import io.github.uharaqo.epoque.api.CommandCodec
import io.github.uharaqo.epoque.api.CommandCodecRegistry
import io.github.uharaqo.epoque.api.CommandType
import io.github.uharaqo.epoque.api.EventCodec
import io.github.uharaqo.epoque.api.EventCodecRegistry
import io.github.uharaqo.epoque.api.EventType
import io.github.uharaqo.epoque.serialization.JsonCodec
import io.github.uharaqo.epoque.serialization.toCommandCodec
import io.github.uharaqo.epoque.serialization.toEventCodec

class DefaultEventCodecRegistry(
  private val registry: Registry<EventType, EventCodec<*>>,
) : EventCodecRegistry {
  @Suppress("UNCHECKED_CAST")
  override fun <E> get(eventType: EventType): EventCodec<E>? =
    registry[eventType]?.let { requireNotNull(it as? EventCodec<E>) { "Unexpected eventType: $eventType" } }
}

class EventCodecRegistryBuilder<E : Any> {
  val registry = DefaultRegistryBuilder<EventType, EventCodec<*>>()

  inline fun <reified E2 : E> register() = this.also {
    registry.register(EventType.of<E2>(), JsonCodec.of<E2>().toEventCodec())
  }

  fun build(): EventCodecRegistry = DefaultEventCodecRegistry(registry.build())
}

class DefaultCommandCodecRegistry(
  private val registry: Registry<CommandType, CommandCodec<*>>,
) : CommandCodecRegistry {
  @Suppress("UNCHECKED_CAST")
  override operator fun <C> get(commandType: CommandType): CommandCodec<C>? =
    registry[commandType]?.let { requireNotNull(it as? CommandCodec<C>) { "Unexpected commandType: $commandType" } }
}

class CommandCodecRegistryBuilder<C : Any> {
  val registry = DefaultRegistryBuilder<CommandType, CommandCodec<*>>()

  inline fun <reified C2 : C> register() = this.also {
    registry.register(CommandType.of<C2>(), JsonCodec.of<C2>().toCommandCodec())
  }

  fun build(): CommandCodecRegistry = DefaultCommandCodecRegistry(registry.build())
}
