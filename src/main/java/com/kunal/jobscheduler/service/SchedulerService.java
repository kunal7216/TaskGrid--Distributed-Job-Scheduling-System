package com.kunal.jobscheduler.service;

import com.kunal.jobscheduler.entity.Job;
import com.kunal.jobscheduler.repository.JobRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SchedulerService {
    private final JobRepository jobRepository;
    private final WorkerService workerService;
    private final LeaderElectionService leaderElectionService;
    private final EventPublisher eventPublisher;

    @Value("${scheduler.worker.enabled:true}")
    private boolean workerEnabled;

    @Value("${scheduler.worker.batch-size:5}")
    private int batchSize;

    public SchedulerService(JobRepository jobRepository, WorkerService workerService, LeaderElectionService leaderElectionService, EventPublisher eventPublisher) {
        this.jobRepository = jobRepository;
        this.workerService = workerService;
        this.leaderElectionService = leaderElectionService;
        this.eventPublisher = eventPublisher;
    }

    @Scheduled(fixedDelayString = "${scheduler.worker.poll-interval-ms:3000}")
    public void pollAndExecuteJobs() {
        if (!workerEnabled) return;
        if (!leaderElectionService.acquireOrRenewLeadership()) return;

        List<Job> runnableJobs = jobRepository.findRunnableJobs(LocalDateTime.now());

        runnableJobs.stream().limit(batchSize).forEach(job -> {
            eventPublisher.publish(job.getJobId(), "SCHEDULER_DISPATCHED", leaderElectionService.nodeId(), "Leader dispatched job");
            workerService.processJob(job.getJobId());
        });
    }
}
