package de.videostorm.indexing.adapter.out.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface IndexingRunJpaRepository extends JpaRepository<IndexingRunEntity, Long> {

    Optional<IndexingRunEntity> findByStatus(String status);

    List<IndexingRunEntity> findAllByOrderByStartedAtDesc(Pageable pageable);

    List<IndexingRunEntity> findAllByOrderByStartedAtDesc();
}
