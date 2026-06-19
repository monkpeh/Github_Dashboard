package com.example.dashboard.dto;

import java.time.LocalDateTime;

// DTO for representing a snapshot of a repo's metrics
public record SnapshotDto(
        LocalDateTime fetchedAt,
        Integer starCount,
        Integer forkCount,
        Integer openIssues
) {}