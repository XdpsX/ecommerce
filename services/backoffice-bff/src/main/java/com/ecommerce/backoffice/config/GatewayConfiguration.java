package com.ecommerce.backoffice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.*;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.*;

@Configuration
public class GatewayConfiguration {
    @Bean
    RouteLocator routes(RouteLocatorBuilder builder, @Value("${bff.ecommerce-uri}") String ecommerce) {
        return builder.routes()
                .route(
                        "ecommerce",
                        route -> route.path("/api/ecommerce/**")
                                .and()
                                .method("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE")
                                .filters(filters -> filters.removeRequestHeader("Cookie")
                                        .removeRequestHeader("X-CSRF-TOKEN")
                                        .removeRequestHeader("X-User")
                                        .removeRequestHeader("X-User-Id")
                                        .tokenRelay()
                                        .rewritePath("/api/ecommerce/(?<remaining>.*)", "/api/${remaining}")
                                        .removeResponseHeader("Set-Cookie"))
                                .uri(ecommerce))
                .build();
    }
}
