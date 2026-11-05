package com.ecommerce.identity.authorization.adapter.oauth2;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.filter.OncePerRequestFilter;

import com.ecommerce.identity.authorization.adapter.oauth2.login.AdministratorSubjectResolver;

final class CurrentAdministratorFilter extends OncePerRequestFilter {
    private final AdministratorSubjectResolver subjects;

    CurrentAdministratorFilter(AdministratorSubjectResolver subjects) {
        this.subjects = subjects;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof UserDetails) {
            try {
                subjects.resolve(authentication.getName());
            } catch (RuntimeException exception) {
                SecurityContextHolder.clearContext();
                if (request.getSession(false) != null) {
                    request.getSession(false).invalidate();
                }
                response.sendRedirect(request.getContextPath() + "/login?error");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
