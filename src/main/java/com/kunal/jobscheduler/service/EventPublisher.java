package com.kunal.jobscheduler.service;

import com.kunal.jobscheduler.entity.JobEvent;
import com.kunal.jobscheduler.repository.JobEventRepository;
import org.springframework.stereotype.Service;

@Service
public class EventPublisher {
    private final JobEventRepository jobEventRepository;

    public EventPublisher(JobEventRepository jobEventRepository) {
        this.jobEventRepository = jobEventRepository;
    }

    public void publish(String jobId, String eventType, String nodeId, String message) {
        jobEventRepository.save(new JobEvent(jobId, eventType, nodeId, message));
    }
}
