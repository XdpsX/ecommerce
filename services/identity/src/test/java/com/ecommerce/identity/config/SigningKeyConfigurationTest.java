package com.ecommerce.identity.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

class SigningKeyConfigurationTest {
    private final SigningKeyConfiguration configuration = new SigningKeyConfiguration();

    @Test
    void requiresExplicitKeySetAndActiveKeyId() {
        assertThatThrownBy(() -> configuration.jwkSource(new IdentityProperties()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("IDENTITY_JWK_SET_PATH");
    }

    @Test
    void requiresTheConfiguredActiveKeyToHavePrivateRsaMaterial() throws Exception {
        RSAKey privateKey = new RSAKeyGenerator(2048)
                .keyID("active")
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .generate();
        Path path = Files.createTempFile("identity-jwks", ".json");
        try {
            Files.writeString(path, new JWKSet(List.of(privateKey.toPublicJWK())).toString(false));
            IdentityProperties properties = new IdentityProperties();
            properties.setSigningKeySetPath(path.toString());
            properties.setActiveSigningKeyId("active");

            assertThatThrownBy(() -> configuration.jwkSource(properties))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("private RSA RS256");
        } finally {
            Files.deleteIfExists(path);
        }
    }

    @Test
    void usesTheExplicitActiveKeyIdFromTheExternalKeySet() throws Exception {
        RSAKey privateKey = new RSAKeyGenerator(2048)
                .keyID("active-kid")
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .generate();
        Path path = Files.createTempFile("identity-jwks", ".json");
        try {
            Files.writeString(path, new JWKSet(List.of(privateKey)).toString(false));
            IdentityProperties properties = new IdentityProperties();
            properties.setSigningKeySetPath(path.toString());
            properties.setActiveSigningKeyId("active-kid");

            JWKSource<SecurityContext> source = configuration.jwkSource(properties);
            assertThat(source).isNotNull();
        } finally {
            Files.deleteIfExists(path);
        }
    }
}
