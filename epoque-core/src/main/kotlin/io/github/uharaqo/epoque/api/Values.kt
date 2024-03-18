package io.github.uharaqo.epoque.api

@JvmInline
value class JournalGroupId(val unwrap: String) {
  override fun toString() = unwrap

  companion object {
    inline fun <reified E : Any> of(): JournalGroupId = JournalGroupId(E::class.qualifiedName!!)
  }
}

@JvmInline
value class JournalId(val unwrap: String) {
  override fun toString() = unwrap
}

data class JournalKey(val groupId: JournalGroupId, val id: JournalId)

@JvmInline
value class Version(val unwrap: Long) {
  operator fun plus(i: Int): Version = Version(unwrap + i)
  override fun toString(): String = unwrap.toString()

  companion object {
    val ZERO = Version(0)
  }
}

data class VersionedEvent(
  val version: Version,
  val type: EventType,
  val event: SerializedEvent,
)

data class VersionedSummary<S>(
  val version: Version,
  val summary: S,
)

data class CommandInput(
  val id: JournalId,
  val type: CommandType,
  val payload: SerializedCommand,
  val commandExecutorOptions: CommandExecutorOptions? = null,
)

data class CommandOutput(
  val events: List<VersionedEvent>,
)

data class CommandExecutorOptions(
  val timeoutMillis: Long = 5000L,
  val lockOption: LockOption = LockOption.DEFAULT,
) : EpoqueContextValue {
  object Key : EpoqueContextKey<CommandExecutorOptions>
}
