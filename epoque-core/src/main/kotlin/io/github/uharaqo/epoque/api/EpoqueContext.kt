package io.github.uharaqo.epoque.api

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineScope

class EpoqueContext private constructor(
  private val map: Map<EpoqueContextKey<*>, Any?>,
) : CoroutineContext.Element {

  override val key = Companion

  operator fun <V> get(key: EpoqueContextKey<V>): V? =
    @Suppress("UNCHECKED_CAST")
    map[key]?.let { it as V }

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
      with(configure).withContext(block)
  }

  interface Builder {
    fun <V> put(key: EpoqueContextKey<V>, value: V)
    suspend fun <V> map(key: EpoqueContextKey<V>, f: suspend (V?) -> V)
  }

  private class DefaultBuilder(private val original: EpoqueContext?) : Builder {
    private val map = mutableMapOf<EpoqueContextKey<*>, Any?>()

    override fun <V> put(key: EpoqueContextKey<V>, value: V) {
      map += (key to value as Any?)
    }

    override suspend fun <V> map(key: EpoqueContextKey<V>, f: suspend (V?) -> V) {
      @Suppress("UNCHECKED_CAST")
      map[key] = f(map[key] as V?)
    }

    fun build(): EpoqueContext =
      original
        ?.let { if (map.isNotEmpty()) EpoqueContext(it.map + map) else it }
        ?: EpoqueContext(map)
  }
}

interface EpoqueContextKey<out V> {
  suspend fun get(): V? = coroutineContext[EpoqueContext]?.let { it[this] }
}
