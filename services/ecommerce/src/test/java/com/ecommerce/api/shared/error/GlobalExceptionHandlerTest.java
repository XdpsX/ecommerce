package com.ecommerce.api.shared.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.context.request.ServletWebRequest;

import com.ecommerce.api.category.application.exception.CategoryException;

class GlobalExceptionHandlerTest {
    @Test
    void leavesNonApiFailuresToTheirExistingHandling() {
        var failure = new IllegalStateException("private diagnostics");
        var request = new MockHttpServletRequest("GET", "/login");

        assertThatThrownBy(() -> new GlobalExceptionHandler().unexpected(failure, new ServletWebRequest(request)))
                .isSameAs(failure);
    }

    @Test
    void preservesFrameworkStatusAndAllowHeaderForApiRequests() throws Exception {
        var failure = new HttpRequestMethodNotSupportedException("POST", List.of("GET"));
        var request = new MockHttpServletRequest("POST", "/api/example");

        var response = new GlobalExceptionHandler().unexpected(failure, new ServletWebRequest(request));

        assertThat(response.getStatusCode().value()).isEqualTo(405);
        assertThat(response.getHeaders().getAllow()).containsExactly(HttpMethod.GET);
        assertThat(((ProblemDetail) response.getBody()).getProperties()).containsEntry("code", "METHOD_NOT_ALLOWED");
    }

    @Test
    void keepsUnexpectedApiFailuresSafeAndUncacheable() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/example");
        var response = new GlobalExceptionHandler()
                .unexpected(new IllegalStateException("private diagnostics"), new ServletWebRequest(request));
        var problem = (ProblemDetail) response.getBody();

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(problem.getProperties()).containsEntry("code", "INTERNAL_ERROR");
        assertThat(problem.getDetail()).doesNotContain("private diagnostics");
    }

    @Test
    void categoryErrorKindsMapToHttpAtTheWebBoundary() {
        var handler = new GlobalExceptionHandler();
        var request = new ServletWebRequest(new MockHttpServletRequest("GET", "/api/admin/categories/123"));

        var invalid = handler.category(
                new CategoryException(CategoryException.Kind.INVALID_CATEGORY_HIERARCHY, "Invalid hierarchy."),
                request);
        var conflict = handler.category(
                new CategoryException(CategoryException.Kind.CATEGORY_STRUCTURE_CONFLICT, "Stale structure."), request);

        assertThat(invalid.getStatusCode().value()).isEqualTo(400);
        assertThat(conflict.getStatusCode().value()).isEqualTo(409);
    }

    @Test
    void unknownCategoryPersistenceConstraintIsAnInternalErrorNotAClientConflict() {
        var request = new ServletWebRequest(new MockHttpServletRequest("PUT", "/api/admin/categories/123"));
        var response = new GlobalExceptionHandler()
                .categoryPersistenceConflict(new DataIntegrityViolationException("unexpected constraint"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(((ProblemDetail) response.getBody()).getProperties()).containsEntry("code", "INTERNAL_ERROR");
    }
}
