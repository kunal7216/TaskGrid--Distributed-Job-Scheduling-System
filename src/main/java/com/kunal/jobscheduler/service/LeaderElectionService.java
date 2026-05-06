package com.kunal.jobscheduler.service;

import com.kunal.jobscheduler.entity.LeaderLock;
import com.kunal.jobscheduler.repository.LeaderLockRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
public class LeaderElectionService {
    private static final String LOCK_NAME = "scheduler-leader";

    private final LeaderLockRepository leaderLockRepository;

    @Value("${scheduler.leader.node-id:node-1}")
    private String nodeId;

    @Value("${scheduler.leader.ttl-seconds:15}")
    private long ttlSeconds;

    public LeaderElectionService(LeaderLockRepository leaderLockRepository) {
        this.leaderLockRepository = leaderLockRepository;
    }

    @Transactional
    public boolean acquireOrRenewLeadership() {
        LocalDateTime now = LocalDateTime.now();
        LeaderLock lock = leaderLockRepository.findById(LOCK_NAME).orElse(null);

        if (lock == null) {
            leaderLockRepository.save(new LeaderLock(LOCK_NAME, nodeId, now.plusSeconds(ttlSeconds)));
            return true;
        }

        boolean expired = lock.getExpiresAt() == null || lock.getExpiresAt().isBefore(now);
        boolean owned = nodeId.equals(lock.getOwnerNodeId());

        if (expired || owned) {
            lock.setOwnerNodeId(nodeId);
            lock.setExpiresAt(now.plusSeconds(ttlSeconds));
            leaderLockRepository.save(lock);
            return true;
        }

        return false;
    }

    public String currentLeader() {
        return leaderLockRepository.findById(LOCK_NAME)
                .map(LeaderLock::getOwnerNodeId)
                .orElse("NONE");
    }

    public String nodeId() {
        return nodeId;
    }
}
