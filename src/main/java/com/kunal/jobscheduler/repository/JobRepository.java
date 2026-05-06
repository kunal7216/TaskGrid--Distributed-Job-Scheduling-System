package com.kunal.jobscheduler.repository;

import com.kunal.jobscheduler.entity.Job;
import com.kunal.jobscheduler.enums.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface JobRepository extends JpaRepository<Job, String> {
    long countByStatus(JobStatus status);

    @Query("SELECT j FROM Job j WHERE j.status IN ('QUEUED', 'RETRY_SCHEDULED') AND j.nextRunAt <= :now ORDER BY j.createdAt ASC")
    List<Job> findRunnableJobs(LocalDateTime now);

    @Query("SELECT AVG(j.executionTimeMs) FROM Job j WHERE j.status = 'COMPLETED' AND j.executionTimeMs > 0")
    Double averageExecutionTimeMs();
}
