package io.github.uharaqo.epoque.api

import arrow.core.Either

class EpoqueException(cause: Cause, details: String? = null, t: Throwable? = null) :
  RuntimeException("${cause.name}${details?.let { ": $it" } ?: ""}", t) {

  enum class Cause {
    EVENT_NOT_SUPPORTED,
    EVENT_ENCODING_FAILURE,
    EVENT_DECODING_FAILURE,
    EVENT_READ_FAILURE,
    EVENT_WRITE_FAILURE,
    EVENT_WRITE_CONFLICT,
    EVENT_HANDLER_FAILURE,
    SUMMARY_AGGREGATION_FAILURE,
    COMMAND_NOT_SUPPORTED,
    COMMAND_ENCODING_FAILURE,
    COMMAND_DECODING_FAILURE,
    COMMAND_PREPARATION_FAILURE,
    COMMAND_REJECTED,
    COMMAND_HANDLER_FAILURE,
    PROJECTION_FAILURE,

    /** An error that should not happen */
    UNEXPECTED_ERROR,
    TIMEOUT,
    ;

    fun toException(t: Throwable? = null): EpoqueException = toException(t, null)
    fun toException(t: Throwable? = null, message: String?): EpoqueException =
      EpoqueException(this, message, t)
  }
}

typealias Failable<T> = Either<EpoqueException, T>
