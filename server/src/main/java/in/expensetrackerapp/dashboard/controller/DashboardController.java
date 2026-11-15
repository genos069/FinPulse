package in.expensetrackerapp.dashboard.controller;

import in.expensetrackerapp.dashboard.dto.DashboardSummaryResponse;
import in.expensetrackerapp.dashboard.service.DashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /** month is optional (yyyy-MM); it selects the "current month" figures and the category breakdown. */
    @GetMapping("/summary")
    public ResponseEntity<DashboardSummaryResponse> summary(
            Authentication authentication,
            @RequestParam(required = false) String month) {
        return ResponseEntity.ok(dashboardService.summary(authentication.getName(), month));
    }
}
