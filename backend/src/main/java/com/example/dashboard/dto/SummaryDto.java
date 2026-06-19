package com.example.dashboard.dto;

// DTO for representing summary of a repo's metrics
public record SummaryDto(
        SnapshotDto current,
        ChangeDto change
) {}