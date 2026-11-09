package com.ecommerce.identity.authorization.adapter.oauth2;

import java.io.IOException;
import java.util.Enumeration;
import java.util.Locale;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.LocaleResolver;

final class LoginLocaleFilter extends OncePerRequestFilter {
    static final String LOCALE_SESSION_ATTRIBUTE = "IDENTITY_LOGIN_LOCALE";
    private static final Pattern SUPPORTED_UI_LOCALE = Pattern.compile("(?i)^(en|vi)(?:-[a-z]{2})?$");

    private final LocaleResolver locales;
    private final String authorizationEndpoint;

    LoginLocaleFilter(LocaleResolver locales, String authorizationEndpoint) {
        this.locales = locales;
        this.authorizationEndpoint = authorizationEndpoint;
    }

    static Locale firstSupportedUiLocale(String value) {
        if (value == null || value.length() > 128) return null;

        for (String candidate : value.trim().split("\\s+", 9)) {
            if (!SUPPORTED_UI_LOCALE.matcher(candidate).matches()) continue;
            return Locale.forLanguageTag(Locale.forLanguageTag(candidate).getLanguage());
        }
        return null;
    }

    static Locale firstSupportedAcceptLanguage(HttpServletRequest request) {
        Enumeration<Locale> preferences = request.getLocales();
        while (preferences.hasMoreElements()) {
            String language = preferences.nextElement().getLanguage();
            if (language.equalsIgnoreCase("en")) return Locale.ENGLISH;
            if (language.equalsIgnoreCase("vi")) return Locale.forLanguageTag("vi");
        }
        return null;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return !path.equals("/login") && !("GET".equals(request.getMethod()) && path.equals(authorizationEndpoint));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Locale hint = "GET".equals(request.getMethod()) && authorizationEndpoint.equals(request.getServletPath())
                ? firstSupportedUiLocale(request.getParameter("ui_locales"))
                : null;
        if (hint != null) {
            locales.setLocale(request, response, hint);
        } else if (request.getSession(false) == null
                || request.getSession(false).getAttribute(LOCALE_SESSION_ATTRIBUTE) == null) {
            Locale preference = firstSupportedAcceptLanguage(request);
            if (preference != null) locales.setLocale(request, response, preference);
        }
        chain.doFilter(request, response);
    }
}
