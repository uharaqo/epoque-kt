package io.github.uharaqo.epoque.api

fun interface CommandHandler<C, S, E> {
  suspend fun handle(c: C, s: S): CommandHandlerOutput<E>
}
