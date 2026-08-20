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

    @Column(name = "rating_votes")
    private Integer ratingVotes;

    private String genres;

    private String plot;

    @Column(name = "imdb_id")
    private String imdbId;

    // The listing only needs to know whether a raw .nfo exists (to render the "Raw data" link); the
    // potentially large text itself is fetched on demand, never hydrated with every listing row. A bare
    // NULL check is a null-bitmap read that never de-TOASTs the text, and matches exactly what the
    // on-demand content query returns present (a NULL raw_nfo collapses to an empty Optional there).
    @Formula("(raw_nfo IS NOT NULL)")
    private boolean hasRawNfo;

    // Whether any actor is stored for this show, so the listing renders the "Actors" link only when
    // there is a cast to open. The cast itself is fetched on demand, never hydrated with a listing row.
    // Hibernate qualifies the unaliased `id` with this entity's table alias.
    @Formula("(EXISTS (SELECT 1 FROM show_actor a WHERE a.show_id = id))")
    private boolean hasCast;

    // Read-side aggregates over the episode table (issue #27); episodes have no JPA entity of their own,
    // so they are derived here as correlated subqueries. A show with no episode rows yields 0/0, which the
    // listing renders verbatim. Hibernate qualifies the unaliased `id` with this entity's table alias.
    @Formula("(SELECT COUNT(DISTINCT e.season_number) FROM episode e WHERE e.show_id = id)")
    private int seasonCount;

    @Formula("(SELECT COUNT(*) FROM episode e WHERE e.show_id = id)")
    private int episodeCount;

    // Read-side sort keys (issue #35). Each maps a "no value" to NULL so the listing can push those
    // entries to the end of the order — in both directions — with NULLS LAST, which a raw column cannot
    // do for the non-null sentinels (a blank title, the year 0). Shows have no resolution column.
    @Formula("NULLIF(normalized_title, '')")
    private String titleSort;

    @Formula("NULLIF(year, 0)")
    private Integer yearSort;
}
