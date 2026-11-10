package com.ecommerce.identity.admin.application.exception;

public class AdministratorNotFoundException extends AdministratorException {
    public AdministratorNotFoundException() {
        super("Administrator does not exist.");
    }
}
