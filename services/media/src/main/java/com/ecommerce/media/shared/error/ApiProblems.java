package com.ecommerce.media.shared.error;

import java.net.URI;
import java.util.Locale;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
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

    public static ProblemDetail create(HttpStatusCode status, String code, String detail, String path) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(HttpStatus.valueOf(status.value()).getReasonPhrase());
        problem.setType(URI.create(
                "urn:ecommerce:problem:" + code.toLowerCase(Locale.ROOT).replace('_', '-')));
        problem.setInstance(URI.create(path));
        problem.setProperty("code", code);
        return problem;
    }

    public void writeSecurity(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            String code,
            String detail,
            boolean challenge)
            throws java.io.IOException {
        if (response.isCommitted()) return;
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setHeader("Cache-Control", "no-store");
        if (challenge) response.setHeader("WWW-Authenticate", "Bearer");
        ProblemDetail problem = create(status, code, detail, request.getRequestURI());
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
