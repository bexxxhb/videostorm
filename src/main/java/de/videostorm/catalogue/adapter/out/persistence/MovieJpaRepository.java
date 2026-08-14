package de.videostorm.catalogue.adapter.out.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

interface MovieJpaRepository extends JpaRepository<MovieEntity, Long> {

    // Shared between findMatching and countMatching so the two queries can never drift apart.
    // likeTitleTerm/likeGenreTerm arrive pre-escaped and wildcard-wrapped; year is null unless the
    // search term was exactly four digits.
    String SEARCH_PREDICATE = """
            m.normalizedTitle LIKE :likeTitleTerm ESCAPE '\\'
                OR (m.normalizedOriginalTitle IS NOT NULL AND m.normalizedOriginalTitle LIKE :likeTitleTerm ESCAPE '\\')
                OR (m.genres IS NOT NULL AND LOWER(m.genres) LIKE :likeGenreTerm ESCAPE '\\')
                OR (:year IS NOT NULL AND m.year = :year)
            """;

    @Query("SELECT m FROM MovieEntity m WHERE " + SEARCH_PREDICATE)
    List<MovieEntity> findMatching(
            @Param("likeTitleTerm") String likeTitleTerm,
            @Param("likeGenreTerm") String likeGenreTerm,
            @Param("year") Integer year,
            Pageable pageable);

    @Query("SELECT count(m) FROM MovieEntity m WHERE " + SEARCH_PREDICATE)
    long countMatching(
            @Param("likeTitleTerm") String likeTitleTerm,
            @Param("likeGenreTerm") String likeGenreTerm,
            @Param("year") Integer year);

    // Reads just the TEXT column (native, so it is not mapped onto the entity and never hydrated with
    // a listing row). A missing row — or a NULL value — yields an empty Optional.
    @Query(value = "SELECT raw_nfo FROM movie WHERE id = :id", nativeQuery = true)
    Optional<String> findRawNfoById(@Param("id") long id);
}
