package de.videostorm.maintenance.adapter.in.web;

import de.videostorm.indexing.application.port.in.IndexingOverview;
import de.videostorm.indexing.application.port.in.IndexingStatus;
import de.videostorm.indexing.application.port.in.TriggerReindex;
import de.videostorm.indexing.application.port.in.TriggerResult;
import de.videostorm.sources.domain.SourcePaths;
import de.videostorm.sources.domain.SourceType;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Optional;

/**
 * The gated maintenance shell. A type's re-index trigger is offered only when that type has source
 * paths configured and no run is currently active; with none configured the trigger is disabled
 * and explained rather than dangling as a button that cannot work. While a run is active the page
 * shows its status and refreshes itself so the operator can watch it finish without clicking. The
 * configured path values are never placed in the model on the normal render; the sole exception is
 * a pre-flight abort, whose failing paths are flashed onto the redirect and rendered once (see
 * below).
 *
 * <p>Triggering is refused server-side too — not only by a disabled button — when a type is
 * unconfigured or a run is already in progress, so a hand-crafted POST cannot slip past the UI.
 *
 * <p>When a pre-flight check aborts the trigger, the failing paths are carried back to the
 * operator as flash attributes on a redirect, so the error survives exactly one render and a
 * refresh does not re-trigger the run. This is the sole exception to paths never reaching the UI:
 * the triggering administrator is told which mounts to fix, and only they, since the page is gated.
 */
@Controller
public class MaintenanceController {

    private static final int META_REFRESH_SECONDS = 3;

    private final SourcePaths sourcePaths;
    private final TriggerReindex triggerReindex;
    private final IndexingStatus indexingStatus;

    public MaintenanceController(SourcePaths sourcePaths, TriggerReindex triggerReindex,
                                 IndexingStatus indexingStatus) {
        this.sourcePaths = sourcePaths;
        this.triggerReindex = triggerReindex;
        this.indexingStatus = indexingStatus;
    }

    @GetMapping("/maintenance")
    public String maintenance(HttpServletRequest request, Model model) {
        CsrfViewAttributes.exposeTo(request, model);

        // Active run and history come from one snapshot, so the page can never show a run as active
        // and settled at once — as two separate reads could when a scan settles between them.
        IndexingOverview overview = indexingStatus.overview();
        Optional<IndexingRunView> activeRun = overview.activeRun().map(IndexingRunView::of);
        boolean runActive = activeRun.isPresent();
        List<IndexingRunView> recentRuns = overview.recentRuns().stream().map(IndexingRunView::of).toList();

        model.addAttribute("moviesConfigured", sourcePaths.hasPathsFor(SourceType.MOVIES));
        model.addAttribute("showsConfigured", sourcePaths.hasPathsFor(SourceType.SHOWS));
        model.addAttribute("runActive", runActive);
        model.addAttribute("activeRun", activeRun.orElse(null));
        model.addAttribute("recentRuns", recentRuns);
        model.addAttribute("hasRuns", !recentRuns.isEmpty());
        if (runActive) {
            model.addAttribute("metaRefreshSeconds", META_REFRESH_SECONDS);
        }

        return "maintenance";
    }

    @PostMapping("/maintenance/movies/reindex")
    public String reindexMovies(RedirectAttributes redirectAttributes) {
        return trigger(SourceType.MOVIES, redirectAttributes);
    }

    @PostMapping("/maintenance/shows/reindex")
    public String reindexShows(RedirectAttributes redirectAttributes) {
        return trigger(SourceType.SHOWS, redirectAttributes);
    }

    private String trigger(SourceType type, RedirectAttributes redirectAttributes) {
        if (!sourcePaths.hasPathsFor(type)) {
            return "redirect:/maintenance";
        }
        TriggerResult result = triggerReindex.trigger(type);
        if (result.outcome() == TriggerResult.Outcome.PATHS_UNREACHABLE) {
            redirectAttributes.addFlashAttribute("unreachableType", type.label());
            redirectAttributes.addFlashAttribute("unreachablePaths", result.unreachablePaths());
        }
        return "redirect:/maintenance";
    }
}
