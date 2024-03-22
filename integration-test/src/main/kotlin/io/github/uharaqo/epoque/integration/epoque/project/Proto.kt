package io.github.uharaqo.epoque.integration.epoque.project

import io.mcarle.konvert.api.KonvertTo
import kotlinx.serialization.Serializable

@Serializable
sealed interface ProjectCommand

@Serializable
sealed interface ProjectEvent

@Serializable
@KonvertTo(ProjectCreated::class)
data class CreateProject(val name: String) : ProjectCommand

@Serializable
data class ProjectCreated(val name: String) : ProjectEvent
