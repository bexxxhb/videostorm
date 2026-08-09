package de.videostorm.catalogue.application;

import de.videostorm.catalogue.application.port.out.ShowRepository;
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
class ListShowsServiceTest {

    @Mock
    private ShowRepository showRepository;

    @Test
    void returnsTheRequestedPageWithTotals() {
        when(showRepository.count(any())).thenReturn(120L);
        when(showRepository.findPage(any(), eq(2), eq(ListShowsService.PAGE_SIZE))).thenReturn(List.of());

        ListShowsService service = new ListShowsService(showRepository);
        ShowPage page = service.list(2, "");

        assertThat(page.pageNumber()).isEqualTo(2);
        assertThat(page.totalPages()).isEqualTo(3);
        assertThat(page.totalElements()).isEqualTo(120L);
        verify(showRepository).findPage(new SearchTerm(""), 2, ListShowsService.PAGE_SIZE);
    }

    @Test
    void clampsAPageNumberBelowOneUpToOne() {
        when(showRepository.count(any())).thenReturn(10L);
        when(showRepository.findPage(any(), anyInt(), anyInt())).thenReturn(List.of());

        ShowPage page = new ListShowsService(showRepository).list(0, "");

        assertThat(page.pageNumber()).isEqualTo(1);
    }

    @Test
    void clampsAPageNumberPastTheEndDownToTheLastPage() {
        when(showRepository.count(any())).thenReturn(10L);
        when(showRepository.findPage(any(), anyInt(), anyInt())).thenReturn(List.of());

        ShowPage page = new ListShowsService(showRepository).list(99, "");

        assertThat(page.pageNumber()).isEqualTo(1);
        assertThat(page.totalPages()).isEqualTo(1);
    }

    @Test
    void anEmptyCatalogueHasOnePageOfZero() {
        when(showRepository.count(any())).thenReturn(0L);
        when(showRepository.findPage(any(), anyInt(), anyInt())).thenReturn(List.of());

        ShowPage page = new ListShowsService(showRepository).list(1, "");

        assertThat(page.totalPages()).isEqualTo(1);
        assertThat(page.hasPrevious()).isFalse();
        assertThat(page.hasNext()).isFalse();
    }

    @Test
    void passesTheTrimmedSearchTermThroughToTheRepository() {
        when(showRepository.count(any())).thenReturn(0L);
        when(showRepository.findPage(any(), anyInt(), anyInt())).thenReturn(List.of());

        ShowPage page = new ListShowsService(showRepository).list(1, "  batman  ");

        assertThat(page.query()).isEqualTo("batman");
        verify(showRepository).count(new SearchTerm("batman"));
        verify(showRepository).findPage(new SearchTerm("batman"), 1, ListShowsService.PAGE_SIZE);
    }
}
