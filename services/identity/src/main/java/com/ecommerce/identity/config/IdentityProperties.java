package com.ecommerce.identity.config;

import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "identity")
public class IdentityProperties {
    private String issuer = "http://localhost:9000";
    private boolean serverEnabled;
    private String signingKeySetPath;
    private String activeSigningKeyId;
    private final Client client = new Client();

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public boolean isServerEnabled() {
        return serverEnabled;
    }

    public void setServerEnabled(boolean serverEnabled) {
        this.serverEnabled = serverEnabled;
    }

    public String getSigningKeySetPath() {
        return signingKeySetPath;
    }

    public void setSigningKeySetPath(String signingKeySetPath) {
        this.signingKeySetPath = signingKeySetPath;
    }

    public String getActiveSigningKeyId() {
        return activeSigningKeyId;
    }

    public void setActiveSigningKeyId(String activeSigningKeyId) {
        this.activeSigningKeyId = activeSigningKeyId;
    }

    public Client getClient() {
        return client;
    }

    public static class Client {
        private String clientId = "backoffice-bff";
        private String clientName = "Backoffice BFF";
        private boolean refreshTokenGrantEnabled;
        private Set<String> redirectUris = new LinkedHashSet<>();
        private Set<String> postLogoutRedirectUris = new LinkedHashSet<>();
        private Set<String> scopes =
                new LinkedHashSet<>(Set.of("openid", "profile", "ecommerce.api", "identity.admin"));

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getClientName() {
            return clientName;
        }

        public void setClientName(String clientName) {
            this.clientName = clientName;
        }

        public boolean isRefreshTokenGrantEnabled() {
            return refreshTokenGrantEnabled;
        }

        public void setRefreshTokenGrantEnabled(boolean refreshTokenGrantEnabled) {
            this.refreshTokenGrantEnabled = refreshTokenGrantEnabled;
        }

        public Set<String> getRedirectUris() {
            return redirectUris;
        }

        public void setRedirectUris(Set<String> redirectUris) {
            this.redirectUris = new LinkedHashSet<>(redirectUris);
        }

        public Set<String> getPostLogoutRedirectUris() {
            return postLogoutRedirectUris;
        }

        public void setPostLogoutRedirectUris(Set<String> postLogoutRedirectUris) {
            this.postLogoutRedirectUris = new LinkedHashSet<>(postLogoutRedirectUris);
        }

        public Set<String> getScopes() {
            return scopes;
        }

        public void setScopes(Set<String> scopes) {
            this.scopes = new LinkedHashSet<>(scopes);
        }
    }
}
