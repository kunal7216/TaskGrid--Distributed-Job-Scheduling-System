package com.kunal.jobscheduler.service;

import com.kunal.jobscheduler.entity.Job;
import com.kunal.jobscheduler.enums.JobStatus;
import com.kunal.jobscheduler.repository.JobRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class WorkerService {
    private final JobRepository jobRepository;
    private final LockService lockService;
    private final JobExecutor jobExecutor;
    private final EventPublisher eventPublisher;

    @Value("${scheduler.leader.node-id:node-1}")
    private String nodeId;

    public WorkerService(JobRepository jobRepository, LockService lockService, JobExecutor jobExecutor, EventPublisher eventPublisher) {
        this.jobRepository = jobRepository;
        this.lockService = lockService;
        this.jobExecutor = jobExecutor;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public boolean processJob(String jobId) {
        if (!lockService.tryAcquireJobLock(jobId, nodeId, 30)) {
            eventPublisher.publish(jobId, "LOCK_SKIPPED", nodeId, "Another worker owns the lock");
            return false;
        }

        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));

        if (job.getStatus() != JobStatus.QUEUED && job.getStatus() != JobStatus.RETRY_SCHEDULED) {
            lockService.releaseJobLock(jobId, nodeId);
            return false;
        }

        job.setStatus(JobStatus.RUNNING);
        job.setStartedAt(LocalDateTime.now());
        job.setUpdatedAt(LocalDateTime.now());
        jobRepository.save(job);

        eventPublisher.publish(jobId, "JOB_STARTED", nodeId, "Worker acquired lock and started execution");

        JobExecutor.ExecutionResult result = jobExecutor.execute(job);

        if (result.success()) {
            markCompleted(job, result);
        } else {
            handleFailure(job, result);
        }

        lockService.releaseJobLock(jobId, nodeId);
        return true;
    }

    private void markCompleted(Job job, JobExecutor.ExecutionResult result) {
        job.setStatus(JobStatus.COMPLETED);
        job.setCompletedAt(LocalDateTime.now());
        job.setUpdatedAt(LocalDateTime.now());
        job.setExecutionTimeMs(result.executionTimeMs());
        job.setLastError(null);
        jobRepository.save(job);
        eventPublisher.publish(job.getJobId(), "JOB_COMPLETED", nodeId, "Job completed in " + result.executionTimeMs() + " ms");
    }

    private void handleFailure(Job job, JobExecutor.ExecutionResult result) {
        int nextAttempt = job.getAttemptCount() + 1;
        job.setAttemptCount(nextAttempt);
        job.setLastError(result.message());
        job.setExecutionTimeMs(result.executionTimeMs());
        job.setUpdatedAt(LocalDateTime.now());

        if (nextAttempt > job.getMaxRetries()) {
            job.setStatus(JobStatus.DEAD_LETTER);
            job.setCompletedAt(LocalDateTime.now());
            eventPublisher.publish(job.getJobId(), "JOB_MOVED_TO_DLQ", nodeId, "Max retries exceeded. " + result.message());
        } else {
            long delaySeconds = calculateBackoffSeconds(nextAttempt);
            job.setStatus(JobStatus.RETRY_SCHEDULED);
            job.setNextRunAt(LocalDateTime.now().plusSeconds(delaySeconds));
            eventPublisher.publish(job.getJobId(), "JOB_RETRY_SCHEDULED", nodeId, "Retry after " + delaySeconds + " seconds. " + result.message());
        }

        jobRepository.save(job);
    }

    private long calculateBackoffSeconds(int attempt) {
        return (long) Math.min(60, Math.pow(2, attempt));
    }
}
