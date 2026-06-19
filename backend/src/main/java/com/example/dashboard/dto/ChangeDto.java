package com.example.dashboard.dto;

// DTO for representing changes in repo metrics
public record ChangeDto(
        Double starsPct,
        Double forksPct,
        Double issuesPct
) {}