# Requirements Document

## Introduction

The GitHub Repository Dashboard is a full-stack application that tracks GitHub repository statistics over time and visualizes trends on a dashboard. The system periodically fetches repository metrics from the GitHub REST API, stores historical snapshots, and presents interactive visualizations to help users monitor repository growth and activity patterns.

## Glossary

- **System**: The complete GitHub Repository Dashboard application (backend + frontend)
- **Data_Collector**: The Spring Batch scheduled job that fetches repository statistics from GitHub API
- **API_Server**: The Spring Boot REST API that serves repository data to the frontend
- **Dashboard**: The React web interface that displays repository statistics and visualizations
- **TrackedRepo**: A GitHub repository being monitored by the system
- **RepoSnapshot**: A timestamped record of repository statistics at a specific point in time
- **GitHub_API**: The GitHub REST API v3 used to fetch repository data
- **Date_Range_Selector**: UI control allowing users to choose time windows (7d/30d/90d)
- **Repository_Toggle**: UI checkbox control to show/hide individual repositories on charts

## Requirements

### Requirement 1: Track Configured Repositories

**User Story:** As a developer, I want to track multiple GitHub repositories, so that I can monitor their growth and activity over time.

#### Acceptance Criteria

1. THE System SHALL persist a list of TrackedRepos containing owner and repository name
2. WHEN a TrackedRepo is added, THE System SHALL validate that the owner and repoName fields are non-empty strings
3. WHEN a TrackedRepo is added, THE System SHALL store the current timestamp as addedAt
4. THE System SHALL maintain a unique identifier for each TrackedRepo
5. WHEN a TrackedRepo is removed, THE System SHALL delete all associated RepoSnapshots

### Requirement 2: Initial Repository Seeding

**User Story:** As a user, I want the system to automatically track popular repositories on first startup, so that I can see sample data immediately.

#### Acceptance Criteria

1. WHEN the System starts for the first time AND no TrackedRepos exist, THE System SHALL seed five repositories: spring-projects/spring-boot, facebook/react, microsoft/vscode, torvalds/linux, jwasham/coding-interview-universe
2. WHEN the System starts AND TrackedRepos already exist, THE System SHALL NOT perform seeding
3. FOR ALL seeded repositories, THE System SHALL store them with valid owner and repoName values

### Requirement 3: Periodic Statistics Collection

**User Story:** As a user, I want repository statistics fetched automatically every hour, so that I have up-to-date trend data without manual intervention.

#### Acceptance Criteria

1. THE Data_Collector SHALL execute every 60 minutes
2. WHEN the Data_Collector executes, THE Data_Collector SHALL fetch statistics for all TrackedRepos
3. FOR ALL TrackedRepos processed during a collection cycle, THE Data_Collector SHALL retrieve star count, fork count, open issue count, and latest commit date from the GitHub_API
4. WHEN statistics are successfully fetched for a TrackedRepo, THE Data_Collector SHALL store a RepoSnapshot with the current timestamp as fetchedAt
5. WHEN the GitHub_API returns an error for a TrackedRepo, THE Data_Collector SHALL log the error and continue processing remaining repositories

### Requirement 4: GitHub API Authentication

**User Story:** As a developer, I want to configure a GitHub API token, so that I can increase rate limits from 60 to 5000 requests per hour.

#### Acceptance Criteria

1. WHEN the configuration property app.github.token is present, THE Data_Collector SHALL attach it as an Authorization Bearer header in all GitHub_API requests
2. WHEN the configuration property app.github.token is absent, THE Data_Collector SHALL make unauthenticated GitHub_API requests
3. THE System SHALL read app.github.token from application.properties at startup
4. THE System SHALL NOT hardcode any GitHub API tokens in source code

### Requirement 5: Historical Snapshot Storage

**User Story:** As a user, I want each statistics fetch stored as a separate snapshot, so that I can query historical trends over time.

#### Acceptance Criteria

1. THE System SHALL persist RepoSnapshots with fields: id, trackedRepo foreign key, starCount, forkCount, openIssues, and fetchedAt timestamp
2. WHEN a RepoSnapshot is stored, THE System SHALL associate it with exactly one TrackedRepo
3. WHEN a RepoSnapshot is stored, THE System SHALL record fetchedAt as the current UTC timestamp
4. THE System SHALL preserve all RepoSnapshots in chronological order
5. FOR ALL RepoSnapshots of a TrackedRepo, THE System SHALL maintain the fetchedAt timestamp ordering

### Requirement 6: Retrieve All Tracked Repositories

