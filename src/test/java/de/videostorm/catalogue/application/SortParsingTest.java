package de.videostorm.catalogue.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The sort whitelist: a request parameter is only ever mapped to a fixed enum value, so a client
 * string never reaches {@code Sort}/SQL. Anything unknown, absent or of the wrong shape falls back to
 * the default (Title ascending) rather than failing.
 */
class SortParsingTest {

    @Test
    void mapsKnownMovieParametersToTheirFieldAndDirection() {
        MovieSort sort = MovieSort.fromParams("resolution", "desc");

        assertThat(sort.field()).isEqualTo(MovieSortField.RESOLUTION);
        assertThat(sort.direction()).isEqualTo(SortDirection.DESC);
    }

    @Test
    void unknownOrAbsentMovieParametersFallBackToTitleAscending() {
        assertThat(MovieSort.fromParams("bogus", "sideways")).isEqualTo(MovieSort.DEFAULT);
        assertThat(MovieSort.fromParams(null, null)).isEqualTo(MovieSort.DEFAULT);
        assertThat(MovieSort.DEFAULT.field()).isEqualTo(MovieSortField.TITLE);
        assertThat(MovieSort.DEFAULT.direction()).isEqualTo(SortDirection.ASC);
    }

    @Test
    void resolutionIsNotAValidShowColumnAndFallsBackToTitle() {
        // Shows have no resolution column, so even the literal "resolution" must not be honoured.
        assertThat(ShowSort.fromParams("resolution", "asc").field()).isEqualTo(ShowSortField.TITLE);
        assertThat(ShowSort.fromParams("rating", "desc"))
                .isEqualTo(new ShowSort(ShowSortField.RATING, SortDirection.DESC));
    }

    @Test
    void directionParsingIsCaseInsensitiveAndOppositeToggles() {
        assertThat(SortDirection.fromParam("DESC")).isEqualTo(SortDirection.DESC);
        assertThat(SortDirection.fromParam("asc")).isEqualTo(SortDirection.ASC);
        assertThat(SortDirection.ASC.opposite()).isEqualTo(SortDirection.DESC);
        assertThat(SortDirection.DESC.opposite()).isEqualTo(SortDirection.ASC);
    }
}
