package com.kunal.jobscheduler.service;

import com.kunal.jobscheduler.entity.Job;
import org.springframework.stereotype.Service;

@Service
public class JobExecutor {
    public ExecutionResult execute(Job job) {
        long start = System.currentTimeMillis();
        try {
            Thread.sleep(150);
            String payload = job.getPayload() == null ? "" : job.getPayload().toLowerCase();

            if (payload.contains("always fail")) {
                return ExecutionResult.failed("Simulated permanent job failure", elapsed(start));
            }

            if (payload.contains("fail once") && job.getAttemptCount() == 0) {
                return ExecutionResult.failed("Simulated transient failure on first attempt", elapsed(start));
            }

            return ExecutionResult.success("Job executed successfully", elapsed(start));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ExecutionResult.failed("Job execution interrupted", elapsed(start));
        }
    }

    private long elapsed(long start) {
        return System.currentTimeMillis() - start;
    }

    public record ExecutionResult(boolean success, String message, long executionTimeMs) {
        public static ExecutionResult success(String message, long executionTimeMs) {
            return new ExecutionResult(true, message, executionTimeMs);
        }

        public static ExecutionResult failed(String message, long executionTimeMs) {
            return new ExecutionResult(false, message, executionTimeMs);
        }
    }
}
