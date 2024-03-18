package io.github.uharaqo.epoque.impl

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
  val registry =
    Registry.builder<EventType, EventCodec<E>, UnexpectedEvent> { UnexpectedEvent("EventCodec not found: $it") }

  inline fun <reified E2 : E> register() = this.also {
    @Suppress("UNCHECKED_CAST")
    registry[EventType.of<E2>()] = JsonCodec.of<E2>().toEventCodec() as EventCodec<E>
  }

  fun build(): EventCodecRegistry<E> = EventCodecRegistry(registry.build())
}

class CommandCodecRegistryBuilder<C : Any> {
  val registry =
    Registry.builder<CommandType, CommandCodec<C>, UnexpectedCommand> { UnexpectedCommand("CommandCodec not found: $it") }

  inline fun <reified C2 : C> register() = this.also {
    @Suppress("UNCHECKED_CAST")
    registry[CommandType.of<C2>()] = JsonCodec.of<C2>().toCommandCodec() as CommandCodec<C>
  }

  fun build(): CommandCodecRegistry<C> = CommandCodecRegistry(registry.build())
}
