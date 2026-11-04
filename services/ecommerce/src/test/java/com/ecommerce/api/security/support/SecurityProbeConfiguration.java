package com.ecommerce.api.security.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;

@TestConfiguration(proxyBeanMethods = false)
public class SecurityProbeConfiguration {
    @Bean
    Customizer<AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry>
            probeAuthorization() {
        return rules -> rules.requestMatchers("/api/test/security-probe").authenticated();
    }
}
