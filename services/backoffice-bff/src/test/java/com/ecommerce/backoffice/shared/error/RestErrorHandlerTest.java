package com.ecommerce.backoffice.shared.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ConnectException;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;

class RestErrorHandlerTest {
    @Test
    void upstreamConnectionFailureUsesSafeProblemDetails() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/ecommerce/probe"));
        new RestErrorHandler(new ApiProblems())
                .handle(exchange, new ConnectException("private-host:secret"))
                .block();
        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(502);
        assertThat(exchange.getResponse().getHeaders().getContentType().toString())
                .isEqualTo("application/problem+json");
        assertThat(exchange.getResponse().getBodyAsString().block())
                .contains("BAD_GATEWAY")
                .doesNotContain("private-host", "secret", "ConnectException");
    }

    @Test
    void oauthNavigationErrorsStayWithTheProtocolBoundary() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/login/oauth2/code/identity"));
        var error = new IllegalStateException("protocol failure");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new RestErrorHandler(new ApiProblems())
                        .handle(exchange, error)
                        .block())
                .isSameAs(error);
    }

    @Test
    void contentNegotiationFailuresUseTheirContractCodes() {
        Map<HttpStatus, String> expectedCodes = Map.of(
                HttpStatus.NOT_ACCEPTABLE, "NOT_ACCEPTABLE",
                HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE");

        expectedCodes.forEach((status, code) -> {
            var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/ecommerce/probe"));
            new RestErrorHandler(new ApiProblems())
                    .handle(exchange, new ResponseStatusException(status))
                    .block();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(status);
            assertThat(exchange.getResponse().getBodyAsString().block()).contains("\"code\":\"" + code + "\"");
        });
    }
}
