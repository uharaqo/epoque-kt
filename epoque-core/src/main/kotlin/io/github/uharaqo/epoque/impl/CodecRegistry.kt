package io.github.uharaqo.epoque.impl

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import io.github.uharaqo.epoque.api.CommandCodec
import io.github.uharaqo.epoque.api.CommandCodecRegistry
import io.github.uharaqo.epoque.api.CommandType
import io.github.uharaqo.epoque.api.EpoqueException.UnexpectedCommand
import io.github.uharaqo.epoque.api.EpoqueException.UnexpectedEvent
import io.github.uharaqo.epoque.api.EventCodec
import io.github.uharaqo.epoque.api.EventCodecRegistry
import io.github.uharaqo.epoque.api.EventType
import io.github.uharaqo.epoque.api.toCommandCodec
import io.github.uharaqo.epoque.api.toEventCodec
import io.github.uharaqo.epoque.serialization.JsonCodec

class EventCodecRegistryBuilder<E : Any> {
  val registry = DefaultRegistryBuilder<EventType, EventCodec<*>>()

  inline fun <reified E2 : E> register() = this.also {
    registry.register(EventType.of<E2>(), JsonCodec.of<E2>().toEventCodec())
  }

  fun build(): EventCodecRegistry<E> = DefaultEventCodecRegistry(registry.build())

  private inner class DefaultEventCodecRegistry(
    private val registry: Registry<EventType, EventCodec<*>>,
  ) : EventCodecRegistry<E> {
    @Suppress("UNCHECKED_CAST")
    override fun find(eventType: EventType): Either<UnexpectedEvent, EventCodec<E>> = either {
      ensureNotNull(registry[eventType]?.let { it as EventCodec<E> }) {
        UnexpectedEvent("EventCodec not found: $eventType")
      }
    }
  }
}

class CommandCodecRegistryBuilder<C : Any> {
  val registry = DefaultRegistryBuilder<CommandType, CommandCodec<*>>()

  inline fun <reified C2 : C> register() = this.also {
    registry.register(CommandType.of<C2>(), JsonCodec.of<C2>().toCommandCodec())
  }

  fun build(): CommandCodecRegistry<C> = DefaultCommandCodecRegistry(registry.build())

  private inner class DefaultCommandCodecRegistry(
    private val registry: Registry<CommandType, CommandCodec<*>>,
  ) : CommandCodecRegistry<C> {
    @Suppress("UNCHECKED_CAST")
    override fun find(commandType: CommandType): Either<UnexpectedCommand, CommandCodec<C>> =
      either {
        ensureNotNull(registry[commandType]?.let { it as CommandCodec<C> }) {
          UnexpectedCommand("CommandCodec not found: $commandType")
        }
      }
  }
}
