package com.kunal.jobscheduler.dto;

import com.kunal.jobscheduler.enums.JobPriority;
import jakarta.validation.constraints.NotBlank;

public class JobSubmitRequest {
    @NotBlank
    private String jobType;

    @NotBlank
    private String payload;

    private JobPriority priority = JobPriority.MEDIUM;
    private Integer maxRetries;

    public String getJobType() { return jobType; }
    public void setJobType(String jobType) { this.jobType = jobType; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public JobPriority getPriority() { return priority; }
    public void setPriority(JobPriority priority) { this.priority = priority; }
    public Integer getMaxRetries() { return maxRetries; }
    public void setMaxRetries(Integer maxRetries) { this.maxRetries = maxRetries; }
}
