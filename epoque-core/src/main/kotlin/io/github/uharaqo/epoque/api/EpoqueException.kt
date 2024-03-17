package io.github.uharaqo.epoque.api

sealed class EpoqueException(msg: String, t: Throwable?) : RuntimeException(msg, t) {

  class EventSerializationFailure(msg: String, t: Throwable? = null) : EpoqueException(msg, t)

  class EventWriteFailure(msg: String, t: Throwable? = null) : EpoqueException(msg, t)

  class EventLoadFailure(msg: String, t: Throwable? = null) : EpoqueException(msg, t)

  class EventHandlerFailure(msg: String, t: Throwable? = null) : EpoqueException(msg, t)

  class SummaryAggregationFailure(msg: String, t: Throwable? = null) : EpoqueException(msg, t)

  class CommandRouterFailure(msg: String, t: Throwable? = null) : EpoqueException(msg, t)

  class CommandDeserializationException(msg: String, t: Throwable? = null) : EpoqueException(msg, t)

  class CommandHandlerFailure(msg: String, t: Throwable? = null) : EpoqueException(msg, t)

  class TimeoutException(msg: String, t: Throwable? = null) : EpoqueException(msg, t)

  class UnknownException(msg: String, t: Throwable) : EpoqueException(msg, t)
}
