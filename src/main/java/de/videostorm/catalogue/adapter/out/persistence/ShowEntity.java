package de.videostorm.catalogue.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Formula;

import java.math.BigDecimal;

/** Maps only the columns the listing read path uses; see V4__show_table.sql for the full schema. */
@Entity
@Table(name = "show")
@Getter
@NoArgsConstructor
class ShowEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    @Column(name = "normalized_title")
    private String normalizedTitle;

    @Column(name = "normalized_original_title")
    private String normalizedOriginalTitle;

    private Integer year;

    private String status;

    @Column(name = "rating_source")
    private String ratingSource;

    @Column(name = "rating_value")
    private BigDecimal ratingValue;

    private String genres;

    private String plot;

    @Column(name = "imdb_id")
    private String imdbId;

    // Read-side aggregates over the episode table (issue #27); episodes have no JPA entity of their own,
    // so they are derived here as correlated subqueries. A show with no episode rows yields 0/0, which the
    // listing renders verbatim. Hibernate qualifies the unaliased `id` with this entity's table alias.
    @Formula("(SELECT COUNT(DISTINCT e.season_number) FROM episode e WHERE e.show_id = id)")
    private int seasonCount;

    @Formula("(SELECT COUNT(*) FROM episode e WHERE e.show_id = id)")
    private int episodeCount;
}
