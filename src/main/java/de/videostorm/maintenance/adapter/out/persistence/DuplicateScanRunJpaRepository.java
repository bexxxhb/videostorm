package de.videostorm.maintenance.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface DuplicateScanRunJpaRepository extends JpaRepository<DuplicateScanRunEntity, Long> {

    List<DuplicateScanRunEntity> findAllByOrderByExecutedAtDesc();
}
