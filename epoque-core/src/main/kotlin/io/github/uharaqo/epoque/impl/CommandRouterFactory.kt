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
import io.github.uharaqo.epoque.api.JournalChecker
import io.github.uharaqo.epoque.builder.CommandProcessorFactory
import io.github.uharaqo.epoque.builder.CommandRouterFactory
import io.github.uharaqo.epoque.builder.CommandRouterFactoryBuilder
import io.github.uharaqo.epoque.builder.DataCodecFactory
import io.github.uharaqo.epoque.builder.DefaultRegistry
import io.github.uharaqo.epoque.builder.RegistryBuilder
import io.github.uharaqo.epoque.builder.toCommandCodec

fun CommandRouter.Companion.fromFactories(
  environment: EpoqueEnvironment,
  vararg factories: CommandRouterFactory,
): CommandRouter =
  fromFactories(environment, factories.toList())

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

  return DefaultCommandRouter(
    CommandCodecRegistry(DefaultRegistry(codecs, onError)),
    CommandProcessorRegistry(DefaultRegistry(processors, onError)),
  )
}

private class DefaultCommandHandler<C, S, E>(
  private val impl: suspend CommandHandlerBuilder<C, S, E>.(C, S) -> Unit,
  private val runtimeEnvFactory: CommandHandlerBuilderFactory<C, S, E>,
) : CommandHandler<C, S, E> {
  override suspend fun handle(c: C, s: S): CommandHandlerOutput<E> =
    runtimeEnvFactory.create().apply { impl(c, s) }.complete()
}

class DefaultCommandRouterFactoryBuilder<C : Any, S, E : Any>(
  private val journal: Journal<S, E>,
  override val codecFactory: DataCodecFactory,
) : CommandRouterFactoryBuilder<C, S, E>() {
  private val commandRouterFactory = DefaultCommandRouterFactory<C, S, E>()

  override fun <CC : C> commandHandlerFor(
    codec: DataCodec<CC>,
    handle: suspend CommandHandlerBuilder<CC, S, E>.(c: CC, s: S) -> Unit,
  ) {
    val handlerFactory = CommandHandlerFactory { env: EpoqueEnvironment ->
      // TODO: no need to depend on env?
      val journalChecker: JournalChecker = env.eventReader

      val runtimeEnvFactory =
        CommandHandlerBuilderFactory {
          // retrieved at runtime
          @Suppress("UNCHECKED_CAST")
          val runtimeEnv =
            CommandHandlerRuntimeEnvironment.get()!! as CommandHandlerRuntimeEnvironment<E>
          DefaultCommandHandlerRuntime<CC, S, E>(runtimeEnv)
        }

      DefaultCommandHandler(handle, runtimeEnvFactory)
    }

    @Suppress("UNCHECKED_CAST")
    register(
      codec = codec as DataCodec<C>,
      commandHandlerFactory = handlerFactory as CommandHandlerFactory<C, S, E>,
    )
  }

  override fun register(
    codec: DataCodec<C>,
    commandHandlerFactory: CommandHandlerFactory<C, S, E>,
  ) {
    commandRouterFactory.register(codec) { env ->
      env.create(journal, codec.toCommandCodec(), commandHandlerFactory)
    }
  }

  override fun build(): CommandRouterFactory = commandRouterFactory
}

private class DefaultCommandRouter(
  override val commandCodecRegistry: CommandCodecRegistry,
  override val commandProcessorRegistry: CommandProcessorRegistry,
) : CommandRouter

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
