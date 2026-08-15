package de.videostorm.maintenance.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** JPA mapping for one movie inside a duplicate group; see V15__duplicate_scan.sql. */
@Entity
@Table(name = "duplicate_scan_member")
@Getter
@Setter
@NoArgsConstructor
class DuplicateScanMemberEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "group_id")
    private DuplicateScanGroupEntity group;

    @Column(name = "imdb_id")
    private String imdbId;

    @Column(name = "original_title")
    private String originalTitle;

    @Column(name = "file_path")
    private String filePath;
}
