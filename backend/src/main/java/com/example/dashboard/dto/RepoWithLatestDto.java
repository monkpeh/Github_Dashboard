package com.example.dashboard.dto;

import java.time.LocalDateTime;

// DTO for representing a repo with its latest snapshot
public record RepoWithLatestDto(
        Long id,
        String owner,
        String repoName,
        LocalDateTime addedAt,
        SnapshotDto latest
) {}