package io.github.uharaqo.epoque.api

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineScope

class EpoqueContext private constructor(
  private val map: Map<EpoqueContextKey<*>, Any>,
) : CoroutineContext.Element {

  override val key = Companion

  operator fun <V> get(key: EpoqueContextKey<V>): V? =
    @Suppress("UNCHECKED_CAST")
    map[key]?.let { it as V }

  fun <V> with(key: EpoqueContextKey<V>, value: V): EpoqueContext =
    DefaultBuilder(this).also { it.add(key, value) }.build()

  @OptIn(ExperimentalContracts::class)
  suspend fun <T> withContext(block: suspend CoroutineScope.() -> T): T {
    contract {
      callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    val context = coroutineContext + this
    return kotlinx.coroutines.withContext(context, block)
  }

  override fun toString(): String = map.toString()

  companion object : CoroutineContext.Key<EpoqueContext> {
    suspend fun get(): EpoqueContext? = coroutineContext[Companion]

    fun create(): EpoqueContext = EpoqueContext(emptyMap())

    suspend fun getOrCreate(): EpoqueContext = get() ?: create()

    suspend fun with(block: Builder.() -> Unit): EpoqueContext =
      DefaultBuilder(getOrCreate()).apply(block).build()

    suspend fun <T> with(configure: Builder.() -> Unit, block: suspend CoroutineScope.() -> T): T =
      Companion.with(configure).withContext(block)
  }

  interface Builder {
    fun <V> add(key: EpoqueContextKey<V>, value: V)
  }

  private class DefaultBuilder(original: EpoqueContext?) : Builder {
    private val map = original?.map?.toMutableMap() ?: mutableMapOf()

    override fun <V> add(key: EpoqueContextKey<V>, value: V) {
      map += (key to value as Any)
    }

    fun build(): EpoqueContext = EpoqueContext(map)
  }
}

interface EpoqueContextKey<out V> {
  suspend fun get(): V? = coroutineContext[EpoqueContext]?.let { it[this] }
}
