package com.ecommerce.identity.authorization.application;

public interface ClientRegistration {
    boolean register(char[] rawSecret);
}
