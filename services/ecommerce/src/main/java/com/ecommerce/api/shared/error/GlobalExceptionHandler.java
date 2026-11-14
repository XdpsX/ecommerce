package com.ecommerce.api.shared.error;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import jakarta.validation.ConstraintViolationException;

import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.method.MethodValidationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import com.ecommerce.api.category.application.exception.CategoryException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final System.Logger LOGGER = System.getLogger(GlobalExceptionHandler.class.getName());

    @ExceptionHandler(CategoryException.class)
    ResponseEntity<Object> category(CategoryException exception, WebRequest request) {
        HttpStatus status = switch (exception.kind()) {
            case BAD_REQUEST, INVALID_CATEGORY_HIERARCHY, CATEGORY_DEPTH_EXCEEDED -> HttpStatus.BAD_REQUEST;
            case CATEGORY_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CATEGORY_VERSION_CONFLICT, CATEGORY_STRUCTURE_CONFLICT, CATEGORY_SLUG_CONFLICT, CATEGORY_IN_USE ->
                HttpStatus.CONFLICT;
        };
        ProblemDetail problem = ApiProblems.create(status, exception.code(), exception.getMessage(), path(request));
        return response(problem, new HttpHeaders());
    }

    @ExceptionHandler({DataIntegrityViolationException.class, ObjectOptimisticLockingFailureException.class})
    ResponseEntity<Object> categoryPersistenceConflict(RuntimeException exception, WebRequest request) {
        if (!path(request).contains("/api/admin/categories")) throw exception;
        String code;
        if (exception instanceof ObjectOptimisticLockingFailureException) {
            code = "CATEGORY_VERSION_CONFLICT";
        } else {
            String constraint = constraintName(exception);
            if (constraint.equals("categories_slug_unique")) {
                code = "CATEGORY_SLUG_CONFLICT";
            } else if (constraint.equals("categories_sibling_order_unique")) {
                code = "CATEGORY_STRUCTURE_CONFLICT";
            } else {
                LOGGER.log(System.Logger.Level.ERROR, "Unexpected Category persistence constraint: " + constraint);
                return response(
                        ApiProblems.generic(HttpStatus.INTERNAL_SERVER_ERROR, path(request)), new HttpHeaders());
            }
        }
        ProblemDetail problem = ApiProblems.create(
                HttpStatus.CONFLICT,
                code,
                "The category conflicts with a concurrent or existing value.",
                path(request));
        return response(problem, new HttpHeaders());
    }

    private static String constraintName(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof org.hibernate.exception.ConstraintViolationException violation) {
                return violation.getConstraintName() == null ? "unknown" : violation.getConstraintName();
            }
            current = current.getCause();
        }
        return "unknown";
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Object> forbidden(AccessDeniedException exception, WebRequest request) {
        if (!isApi(request)) throw exception;
        return response(ApiProblems.generic(HttpStatus.FORBIDDEN, path(request)), new HttpHeaders());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> unexpected(Exception exception, WebRequest request) throws Exception {
        if (!isApi(request)) throw exception;
        if (exception instanceof ErrorResponse error) {
            return response(
                    ApiProblems.generic(HttpStatus.valueOf(error.getStatusCode().value()), path(request)),
                    error.getHeaders());
        }
        if (exception instanceof TypeMismatchException || exception instanceof HttpMessageNotReadableException) {
            return response(ApiProblems.generic(HttpStatus.BAD_REQUEST, path(request)), new HttpHeaders());
        }
        LOGGER.log(
                System.Logger.Level.ERROR,
                "Unexpected API exception: " + exception.getClass().getName());
        return response(ApiProblems.generic(HttpStatus.INTERNAL_SERVER_ERROR, path(request)), new HttpHeaders());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Object> validation(MethodArgumentNotValidException exception, WebRequest request) throws Exception {
        if (!isApi(request)) throw exception;
        ProblemDetail problem = ApiProblems.create(
                HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Some fields are invalid.", path(request));
        List<Map<String, String>> errors = Stream.concat(
                        exception.getBindingResult().getFieldErrors().stream()
                                .map(error -> Map.of(
                                        "location",
                                        "body",
                                        "pointer",
                                        pointer(error.getField()),
                                        "code",
                                        constraintCode(error.getCode()),
                                        "message",
                                        constraintMessage(error.getCode()))),
                        exception.getBindingResult().getGlobalErrors().stream()
                                .map(error -> Map.of(
                                        "location",
                                        "body",
                                        "pointer",
                                        "",
                                        "code",
                                        constraintCode(error.getCode()),
                                        "message",
                                        constraintMessage(error.getCode()))))
                .toList();
        problem.setProperty("errors", errors);
        return response(problem, new HttpHeaders());
    }

    @ExceptionHandler({HandlerMethodValidationException.class, MethodValidationException.class})
    ResponseEntity<Object> methodValidation(Exception exception, WebRequest request) {
        if (!isApi(request)) throw new IllegalStateException("Method validation outside API.", exception);
        return response(
                ApiProblems.create(
                        HttpStatus.BAD_REQUEST,
                        "VALIDATION_FAILED",
                        "Some query parameters are invalid.",
                        path(request)),
                new HttpHeaders());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<Object> constraintValidation(ConstraintViolationException exception, WebRequest request) {
        if (!path(request).contains("/api/admin/categories/slug-suggestion")) {
            return response(ApiProblems.generic(HttpStatus.BAD_REQUEST, path(request)), new HttpHeaders());
        }
        List<Map<String, String>> errors = new java.util.ArrayList<>();
        exception.getConstraintViolations().forEach(violation -> {
            String name = "name";
            for (var node : violation.getPropertyPath()) name = node.getName();
            errors.add(Map.of(
                    "location", "query",
                    "name", name,
                    "code", "INVALID_VALUE",
                    "message", "The query parameter is invalid."));
        });
        ProblemDetail problem = ApiProblems.create(
                HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Some query parameters are invalid.", path(request));
        problem.setProperty("errors", errors);
        return response(problem, new HttpHeaders());
    }

    private static boolean isApi(WebRequest request) {
        var servletRequest = ((ServletWebRequest) request).getRequest();
        String path = servletRequest
                .getRequestURI()
                .substring(servletRequest.getContextPath().length());
        return path.equals("/api") || path.startsWith("/api/");
    }

    private static String path(WebRequest request) {
        return ((ServletWebRequest) request).getRequest().getRequestURI();
    }

    private static ResponseEntity<Object> response(ProblemDetail problem, HttpHeaders original) {
        HttpHeaders headers = new HttpHeaders();
        headers.putAll(original);
        headers.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        headers.setCacheControl("no-store");
        if (problem.getStatus() == 401) headers.set("WWW-Authenticate", "Bearer");
        return new ResponseEntity<>(problem, headers, problem.getStatus());
    }

    private static String pointer(String field) {
        return "/"
                + field.replace("[]", "")
                        .replace("~", "~0")
                        .replace("/", "~1")
                        .replaceAll("\\[([^]]*)\\]", "/$1")
                        .replace('.', '/');
    }

    private static String constraintCode(String constraint) {
        return switch (constraint == null ? "" : constraint) {
            case "NotBlank", "NotNull" -> "REQUIRED";
            case "Size" -> "INVALID_SIZE";
            default -> "INVALID_VALUE";
        };
    }

    private static String constraintMessage(String constraint) {
        return switch (constraintCode(constraint)) {
            case "REQUIRED" -> "A value is required.";
            case "INVALID_SIZE" -> "The value has an invalid size.";
            default -> "The value is invalid.";
        };
    }
}
