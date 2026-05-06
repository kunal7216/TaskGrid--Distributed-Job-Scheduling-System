package com.kunal.jobscheduler.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "job_events", indexes = {
        @Index(name = "idx_event_job", columnList = "jobId"),
        @Index(name = "idx_event_type", columnList = "eventType")
})
public class JobEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String jobId;
    private String eventType;
    private String nodeId;

    @Column(length = 4000)
    private String message;

    private LocalDateTime createdAt;

    public JobEvent() {}

    public JobEvent(String jobId, String eventType, String nodeId, String message) {
        this.jobId = jobId;
        this.eventType = eventType;
        this.nodeId = nodeId;
        this.message = message;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getJobId() { return jobId; }
    public String getEventType() { return eventType; }
    public String getNodeId() { return nodeId; }
    public String getMessage() { return message; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
