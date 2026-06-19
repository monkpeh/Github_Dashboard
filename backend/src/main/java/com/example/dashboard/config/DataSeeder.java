package com.example.dashboard.config;

import com.example.dashboard.model.TrackedRepo;
import com.example.dashboard.repository.TrackedRepoRepository;
import com.example.dashboard.service.StatsCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.boot.ApplicationArguments;

@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataSeeder.class);

    private final TrackedRepoRepository trackedRepoRepository;
    private final StatsCollector statsCollector;

    public DataSeeder(TrackedRepoRepository trackedRepoRepository, StatsCollector statsCollector) {
        this.trackedRepoRepository = trackedRepoRepository;
        this.statsCollector = statsCollector;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        // Only seed if no repos exist
        if (trackedRepoRepository.count() > 0) {
            logger.info("Database already has repos, skipping seeding");
            return;
        }

        logger.info("Seeding default repositories");

        String[][] repos = {
                {"spring-projects", "spring-boot"},
                {"facebook", "react"},
                {"microsoft", "vscode"},
                {"torvalds", "linux"},
                {"jwasham", "coding-interview-universe"}
        };

        for (String[] repo : repos) {
            TrackedRepo trackedRepo = new TrackedRepo(repo[0], repo[1]);
            trackedRepoRepository.save(trackedRepo);
            logger.info("Seeded repo: {}/{}", repo[0], repo[1]);
        }

        // Collect stats immediately so data is available
        logger.info("Collecting initial stats");
        statsCollector.collectNow();
    }
}