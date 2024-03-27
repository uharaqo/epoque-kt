package io.github.uharaqo.epoque.dsl

import io.github.uharaqo.epoque.api.EpoqueEnvironment

@EnvironmentDslMarker
class EnvironmentBuilder : EnvironmentDsl() {
  fun build(): EpoqueEnvironment = EpoqueEnvironment(
    requireNotNull(eventReader ?: eventStore) { "EventReader or EventStore is required" },
    requireNotNull(eventWriter ?: eventStore) { "EventWriter or EventStore is required" },
    requireNotNull(transactionStarter ?: eventStore) {
      "TransactionStarter or EventStore is required"
    },
    defaultCommandExecutorOptions,
    globalCallbackHandler,
    globalCache,
  )
}
