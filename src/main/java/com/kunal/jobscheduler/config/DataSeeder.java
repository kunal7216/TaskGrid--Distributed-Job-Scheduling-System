package com.kunal.jobscheduler.config;

import com.kunal.jobscheduler.dto.JobSubmitRequest;
import com.kunal.jobscheduler.enums.JobPriority;
import com.kunal.jobscheduler.repository.JobRepository;
import com.kunal.jobscheduler.service.JobService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataSeeder {
    @Bean
    CommandLineRunner seedJobs(JobRepository jobRepository, JobService jobService) {
        return args -> {
            if (jobRepository.count() > 0) return;

            JobSubmitRequest email = new JobSubmitRequest();
            email.setJobType("EMAIL");
            email.setPayload("send welcome email to user@example.com");
            email.setPriority(JobPriority.HIGH);
            jobService.submit(email);

            JobSubmitRequest invoice = new JobSubmitRequest();
            invoice.setJobType("INVOICE");
            invoice.setPayload("generate invoice pdf");
            invoice.setPriority(JobPriority.MEDIUM);
            jobService.submit(invoice);

            JobSubmitRequest transientFailure = new JobSubmitRequest();
            transientFailure.setJobType("REPORT");
            transientFailure.setPayload("fail once then generate report");
            transientFailure.setPriority(JobPriority.CRITICAL);
            transientFailure.setMaxRetries(3);
            jobService.submit(transientFailure);
        };
    }
}
