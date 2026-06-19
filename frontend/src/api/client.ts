import type { RepoWithLatestDto, SnapshotDto, SummaryDto, CreateRepoRequest } from '../types';
const API_BASE = '/api';

export const api = {
    // Get all repos with latest snapshot
    async getRepos(): Promise<RepoWithLatestDto[]> {
        const response = await fetch(`${API_BASE}/repos`);
        if (!response.ok) throw new Error('Failed to fetch repos');
        return response.json();
    },

    // Get historical snapshots for a repo
    async getHistory(repoId: number, days: number = 30): Promise<SnapshotDto[]> {
        const response = await fetch(`${API_BASE}/repos/${repoId}/history?days=${days}`);
        if (!response.ok) throw new Error('Failed to fetch history');
        return response.json();
    },

    // Get summary (current stats + 7-day change)
    async getSummary(repoId: number): Promise<SummaryDto> {
        const response = await fetch(`${API_BASE}/repos/${repoId}/summary`);
        if (!response.ok) throw new Error('Failed to fetch summary');
        return response.json();
    },

    // Add a new repo
    async addRepo(request: CreateRepoRequest): Promise<void> {
        const response = await fetch(`${API_BASE}/repos`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(request),
        });
        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.error || 'Failed to add repo');
        }
    },

    // Delete a repo
    async deleteRepo(repoId: number): Promise<void> {
        const response = await fetch(`${API_BASE}/repos/${repoId}`, {
            method: 'DELETE',
        });
        if (!response.ok) throw new Error('Failed to delete repo');
    },
};