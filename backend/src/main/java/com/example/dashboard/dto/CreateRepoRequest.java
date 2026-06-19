package com.example.dashboard.dto;

// Request to create a new tracked repo
public record CreateRepoRequest(
        String owner,
        String repoName
) {}