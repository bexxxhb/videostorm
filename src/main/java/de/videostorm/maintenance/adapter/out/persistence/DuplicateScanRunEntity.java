package de.videostorm.maintenance.adapter.out.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** JPA mapping for one duplicate scan run; see V15__duplicate_scan.sql for the schema. */
@Entity
@Table(name = "duplicate_scan_run")
@Getter
@Setter
@NoArgsConstructor
class DuplicateScanRunEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "executed_at")
    private Instant executedAt;

    @Column(name = "duration_ms")
    private long durationMs;

    @Column(name = "group_count")
    private int groupCount;

    @OneToMany(mappedBy = "run", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<DuplicateScanGroupEntity> groups = new ArrayList<>();

    void addGroup(DuplicateScanGroupEntity group) {
        group.setRun(this);
        groups.add(group);
    }
}
