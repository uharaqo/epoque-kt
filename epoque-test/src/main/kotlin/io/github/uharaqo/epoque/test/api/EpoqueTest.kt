package io.github.uharaqo.epoque.test.api

import io.github.uharaqo.epoque.api.Journal
import io.github.uharaqo.epoque.api.JournalId

interface Tester {
  fun <S, E : Any> forJournal(journal: Journal<*, S, E>): CommandTester<S, E>
  fun <S, E : Any> forJournal(journal: Journal<*, S, E>, block: (CommandTester<S, E>.() -> Unit))
}

interface CommandTester<S, E : Any> {

  fun command(
    id: JournalId,
    command: Any,
    metadata: Map<out Any, Any> = emptyMap(),
    block: (Validator<S, E>).() -> Unit,
  )
}

interface Validator<S, E : Any> {
  val events: List<E>
  val summary: S
}
