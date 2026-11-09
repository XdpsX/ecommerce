package com.ecommerce.backoffice.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

class CookieBoundaryTest {
    @Test
    void productionCookieIsHostOnlySecureAndDistinctFromIdentity() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("https://backoffice.example/bff/csrf"));
        new BffSecurityConfiguration().sessionIds(true).setSessionId(exchange, "test-session");
        var cookie = exchange.getResponse().getCookies().getFirst("BACKOFFICE_SESSION");
        assertThat(cookie).isNotNull();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
        assertThat(cookie.getPath()).isEqualTo("/");
        assertThat(cookie.getDomain()).isNull();
    }
}
