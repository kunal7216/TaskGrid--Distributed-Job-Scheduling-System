package com.kunal.jobscheduler.controller;

import com.kunal.jobscheduler.dto.JobResponse;
import com.kunal.jobscheduler.dto.JobSubmitRequest;
import com.kunal.jobscheduler.entity.JobEvent;
import com.kunal.jobscheduler.repository.JobEventRepository;
import com.kunal.jobscheduler.service.JobService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/jobs")
public class JobController {
    private final JobService jobService;
    private final JobEventRepository jobEventRepository;

    public JobController(JobService jobService, JobEventRepository jobEventRepository) {
        this.jobService = jobService;
        this.jobEventRepository = jobEventRepository;
    }

    @PostMapping
    public JobResponse submit(@Valid @RequestBody JobSubmitRequest request) {
        return jobService.submit(request);
    }

    @GetMapping("/{jobId}")
    public JobResponse get(@PathVariable String jobId) {
        return jobService.get(jobId);
    }

    @GetMapping
    public List<JobResponse> all() {
        return jobService.all();
    }

    @PostMapping("/{jobId}/retry")
    public JobResponse retry(@PathVariable String jobId) {
        return jobService.retry(jobId);
    }

    @GetMapping("/{jobId}/events")
    public List<JobEvent> events(@PathVariable String jobId) {
        return jobEventRepository.findByJobIdOrderByCreatedAtAsc(jobId);
    }
}
