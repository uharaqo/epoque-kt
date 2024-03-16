package io.github.uharaqo.epoque.api

@JvmInline
value class JournalGroupId(val unwrap: String) {
  companion object {
    inline fun <reified E : Any> of(): JournalGroupId = JournalGroupId(E::class.qualifiedName!!)
  }
}

@JvmInline
value class JournalId(val unwrap: String)

data class JournalKey(val groupId: JournalGroupId, val id: JournalId)

@JvmInline
value class Version(val unwrap: Long) {
  operator fun plus(i: Int): Version = Version(unwrap + i)

  companion object {
    val ZERO = Version(0)
  }
}

@JvmInline
value class EventType(val unwrap: String) {
  companion object {
    fun <E : Any> of(clazz: Class<E>): EventType = EventType(clazz.canonicalName!!)
    inline fun <reified E : Any> of(): EventType = EventType.of(E::class.java)
  }
}

@JvmInline
value class SerializedEvent(val unwrap: SerializedData) {
  override fun toString(): String = unwrap.toString()
}

data class VersionedEvent(
  val version: Version,
  val type: EventType,
  val event: SerializedEvent,
)