**User Story:** As a user, I want to see all tracked repositories with their latest statistics, so that I can get an overview of current status.

#### Acceptance Criteria

1. WHEN a GET request is made to /api/repos, THE API_Server SHALL return all TrackedRepos
2. FOR ALL TrackedRepos returned, THE API_Server SHALL include the most recent RepoSnapshot data
3. WHEN a TrackedRepo has no RepoSnapshots, THE API_Server SHALL include the TrackedRepo with null snapshot values
4. THE API_Server SHALL return responses in JSON format with HTTP status 200

### Requirement 7: Retrieve Repository History

**User Story:** As a user, I want to fetch historical snapshots for a specific repository, so that I can analyze trends over a selected time period.

#### Acceptance Criteria

1. WHEN a GET request is made to /api/repos/{id}/history with a days query parameter, THE API_Server SHALL return all RepoSnapshots for the TrackedRepo where fetchedAt is within the last N days
2. WHEN the days parameter is absent, THE API_Server SHALL default to 30 days
3. WHEN the TrackedRepo ID does not exist, THE API_Server SHALL return HTTP status 404
4. THE API_Server SHALL return RepoSnapshots ordered by fetchedAt ascending
5. WHEN a TrackedRepo has no RepoSnapshots in the specified range, THE API_Server SHALL return an empty array with HTTP status 200

### Requirement 8: Calculate Summary Statistics

**User Story:** As a user, I want to see current statistics and percentage change over the last 7 days, so that I can quickly assess recent repository growth.

#### Acceptance Criteria

1. WHEN a GET request is made to /api/repos/{id}/summary, THE API_Server SHALL return the most recent RepoSnapshot data
2. THE API_Server SHALL calculate percentage change for starCount, forkCount, and openIssues by comparing the most recent snapshot to the snapshot closest to 7 days ago
3. WHEN no snapshot exists from 7 days ago, THE API_Server SHALL return null for percentage change values
4. WHEN the TrackedRepo ID does not exist, THE API_Server SHALL return HTTP status 404
5. WHEN division by zero would occur in percentage calculation, THE API_Server SHALL return null for that metric

### Requirement 9: Add New Repository to Track

**User Story:** As a user, I want to add new repositories via the UI, so that I can expand the set of repositories being monitored.

#### Acceptance Criteria

1. WHEN a POST request is made to /api/repos with JSON body containing owner and repoName, THE API_Server SHALL create a new TrackedRepo
2. WHEN the owner or repoName fields are empty or missing, THE API_Server SHALL return HTTP status 400 with an error message
3. WHEN a TrackedRepo with the same owner and repoName already exists, THE API_Server SHALL return HTTP status 409 with an error message
4. WHEN a TrackedRepo is successfully created, THE API_Server SHALL return the created TrackedRepo with HTTP status 201
5. WHEN a new TrackedRepo is added, THE Data_Collector SHALL include it in the next scheduled collection cycle

### Requirement 10: Remove Tracked Repository

**User Story:** As a user, I want to stop tracking repositories, so that I can remove repositories I'm no longer interested in monitoring.

#### Acceptance Criteria

1. WHEN a DELETE request is made to /api/repos/{id}, THE API_Server SHALL delete the TrackedRepo and all associated RepoSnapshots
2. WHEN the TrackedRepo ID does not exist, THE API_Server SHALL return HTTP status 404
3. WHEN a TrackedRepo is successfully deleted, THE API_Server SHALL return HTTP status 204
4. WHEN a TrackedRepo is deleted, THE Data_Collector SHALL NOT attempt to fetch statistics for it in subsequent collection cycles

### Requirement 11: Display Line Chart of Stars Over Time

**User Story:** As a user, I want to see a line chart showing star count trends, so that I can compare repository popularity growth across multiple repositories.

#### Acceptance Criteria

1. THE Dashboard SHALL display a line chart with time on the X-axis and star count on the Y-axis
2. FOR ALL visible TrackedRepos, THE Dashboard SHALL render a distinct line with a unique color
3. WHEN a user interacts with Repository_Toggles, THE Dashboard SHALL show or hide the corresponding repository lines
4. WHEN the Date_Range_Selector changes, THE Dashboard SHALL update the line chart to show data for the selected time window
5. THE Dashboard SHALL fetch historical data from /api/repos/{id}/history with the appropriate days parameter

### Requirement 12: Display Bar Chart of Current Fork Counts

**User Story:** As a user, I want to see a bar chart of current fork counts, so that I can compare the current fork distribution across repositories.

