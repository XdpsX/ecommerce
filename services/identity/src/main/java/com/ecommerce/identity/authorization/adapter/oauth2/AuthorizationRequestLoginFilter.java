package com.ecommerce.identity.authorization.adapter.oauth2;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.web.filter.OncePerRequestFilter;

final class AuthorizationRequestLoginFilter extends OncePerRequestFilter {
    private static final AuthenticationTrustResolver TRUST_RESOLVER = new AuthenticationTrustResolverImpl();

    private final String authorizationEndpoint;
    private final RequestCache requestCache;
    private final AuthenticationEntryPoint loginEntryPoint;

    AuthorizationRequestLoginFilter(
            String authorizationEndpoint, RequestCache requestCache, AuthenticationEntryPoint loginEntryPoint) {
        this.authorizationEndpoint = authorizationEndpoint;
        this.requestCache = requestCache;
        this.loginEntryPoint = loginEntryPoint;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean unauthenticated = authentication == null
                || !authentication.isAuthenticated()
                || TRUST_RESOLVER.isAnonymous(authentication);
        if ("GET".equals(request.getMethod())
                && authorizationEndpoint.equals(request.getServletPath())
                && unauthenticated) {
            requestCache.saveRequest(request, response);
            loginEntryPoint.commence(request, response, new InsufficientAuthenticationException("Login required."));
            return;
        }
        filterChain.doFilter(request, response);
    }
}
