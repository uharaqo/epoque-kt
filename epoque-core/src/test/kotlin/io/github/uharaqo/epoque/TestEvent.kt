package io.github.uharaqo.epoque

import kotlinx.serialization.Serializable

sealed interface TestEvent {
  @Serializable
  data class ResourceCreated(val name: String) : TestEvent
}
