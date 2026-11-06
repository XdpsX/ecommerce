package com.ecommerce.media.adapter.web;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.ecommerce.media.asset.application.exception.MediaException;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    private static ResponseEntity<Object> response(ProblemDetail problem, HttpHeaders original) {
        HttpHeaders headers = new HttpHeaders();
        headers.putAll(original);
        headers.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        headers.setCacheControl("no-store");
        return new ResponseEntity<>(problem, headers, problem.getStatus());
    }

    @ExceptionHandler(MediaException.class)
    ResponseEntity<Object> media(MediaException exception, HttpServletRequest request) {
        return response(
                ApiProblems.create(
                        status(exception.kind()), exception.code(), exception.getMessage(), request.getRequestURI()),
                new HttpHeaders());
    }

    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return response(
                ApiProblems.create(
                        HttpStatus.CONTENT_TOO_LARGE,
                        "FILE_TOO_LARGE",
                        "The uploaded file exceeds the request limit.",
                        path(request)),
                headers);
    }

    @ExceptionHandler(MultipartException.class)
    ResponseEntity<Object> malformed(MultipartException exception, HttpServletRequest request) {
        return response(
                ApiProblems.create(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_REQUEST",
                        "The multipart form is invalid.",
                        request.getRequestURI()),
                new HttpHeaders());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> unexpected(Exception exception, HttpServletRequest request) {
        return response(
                ApiProblems.create(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "INTERNAL_ERROR",
                        "An unexpected server error occurred.",
                        request.getRequestURI()),
                new HttpHeaders());
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String code = code(status);
        return response(ApiProblems.create(status, code, detail(status), path(request)), headers);
    }

    private static HttpStatus status(MediaException.Kind kind) {
        return switch (kind) {
            case INVALID_IMAGE -> HttpStatus.UNPROCESSABLE_CONTENT;
            case FILE_TOO_LARGE -> HttpStatus.CONTENT_TOO_LARGE;
            case UNSUPPORTED_MEDIA_TYPE -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;
            case STATE_CONFLICT -> HttpStatus.CONFLICT;
            case STORAGE_UNAVAILABLE, PROCESSING_BUSY -> HttpStatus.SERVICE_UNAVAILABLE;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
        };
    }

    private static String code(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> "INVALID_REQUEST";
            case 405 -> "METHOD_NOT_ALLOWED";
            case 406 -> "NOT_ACCEPTABLE";
            case 413 -> "FILE_TOO_LARGE";
            case 415 -> "UNSUPPORTED_MEDIA_TYPE";
            default -> status.is4xxClientError() ? "INVALID_REQUEST" : "INTERNAL_ERROR";
        };
    }

    private static String detail(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> "The request is invalid.";
            case 405 -> "The request method is not allowed for this resource.";
            case 406 -> "The requested response format is not available.";
            case 413 -> "The uploaded file exceeds the request limit.";
            case 415 -> "The request media type is not supported.";
            default ->
                status.is4xxClientError()
                        ? "The request could not be processed."
                        : "An unexpected server error occurred.";
        };
    }

    private static String path(WebRequest request) {
        return request instanceof ServletWebRequest servlet
                ? servlet.getRequest().getRequestURI()
                : "/";
    }
}
