package com.kunal.jobscheduler.controller;

import com.kunal.jobscheduler.dto.DashboardResponse;
import com.kunal.jobscheduler.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DashboardController {
    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/api/dashboard")
    public DashboardResponse dashboard() {
        return dashboardService.dashboard();
    }
}
