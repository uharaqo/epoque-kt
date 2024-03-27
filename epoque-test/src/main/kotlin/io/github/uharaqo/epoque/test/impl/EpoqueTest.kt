package io.github.uharaqo.epoque.test.impl

import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.raise.either
import io.github.uharaqo.epoque.Epoque
import io.github.uharaqo.epoque.api.CallbackHandler
import io.github.uharaqo.epoque.api.CanLoadSummary
import io.github.uharaqo.epoque.api.CommandExecutorOptions
import io.github.uharaqo.epoque.api.CommandInput
import io.github.uharaqo.epoque.api.CommandOutput
import io.github.uharaqo.epoque.api.CommandRouter
import io.github.uharaqo.epoque.api.CommandType
import io.github.uharaqo.epoque.api.EpoqueEnvironment
import io.github.uharaqo.epoque.api.EventReader
import io.github.uharaqo.epoque.api.EventStore
import io.github.uharaqo.epoque.api.Journal
import io.github.uharaqo.epoque.api.JournalId
import io.github.uharaqo.epoque.api.JournalKey
import io.github.uharaqo.epoque.api.SummaryCache
import io.github.uharaqo.epoque.api.TransactionStarter
import io.github.uharaqo.epoque.api.WriteOption
import io.github.uharaqo.epoque.db.jooq.JooqEventStore
import io.github.uharaqo.epoque.db.jooq.TableDefinition
import io.github.uharaqo.epoque.db.jooq.h2.H2JooqQueries
import io.github.uharaqo.epoque.test.api.CommandTester
import io.github.uharaqo.epoque.test.api.Tester
import io.github.uharaqo.epoque.test.api.Validator
import java.sql.DriverManager
import kotlinx.coroutines.runBlocking
import org.jooq.DSLContext
import org.jooq.JSONB
import org.jooq.SQLDialect
import org.jooq.conf.RenderNameCase
import org.jooq.conf.RenderQuotedNames
import org.jooq.conf.Settings
import org.jooq.impl.DSL

fun Epoque.newTester(
  commandRouter: CommandRouter,
  environment: EpoqueEnvironment,
): Tester = DefaultTester(commandRouter, environment)

fun Epoque.newH2JooqContext(): DSLContext =
  DriverManager.getConnection(
    "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=TRUE",
    "sa",
    "",
  )
    .also { it.autoCommit = false }
    .also { Runtime.getRuntime().addShutdownHook(Thread { it.close() }) }
    .let {
      DSL.using(
        it,
        SQLDialect.H2,
        Settings()
          .withRenderNameCase(RenderNameCase.UPPER)
          .withRenderQuotedNames(RenderQuotedNames.ALWAYS),
      )
    }

fun Epoque.newH2EventStore() = Epoque.newH2JooqContext().toH2EventStore()

fun DSLContext.toH2EventStore(tableDefinition: TableDefinition = TableDefinition()): JooqEventStore<JSONB> =
  JooqEventStore(this, H2JooqQueries(tableDefinition))

fun Epoque.newTestEnvironment(
  eventStore: EventStore = Epoque.newH2EventStore(),
  options: CommandExecutorOptions = CommandExecutorOptions(
    timeoutMillis = 30000,
    writeOption = WriteOption.JOURNAL_LOCK,
  ),
  globalCallbackHandler: CallbackHandler = DebugLogger(),
  globalCache: SummaryCache? = null,
): EpoqueEnvironment =
  EpoqueEnvironment(
    eventReader = eventStore,
    eventWriter = eventStore,
    transactionStarter = eventStore,
    defaultCommandExecutorOptions = options,
    globalCallbackHandler = globalCallbackHandler,
    globalCache = globalCache,
  )

class DefaultTester(
  val commandRouter: CommandRouter,
  val environment: EpoqueEnvironment,
) : Tester {
  override fun <S, E : Any> forJournal(journal: Journal<*, S, E>): CommandTester<S, E> =
    DefaultCommandTester(commandRouter, environment, journal)

  override fun <S, E : Any> forJournal(
    journal: Journal<*, S, E>,
    block: CommandTester<S, E>.() -> Unit,
  ) {
    forJournal(journal).apply(block)
  }
}

class DefaultCommandTester<S, E : Any>(
  val commandRouter: CommandRouter,
  val environment: EpoqueEnvironment,
  val journal: Journal<*, S, E>,
) : CommandTester<S, E> {
  override fun command(
    id: JournalId,
    command: Any,
    metadata: Map<out Any, Any>,
    block: (Validator<S, E>).() -> Unit,
  ) {
    either {
      val type = CommandType.of(command::class.java)
      val commandCodec = journal.commandCodecRegistry.find<Any>(type).bind()
      val payload = commandCodec.encode(command).bind()
      val input = CommandInput(id, type, payload, metadata)

      val result = runBlocking { commandRouter.process(input) }.bind()

      val summaryProvider =
        SummaryProvider(journal, environment.transactionStarter, environment.eventReader)

      DefaultCommandValidator(input, result, journal, summaryProvider).apply(block)
    }.getOrElse { throw it }
  }
}

class DefaultCommandValidator<S, E : Any>(
  val input: CommandInput,
  result: CommandOutput,
  val journal: Journal<*, S, E>,
  private val summaryProvider: SummaryProvider<S, E>,
) : Validator<S, E> {

  override val events: List<E> =
    result.events.map { ve ->
      journal.eventCodecRegistry.find<E>(ve.type)
        .flatMap { it.decode(ve.event) }
        .getOrElse { throw it }
    }

  override val summary: S
    get() = runBlocking { summaryProvider.get(input.id) }
}

class SummaryProvider<S, E : Any>(
  private val journal: Journal<*, S, E>,
  private val transactionStarter: TransactionStarter,
  override val eventReader: EventReader,
) : CanLoadSummary<S> {
  override val eventAggregator = journal

  suspend fun get(id: JournalId): S =
    transactionStarter.startDefaultTransaction { tx ->
      loadSummary(JournalKey(journal.journalGroupId, id), tx).map { it.summary }
        .getOrElse { throw it }
    }
      .getOrElse { throw it }
}
