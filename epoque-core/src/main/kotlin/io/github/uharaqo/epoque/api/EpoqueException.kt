package io.github.uharaqo.epoque.api

sealed class EpoqueException(msg: String, t: Throwable?) : RuntimeException(msg, t) {

  class EventSerializationFailure(msg: String, t: Throwable? = null) : EpoqueException(msg, t)

  class EventWriteFailure(msg: String, t: Throwable? = null) : EpoqueException(msg, t)

  class EventLoadFailure(msg: String, t: Throwable? = null) : EpoqueException(msg, t)

  class SummaryAggregationFailure(msg: String, t: Throwable? = null) : EpoqueException(msg, t)
}
