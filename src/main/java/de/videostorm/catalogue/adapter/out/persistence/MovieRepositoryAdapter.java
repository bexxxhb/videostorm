package de.videostorm.catalogue.adapter.out.persistence;

import de.videostorm.catalogue.application.MovieSort;
import de.videostorm.catalogue.application.MovieSortField;
import de.videostorm.catalogue.application.port.out.MovieRepository;
import de.videostorm.catalogue.domain.CastMember;
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

    private final MovieJpaRepository jpaRepository;

    @Override
    public long count(SearchTerm searchTerm) {
        return jpaRepository.countMatching(likeTitleTerm(searchTerm), likeGenreTerm(searchTerm), yearFilter(searchTerm));
    }

    @Override
    public List<Movie> findPage(SearchTerm searchTerm, MovieSort sort, int pageNumber, int pageSize) {
        PageRequest pageRequest = PageRequest.of(pageNumber - 1, pageSize, sortOf(sort));
        return jpaRepository
                .findMatching(likeTitleTerm(searchTerm), likeGenreTerm(searchTerm), yearFilter(searchTerm), pageRequest)
                .stream()
                .map(MovieRepositoryAdapter::toDomain)
                .toList();
    }

    @Override
    public Optional<String> findRawNfo(long id) {
        return jpaRepository.findRawNfoById(id);
    }

    @Override
    public Optional<List<CastMember>> findCast(long id) {
        // Distinguish an unknown movie (empty Optional -> 404) from a movie with no cast (present, empty).
        if (!jpaRepository.existsById(id)) {
            return Optional.empty();
        }
        return Optional.of(jpaRepository.findCastByMovieId(id).stream()
                .map(CastRow::toCastMember)
                .toList());
    }

    private static Sort sortOf(MovieSort sort) {
        return ListingSort.by(property(sort.field()), sort.direction());
    }

    private static String property(MovieSortField field) {
        return switch (field) {
            case TITLE -> "titleSort";
            case YEAR -> "yearSort";
            case RATING -> "ratingValue";
            case RESOLUTION -> "resolutionHeight";
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

    private static Movie toDomain(MovieEntity entity) {
        Optional<Rating> rating = entity.getRatingSource() != null && entity.getRatingValue() != null
                ? Optional.of(new Rating(entity.getRatingSource(), entity.getRatingValue(), entity.getRatingVotes()))
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
                Optional.ofNullable(entity.getPlot()),
                entity.isHasRawNfo(),
                entity.isHasCast());
    }
}
