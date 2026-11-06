package com.ecommerce.identity.authorization.application.port;

public interface ClientRegistration {
    boolean register(char[] rawSecret);

    boolean addMediaScope(String clientId);
}
