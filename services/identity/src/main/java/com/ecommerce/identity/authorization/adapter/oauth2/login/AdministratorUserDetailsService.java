package com.ecommerce.identity.authorization.adapter.oauth2.login;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.ecommerce.identity.admin.application.service.AdministratorDirectory;
import com.ecommerce.identity.admin.domain.Administrator;

@Service
public class AdministratorUserDetailsService implements UserDetailsService {
    private final AdministratorDirectory administrators;

    public AdministratorUserDetailsService(AdministratorDirectory administrators) {
        this.administrators = administrators;
    }

    @Override
    public UserDetails loadUserByUsername(String usernameInput) throws UsernameNotFoundException {
        String username = usernameInput.trim().toLowerCase(Locale.ROOT);
        Administrator administrator = administrators
                .findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Administrator credentials are invalid."));
        String passwordHash = administrators.passwordHash(administrator.id());
        var entitlements = administrators.entitlements(administrator.id());
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        entitlements.roles().forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
        entitlements
                .permissions()
                .forEach(permission -> authorities.add(new SimpleGrantedAuthority("PERM_" + permission.code())));
        return User.withUsername(administrator.username())
                .password(passwordHash)
                .disabled(!administrator.enabled())
                .authorities(authorities)
                .build();
    }
}
