package io.github.uharaqo.epoque.impl

import io.github.uharaqo.epoque.api.CommandCodec
import io.github.uharaqo.epoque.api.CommandExecutable
import io.github.uharaqo.epoque.api.CommandHandler
import io.github.uharaqo.epoque.api.CommandInput
import io.github.uharaqo.epoque.api.CommandProcessable
import io.github.uharaqo.epoque.api.CommandProcessor
import io.github.uharaqo.epoque.api.CommandRouter
import io.github.uharaqo.epoque.api.CommandType
import io.github.uharaqo.epoque.api.EventCodecRegistry
import io.github.uharaqo.epoque.api.EventLoader
import io.github.uharaqo.epoque.api.EventWriter
import io.github.uharaqo.epoque.api.JournalGroupId
import io.github.uharaqo.epoque.api.SummaryGenerator
import io.github.uharaqo.epoque.api.TransactionStarter

class DefaultCommandRouter(
  private val map: Map<CommandType, CommandProcessor>,
) : CommandRouter {
  override fun get(input: CommandInput): CommandProcessor? = map[input.type]

  fun plus(other: DefaultCommandRouter): DefaultCommandRouter =
    DefaultCommandRouter(this.map + other.map)
}

class TypedCommandProcessor<C>(
  override val commandCodec: CommandCodec<C>,
  override val commandExecutable: CommandExecutable<C, *, *>,
) : CommandProcessable<C>

class CommandExecutor<C, S, E : Any>(
  override val journalGroupId: JournalGroupId,
  override val commandHandler: CommandHandler<C, S, E>,
  override val eventCodecRegistry: EventCodecRegistry,
  override val summaryGenerator: SummaryGenerator<S>,
  override val eventLoader: EventLoader,
  override val eventWriter: EventWriter,
  transactionStarter: TransactionStarter,
) : CommandExecutable<C, S, E>, TransactionStarter by transactionStarter
