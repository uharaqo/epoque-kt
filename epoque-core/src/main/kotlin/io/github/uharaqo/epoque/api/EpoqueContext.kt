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
      // TODO: withContext only when anything has changed
      with(configure).withContext(block)
  }

  interface Builder {
    fun <V> put(key: EpoqueContextKey<V>, value: V): Builder
    suspend fun <V> map(key: EpoqueContextKey<V>, f: suspend (V?) -> V): Builder
  }

  private class DefaultBuilder(private val original: EpoqueContext) : Builder {
    private val map = original.map.toMutableMap()
    private var changed = false

    override fun <V> put(key: EpoqueContextKey<V>, value: V) = this.also {
      map += (key to value as Any?)
      changed = true
    }

    override suspend fun <V> map(key: EpoqueContextKey<V>, f: suspend (V?) -> V) = this.also {
      @Suppress("UNCHECKED_CAST")
      map[key] = f(map[key] as V?)
      changed = true
    }

    fun build(): EpoqueContext = if (changed) EpoqueContext(map) else original
  }
}

interface EpoqueContextKey<out V> {
  suspend fun get(): V? = coroutineContext[EpoqueContext]?.let { it[this] }
}
