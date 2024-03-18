package io.github.uharaqo.epoque.api

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineScope

data class EpoqueContext(
  private val map: Map<EpoqueContextKey<*>, EpoqueContextValue> = emptyMap(),
) : CoroutineContext.Element {

  override val key = Key

  @Suppress("UNCHECKED_CAST")
  operator fun <T : EpoqueContextValue> get(key: EpoqueContextKey<T>): T? =
    map[key]?.let { it as T }

  @OptIn(ExperimentalContracts::class)
  suspend fun <T, V : EpoqueContextValue> withContext(
    key: EpoqueContextKey<V>,
    value: V,
    block: suspend CoroutineScope.() -> T,
  ): T {
    contract {
      callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    val context = coroutineContext + EpoqueContext(map + (key to value))
    return kotlinx.coroutines.withContext(context, block)
  }

  object Key : CoroutineContext.Key<EpoqueContext>
}

fun <T : EpoqueContextValue> CoroutineContext.getEpoqueContext(key: EpoqueContextKey<T>): T? =
  this[EpoqueContext.Key]?.let { it[key] }

interface EpoqueContextKey<out T : EpoqueContextValue> {
  suspend fun get(): T? = coroutineContext.getEpoqueContext(this)
}

interface EpoqueContextValue
