package io.github.uharaqo.epoque.impl

import io.github.uharaqo.epoque.api.CommandCodec
import io.github.uharaqo.epoque.api.CommandCodecRegistry
import io.github.uharaqo.epoque.api.CommandProcessor
import io.github.uharaqo.epoque.api.CommandProcessorRegistry
import io.github.uharaqo.epoque.api.CommandRouter
import io.github.uharaqo.epoque.api.CommandType
import io.github.uharaqo.epoque.api.DataCodec
import io.github.uharaqo.epoque.api.DataCodecFactory
import io.github.uharaqo.epoque.api.EpoqueEnvironment
import io.github.uharaqo.epoque.api.EpoqueException.Cause.COMMAND_NOT_SUPPORTED
import io.github.uharaqo.epoque.api.Journal
import io.github.uharaqo.epoque.api.codecFor

fun interface CommandRouterFactory {
  fun create(environment: EpoqueEnvironment): CommandRouter
}

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

fun interface CommandProcessorFactory {
  fun create(environment: EpoqueEnvironment): CommandProcessor
}

class DefaultCommandRouterFactory<C : Any, S, E : Any>(
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

class DefaultCommandRouter(
  override val commandCodecRegistry: CommandCodecRegistry,
  override val commandProcessorRegistry: CommandProcessorRegistry,
) : CommandRouter

class CommandRouterFactoryBuilder<C : Any, S, E : Any>(
  private val journal: Journal<S, E>,
  val codecFactory: DataCodecFactory,
) {
  private val commandRouterFactory = DefaultCommandRouterFactory<C, S, E>()

  /** [CC]: Concrete type of the command */
  inline fun <reified CC : C> commandHandlerFor(
    noinline handle: suspend CommandHandlerBuilder<CC, S, E>.(c: CC, s: S) -> Unit,
  ): CommandRouterFactoryBuilder<C, S, E> {
    val codec = codecFactory.codecFor<CC>()
    val handlerFactory = CommandHandlerFactory { env: EpoqueEnvironment ->
      // TODO: no need to depend on env?
      val runtimeEnvFactory = CommandHandlerRuntimeEnvironmentFactory<CC, S, E> {
        val runtimeEnv = CommandHandlerRuntimeEnvironment.get()!! // retrieved at runtime
        DefaultCommandHandlerRuntime(runtimeEnv)
      }
      DefaultCommandHandler(handle, runtimeEnvFactory)
    }
    val codec = codecFactory.codecFor<CC>()

    @Suppress("UNCHECKED_CAST")
    return register(
      codec = codec as DataCodec<C>,
      commandHandlerFactory = factory as CommandHandlerFactory<C, S, E>,
    )
  }

  fun register(
    codec: DataCodec<C>,
    commandHandlerFactory: CommandHandlerFactory<C, S, E>,
  ): CommandRouterFactoryBuilder<C, S, E> = this.also {
    commandRouterFactory.register(codec) { env ->
      CommandExecutor.create(journal, codec.toCommandCodec(), commandHandlerFactory, env)
    }
  }

  fun build(): CommandRouterFactory = commandRouterFactory
}
