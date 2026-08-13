package de.videostorm.catalogue.adapter.out.persistence;

import de.videostorm.catalogue.application.ShowSort;
import de.videostorm.catalogue.application.ShowSortField;
import de.videostorm.catalogue.application.port.out.ShowRepository;
import de.videostorm.catalogue.domain.GenreList;
import de.videostorm.catalogue.domain.Rating;
import de.videostorm.catalogue.domain.SearchTerm;
import de.videostorm.catalogue.domain.Show;
import de.videostorm.catalogue.domain.ShowStatus;
import de.videostorm.catalogue.domain.Year;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
class ShowRepositoryAdapter implements ShowRepository {

    private final ShowJpaRepository jpaRepository;

    @Override
    public long count(SearchTerm searchTerm) {
        return jpaRepository.countMatching(likeTitleTerm(searchTerm), likeGenreTerm(searchTerm), yearFilter(searchTerm));
    }

    @Override
    public List<Show> findPage(SearchTerm searchTerm, ShowSort sort, int pageNumber, int pageSize) {
        PageRequest pageRequest = PageRequest.of(pageNumber - 1, pageSize, sortOf(sort));
        return jpaRepository
                .findMatching(likeTitleTerm(searchTerm), likeGenreTerm(searchTerm), yearFilter(searchTerm), pageRequest)
                .stream()
                .map(ShowRepositoryAdapter::toDomain)
                .toList();
    }

    private static Sort sortOf(ShowSort sort) {
        return ListingSort.by(property(sort.field()), sort.direction());
    }

    private static String property(ShowSortField field) {
        return switch (field) {
            case TITLE -> "titleSort";
            case YEAR -> "yearSort";
            case RATING -> "ratingValue";
        };
    }

    private static String likeTitleTerm(SearchTerm searchTerm) {
        return "%" + escapeLike(searchTerm.normalizedTitle()) + "%";
    }

    private static String likeGenreTerm(SearchTerm searchTerm) {
        return "%" + escapeLike(searchTerm.genreFragment().toLowerCase(Locale.ROOT)) + "%";
    }

    private static Integer yearFilter(SearchTerm searchTerm) {
        return searchTerm.yearExactMatch().orElse(null);
    }

    // Neutralises LIKE's own wildcards so a search term is matched as a literal string.
    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static Show toDomain(ShowEntity entity) {
        Optional<Rating> rating = entity.getRatingSource() != null && entity.getRatingValue() != null
                ? Optional.of(new Rating(entity.getRatingSource(), entity.getRatingValue()))
                : Optional.empty();

        return new Show(
                entity.getId(),
                entity.getTitle(),
                Year.of(entity.getYear()),
                ShowStatus.valueOf(entity.getStatus()),
                rating,
                GenreList.parse(entity.getGenres()),
                Optional.ofNullable(entity.getPlot()),
                entity.getSeasonCount(),
                entity.getEpisodeCount(),
                Optional.ofNullable(entity.getImdbId()));
    }
}
