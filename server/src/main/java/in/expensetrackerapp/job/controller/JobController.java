package in.expensetrackerapp.job.controller;

import in.expensetrackerapp.job.dto.JobResponse;
import in.expensetrackerapp.job.service.JobService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<JobResponse> getJob(Authentication authentication, @PathVariable String jobId) {
        return ResponseEntity.ok(JobResponse.fromJob(jobService.getJob(authentication.getName(), jobId)));
    }
}
