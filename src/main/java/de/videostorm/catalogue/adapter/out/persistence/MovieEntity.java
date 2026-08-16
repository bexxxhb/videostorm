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

/** Maps only the columns the listing read path uses; see V2__movie_table.sql for the full schema. */
@Entity
@Table(name = "movie")
@Getter
@NoArgsConstructor
class MovieEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    @Column(name = "normalized_title")
    private String normalizedTitle;

    @Column(name = "normalized_original_title")
    private String normalizedOriginalTitle;

    private Integer year;

    @Column(name = "rating_source")
    private String ratingSource;

    @Column(name = "rating_value")
    private BigDecimal ratingValue;

    private String genres;

    @Column(name = "runtime_minutes")
    private Integer runtimeMinutes;

    private String resolution;

    @Column(name = "imdb_id")
    private String imdbId;

    private String plot;

    // The listing only needs to know whether a raw .nfo exists (to render the "Raw data" link); the
    // potentially large text itself is fetched on demand, never hydrated with every listing row. A bare
    // NULL check is a null-bitmap read that never de-TOASTs the text, and matches exactly what the
    // on-demand content query returns present (a NULL raw_nfo collapses to an empty Optional there).
    @Formula("(raw_nfo IS NOT NULL)")
    private boolean hasRawNfo;

    // Whether any actor is stored for this movie, so the listing renders the "Actors" link only when
    // there is a cast to open. The cast itself is fetched on demand, never hydrated with a listing row.
    // Hibernate qualifies the unaliased `id` with this entity's table alias.
    @Formula("(EXISTS (SELECT 1 FROM movie_actor a WHERE a.movie_id = id))")
    private boolean hasCast;

    // Read-side sort keys (issue #35). Each maps a "no value" to NULL so the listing can push those
    // entries to the end of the order — in both directions — with NULLS LAST, which a raw column cannot
    // do for the non-null sentinels (a blank title, the year 0). Hibernate qualifies the unaliased
    // column names with this entity's table alias.
    @Formula("NULLIF(normalized_title, '')")
    private String titleSort;

    @Formula("NULLIF(year, 0)")
    private Integer yearSort;

    // The numeric pixel height behind the p-suffixed resolution string (e.g. 1080p -> 1080), so the
    // column orders numerically (2160 > 1080 > 720) rather than lexicographically. NULL when the film
    // carries no resolution or no digits, so it sorts last.
    @Formula("CAST(NULLIF(regexp_replace(COALESCE(resolution, ''), '[^0-9]', '', 'g'), '') AS INTEGER)")
    private Integer resolutionHeight;
}
