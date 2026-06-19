package com.example.dashboard.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tracked_repo", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"owner", "repo_name"})
})
public class TrackedRepo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Owner cannot be blank")
    private String owner;

    @Column(name = "repo_name")
    @NotBlank(message = "Repository name cannot be blank")
    private String repoName;

    @Column(name = "added_at", nullable = false)
    private LocalDateTime addedAt;

    @OneToMany(mappedBy = "trackedRepo", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RepoSnapshot> snapshots = new ArrayList<>();

    // Constructors, getters, setters
    public TrackedRepo() {}

    public TrackedRepo(String owner, String repoName) {
        this.owner = owner;
        this.repoName = repoName;
        this.addedAt = LocalDateTime.now();
    }

    // Getters and setters (you can generate these in your IDE)
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }

    public String getRepoName() { return repoName; }
    public void setRepoName(String repoName) { this.repoName = repoName; }

    public LocalDateTime getAddedAt() { return addedAt; }
    public void setAddedAt(LocalDateTime addedAt) { this.addedAt = addedAt; }

    public List<RepoSnapshot> getSnapshots() { return snapshots; }
    public void setSnapshots(List<RepoSnapshot> snapshots) { this.snapshots = snapshots; }
}