package com.ecommerce.identity.admin.application.exception;

public class DuplicateAdministratorException extends AdministratorException {
    public DuplicateAdministratorException() {
        super("Username is already in use.");
    }
}
