package com.kunal.jobscheduler.enums;

public enum JobStatus {
    QUEUED,
    RUNNING,
    RETRY_SCHEDULED,
    COMPLETED,
    FAILED,
    DEAD_LETTER
}
