package de.videostorm.indexing.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** JPA mapping for a single indexing run; see V5__indexing_run_table.sql for the schema. */
@Entity
@Table(name = "indexing_run")
@Getter
@Setter
@NoArgsConstructor
class IndexingRunEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String type;

    private String status;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "entries_found")
    private int entriesFound;

    @Column(name = "entries_indexed")
    private int entriesIndexed;

    @Column(name = "entries_skipped")
    private int entriesSkipped;

    @Column(name = "entries_missing_data")
    private int entriesMissingData;
}
