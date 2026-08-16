package de.videostorm.maintenance.adapter.out.persistence;

import de.videostorm.maintenance.application.port.out.DuplicateScanRunStore;
import de.videostorm.maintenance.domain.DuplicateCriterion;
import de.videostorm.maintenance.domain.DuplicateGroup;
import de.videostorm.maintenance.domain.DuplicateMember;
import de.videostorm.maintenance.domain.DuplicateScanRun;
import de.videostorm.maintenance.domain.DuplicateScanRunSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Stores duplicate scan runs as a run row with its groups and members hanging off it (cascaded on
 * save), and reads them back. The history maps only the run's own columns, so it never touches the
 * lazy group collections; {@link #findById} runs in a read transaction and maps the whole graph while
 * it is open.
 */
@Repository
@RequiredArgsConstructor
class DuplicateScanRunStoreAdapter implements DuplicateScanRunStore {

    private final DuplicateScanRunJpaRepository jpaRepository;

    @Override
    @Transactional
    public DuplicateScanRun save(DuplicateScanRun run) {
        return toDomain(jpaRepository.save(toEntity(run)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<DuplicateScanRunSummary> history() {
        return jpaRepository.findAllByOrderByExecutedAtDesc().stream()
                .map(DuplicateScanRunStoreAdapter::toSummary)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DuplicateScanRun> findById(long runId) {
        return jpaRepository.findById(runId).map(DuplicateScanRunStoreAdapter::toDomain);
    }

    private static DuplicateScanRunEntity toEntity(DuplicateScanRun run) {
        DuplicateScanRunEntity entity = new DuplicateScanRunEntity();
        entity.setExecutedAt(run.executedAt());
        entity.setDurationMs(run.duration().toMillis());
        entity.setGroupCount(run.groupCount());
        for (DuplicateGroup group : run.groups()) {
            entity.addGroup(toEntity(group));
        }
        return entity;
    }

    private static DuplicateScanGroupEntity toEntity(DuplicateGroup group) {
        DuplicateScanGroupEntity entity = new DuplicateScanGroupEntity();
        entity.setCriterion(group.criterion().name());
        entity.setSharedValue(group.sharedValue());
        for (DuplicateMember member : group.members()) {
            entity.addMember(toEntity(member));
        }
        return entity;
    }

    private static DuplicateScanMemberEntity toEntity(DuplicateMember member) {
        DuplicateScanMemberEntity entity = new DuplicateScanMemberEntity();
        entity.setImdbId(member.imdbId().orElse(null));
        entity.setOriginalTitle(member.originalTitle().orElse(null));
        entity.setFilePath(member.filePath().orElse(null));
        entity.setSizeBytes(member.sizeBytes().orElse(null));
        return entity;
    }

    private static DuplicateScanRunSummary toSummary(DuplicateScanRunEntity entity) {
        return new DuplicateScanRunSummary(
                entity.getId(), entity.getExecutedAt(),
                Duration.ofMillis(entity.getDurationMs()), entity.getGroupCount());
    }

    private static DuplicateScanRun toDomain(DuplicateScanRunEntity entity) {
        List<DuplicateGroup> groups = entity.getGroups().stream()
                .map(DuplicateScanRunStoreAdapter::toDomain)
                .toList();
        return new DuplicateScanRun(
                entity.getId(), entity.getExecutedAt(), Duration.ofMillis(entity.getDurationMs()), groups);
    }

    private static DuplicateGroup toDomain(DuplicateScanGroupEntity entity) {
        List<DuplicateMember> members = entity.getMembers().stream()
                .map(DuplicateScanRunStoreAdapter::toDomain)
                .toList();
        return new DuplicateGroup(DuplicateCriterion.valueOf(entity.getCriterion()), entity.getSharedValue(), members);
    }

    private static DuplicateMember toDomain(DuplicateScanMemberEntity entity) {
        return new DuplicateMember(
                Optional.ofNullable(entity.getImdbId()),
                Optional.ofNullable(entity.getOriginalTitle()),
                Optional.ofNullable(entity.getFilePath()),
                Optional.ofNullable(entity.getSizeBytes()));
    }
}
