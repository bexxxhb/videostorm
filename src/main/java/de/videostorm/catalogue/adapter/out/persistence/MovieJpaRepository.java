package de.videostorm.catalogue.adapter.out.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

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
}
