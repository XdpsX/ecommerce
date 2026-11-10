package com.ecommerce.identity.admin.application.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ecommerce.identity.admin.application.port.AdministratorRepository;
import com.ecommerce.identity.admin.domain.Administrator;
import com.ecommerce.identity.admin.domain.Entitlements;

@Service
public class AdministratorDirectory {
    private final AdministratorRepository repository;

    public AdministratorDirectory(AdministratorRepository repository) {
        this.repository = repository;
    }

    public Optional<Administrator> findByUsername(String username) {
        return repository.findByUsername(username);
    }

    public Entitlements entitlements(UUID id) {
        return repository.entitlements(id);
    }

    public String passwordHash(UUID id) {
        return repository.passwordHash(id);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lockByUsername(String username) {
        repository.lockByUsername(username);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean lockEnabled(UUID id) {
        return repository.lockEnabled(id);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void auditRefreshReuse(UUID id) {
        repository.auditRefreshReuse(id);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void auditClientScopeChange(String clientId) {
        repository.auditClientScopeChange(clientId);
    }
}
