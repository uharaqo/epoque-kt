package io.github.uharaqo.epoque.impl2

import io.github.uharaqo.epoque.api.CallbackHandler
import io.github.uharaqo.epoque.api.CommandDecoder
import io.github.uharaqo.epoque.api.CommandHandler
import io.github.uharaqo.epoque.api.EpoqueEnvironment
import io.github.uharaqo.epoque.api.Journal
import io.github.uharaqo.epoque.impl.CommandExecutor

fun <C : Any, S, E : Any> EpoqueEnvironment.newCommandExecutor(
  journal: Journal<S, E>,
  commandDecoder: CommandDecoder<C>,
  commandHandler: CommandHandler<C, S, E>,
): CommandExecutor<C, S, E> {
  val cbh = (callbackHandler ?: CallbackHandler.EMPTY) // + EpoqueRuntimeEnvironment.get()!!

  return CommandExecutor(
    journalGroupId = journal.journalGroupId,
    commandDecoder = commandDecoder,
    commandHandler = commandHandler,
    callbackHandler = cbh,
    eventCodecRegistry = journal.eventCodecRegistry,
    eventHandlerExecutor = journal,
    eventReader = eventReader,
    eventWriter = eventWriter,
    transactionStarter = transactionStarter,
    defaultCommandExecutorOptions = defaultCommandExecutorOptions,
  )
}
