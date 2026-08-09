package de.videostorm.catalogue.adapter.out.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface MovieJpaRepository extends JpaRepository<MovieEntity, Long> {

    List<MovieEntity> findAllBy(Pageable pageable);
}
