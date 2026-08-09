package de.videostorm.catalogue.application;

import de.videostorm.catalogue.application.port.in.ListShowsQuery;
import de.videostorm.catalogue.application.port.out.ShowRepository;
import de.videostorm.catalogue.domain.SearchTerm;
import de.videostorm.catalogue.domain.Show;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
class ListShowsService implements ListShowsQuery {

    static final int PAGE_SIZE = 50;

    private final ShowRepository showRepository;

    @Override
    public ShowPage list(int requestedPage, String query) {
        SearchTerm searchTerm = new SearchTerm(query);
        long totalElements = showRepository.count(searchTerm);
        int totalPages = totalElements == 0 ? 1 : (int) Math.ceil((double) totalElements / PAGE_SIZE);
        int pageNumber = clamp(requestedPage, totalPages);
        List<Show> shows = showRepository.findPage(searchTerm, pageNumber, PAGE_SIZE);
        return new ShowPage(shows, pageNumber, totalPages, totalElements, searchTerm.raw());
    }

    private static int clamp(int requestedPage, int totalPages) {
        if (requestedPage < 1) {
            return 1;
        }
        return Math.min(requestedPage, totalPages);
    }
}
