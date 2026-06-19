package com.example.dashboard.service;

import com.example.dashboard.config.GitHubProperties;
import com.example.dashboard.dto.GitHubRepoResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class GitHubClient {

    private static final Logger logger = LoggerFactory.getLogger(GitHubClient.class);
    private static final String GITHUB_API_URL = "https://api.github.com/repos/{owner}/{repo}";

    private final RestClient restClient;
    private final GitHubProperties gitHubProperties;

    public GitHubClient(GitHubProperties gitHubProperties) {
        this.gitHubProperties = gitHubProperties;
        this.restClient = RestClient.create();
    }

    /**
     * Fetch repository stats from GitHub API
     */
    public GitHubRepoResponse fetchRepoStats(String owner, String repo) {
        try {
            var requestSpec = restClient
                    .get()
                    .uri(GITHUB_API_URL, owner, repo);

            // Add auth header if token is configured
            if (gitHubProperties.getToken() != null && !gitHubProperties.getToken().isEmpty()) {
                requestSpec = requestSpec.header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + gitHubProperties.getToken());
            }

            return requestSpec
                    .retrieve()
                    .body(GitHubRepoResponse.class);

        } catch (RestClientException e) {
            logger.error("Failed to fetch GitHub repo {}/{}: {}", owner, repo, e.getMessage());
            throw new RuntimeException("GitHub API error for " + owner + "/" + repo, e);
        }
    }
}