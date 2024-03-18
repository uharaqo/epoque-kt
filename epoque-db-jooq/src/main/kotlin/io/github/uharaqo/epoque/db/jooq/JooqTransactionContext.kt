package io.github.uharaqo.epoque.db.jooq

import io.github.uharaqo.epoque.api.JournalKey
import io.github.uharaqo.epoque.api.LockOption
import io.github.uharaqo.epoque.api.TransactionContext
import org.jooq.DSLContext

data class JooqTransactionContext(
  val ctx: DSLContext,
  override val lockOption: LockOption,
  override val lockedKeys: Set<JournalKey>,
) : TransactionContext

suspend fun <T> TransactionContext.asJooq(block: suspend JooqTransactionContext.() -> T): T =
  (this as JooqTransactionContext).run { block() }

fun <T> TransactionContext.asJooqBlocking(block: JooqTransactionContext.() -> T): T =
  (this as JooqTransactionContext).run { block() }
