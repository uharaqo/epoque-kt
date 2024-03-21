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

  operator fun <V : EpoqueContextValue> get(key: EpoqueContextKey<V>): V? =
    @Suppress("UNCHECKED_CAST")
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

  object Key : CoroutineContext.Key<EpoqueContext>

  companion object {
    fun create(): EpoqueContext = EpoqueContext(emptyMap())

    suspend fun get(): EpoqueContext = coroutineContext[Key]!!
  }
}

interface EpoqueContextKey<out V : EpoqueContextValue> {
  suspend fun get(): V? = coroutineContext[EpoqueContext.Key]?.let { it[this] }
}

interface EpoqueContextValue
