package io.github.uharaqo.epoque.api

import arrow.core.Either

class EpoqueException(cause: Cause, details: String? = null, t: Throwable? = null) :
  RuntimeException("${cause.name}${details?.let { ": $it" } ?: ""}", t) {

  enum class Cause {
    EVENT_NOT_SUPPORTED,
    EVENT_SERIALIZATION_FAILURE,
    EVENT_DESERIALIZATION_FAILURE,
    EVENT_READ_FAILURE,
    EVENT_WRITE_FAILURE,
    EVENT_WRITE_CONFLICT,
    EVENT_HANDLER_FAILURE,
    SUMMARY_AGGREGATION_FAILURE,
    COMMAND_NOT_SUPPORTED,
    COMMAND_SERIALIZATION_FAILURE,
    COMMAND_DESERIALIZATION_FAILURE,
    COMMAND_HANDLER_FAILURE,
    TIMEOUT_EXCEPTION,
    UNKNOWN_EXCEPTION,
    ;

    operator fun invoke(t: Throwable? = null): EpoqueException = EpoqueException(this, null, t)

    operator fun invoke(message: String, t: Throwable? = null): EpoqueException =
      EpoqueException(this, message, t)
  }
}

typealias Failable<T> = Either<EpoqueException, T>
