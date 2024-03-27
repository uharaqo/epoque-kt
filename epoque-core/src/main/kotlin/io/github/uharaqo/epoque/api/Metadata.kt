package io.github.uharaqo.epoque.api

@JvmInline
value class InputMetadata(val unwrap: Map<out Any, Any>) {
  override fun toString(): String = unwrap.toString()

  companion object {
    val EMPTY = InputMetadata(emptyMap())
  }
}

@JvmInline
value class OutputMetadata(val unwrap: Map<out Any, Any>) {
  override fun toString(): String = unwrap.toString()

  companion object {
    val EMPTY = OutputMetadata(emptyMap())
  }
}

fun Map<out Any, Any>.asInputMetadata() = InputMetadata(this)
fun Map<out Any, Any>.asOutputMetadata() = OutputMetadata(this)
