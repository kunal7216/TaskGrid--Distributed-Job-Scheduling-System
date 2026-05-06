package com.kunal.jobscheduler.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI jobSchedulerOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("Distributed Job Scheduling System API")
                .description("Job submission, worker execution, retries, distributed-lock simulation, DLQ and monitoring.")
                .version("1.0.0"));
    }
}
