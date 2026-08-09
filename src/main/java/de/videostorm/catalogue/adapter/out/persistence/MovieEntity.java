package de.videostorm.catalogue.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

    private Integer year;

    @Column(name = "rating_source")
    private String ratingSource;

    @Column(name = "rating_value")
    private BigDecimal ratingValue;

    private String genres;

    @Column(name = "runtime_minutes")
    private Integer runtimeMinutes;
}
