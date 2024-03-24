package io.github.uharaqo.epoque.impl

import io.github.uharaqo.epoque.api.CommandCodec
import io.github.uharaqo.epoque.api.CommandCodecRegistry
import io.github.uharaqo.epoque.api.CommandHandler
import io.github.uharaqo.epoque.api.CommandHandlerOutput
import io.github.uharaqo.epoque.api.CommandProcessor
import io.github.uharaqo.epoque.api.CommandProcessorRegistry
import io.github.uharaqo.epoque.api.CommandRouter
import io.github.uharaqo.epoque.api.CommandType
import io.github.uharaqo.epoque.api.DataCodec
import io.github.uharaqo.epoque.api.EpoqueEnvironment
import io.github.uharaqo.epoque.api.EpoqueException.Cause.COMMAND_NOT_SUPPORTED
import io.github.uharaqo.epoque.api.Journal
import io.github.uharaqo.epoque.builder.CommandProcessorFactory
import io.github.uharaqo.epoque.builder.CommandRouterFactory
import io.github.uharaqo.epoque.builder.CommandRouterFactoryBuilder
import io.github.uharaqo.epoque.builder.DataCodecFactory
import io.github.uharaqo.epoque.builder.DefaultRegistry
import io.github.uharaqo.epoque.builder.EpoqueRuntimeEnvironment
import io.github.uharaqo.epoque.builder.RegistryBuilder
import io.github.uharaqo.epoque.builder.WithPreparedParam
import io.github.uharaqo.epoque.builder.toCommandCodec

fun CommandRouter.Companion.fromFactories(
  environment: EpoqueEnvironment,
  factories: List<CommandRouterFactory>,
): CommandRouter {
  if (factories.isEmpty()) error("No CommandRouterFactory is provided")

  val routers = factories.map { it.create(environment) }
  val codecs = buildMap { routers.forEach { putAll(it.commandCodecRegistry.toMap()) } }
  val processors = buildMap { routers.forEach { putAll(it.commandProcessorRegistry.toMap()) } }
  val onError =
    { type: CommandType -> COMMAND_NOT_SUPPORTED.toException(message = type.toString()) }

  val defaultCommandRouter = DefaultCommandRouter(
    CommandCodecRegistry(DefaultRegistry(codecs, onError)),
    CommandProcessorRegistry(DefaultRegistry(processors, onError)),
  )
  return environment.runtimeEnvironmentFactoryFactory.create(defaultCommandRouter, environment)
}

private class DefaultCommandRouter(
  override val commandCodecRegistry: CommandCodecRegistry,
  override val commandProcessorRegistry: CommandProcessorRegistry,
) : CommandRouter

private class DefaultCommandHandler<C, S, E>(
  private val impl: suspend EpoqueRuntimeEnvironment<C, S, E>.(C, S) -> Unit,
) : CommandHandler<C, S, E> {
  override suspend fun handle(c: C, s: S): CommandHandlerOutput<E> =
    @Suppress("UNCHECKED_CAST")
    (EpoqueRuntimeEnvironment.get()!! as EpoqueRuntimeEnvironment<C, S, E>)
      .apply { impl(c, s) }
      .complete()
}

class DefaultPreparedCommandHandler<C, S, E, X>(
  override val prepare: suspend (c: C) -> X?,
  private val impl: suspend EpoqueRuntimeEnvironment<C, S, E>.(C, S, X?) -> Unit,
) : PreparedCommandHandler<C, S, E> {
  override suspend fun handle(c: C, s: S): CommandHandlerOutput<E> =
    @Suppress("UNCHECKED_CAST")
    EpoqueRuntimeEnvironment.get()!!.let { workflow ->
      val x = if (workflow is WithPreparedParam) workflow.getPreparedParam<X>() else null
      (workflow as EpoqueRuntimeEnvironment<C, S, E>).apply { impl(c, s, x) }.complete()
    }
}

class DefaultCommandRouterFactoryBuilder<C : Any, S, E : Any>(
  private val journal: Journal<S, E>,
  override val codecFactory: DataCodecFactory,
) : CommandRouterFactoryBuilder<C, S, E>() {
  private val commandRouterFactory = DefaultCommandRouterFactory<C, S, E>()

  override fun <CC : C> commandHandlerFor(
    codec: DataCodec<CC>,
    handle: suspend EpoqueRuntimeEnvironment<CC, S, E>.(c: CC, s: S) -> Unit,
  ) {
    val commandHandler = DefaultCommandHandler(handle)

    @Suppress("UNCHECKED_CAST")
    register(codec as DataCodec<C>, commandHandler as CommandHandler<C, S, E>)
  }

  override fun <CC : C, X> commandHandlerFor(
    codec: DataCodec<CC>,
    prepare: suspend (c: CC) -> X?,
    handle: suspend EpoqueRuntimeEnvironment<CC, S, E>.(c: CC, s: S, x: X?) -> Unit,
  ) {
    val commandHandler = DefaultPreparedCommandHandler(prepare, handle)

    @Suppress("UNCHECKED_CAST")
    register(
      codec = codec as DataCodec<C>,
      commandHandler = commandHandler as CommandHandler<C, S, E>,
    )
  }

  override fun register(codec: DataCodec<C>, commandHandler: CommandHandler<C, S, E>) {
    commandRouterFactory.register(codec) { env ->
      env.newCommandExecutor(journal, codec.toCommandCodec(), commandHandler)
    }
  }

  override fun build(): CommandRouterFactory = commandRouterFactory
}

private class DefaultCommandRouterFactory<C : Any, S, E : Any>(
  private val commandCodecRegistryBuilder: RegistryBuilder<CommandType, CommandCodec<*>> = RegistryBuilder(),
  private val commandProcessorRegistryBuilder: RegistryBuilder<CommandType, CommandProcessorFactory> = RegistryBuilder(),
) : CommandRouterFactory {

  fun register(
    codec: DataCodec<C>,
    handlerFactory: CommandProcessorFactory,
  ): DefaultCommandRouterFactory<C, S, E> {
    val type = CommandType.of(codec.type)
    return this.also {
      commandCodecRegistryBuilder[type] = codec.toCommandCodec()
      commandProcessorRegistryBuilder[type] = handlerFactory
    }
  }

  override fun create(environment: EpoqueEnvironment): CommandRouter {
    val registry = RegistryBuilder<CommandType, CommandProcessor>()

    commandProcessorRegistryBuilder.buildIntoMap().forEach { (type, processorFactory) ->
      registry[type] = processorFactory.create(environment)
    }

    return DefaultCommandRouter(
      commandCodecRegistry = CommandCodecRegistry(
        commandCodecRegistryBuilder.build { COMMAND_NOT_SUPPORTED.toException(message = it.toString()) },
      ),
      commandProcessorRegistry = CommandProcessorRegistry(
        registry.build { COMMAND_NOT_SUPPORTED.toException(message = it.toString()) },
      ),
    )
  }
}
