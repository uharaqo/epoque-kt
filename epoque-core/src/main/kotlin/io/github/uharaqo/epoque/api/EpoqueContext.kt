package io.github.uharaqo.epoque.api

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineScope

class EpoqueContext private constructor(
  private val map: Map<EpoqueContextKey<*>, EpoqueContextValue>,
) : CoroutineContext.Element {

  override val key = Key

  @Suppress("UNCHECKED_CAST")
  operator fun <T : EpoqueContextValue> get(key: EpoqueContextKey<T>): T? =
    map[key]?.let { it as T }

  fun <V : EpoqueContextValue> with(key: EpoqueContextKey<V>, value: V): EpoqueContext =
    EpoqueContext(map + (key to value))

  @OptIn(ExperimentalContracts::class)
  suspend fun <T, V : EpoqueContextValue> withContext(
    key: EpoqueContextKey<V>,
    value: V,
    block: suspend CoroutineScope.() -> T,
  ): T {
    contract {
      callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    val context = coroutineContext + with(key, value)
    return kotlinx.coroutines.withContext(context, block)
  }

  object Key : CoroutineContext.Key<EpoqueContext>

  companion object {
    fun create(): EpoqueContext = EpoqueContext(emptyMap())
  }
}

fun <T : EpoqueContextValue> CoroutineContext.getEpoqueContext(key: EpoqueContextKey<T>): T? =
  this[EpoqueContext.Key]?.let { it[key] }

interface EpoqueContextKey<out T : EpoqueContextValue> {
  suspend fun get(): T? = coroutineContext.getEpoqueContext(this)
}

interface EpoqueContextValue
