package in.expensetrackerapp.job.controller;

import in.expensetrackerapp.auth.security.JwtAuthenticationEntryPoint;
import in.expensetrackerapp.auth.security.JwtAuthenticationFilter;
import in.expensetrackerapp.auth.security.JwtService;
import in.expensetrackerapp.auth.security.SecurityConfig;
import in.expensetrackerapp.common.exception.GlobalExceptionHandler;
import in.expensetrackerapp.common.exception.ResourceNotFoundException;
import in.expensetrackerapp.config.CorsConfig;
import in.expensetrackerapp.job.model.JobStatus;
import in.expensetrackerapp.job.model.ProcessingJob;
import in.expensetrackerapp.job.service.JobService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = JobController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class,
        CorsConfig.class, GlobalExceptionHandler.class})
class JobControllerTest {

    private static final String TOKEN = "valid.jwt.token";
    private static final String USER_ID = "user-123";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JobService jobService;

    @MockitoBean
    private JwtService jwtService;

    @BeforeEach
    void authenticate() {
        when(jwtService.extractUserId(TOKEN)).thenReturn(USER_ID);
        when(jwtService.isTokenValid(TOKEN, USER_ID)).thenReturn(true);
    }

    @Test
    void getJob_returnsOwnJobWithoutInternalFields() throws Exception {
        ProcessingJob job = new ProcessingJob();
        job.setId("job-1");
        job.setUserId(USER_ID);
        job.setDocumentId("doc-1");
        job.setGridFsFileId("secret-gridfs-id");
        job.setLeaseOwner("worker-a");
        job.setStatus(JobStatus.COMPLETED);
        job.setAttempts(1);
        job.setMaxAttempts(3);
        job.setExtractedCount(12);
        job.setDuplicateCount(2);
        job.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        when(jobService.getJob(USER_ID, "job-1")).thenReturn(job);

        mockMvc.perform(get("/api/jobs/job-1").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("job-1"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.extractedCount").value(12))
                .andExpect(jsonPath("$.duplicateCount").value(2))
                .andExpect(jsonPath("$.userId").doesNotExist())
                .andExpect(jsonPath("$.gridFsFileId").doesNotExist())
                .andExpect(jsonPath("$.leaseOwner").doesNotExist());

        verify(jobService).getJob(USER_ID, "job-1");
    }

    @Test
    void getJob_ofAnotherUserIs404() throws Exception {
        when(jobService.getJob(USER_ID, "job-9")).thenThrow(new ResourceNotFoundException("Job not found"));

        mockMvc.perform(get("/api/jobs/job-9").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isNotFound());
    }

    @Test
    void getJob_withoutTokenIs401() throws Exception {
        mockMvc.perform(get("/api/jobs/job-1")).andExpect(status().isUnauthorized());
    }
}
