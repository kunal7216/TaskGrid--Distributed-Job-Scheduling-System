package com.kunal.jobscheduler.service;

import com.kunal.jobscheduler.entity.Job;
import com.kunal.jobscheduler.repository.JobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
public class LockService {
    private final JobRepository jobRepository;

    public LockService(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    @Transactional
    public boolean tryAcquireJobLock(String jobId, String workerNodeId, int ttlSeconds) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));

        LocalDateTime now = LocalDateTime.now();
        boolean lockExpired = job.getLockExpiresAt() == null || job.getLockExpiresAt().isBefore(now);
        boolean unlocked = job.getLockedBy() == null || job.getLockedBy().isBlank();

        if (unlocked || lockExpired) {
            job.setLockedBy(workerNodeId);
            job.setLockExpiresAt(now.plusSeconds(ttlSeconds));
            job.setUpdatedAt(now);
            jobRepository.save(job);
            return true;
        }

        return workerNodeId.equals(job.getLockedBy());
    }

    @Transactional
    public void releaseJobLock(String jobId, String workerNodeId) {
        jobRepository.findById(jobId).ifPresent(job -> {
            if (workerNodeId.equals(job.getLockedBy())) {
                job.setLockedBy(null);
                job.setLockExpiresAt(null);
                job.setUpdatedAt(LocalDateTime.now());
                jobRepository.save(job);
            }
        });
    }
}
