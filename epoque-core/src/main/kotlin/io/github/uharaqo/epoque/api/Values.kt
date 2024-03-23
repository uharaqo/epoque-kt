package io.github.uharaqo.epoque.api

import java.time.Instant

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
  val metadata: Map<out Any, Any> = emptyMap(),
  val commandExecutorOptions: CommandExecutorOptions? = null,
)

data class CommandContext(
  val key: JournalKey,
  val commandType: CommandType,
  val command: SerializedCommand,
  val metadata: InputMetadata,
  val options: CommandExecutorOptions,
  val receivedTime: Instant,
) {

  companion object : EpoqueContextKey<CommandContext>
}

fun CommandContext.getElapsedTimeMillis(): Long =
  Instant.now().toEpochMilli() - receivedTime.toEpochMilli()

fun CommandContext.getRemainingTimeMillis(): Long =
  options.timeoutMillis - getElapsedTimeMillis()

data class CommandHandlerOutput<E>(
  val events: List<E>,
  val metadata: OutputMetadata,
)

data class CommandOutput(
  val events: List<VersionedEvent>,
  val metadata: OutputMetadata,
  val context: CommandContext,
)

data class CommandExecutorOptions(
  val timeoutMillis: Long = 5000L,
  val writeOption: WriteOption = WriteOption.DEFAULT,
) {
  companion object {
    val DEFAULT = CommandExecutorOptions()
  }
}

data class EpoqueEnvironment(
  val eventReader: EventReader,
  val eventWriter: EventWriter,
  val transactionStarter: TransactionStarter,
  val defaultCommandExecutorOptions: CommandExecutorOptions?,
  val callbackHandler: CallbackHandler?,
)

interface Registry<K : Any, V : Any> {
  fun find(key: K): Failable<V>

  fun toMap(): Map<K, V>
}
