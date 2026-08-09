package de.videostorm.maintenance.adapter.in.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.ui.Model;

/**
 * Pug4j has no Thymeleaf-style CSRF integration, so the token Spring Security stashes on the
 * request has to be pushed into the model by hand for every form-bearing page.
 */
final class CsrfViewAttributes {

    private CsrfViewAttributes() {
    }

    static void exposeTo(HttpServletRequest request, Model model) {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        model.addAttribute("csrfParameterName", csrfToken.getParameterName());
        model.addAttribute("csrfToken", csrfToken.getToken());
    }
}
