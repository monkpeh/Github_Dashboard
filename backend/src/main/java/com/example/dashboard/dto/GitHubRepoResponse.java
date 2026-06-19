package com.example.dashboard.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

public record GitHubRepoResponse(
        @JsonProperty("stargazers_count")
        Integer starsCount,

        @JsonProperty("forks_count")
        Integer forksCount,

        @JsonProperty("open_issues_count")
        Integer openIssuesCount,

        @JsonProperty("pushed_at")
        LocalDateTime pushedAt
) {}