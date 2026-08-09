package de.videostorm.catalogue.adapter.out.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

interface ShowJpaRepository extends JpaRepository<ShowEntity, Long> {

    // Shared between findMatching and countMatching so the two queries can never drift apart.
    // likeTitleTerm/likeGenreTerm arrive pre-escaped and wildcard-wrapped; year is null unless the
    // search term was exactly four digits.
    String SEARCH_PREDICATE = """
            s.normalizedTitle LIKE :likeTitleTerm ESCAPE '\\'
                OR (s.normalizedOriginalTitle IS NOT NULL AND s.normalizedOriginalTitle LIKE :likeTitleTerm ESCAPE '\\')
                OR (s.genres IS NOT NULL AND LOWER(s.genres) LIKE :likeGenreTerm ESCAPE '\\')
                OR (:year IS NOT NULL AND s.year = :year)
            """;

    @Query("SELECT s FROM ShowEntity s WHERE " + SEARCH_PREDICATE)
    List<ShowEntity> findMatching(
            @Param("likeTitleTerm") String likeTitleTerm,
            @Param("likeGenreTerm") String likeGenreTerm,
            @Param("year") Integer year,
            Pageable pageable);

    @Query("SELECT count(s) FROM ShowEntity s WHERE " + SEARCH_PREDICATE)
    long countMatching(
            @Param("likeTitleTerm") String likeTitleTerm,
            @Param("likeGenreTerm") String likeGenreTerm,
            @Param("year") Integer year);
}
