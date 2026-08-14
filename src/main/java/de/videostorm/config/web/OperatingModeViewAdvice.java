package de.videostorm.config.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Exposes the operating mode to every rendered page as a single {@code maintenanceEnabled}
 * flag, so the shared layout can hide the Maintenance nav link (and any login affordance) in
 * the default view-only {@code presentation} mode. The backend routes are already inert in that
 * mode; this only keeps the chrome honest about what is reachable.
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
}
