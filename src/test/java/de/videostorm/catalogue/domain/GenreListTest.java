package de.videostorm.catalogue.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GenreListTest {

    @Test
    void parsesADelimiterPaddedStorageValue() {
        GenreList genres = GenreList.parse("|Action|Thriller|");

        assertThat(genres.values()).containsExactly("Action", "Thriller");
    }

    @Test
    void parsingANullOrBlankValueYieldsEmpty() {
        assertThat(GenreList.parse(null).isEmpty()).isTrue();
        assertThat(GenreList.parse("").isEmpty()).isTrue();
    }

    @Test
    void storesAsADelimiterPaddedValue() {
        GenreList genres = new GenreList(List.of("Action", "Thriller"));

        assertThat(genres.toStorage()).isEqualTo("|Action|Thriller|");
    }

    @Test
    void anEmptyGenreListHasNoStorageValue() {
        assertThat(GenreList.EMPTY.toStorage()).isNull();
    }

    @Test
    void displaysThreeOrFewerGenresInFull() {
        GenreList genres = new GenreList(List.of("Action", "Thriller"));

        assertThat(genres.displayLabel()).isEqualTo("Action, Thriller");
    }

    @Test
    void nineGenresShowTheFirstThreeInStoredOrderFollowedByACountMarker() {
        GenreList genres = new GenreList(List.of(
                "Action", "Thriller", "Comedy", "Drama", "Horror",
                "Romance", "Sci-Fi", "Fantasy", "Mystery"));

        assertThat(genres.displayLabel()).isEqualTo("Action, Thriller, Comedy +6");
    }

    @Test
    void fullTextListsEveryGenreForTheHoverTooltip() {
        GenreList genres = new GenreList(List.of(
                "Action", "Thriller", "Comedy", "Drama", "Horror",
                "Romance", "Sci-Fi", "Fantasy", "Mystery"));

        assertThat(genres.fullText())
                .isEqualTo("Action, Thriller, Comedy, Drama, Horror, Romance, Sci-Fi, Fantasy, Mystery");
    }
}
