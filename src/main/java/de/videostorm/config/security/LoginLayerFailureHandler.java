package de.videostorm.config.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

import java.io.IOException;
import java.util.Set;

/**
 * Sends a failed sign-in back to the page the header's login layer was opened from — carried in
 * the form's own {@code returnTo} field rather than the {@code Referer} header, which browsers
 * are free to omit — instead of Spring Security's default target, the bare login page. That page
 * is still where a request lands, and {@code returnTo} defaults there, when the layer's
 * JavaScript never ran (a saved-request redirect, or {@code /login} submitted directly).
 *
 * <p>{@code returnTo} is restricted to the fixed set of pages that ever render the layer
 * unauthenticated, so a tampered value cannot turn this into an open redirect.
 */
public class LoginLayerFailureHandler implements AuthenticationFailureHandler {

    private static final Set<String> ALLOWED_RETURN_PATHS = Set.of("/", "/movies", "/shows", "/login");

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                         AuthenticationException exception) throws IOException {
        String returnTo = request.getParameter("returnTo");
        String target = ALLOWED_RETURN_PATHS.contains(returnTo) ? returnTo : "/login";
        response.sendRedirect(request.getContextPath() + target + "?loginFailed");
    }
}
