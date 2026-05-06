package com.kunal.jobscheduler.repository;

import com.kunal.jobscheduler.entity.JobEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface JobEventRepository extends JpaRepository<JobEvent, Long> {
    List<JobEvent> findByJobIdOrderByCreatedAtAsc(String jobId);
}
