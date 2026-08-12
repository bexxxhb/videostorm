package de.videostorm.catalogue.adapter.out.persistence;

import de.videostorm.catalogue.application.port.out.MovieRepository;
import de.videostorm.catalogue.domain.GenreList;
import de.videostorm.catalogue.domain.Movie;
import de.videostorm.catalogue.domain.Rating;
import de.videostorm.catalogue.domain.SearchTerm;
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
class MovieRepositoryAdapter implements MovieRepository {

    // Matches idx_movie_listing_sort: normalized title ascending, with (year, id) as a
    // deterministic tiebreak so paging can never duplicate or skip a row.
    private static final Sort FIXED_SORT = Sort.by(
            Sort.Order.asc("normalizedTitle"),
            Sort.Order.asc("year"),
            Sort.Order.asc("id"));

    private final MovieJpaRepository jpaRepository;

    @Override
    public long count(SearchTerm searchTerm) {
        return jpaRepository.countMatching(likeTitleTerm(searchTerm), likeGenreTerm(searchTerm), yearFilter(searchTerm));
    }

    @Override
    public List<Movie> findPage(SearchTerm searchTerm, int pageNumber, int pageSize) {
        PageRequest pageRequest = PageRequest.of(pageNumber - 1, pageSize, FIXED_SORT);
        return jpaRepository
                .findMatching(likeTitleTerm(searchTerm), likeGenreTerm(searchTerm), yearFilter(searchTerm), pageRequest)
                .stream()
                .map(MovieRepositoryAdapter::toDomain)
                .toList();
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

    private static Movie toDomain(MovieEntity entity) {
        Optional<Rating> rating = entity.getRatingSource() != null && entity.getRatingValue() != null
                ? Optional.of(new Rating(entity.getRatingSource(), entity.getRatingValue()))
                : Optional.empty();

        return new Movie(
                entity.getId(),
                entity.getTitle(),
                Year.of(entity.getYear()),
                rating,
                GenreList.parse(entity.getGenres()),
                Optional.ofNullable(entity.getRuntimeMinutes()),
                Optional.ofNullable(entity.getResolution()),
                Optional.ofNullable(entity.getImdbId()),
                Optional.ofNullable(entity.getPlot()));
    }
}
