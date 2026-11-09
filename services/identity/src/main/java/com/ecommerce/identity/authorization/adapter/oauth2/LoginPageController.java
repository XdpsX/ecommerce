package com.ecommerce.identity.authorization.adapter.oauth2;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
final class LoginPageController {
    @GetMapping("/login")
    String login(
            @RequestParam(name = "error", required = false) String error,
            @Value("${identity.backoffice-url}") String backofficeUrl,
            HttpServletResponse response,
            Model model) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
        if (error != null) model.addAttribute("loginError", true);
        model.addAttribute("backofficeUrl", backofficeUrl);
        return "login";
    }
}
