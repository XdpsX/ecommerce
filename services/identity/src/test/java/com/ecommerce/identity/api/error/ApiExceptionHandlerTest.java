package com.ecommerce.identity.api.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.server.ResponseStatusException;

import com.ecommerce.identity.admin.api.http.AdministratorManagementController;
import com.ecommerce.identity.admin.application.AdministratorQueries;

class ApiExceptionHandlerTest {
    @Test
    void leavesNonApiHandlingUnchangedAndRedactsCredentialDto() throws Exception {
        var request = new MockHttpServletRequest("GET", "/login");
        var exception = new ResponseStatusException(HttpStatus.BAD_REQUEST);
        assertThatThrownBy(() -> new ApiExceptionHandler().unexpected(exception, new ServletWebRequest(request)))
                .isSameAs(exception);
        var contextualRequest = new MockHttpServletRequest("GET", "/identity/api/admin/administrators");
        contextualRequest.setContextPath("/identity");
        assertThat(new ApiExceptionHandler()
                        .unexpected(exception, new ServletWebRequest(contextualRequest))
                        .getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(new com.ecommerce.identity.admin.api.http.AdministratorManagementWriteController.CreateAdministrator(
                                "username", "sensitive-password")
                        .toString())
                .doesNotContain("sensitive-password", "username");
    }

    @Test
    void hidesUnexpectedExceptionDetailsAndPreservesMethodAllowHeader() throws Exception {
        AdministratorQueries queries = mock(AdministratorQueries.class);
        when(queries.list(0, 20)).thenThrow(new IllegalStateException("database-password-do-not-expose"));
        var mvc = MockMvcBuilders.standaloneSetup(new AdministratorManagementController(queries))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();

        mvc.perform(get("/api/admin/administrators"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred."))
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(content()
                        .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("database-password"))));

        mvc.perform(patch("/api/admin/administrators"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().exists("Allow"))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void mapsPaginationConstraintViolationsToSafeBadRequestProblems() throws Exception {
        AdministratorQueries queries = mock(AdministratorQueries.class);
        var mvc = MockMvcBuilders.standaloneSetup(new AdministratorManagementController(queries))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();

        mvc.perform(get("/api/admin/administrators").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.detail").value("The request is invalid."));

        mvc.perform(get("/api/admin/administrators").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }
}
