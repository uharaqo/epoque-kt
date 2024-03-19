package io.github.uharaqo.epoque.db.jooq

import io.github.uharaqo.epoque.api.Failable
import io.github.uharaqo.epoque.api.JournalKey
import io.github.uharaqo.epoque.api.SerializedData
import io.github.uharaqo.epoque.api.Version
import io.github.uharaqo.epoque.api.VersionedEvent
import kotlinx.coroutines.flow.Flow
import org.jooq.DSLContext

/**
 * [D]: data type of [SerializedData] in the database
 */
interface JooqQueries<D> {
  fun D.toSerializedData(): SerializedData
  fun SerializedData.toFieldValue(): D

  fun selectById(ctx: DSLContext, key: JournalKey, prevVersion: Version): Flow<VersionedEvent>

  suspend fun writeEvents(ctx: DSLContext, key: JournalKey, events: List<VersionedEvent>)

  suspend fun lockNextEvent(ctx: DSLContext, key: JournalKey): Failable<Unit>
}
