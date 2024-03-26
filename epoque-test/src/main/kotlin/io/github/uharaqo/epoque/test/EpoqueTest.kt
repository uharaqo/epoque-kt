package io.github.uharaqo.epoque.test

import io.github.uharaqo.epoque.Epoque
import io.github.uharaqo.epoque.api.CommandRouter
import io.github.uharaqo.epoque.api.EpoqueEnvironment
import io.github.uharaqo.epoque.builder.CommandRouterFactory
import io.github.uharaqo.epoque.builder.EpoqueRuntimeEnvironmentFactoryFactory
import io.github.uharaqo.epoque.impl2.newRouter
import io.github.uharaqo.epoque.test.api.Tester
import io.github.uharaqo.epoque.test.impl.DefaultTester

object EpoqueTest {
  val runtimeEnvironmentFactoryFactory: EpoqueRuntimeEnvironmentFactoryFactory =
    EpoqueRuntimeEnvironmentFactoryFactory.create()

  fun newTester(
    environment: EpoqueEnvironment,
    vararg commandRouterFactories: CommandRouterFactory,
  ): Tester =
    newTester(Epoque.newRouter(environment, *commandRouterFactories), environment)

  fun newTester(
    commandRouter: CommandRouter,
    environment: EpoqueEnvironment,
  ): Tester = DefaultTester(commandRouter, environment)
}
