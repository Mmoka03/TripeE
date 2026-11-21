package com.tripee.backend.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
@Order(-1)
public class ApiProblems {
  private static final Logger log = LoggerFactory.getLogger(ApiProblems.class);

  public static ProblemDetail problem(HttpStatus status, String code, String detail, HttpServletRequest request) {
    var problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setType(URI.create("about:blank"));
    problem.setInstance(URI.create(request.getRequestURI()));
    problem.setProperty("code", code);
    problem.setProperty("traceId", UUID.randomUUID().toString());
    return problem;
  }

  @ExceptionHandler(ApiException.class)
  ProblemDetail applicationError(ApiException ex, HttpServletRequest request) {
    return problem(ex.status(), ex.code(), ex.getMessage(), request);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ProblemDetail malformedBody(HttpServletRequest request) {
    return problem(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "요청 본문을 확인해 주세요.", request);
  }

  @ExceptionHandler(MissingRequestHeaderException.class)
  ProblemDetail missingHeader(HttpServletRequest request) {
    return problem(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "필수 요청 헤더를 확인해 주세요.", request);
  }

  @ExceptionHandler(MissingServletRequestPartException.class)
  ProblemDetail missingPart(HttpServletRequest request) {
    return problem(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "필수 요청 항목을 확인해 주세요.", request);
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  ProblemDetail unsupportedMedia(HttpServletRequest request) {
    return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "지원하지 않는 요청 형식입니다.", request);
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ProblemDetail tooLarge(HttpServletRequest request) {
    return problem(HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE", "요청 크기를 확인해 주세요.", request);
  }

  @ExceptionHandler({HandlerMethodValidationException.class, MissingServletRequestParameterException.class,
      MethodArgumentTypeMismatchException.class})
  ProblemDetail invalidParameter(HttpServletRequest request) {
    return problem(HttpStatus.BAD_REQUEST, "INVALID_QUERY", "조회 조건을 확인해 주세요.", request);
  }

  @ExceptionHandler(DataAccessException.class)
  ProblemDetail databaseError(HttpServletRequest request) {
    var response = problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "데이터를 조회할 수 없습니다.", request);
    log.error("Database query failed at {} (traceId={})", request.getRequestURI(),
        response.getProperties().get("traceId"));
    return response;
  }
}
