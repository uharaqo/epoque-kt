package io.github.uharaqo.epoque.api

@JvmInline
value class InputMetadata(val unwrap: Map<out Any, Any>) {
  fun map(f: (Map<out Any, Any>) -> Map<Any, Any>) = f(unwrap).asInputMetadata()
  override fun toString(): String = unwrap.toString()

  companion object {
    val EMPTY = InputMetadata(emptyMap())
  }
}

@JvmInline
value class OutputMetadata(val unwrap: Map<out Any, Any>) {
  fun map(f: (Map<out Any, Any>) -> Map<Any, Any>) = f(unwrap).asOutputMetadata()
  override fun toString(): String = unwrap.toString()

  companion object {
    val EMPTY = OutputMetadata(emptyMap())
  }
}

fun Map<out Any, Any>.asInputMetadata() = InputMetadata(this)
fun Map<out Any, Any>.asOutputMetadata() = OutputMetadata(this)
