package de.videostorm.maintenance.adapter.in.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Reachable directly at {@code /login} but linked from nowhere in the public UI — Spring
 * Security's saved-request flow is what actually sends callers here.
 */
@Controller
public class LoginController {

    @GetMapping("/login")
    public String login(
            @RequestParam(name = "error", required = false) String error,
            HttpServletRequest request, Model model) {
        CsrfViewAttributes.exposeTo(request, model);
        model.addAttribute("loginFailed", error != null);

        return "login";
    }
}
