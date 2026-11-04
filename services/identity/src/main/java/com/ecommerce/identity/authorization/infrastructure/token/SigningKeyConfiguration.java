package com.ecommerce.identity.authorization.infrastructure.token;

import java.io.IOException;
import java.nio.file.Path;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ecommerce.identity.configuration.IdentityProperties;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

@Configuration
@ConditionalOnProperty(prefix = "identity", name = "server-enabled", havingValue = "true")
public class SigningKeyConfiguration {
    @Bean
    JWKSource<SecurityContext> jwkSource(IdentityProperties properties) {
        if (properties.getSigningKeySetPath() == null
                || properties.getSigningKeySetPath().isBlank()
                || properties.getActiveSigningKeyId() == null) {
            throw new IllegalStateException("IDENTITY_JWK_SET_PATH and IDENTITY_ACTIVE_KEY_ID are required.");
        }
        try {
            Path keySetPath =
                    Path.of(properties.getSigningKeySetPath()).toAbsolutePath().normalize();
            JWKSet keySet = JWKSet.load(keySetPath.toFile());
            long activeKeyCount = keySet.getKeys().stream()
                    .filter(key -> properties.getActiveSigningKeyId().equals(key.getKeyID()))
                    .count();
            JWK activeKey = keySet.getKeyByKeyId(properties.getActiveSigningKeyId());
            if (activeKeyCount != 1
                    || !(activeKey instanceof RSAKey rsaKey)
                    || rsaKey.toRSAPrivateKey() == null
                    || rsaKey.size() < 2048
                    || (rsaKey.getKeyUse() != null && !KeyUse.SIGNATURE.equals(rsaKey.getKeyUse()))
                    || (rsaKey.getAlgorithm() != null && !JWSAlgorithm.RS256.equals(rsaKey.getAlgorithm()))) {
                throw new IllegalStateException("The configured active key must be a private RSA RS256 signing key.");
            }
            return new ImmutableJWKSet<>(keySet);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read the configured signing JWK set.", exception);
        } catch (Exception exception) {
            if (exception instanceof IllegalStateException stateException) {
                throw stateException;
            }
            throw new IllegalStateException("The configured signing JWK set is invalid.", exception);
        }
    }
}
