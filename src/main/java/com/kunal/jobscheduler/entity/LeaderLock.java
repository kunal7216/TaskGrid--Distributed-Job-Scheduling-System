package com.kunal.jobscheduler.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.LocalDateTime;

@Entity
public class LeaderLock {
    @Id
    private String lockName;

    private String ownerNodeId;
    private LocalDateTime expiresAt;

    public LeaderLock() {}

    public LeaderLock(String lockName, String ownerNodeId, LocalDateTime expiresAt) {
        this.lockName = lockName;
        this.ownerNodeId = ownerNodeId;
        this.expiresAt = expiresAt;
    }

    public String getLockName() { return lockName; }
    public void setLockName(String lockName) { this.lockName = lockName; }
    public String getOwnerNodeId() { return ownerNodeId; }
    public void setOwnerNodeId(String ownerNodeId) { this.ownerNodeId = ownerNodeId; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
}
