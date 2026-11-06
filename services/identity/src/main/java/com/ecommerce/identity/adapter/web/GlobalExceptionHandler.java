package com.ecommerce.identity.adapter.web;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

import com.ecommerce.identity.admin.application.exception.AdministratorException;
import com.ecommerce.identity.admin.application.exception.AdministratorNotAuthorizedException;
import com.ecommerce.identity.admin.application.exception.AdministratorNotFoundException;
import com.ecommerce.identity.admin.application.exception.DuplicateAdministratorException;
import com.ecommerce.identity.admin.application.exception.LastAdministratorException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final System.Logger LOGGER = System.getLogger(GlobalExceptionHandler.class.getName());

    @ExceptionHandler(AdministratorException.class)
    ResponseEntity<Object> administrator(AdministratorException exception, WebRequest request) {
        if (!isApi(request)) throw exception;
        HttpStatus status = HttpStatus.BAD_REQUEST;
        String code = "INVALID_ADMINISTRATOR";
        if (exception instanceof AdministratorNotFoundException) {
            status = HttpStatus.NOT_FOUND;
            code = "ADMINISTRATOR_NOT_FOUND";
        } else if (exception instanceof AdministratorNotAuthorizedException) {
            status = HttpStatus.FORBIDDEN;
            code = "FORBIDDEN";
        } else if (exception instanceof DuplicateAdministratorException) {
            status = HttpStatus.CONFLICT;
            code = "DUPLICATE_ADMINISTRATOR";
        } else if (exception instanceof LastAdministratorException) {
            status = HttpStatus.CONFLICT;
            code = "LAST_PRIVILEGED_ADMINISTRATOR";
        }
        return response(ApiProblems.create(status, code, exception.getMessage(), path(request)), new HttpHeaders());
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
