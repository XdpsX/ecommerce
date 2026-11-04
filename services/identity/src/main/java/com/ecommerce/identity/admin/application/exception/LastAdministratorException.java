package com.ecommerce.identity.admin.application.exception;

public final class LastAdministratorException extends AdministratorException {
    public LastAdministratorException() {
        super("At least one enabled SUPER_ADMIN with all management permissions must remain.");
    }
}
