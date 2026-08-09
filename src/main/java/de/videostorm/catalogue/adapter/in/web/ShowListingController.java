package de.videostorm.catalogue.adapter.in.web;

import de.videostorm.catalogue.application.ShowPage;
import de.videostorm.catalogue.application.port.in.ListShowsQuery;
import de.videostorm.catalogue.domain.Show;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
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
            Model model) {
        ShowPage showPage = listShowsQuery.list(page, query);

        List<Show> shows = showPage.shows();
        List<ShowRow> rows = IntStream.range(0, shows.size())
                .mapToObj(index -> ShowRow.from(shows.get(index), index))
                .toList();

        model.addAttribute("shows", rows);
        model.addAttribute("pageNumber", showPage.pageNumber());
        model.addAttribute("totalPages", showPage.totalPages());
        model.addAttribute("totalElements", showPage.totalElements());
        model.addAttribute("query", showPage.query());
        model.addAttribute("pagination", PaginationLinks.from(
                showPage.pageNumber(), showPage.totalPages(),
                showPage.hasPrevious(), showPage.hasNext(), showPage.query(), "/shows"));
        model.addAttribute("activeTab", "shows");

        return "shows";
    }
}
