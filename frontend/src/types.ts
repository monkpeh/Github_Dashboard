export interface SnapshotDto {
    fetchedAt: string;
    starCount: number;
    forkCount: number;
    openIssues: number;
}

export interface RepoWithLatestDto {
    id: number;
    owner: string;
    repoName: string;
    addedAt: string;
    latest: SnapshotDto | null;
}

export interface SummaryDto {
    current: SnapshotDto;
    change: ChangeDto;
}

export interface ChangeDto {
    starsPct: number | null;
    forksPct: number | null;
    issuesPct: number | null;
}

export interface CreateRepoRequest {
    owner: string;
    repoName: string;
}