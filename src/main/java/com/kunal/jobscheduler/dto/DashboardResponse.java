package com.kunal.jobscheduler.dto;

public class DashboardResponse {
    private long totalJobs;
    private long queuedJobs;
    private long runningJobs;
    private long completedJobs;
    private long retryScheduledJobs;
    private long failedJobs;
    private long deadLetterJobs;
    private double successRate;
    private double averageExecutionTimeMs;
    private String currentLeaderNode;

    public DashboardResponse(long totalJobs, long queuedJobs, long runningJobs, long completedJobs,
                             long retryScheduledJobs, long failedJobs, long deadLetterJobs,
                             double successRate, double averageExecutionTimeMs, String currentLeaderNode) {
        this.totalJobs = totalJobs;
        this.queuedJobs = queuedJobs;
        this.runningJobs = runningJobs;
        this.completedJobs = completedJobs;
        this.retryScheduledJobs = retryScheduledJobs;
        this.failedJobs = failedJobs;
        this.deadLetterJobs = deadLetterJobs;
        this.successRate = successRate;
        this.averageExecutionTimeMs = averageExecutionTimeMs;
        this.currentLeaderNode = currentLeaderNode;
    }

    public long getTotalJobs() { return totalJobs; }
    public long getQueuedJobs() { return queuedJobs; }
    public long getRunningJobs() { return runningJobs; }
    public long getCompletedJobs() { return completedJobs; }
    public long getRetryScheduledJobs() { return retryScheduledJobs; }
    public long getFailedJobs() { return failedJobs; }
    public long getDeadLetterJobs() { return deadLetterJobs; }
    public double getSuccessRate() { return successRate; }
    public double getAverageExecutionTimeMs() { return averageExecutionTimeMs; }
    public String getCurrentLeaderNode() { return currentLeaderNode; }
}
