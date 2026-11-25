package com.example.voicenotes.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import org.springframework.core.MethodParameter;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Turns exceptions into RFC 9457 "problem+json" responses.
 *
 * <p>Extending {@link ResponseEntityExceptionHandler} gives correct statuses for
 * Spring's own errors (malformed JSON, missing header, wrong method, ...) for free.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail handleNotFound(NotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Not found", ex.getMessage());
    }

    @ExceptionHandler({InvalidStateException.class, ConflictException.class})
    ProblemDetail handleConflict(RuntimeException ex) {
        return problem(HttpStatus.CONFLICT, "Conflict", ex.getMessage());
    }

    /** Someone else changed the row between our read and our write (@Version mismatch). */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ProblemDetail handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        return problem(HttpStatus.CONFLICT, "Concurrent modification",
                "The resource was modified by another request, reload and retry");
    }

    /**
     * Last line of defence: a DB constraint fired (e.g. two concurrent uploads of the
     * same chunk both passed the "already exists?" check). The DB is the source of truth.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleIntegrity(DataIntegrityViolationException ex) {
        log.warn("Constraint violation: {}", ex.getMostSpecificCause().getMessage());
        return problem(HttpStatus.CONFLICT, "Conflict", "Request conflicts with existing data, retry");
    }

    /** @Valid on a request body failed: list every bad field, not just the first. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> fieldError(e.getField(), e.getDefaultMessage()))
                .toList();
        return validationFailed(errors);
    }

    /**
     * Raised instead of the one above when a handler has constraints on plain parameters too
     * (@RequestHeader @NotBlank, @RequestParam @Max...): Spring then validates the whole
     * method and reports body errors and parameter errors together.
     */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = new ArrayList<>();
        for (ParameterValidationResult result : ex.getParameterValidationResults()) {
            if (result instanceof ParameterErrors bodyErrors) {           // @Valid @RequestBody
                bodyErrors.getFieldErrors().forEach(e -> errors.add(fieldError(e.getField(), e.getDefaultMessage())));
            } else {                                                     // header, query or path param
                String name = parameterName(result.getMethodParameter());
                result.getResolvableErrors().forEach(e -> errors.add(fieldError(name, e.getDefaultMessage())));
            }
        }
        return validationFailed(errors);
    }

    /** Name the client actually sent (e.g. "Idempotency-Key"), not the Java parameter name. */
    private static String parameterName(MethodParameter p) {
        RequestHeader header = p.getParameterAnnotation(RequestHeader.class);
        if (header != null && !header.value().isEmpty()) return header.value();
        RequestParam param = p.getParameterAnnotation(RequestParam.class);
        if (param != null && !param.value().isEmpty()) return param.value();
        return p.getParameterName();
    }

    private static Map<String, String> fieldError(String field, String message) {
        return Map.of("field", field, "message", String.valueOf(message));
    }

    private static ResponseEntity<Object> validationFailed(List<Map<String, String>> errors) {
        ProblemDetail body = problem(HttpStatus.BAD_REQUEST, "Validation failed", "Request has invalid fields");
        body.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(body);
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        return pd;
    }
}