#### Acceptance Criteria

1. THE Dashboard SHALL display a bar chart with repository names on the X-axis and fork count on the Y-axis
2. FOR ALL TrackedRepos, THE Dashboard SHALL render a bar showing the most recent fork count
3. WHEN a user interacts with Repository_Toggles, THE Dashboard SHALL show or hide the corresponding repository bars
4. THE Dashboard SHALL fetch current data from /api/repos endpoint
5. THE Dashboard SHALL update the bar chart when new data is fetched

### Requirement 13: Display Summary Card Grid

**User Story:** As a user, I want to see summary cards with current statistics and 7-day percentage change, so that I can quickly assess recent repository activity.

#### Acceptance Criteria

1. THE Dashboard SHALL display summary cards for all TrackedRepos in a grid layout
2. FOR ALL TrackedRepos, THE Dashboard SHALL display current star count, fork count, open issue count, and 7-day percentage change for each metric
3. WHEN percentage change is positive, THE Dashboard SHALL display it in green
4. WHEN percentage change is negative, THE Dashboard SHALL display it in red
5. WHEN percentage change is null, THE Dashboard SHALL display a neutral indicator
6. THE Dashboard SHALL fetch summary data from /api/repos/{id}/summary endpoint

### Requirement 14: Date Range Selection

**User Story:** As a user, I want to select different time windows (7d/30d/90d), so that I can view repository trends over various time periods.

#### Acceptance Criteria

1. THE Dashboard SHALL provide a Date_Range_Selector with options: 7 days, 30 days, and 90 days
2. WHEN a user selects a date range, THE Dashboard SHALL update all time-based charts to display data for the selected period
3. THE Dashboard SHALL default to 7 days when first loaded
4. WHEN the date range changes, THE Dashboard SHALL fetch historical data with the corresponding days parameter

### Requirement 15: Repository Visibility Toggle

**User Story:** As a user, I want to show or hide individual repositories on charts, so that I can focus on specific repositories of interest.

#### Acceptance Criteria

1. THE Dashboard SHALL display Repository_Toggles for all TrackedRepos
2. WHEN a Repository_Toggle is checked, THE Dashboard SHALL display that repository's data on all charts
3. WHEN a Repository_Toggle is unchecked, THE Dashboard SHALL hide that repository's data from all charts
4. THE Dashboard SHALL enable all Repository_Toggles by default when first loaded
5. THE Dashboard SHALL preserve Repository_Toggle states when the Date_Range_Selector changes

### Requirement 16: Database Schema Management

**User Story:** As a developer, I want the database schema automatically managed, so that I can focus on application logic without manual schema migrations.

#### Acceptance Criteria

1. THE System SHALL use Hibernate with ddl-auto=update configuration
2. WHEN the System starts, THE System SHALL automatically create or update database tables based on entity definitions
3. THE System SHALL create a table for TrackedRepo with columns: id, owner, repoName, addedAt
4. THE System SHALL create a table for RepoSnapshot with columns: id, trackedRepo foreign key, starCount, forkCount, openIssues, fetchedAt
5. THE System SHALL establish a foreign key relationship from RepoSnapshot to TrackedRepo

### Requirement 17: Cross-Origin Request Handling

**User Story:** As a developer, I want the frontend to communicate with the backend without CORS issues, so that API requests function correctly during development.

#### Acceptance Criteria

1. THE Dashboard SHALL run on port 5173
2. THE API_Server SHALL run on port 8080
3. WHEN the Dashboard makes requests to /api endpoints, THE Vite development server SHALL proxy them to http://localhost:8080
4. THE API_Server SHALL NOT require CORS configuration
5. THE Dashboard SHALL use relative /api paths for all backend requests

### Requirement 18: Error Handling and Logging

**User Story:** As a developer, I want comprehensive error logging, so that I can diagnose issues when GitHub API requests fail or data processing errors occur.

#### Acceptance Criteria

1. WHEN the GitHub_API returns a non-success HTTP status, THE Data_Collector SHALL log the repository identifier, HTTP status code, and error message
2. WHEN a network timeout occurs during GitHub_API requests, THE Data_Collector SHALL log the timeout error and continue processing
3. WHEN JSON parsing fails for GitHub_API responses, THE Data_Collector SHALL log the parsing error with the raw response
4. WHEN database persistence fails for a RepoSnapshot, THE System SHALL log the database error with the TrackedRepo identifier
5. THE System SHALL use structured logging with appropriate log levels (INFO for normal operations, ERROR for failures)
