package de.videostorm.config.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Exposes the operating mode to every rendered page as a single {@code maintenanceEnabled}
 * flag, so the shared layout can hide the Maintenance nav link (and any login affordance) in
 * the default view-only {@code presentation} mode. The backend routes are already inert in that
 * mode; this only keeps the chrome honest about what is reachable.
 *
 * <p>In maintenance mode it also feeds the header's sign-in layer: whether an admin session is
 * already active, so the layout can render a plain link straight through instead of the layer;
 * the CSRF pair every rendered page's forms need, since the layer's form lives on every public
 * page, not just {@code /login}; the current path, so the layer's form can carry it back as
 * {@code returnTo} and a failed attempt lands back on the same page instead of the bare login
 * page; and whether that page is itself the bounce-back from a failed attempt, so the layer can
 * open pre-populated with the error instead of leaving it to render inertly underneath.
 */
@ControllerAdvice
public class OperatingModeViewAdvice {

    private final boolean maintenanceEnabled;

    public OperatingModeViewAdvice(
            @Value("${application.operating.mode:presentation}") String operatingMode) {
        this.maintenanceEnabled = "maintenance".equals(operatingMode);
    }

    @ModelAttribute("maintenanceEnabled")
    public boolean maintenanceEnabled() {
        return maintenanceEnabled;
    }

    @ModelAttribute
    public void loginLayerAttributes(HttpServletRequest request, Model model) {
        if (!maintenanceEnabled) {
            return;
        }
        model.addAttribute("administratorAuthenticated", request.isUserInRole("ADMIN"));
        model.addAttribute("loginReturnPath", request.getServletPath());
        model.addAttribute("loginFailed", request.getParameter("loginFailed") != null);
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        model.addAttribute("csrfParameterName", csrfToken.getParameterName());
        model.addAttribute("csrfToken", csrfToken.getToken());
    }
}
