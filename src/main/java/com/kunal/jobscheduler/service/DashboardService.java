package com.kunal.jobscheduler.service;

import com.kunal.jobscheduler.dto.DashboardResponse;
import com.kunal.jobscheduler.enums.JobStatus;
import com.kunal.jobscheduler.repository.JobRepository;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {
    private final JobRepository jobRepository;
    private final LeaderElectionService leaderElectionService;

    public DashboardService(JobRepository jobRepository, LeaderElectionService leaderElectionService) {
        this.jobRepository = jobRepository;
        this.leaderElectionService = leaderElectionService;
    }

    public DashboardResponse dashboard() {
        long total = jobRepository.count();
        long queued = jobRepository.countByStatus(JobStatus.QUEUED);
        long running = jobRepository.countByStatus(JobStatus.RUNNING);
        long completed = jobRepository.countByStatus(JobStatus.COMPLETED);
        long retry = jobRepository.countByStatus(JobStatus.RETRY_SCHEDULED);
        long failed = jobRepository.countByStatus(JobStatus.FAILED);
        long dlq = jobRepository.countByStatus(JobStatus.DEAD_LETTER);
        double successRate = total == 0 ? 0.0 : Math.round(((double) completed / total) * 10000.0) / 100.0;
        Double avg = jobRepository.averageExecutionTimeMs();

        return new DashboardResponse(total, queued, running, completed, retry, failed, dlq,
                successRate, avg == null ? 0.0 : Math.round(avg * 100.0) / 100.0, leaderElectionService.currentLeader());
    }
}
