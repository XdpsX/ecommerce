package com.ecommerce.api.shared.error;

import java.net.URI;
import java.util.Locale;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

@Component
public class ApiProblems {
    private final JsonMapper json;

    public ApiProblems(JsonMapper json) {
        this.json = json;
    }

    public static ProblemDetail create(HttpStatus status, String code, String detail, String path) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setType(URI.create(
                "urn:ecommerce:problem:" + code.toLowerCase(Locale.ROOT).replace('_', '-')));
        problem.setInstance(URI.create(path));
        problem.setProperty("code", code);
        return problem;
    }

    public static ProblemDetail generic(HttpStatus status, String path) {
        String code = switch (status) {
            case BAD_REQUEST -> "BAD_REQUEST";
            case UNAUTHORIZED -> "UNAUTHENTICATED";
            case FORBIDDEN -> "FORBIDDEN";
            case NOT_FOUND -> "NOT_FOUND";
            case METHOD_NOT_ALLOWED -> "METHOD_NOT_ALLOWED";
            case UNSUPPORTED_MEDIA_TYPE -> "UNSUPPORTED_MEDIA_TYPE";
            case NOT_ACCEPTABLE -> "NOT_ACCEPTABLE";
            default -> status.is5xxServerError() ? "INTERNAL_ERROR" : "REQUEST_REJECTED";
        };
        String detail = switch (status) {
            case BAD_REQUEST -> "The request is invalid.";
            case UNAUTHORIZED -> "Authentication is required.";
            case FORBIDDEN -> "The operation is not permitted.";
            case NOT_FOUND -> "The resource does not exist.";
            case METHOD_NOT_ALLOWED -> "The HTTP method is not allowed.";
            case UNSUPPORTED_MEDIA_TYPE -> "The request content type is not supported.";
            case NOT_ACCEPTABLE -> "The requested response representation is not supported.";
            default -> status.is5xxServerError() ? "An unexpected error occurred." : "The request was rejected.";
        };
        return create(status, code, detail, path);
    }

    public void writeSecurity(HttpServletRequest request, HttpServletResponse response, HttpStatus status)
            throws java.io.IOException {
        if (response.isCommitted()) return;
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setHeader("Cache-Control", "no-store");
        response.setHeader(
                "WWW-Authenticate",
                status == HttpStatus.UNAUTHORIZED ? "Bearer" : "Bearer error=\"insufficient_scope\"");
        ProblemDetail problem = generic(status, request.getRequestURI());
        json.writeValue(
                response.getOutputStream(),
                Map.of(
                        "type",
                        problem.getType().toString(),
                        "title",
                        problem.getTitle(),
                        "status",
                        problem.getStatus(),
                        "detail",
                        problem.getDetail(),
                        "instance",
                        problem.getInstance().toString(),
                        "code",
                        problem.getProperties().get("code")));
    }
}
