package com.example.dashboard.repository;

import com.example.dashboard.model.TrackedRepo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface TrackedRepoRepository extends JpaRepository<TrackedRepo, Long> {

    // Find a repo by owner and name
    Optional<TrackedRepo> findByOwnerAndRepoName(String owner, String repoName);

    // Check if a repo exists by owner and name (for duplicate detection)
    boolean existsByOwnerAndRepoName(String owner, String repoName);
}