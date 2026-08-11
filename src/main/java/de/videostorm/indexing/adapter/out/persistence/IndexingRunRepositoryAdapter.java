package de.videostorm.indexing.adapter.out.persistence;

import de.videostorm.indexing.application.port.out.IndexingRunRepository;
import de.videostorm.indexing.domain.IndexingRun;
import de.videostorm.indexing.domain.RunCounts;
import de.videostorm.indexing.domain.RunStatus;
import de.videostorm.sources.domain.SourceType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
class IndexingRunRepositoryAdapter implements IndexingRunRepository {

    private final IndexingRunJpaRepository jpaRepository;

    @Override
    public IndexingRun save(IndexingRun run) {
        return toDomain(jpaRepository.save(toEntity(run)));
    }

    @Override
    public Optional<IndexingRun> findActiveRun() {
        return jpaRepository.findByStatus(RunStatus.RUNNING.name())
                .map(IndexingRunRepositoryAdapter::toDomain);
    }

    @Override
    public List<IndexingRun> findRecent(int limit) {
        return jpaRepository.findAllByOrderByStartedAtDesc(PageRequest.of(0, limit))
                .stream()
                .map(IndexingRunRepositoryAdapter::toDomain)
                .toList();
    }

    @Override
    public List<IndexingRun> findAll() {
        return jpaRepository.findAllByOrderByStartedAtDesc()
                .stream()
                .map(IndexingRunRepositoryAdapter::toDomain)
                .toList();
    }

    private static IndexingRunEntity toEntity(IndexingRun run) {
        IndexingRunEntity entity = new IndexingRunEntity();
        entity.setId(run.id());
        entity.setType(run.type().name());
        entity.setStatus(run.status().name());
        entity.setStartedAt(run.startedAt());
        entity.setFinishedAt(run.finishedAt());
        entity.setEntriesFound(run.counts().found());
        entity.setEntriesIndexed(run.counts().indexed());
        return entity;
    }

    private static IndexingRun toDomain(IndexingRunEntity entity) {
        return new IndexingRun(
                entity.getId(),
                SourceType.valueOf(entity.getType()),
                RunStatus.valueOf(entity.getStatus()),
                entity.getStartedAt(),
                entity.getFinishedAt(),
                new RunCounts(entity.getEntriesFound(), entity.getEntriesIndexed()));
    }
}
