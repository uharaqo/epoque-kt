package io.github.uharaqo.epoque.test.api

import io.github.uharaqo.epoque.api.Journal
import io.github.uharaqo.epoque.api.JournalId
import io.github.uharaqo.epoque.api.Metadata

interface Tester {
  fun <S, E : Any> forJournal(journal: Journal<S, E>, block: (CommandTester<S, E>.() -> Unit))
}

interface CommandTester<S, E : Any> {

  fun command(
    id: JournalId,
    command: Any,
    metadata: Metadata = Metadata.empty,
    block: (Validator<S, E>).() -> Unit,
  )
}

interface Validator<S, E : Any> {
  val events: List<E>
  val summary: S
}
