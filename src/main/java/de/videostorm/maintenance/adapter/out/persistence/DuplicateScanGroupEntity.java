package de.videostorm.maintenance.adapter.out.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/** JPA mapping for one detected duplicate group within a scan run; see V15__duplicate_scan.sql. */
@Entity
@Table(name = "duplicate_scan_group")
@Getter
@Setter
@NoArgsConstructor
class DuplicateScanGroupEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "run_id")
    private DuplicateScanRunEntity run;

    private String criterion;

    @Column(name = "shared_value")
    private String sharedValue;

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<DuplicateScanMemberEntity> members = new ArrayList<>();

    void addMember(DuplicateScanMemberEntity member) {
        member.setGroup(this);
        members.add(member);
    }
}
