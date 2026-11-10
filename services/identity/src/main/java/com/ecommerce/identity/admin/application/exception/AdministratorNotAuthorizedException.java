package com.ecommerce.identity.admin.application.exception;

public class AdministratorNotAuthorizedException extends AdministratorException {
    public AdministratorNotAuthorizedException() {
        super("The current administrator is not authorized.");
    }
}
