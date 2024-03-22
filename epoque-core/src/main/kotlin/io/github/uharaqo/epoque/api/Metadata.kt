package io.github.uharaqo.epoque.api

interface Metadata {

  fun isEmpty(): Boolean

  operator fun <T : Any> get(key: Key<T>): T?

  operator fun plus(other: Metadata): Metadata

  abstract class Key<T : Any> private constructor(val name: String) {
    override fun toString(): String = name
    override fun hashCode(): Int = name.hashCode()
    override fun equals(other: Any?): Boolean =
      other !is Key<*> || other.name === this.name

    companion object {
      fun <T : Any> of(name: String): Key<T> = object : Key<T>(name) {}
      inline fun <reified T : Any> of(): Key<T> = of(T::class.qualifiedName!!)
    }
  }

  companion object {
    val empty: Metadata = object : Metadata {
      override fun isEmpty(): Boolean = true
      override fun <T : Any> get(key: Key<T>): T? = null
      override fun plus(other: Metadata): Metadata = other
      override fun toString(): String = "{}"
    }
  }
}

@JvmInline
value class InputMetadata(val unwrap: Metadata) {
  fun map(f: (Metadata) -> Metadata) = f(unwrap).asInput()
  override fun toString(): String = unwrap.toString()
}

@JvmInline
value class OutputMetadata(val unwrap: Metadata) {
  fun map(f: (Metadata) -> Metadata) = f(unwrap).asOutput()
  override fun toString(): String = unwrap.toString()
}

fun Metadata.asInput() = InputMetadata(this)
fun Metadata.asOutput() = OutputMetadata(this)
