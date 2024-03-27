package io.github.uharaqo.epoque.api

import arrow.core.Either

class EpoqueException(cause: Cause, details: String? = null, t: Throwable? = null) :
  RuntimeException("${cause.name}${details?.let { ": $it" } ?: ""}", t) {

  enum class Cause(val type: Type) {
    EVENT_NOT_SUPPORTED(Type.INVALID_STATE),
    EVENT_ENCODING_FAILURE(Type.UNEXPECTED),
    EVENT_DECODING_FAILURE(Type.INVALID_STATE),
    EVENT_READ_FAILURE(Type.TEMPORARY_FAILURE),
    EVENT_WRITE_FAILURE(Type.TEMPORARY_FAILURE),
    EVENT_WRITE_CONFLICT(Type.TEMPORARY_FAILURE),
    EVENT_HANDLER_FAILURE(Type.INVALID_STATE),
    SUMMARY_AGGREGATION_FAILURE(Type.INVALID_STATE),
    COMMAND_NOT_SUPPORTED(Type.INVALID_INPUT),
    COMMAND_ENCODING_FAILURE(Type.INVALID_INPUT),
    COMMAND_DECODING_FAILURE(Type.INVALID_INPUT),
    COMMAND_REJECTED(Type.INVALID_INPUT),
    COMMAND_PREPARATION_FAILURE(Type.TEMPORARY_FAILURE),
    COMMAND_HANDLER_FAILURE(Type.TEMPORARY_FAILURE),
    COMMAND_CHAIN_FAILURE(Type.TEMPORARY_FAILURE),
    PROJECTION_FAILURE(Type.TEMPORARY_FAILURE),
    NOTIFICATION_FAILURE(Type.TEMPORARY_FAILURE),
    TIMEOUT(Type.TEMPORARY_FAILURE),
    UNEXPECTED_ERROR(Type.UNEXPECTED),
    ;

    fun toException(t: Throwable? = null): EpoqueException = toException(t, null)
    fun toException(t: Throwable? = null, message: String?): EpoqueException =
      EpoqueException(this, message, t)

    enum class Type {
      INVALID_INPUT,
      INVALID_STATE,
      TEMPORARY_FAILURE,
      UNEXPECTED,
    }
  }
}

typealias Failable<T> = Either<EpoqueException, T>
