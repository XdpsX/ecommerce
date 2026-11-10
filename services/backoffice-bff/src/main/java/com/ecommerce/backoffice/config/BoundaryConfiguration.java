package com.ecommerce.backoffice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.server.WebFilter;

import com.ecommerce.backoffice.shared.error.ApiProblems;
import com.ecommerce.backoffice.shared.security.BrowserBoundaryFilter;

@Configuration
public class BoundaryConfiguration {
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    WebFilter boundary(ApiProblems problems, @Value("${bff.public-origin}") String origin) {
        return new BrowserBoundaryFilter(problems, origin);
    }
}
