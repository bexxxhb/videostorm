package de.videostorm.catalogue.application;

import de.videostorm.catalogue.application.port.out.MovieRepository;
import de.videostorm.catalogue.domain.SearchTerm;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListMoviesServiceTest {

    @Mock
    private MovieRepository movieRepository;

    @Test
    void returnsTheRequestedPageWithTotals() {
        when(movieRepository.count(any())).thenReturn(120L);
        when(movieRepository.findPage(any(), any(), eq(2), eq(ListMoviesService.PAGE_SIZE))).thenReturn(List.of());

        ListMoviesService service = new ListMoviesService(movieRepository);
        MoviePage page = service.list(2, "", MovieSort.DEFAULT);

        assertThat(page.pageNumber()).isEqualTo(2);
        assertThat(page.totalPages()).isEqualTo(3);
        assertThat(page.totalElements()).isEqualTo(120L);
        verify(movieRepository).findPage(new SearchTerm(""), MovieSort.DEFAULT, 2, ListMoviesService.PAGE_SIZE);
    }

    @Test
    void clampsAPageNumberBelowOneUpToOne() {
        when(movieRepository.count(any())).thenReturn(10L);
        when(movieRepository.findPage(any(), any(), anyInt(), anyInt())).thenReturn(List.of());

        MoviePage page = new ListMoviesService(movieRepository).list(0, "", MovieSort.DEFAULT);

        assertThat(page.pageNumber()).isEqualTo(1);
    }

    @Test
    void clampsAPageNumberPastTheEndDownToTheLastPage() {
        when(movieRepository.count(any())).thenReturn(10L);
        when(movieRepository.findPage(any(), any(), anyInt(), anyInt())).thenReturn(List.of());

        MoviePage page = new ListMoviesService(movieRepository).list(99, "", MovieSort.DEFAULT);

        assertThat(page.pageNumber()).isEqualTo(1);
        assertThat(page.totalPages()).isEqualTo(1);
    }

    @Test
    void anEmptyCatalogueHasOnePageOfZero() {
        when(movieRepository.count(any())).thenReturn(0L);
        when(movieRepository.findPage(any(), any(), anyInt(), anyInt())).thenReturn(List.of());

        MoviePage page = new ListMoviesService(movieRepository).list(1, "", MovieSort.DEFAULT);

        assertThat(page.totalPages()).isEqualTo(1);
        assertThat(page.hasPrevious()).isFalse();
        assertThat(page.hasNext()).isFalse();
    }

    @Test
    void passesTheTrimmedSearchTermThroughToTheRepository() {
        when(movieRepository.count(any())).thenReturn(0L);
        when(movieRepository.findPage(any(), any(), anyInt(), anyInt())).thenReturn(List.of());

        MoviePage page = new ListMoviesService(movieRepository).list(1, "  batman  ", MovieSort.DEFAULT);

        assertThat(page.query()).isEqualTo("batman");
        verify(movieRepository).count(new SearchTerm("batman"));
        verify(movieRepository).findPage(new SearchTerm("batman"), MovieSort.DEFAULT, 1, ListMoviesService.PAGE_SIZE);
    }

    @Test
    void passesTheActiveSortThroughToTheRepositoryAndEchoesItOnThePage() {
        when(movieRepository.count(any())).thenReturn(0L);
        when(movieRepository.findPage(any(), any(), anyInt(), anyInt())).thenReturn(List.of());
        MovieSort sort = new MovieSort(MovieSortField.RATING, SortDirection.DESC);

        MoviePage page = new ListMoviesService(movieRepository).list(1, "", sort);

        assertThat(page.sort()).isEqualTo(sort);
        verify(movieRepository).findPage(new SearchTerm(""), sort, 1, ListMoviesService.PAGE_SIZE);
    }
}
