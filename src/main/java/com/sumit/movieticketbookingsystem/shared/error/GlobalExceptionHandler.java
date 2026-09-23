package com.sumit.movieticketbookingsystem.shared.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;

/**
 * Turns every exception into a {@link ProblemDetail} carrying one of our {@link ErrorCode}s.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String CODE = "code";

    @ExceptionHandler(DomainException.class)
    ProblemDetail handleDomain(DomainException ex) {
        ProblemDetail problem = problem(ex.code(), ex.getMessage());
        ex.details().forEach(problem::setProperty);
        return problem;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex, WebRequest request) {
        log.error("Unhandled error on {}", request.getDescription(false), ex);
        return problem(ErrorCode.INTERNAL_ERROR, "Something went wrong. Please try again.");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldViolation(error.getField(), error.getDefaultMessage()))
                .toList();

        ProblemDetail body = problem(ErrorCode.VALIDATION_FAILED, "One or more fields are invalid.");
        body.setProperty("errors", errors);
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    // Framework errors (unreadable JSON, missing params, unknown path ...) already come back as
    // ProblemDetail; this just adds our code so clients can rely on it being present.
    @Override
    protected ResponseEntity<Object> createResponseEntity(Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail problem && !hasCode(problem)) {
            if (statusCode.isSameCodeAs(ErrorCode.VALIDATION_FAILED.status())) {
                problem.setProperty(CODE, ErrorCode.VALIDATION_FAILED.name());
            } else if (statusCode.isSameCodeAs(ErrorCode.NOT_FOUND.status())) {
                problem.setProperty(CODE, ErrorCode.NOT_FOUND.name());
            }
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }

    private static boolean hasCode(ProblemDetail problem) {
        return problem.getProperties() != null && problem.getProperties().containsKey(CODE);
    }

    private static ProblemDetail problem(ErrorCode code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), detail);
        problem.setTitle(code.title());
        problem.setProperty(CODE, code.name());
        return problem;
    }

    private record FieldViolation(String field, String message) {
    }
}
