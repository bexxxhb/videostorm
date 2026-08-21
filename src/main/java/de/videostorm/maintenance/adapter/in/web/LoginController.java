package de.videostorm.maintenance.adapter.in.web;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Reachable directly at {@code /login} as a fallback for callers without JavaScript — Spring
 * Security's saved-request flow is what sends them here. With JavaScript, the header's sign-in
 * layer covers this without leaving the page. {@code loginFailed} and the CSRF pair its form
 * needs come from {@link de.videostorm.config.web.OperatingModeViewAdvice}, which feeds every
 * page that can render the layer, not just this one.
 */
@Controller
@ConditionalOnProperty(name = "application.operating.mode", havingValue = "maintenance")
public class LoginController {

    @GetMapping("/login")
    public String login() {
        return "login";
    }
}
