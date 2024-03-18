package io.github.uharaqo.epoque.db.jooq

import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.JSONB
import org.jooq.Record
import org.jooq.Table
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.name
import org.jooq.impl.DSL.table
import org.jooq.impl.SQLDataType.BIGINT
import org.jooq.impl.SQLDataType.CLOB
import org.jooq.impl.SQLDataType.JSONB
import org.jooq.impl.SQLDataType.VARCHAR

open class TableDefinition(
  tableName: String = "event",
  groupColumnName: String = "group",
  idColumnName: String = "id",
  versionColumnName: String = "version",
  typeColumnName: String = "type",
  contentColumnName: String = "content",
  sequenceColumnName: String = "seq",
) {
  open val EVENT: Table<Record> = table(name(tableName))
  open val GROUP: Field<String> =
    field(name(groupColumnName), VARCHAR(255).nullable(false))
  open val ID: Field<String> =
    field(name(idColumnName), VARCHAR(255).nullable(false))
  open val VERSION: Field<Long> =
    field(name(versionColumnName), BIGINT.nullable(false))
  open val TYPE: Field<String> =
    field(name(typeColumnName), CLOB.nullable(false))
  open val CONTENT: Field<JSONB> =
    field(name(contentColumnName), JSONB.nullable(false))
  open val SEQ: Field<Long> =
    field(name(sequenceColumnName), BIGINT.nullable(false).identity(true))

  fun DSLContext.createTableQuery() =
    createTableIfNotExists(EVENT)
      .column(GROUP)
      .column(ID)
      .column(VERSION)
      .column(TYPE)
      .column(CONTENT)
      .column(SEQ)
      .primaryKey(GROUP, ID, VERSION)
      .unique(SEQ)
}
