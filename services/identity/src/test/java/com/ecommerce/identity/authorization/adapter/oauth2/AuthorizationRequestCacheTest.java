package com.ecommerce.identity.authorization.adapter.oauth2;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.DispatcherType;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;

class AuthorizationRequestCacheTest {
    @Test
    void faviconAndErrorDispatchCannotOverwritePendingAuthorization() {
        var cache = new AuthorizationServerConfiguration()
                .identityRequestCache(AuthorizationServerSettings.builder().build());
        var session = new MockHttpSession();
        var response = new MockHttpServletResponse();
        var authorize = request(session, "/oauth2/authorize");
        authorize.setQueryString("client_id=backoffice-bff&response_type=code");
        cache.saveRequest(authorize, response);
        String destination = cache.getRequest(authorize, response).getRedirectUrl();
        cache.saveRequest(request(session, "/favicon.ico"), response);
        var error = request(session, "/error");
        error.setDispatcherType(DispatcherType.ERROR);
        cache.saveRequest(error, response);
        var post = request(session, "/oauth2/authorize");
        post.setMethod("POST");
        cache.saveRequest(post, response);
        assertThat(cache.getRequest(authorize, response).getRedirectUrl()).isEqualTo(destination);
        assertThat(destination).contains("/oauth2/authorize", "client_id=backoffice-bff");
    }

    private MockHttpServletRequest request(MockHttpSession session, String path) {
        var request = new MockHttpServletRequest("GET", path);
        request.setSession(session);
        request.setServletPath(path);
        return request;
    }
}
