package com.ecommerce.identity.authorization.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import com.ecommerce.identity.configuration.IdentityProperties;

@ExtendWith(MockitoExtension.class)
class ClientRegistrationServiceTest {
    private static final String CLIENT_SECRET = "0123456789abcdef0123456789abcdef";

    @Mock
    RegisteredClientRepository clientRepository;

    @Mock
    JdbcTemplate jdbc;

    private IdentityProperties properties;
    private ClientRegistrationService service;
    private AtomicReference<RegisteredClient> savedClient;

    @BeforeEach
    void setUp() {
        properties = new IdentityProperties();
        properties.getClient().setRedirectUris(Set.of("https://backoffice.example.com/oauth/callback"));
        properties.getClient().setPostLogoutRedirectUris(Set.of("https://backoffice.example.com/logout/callback"));
        service = new ClientRegistrationService(clientRepository, new BCryptPasswordEncoder(4), properties, jdbc);
        savedClient = new AtomicReference<>();
    }

    private void stubClientRepository() {
        when(clientRepository.findByClientId(properties.getClient().getClientId()))
                .thenAnswer(invocation -> savedClient.get());
        doAnswer(invocation -> {
                    savedClient.set(invocation.getArgument(0));
                    return null;
                })
                .when(clientRepository)
                .save(any(RegisteredClient.class));
    }

    @Test
    void storesHashedSecretAndCreatesPkceAuthorizationCodeClientIdempotently() {
        stubClientRepository();
        char[] firstSecret = CLIENT_SECRET.toCharArray();
        assertThat(service.register(firstSecret)).isTrue();
        assertThat(firstSecret).containsOnly('\0');

        RegisteredClient registered = savedClient.get();
        assertThat(registered.getClientSecret()).isNotEqualTo(CLIENT_SECRET);
        assertThat(registered.getAuthorizationGrantTypes()).containsExactly(AuthorizationGrantType.AUTHORIZATION_CODE);
        assertThat(registered.getClientSettings().isRequireProofKey()).isTrue();
        assertThat(registered.getRedirectUris()).containsExactly("https://backoffice.example.com/oauth/callback");
        assertThat(registered.getScopes())
                .containsExactlyInAnyOrder("openid", "profile", "ecommerce.api", "identity.admin");

        assertThat(service.register(CLIENT_SECRET.toCharArray())).isFalse();
        verify(clientRepository).save(registered);
        verify(clientRepository, org.mockito.Mockito.times(2))
                .findByClientId(properties.getClient().getClientId());
        verifyNoMoreInteractions(clientRepository);
    }

    @Test
    void refusesToOverwriteAClientWhenItsSecretDoesNotMatch() {
        stubClientRepository();
        service.register(CLIENT_SECRET.toCharArray());

        assertThatThrownBy(() -> service.register("abcdef0123456789abcdef0123456789".toCharArray()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("refusing to overwrite");
        verify(clientRepository).save(ArgumentMatchers.any(RegisteredClient.class));
        verify(clientRepository, org.mockito.Mockito.times(2))
                .findByClientId(properties.getClient().getClientId());
        verifyNoMoreInteractions(clientRepository);
    }

    @Test
    void rejectsAnUnapprovedRedirectScheme() {
        properties.getClient().setRedirectUris(Set.of("http://backoffice.example.com/callback"));

        assertThatThrownBy(() -> service.register(CLIENT_SECRET.toCharArray()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTPS");
        verify(clientRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void enablesRefreshOnlyThroughAnExplicitRegistrationAfterReplayProtectionIsEnabled() {
        stubClientRepository();
        service.register(CLIENT_SECRET.toCharArray());
        properties.getClient().setRefreshTokenGrantEnabled(true);

        assertThat(service.register(CLIENT_SECRET.toCharArray())).isTrue();

        org.mockito.Mockito.verify(jdbc)
                .update(
                        "UPDATE oauth2_registered_client SET authorization_grant_types = ? WHERE id = ?",
                        "authorization_code,refresh_token",
                        savedClient.get().getId());
    }
}
