package de.videostorm.catalogue.adapter.in.web;

import de.videostorm.catalogue.application.ShowPage;
import de.videostorm.catalogue.application.ShowSort;
import de.videostorm.catalogue.application.port.in.ListShowsQuery;
import de.videostorm.catalogue.domain.Show;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.stream.IntStream;

/**
 * Public, unauthenticated entry point to the catalogue's shows tab.
 *
 * <p>The listing is served entirely from the database — never from the filesystem.
 */
@Controller
@RequiredArgsConstructor
public class ShowListingController {

    private final ListShowsQuery listShowsQuery;

    @GetMapping("/shows")
    public String listShows(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "q", defaultValue = "") String query,
            @RequestParam(name = "sort", required = false) String sort,
            @RequestParam(name = "dir", required = false) String dir,
            @RequestHeader(name = "X-Requested-With", required = false) String requestedWith,
            Model model) {
        ShowSort showSort = ShowSort.fromParams(sort, dir);
        ShowPage showPage = listShowsQuery.list(page, query, showSort);

        List<Show> shows = showPage.shows();
        long firstNumber = showPage.firstItemNumber();
        List<ShowRow> rows = IntStream.range(0, shows.size())
                .mapToObj(index -> ShowRow.from(shows.get(index), index, firstNumber + index))
                .toList();

        model.addAttribute("shows", rows);
        model.addAttribute("pageNumber", showPage.pageNumber());
        model.addAttribute("totalPages", showPage.totalPages());
        model.addAttribute("totalElements", showPage.totalElements());
        model.addAttribute("query", showPage.query());
        model.addAttribute("sort", ShowSortView.from(showPage.sort(), showPage.query(), "/shows"));
        model.addAttribute("pagination", PaginationLinks.from(
                showPage.pageNumber(), showPage.totalPages(),
                showPage.hasPrevious(), showPage.hasNext(), showPage.query(),
                showPage.sort().field().param(), showPage.sort().direction().param(), "/shows"));
        model.addAttribute("activeTab", "shows");

        return "XMLHttpRequest".equals(requestedWith) ? "shows-results" : "shows";
    }
}
