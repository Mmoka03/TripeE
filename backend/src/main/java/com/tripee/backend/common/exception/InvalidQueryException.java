package com.tripee.backend.common.exception;

import org.springframework.http.HttpStatus;

public final class InvalidQueryException extends ApiException {
  public InvalidQueryException(String detail) {
    super(HttpStatus.BAD_REQUEST, "INVALID_QUERY", detail);
  }
}
