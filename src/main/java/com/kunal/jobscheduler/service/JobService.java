package com.kunal.jobscheduler.service;

import com.kunal.jobscheduler.dto.JobResponse;
import com.kunal.jobscheduler.dto.JobSubmitRequest;
import com.kunal.jobscheduler.entity.Job;
import com.kunal.jobscheduler.enums.JobPriority;
import com.kunal.jobscheduler.enums.JobStatus;
import com.kunal.jobscheduler.repository.JobRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class JobService {
    private final JobRepository jobRepository;
    private final EventPublisher eventPublisher;
    private final LeaderElectionService leaderElectionService;

    @Value("${scheduler.job.max-retries:3}")
    private int defaultMaxRetries;

    public JobService(JobRepository jobRepository, EventPublisher eventPublisher, LeaderElectionService leaderElectionService) {
        this.jobRepository = jobRepository;
        this.eventPublisher = eventPublisher;
        this.leaderElectionService = leaderElectionService;
    }

    @Transactional
    public JobResponse submit(JobSubmitRequest request) {
        Job job = new Job();
        job.setJobId("job-" + UUID.randomUUID());
        job.setJobType(request.getJobType());
        job.setPayload(request.getPayload());
        job.setPriority(request.getPriority() == null ? JobPriority.MEDIUM : request.getPriority());
        job.setStatus(JobStatus.QUEUED);
        job.setMaxRetries(request.getMaxRetries() == null ? defaultMaxRetries : request.getMaxRetries());
        job.setAttemptCount(0);
        job.setCreatedAt(LocalDateTime.now());
        job.setUpdatedAt(LocalDateTime.now());
        job.setNextRunAt(LocalDateTime.now());

        Job saved = jobRepository.save(job);
        eventPublisher.publish(saved.getJobId(), "JOB_QUEUED", "api-service", "Job accepted and published to simulated job-topic");
        return toResponse(saved);
    }

    public JobResponse get(String jobId) {
        return jobRepository.findById(jobId)
                .map(this::toResponse)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));
    }

    public List<JobResponse> all() {
        return jobRepository.findAll().stream()
                .sorted(Comparator.comparing(Job::getCreatedAt).reversed())
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public JobResponse retry(String jobId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));

        if (job.getStatus() != JobStatus.FAILED && job.getStatus() != JobStatus.DEAD_LETTER) {
            throw new IllegalArgumentException("Only FAILED or DEAD_LETTER jobs can be manually retried");
        }

        job.setStatus(JobStatus.QUEUED);
        job.setNextRunAt(LocalDateTime.now());
        job.setLockedBy(null);
        job.setLockExpiresAt(null);
        job.setLastError(null);
        job.setUpdatedAt(LocalDateTime.now());

        Job saved = jobRepository.save(job);
        eventPublisher.publish(jobId, "JOB_MANUAL_RETRY", leaderElectionService.nodeId(), "Manual retry requested");
        return toResponse(saved);
    }

    public JobResponse toResponse(Job job) {
        JobResponse response = new JobResponse();
        response.setJobId(job.getJobId());
        response.setJobType(job.getJobType());
        response.setStatus(job.getStatus());
        response.setPriority(job.getPriority());
        response.setAttemptCount(job.getAttemptCount());
        response.setMaxRetries(job.getMaxRetries());
        response.setNextRunAt(job.getNextRunAt());
        response.setCreatedAt(job.getCreatedAt());
        response.setCompletedAt(job.getCompletedAt());
        response.setLockedBy(job.getLockedBy());
        response.setLastError(job.getLastError());
        response.setExecutionTimeMs(job.getExecutionTimeMs());
        return response;
    }
}
