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
  operator fun <V : EpoqueContextValue> get(key: EpoqueContextKey<V>): V? =
    map[key]?.let { it as V }

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

  object Key : CoroutineContext.Key<EpoqueContext> {
    suspend fun get(): EpoqueContext = coroutineContext[this]!!
  }

  companion object {
    fun create(): EpoqueContext = EpoqueContext(emptyMap())
  }
}

interface EpoqueContextKey<out V : EpoqueContextValue> {
  suspend fun get(): V? = coroutineContext[EpoqueContext.Key]?.let { it[this] }
}

interface EpoqueContextValue
