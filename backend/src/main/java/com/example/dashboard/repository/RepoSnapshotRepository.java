package com.example.dashboard.repository;

import com.example.dashboard.model.RepoSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RepoSnapshotRepository extends JpaRepository<RepoSnapshot, Long> {

    // Get all snapshots for a repo within the last N days, ordered by fetchedAt ASC
    @Query("SELECT rs FROM RepoSnapshot rs WHERE rs.trackedRepo.id = :repoId " +
            "AND rs.fetchedAt >= :startDate ORDER BY rs.fetchedAt ASC")
    List<RepoSnapshot> findByRepoIdWithinDays(@Param("repoId") Long repoId,
                                              @Param("startDate") LocalDateTime startDate);

    // Get the most recent snapshot for each repo (used for /api/repos endpoint)
    @Query("SELECT rs FROM RepoSnapshot rs WHERE rs.trackedRepo.id = :repoId " +
            "ORDER BY rs.fetchedAt DESC LIMIT 1")
    Optional<RepoSnapshot> findLatestByRepoId(@Param("repoId") Long repoId);

    // Find snapshot closest to a target date (for 7-day change calculation)
    @Query("SELECT rs FROM RepoSnapshot rs WHERE rs.trackedRepo.id = :repoId " +
            "AND rs.fetchedAt <= :targetDate ORDER BY rs.fetchedAt DESC LIMIT 1")
    Optional<RepoSnapshot> findClosestToDate(@Param("repoId") Long repoId,
                                             @Param("targetDate") LocalDateTime targetDate);
}