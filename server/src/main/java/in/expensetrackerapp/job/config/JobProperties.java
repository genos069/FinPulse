package in.expensetrackerapp.job.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Retry and lease behaviour. The Python worker reads its own equivalents (JOB_LEASE_SECONDS, MAX_JOB_ATTEMPTS). */
@Validated
@ConfigurationProperties(prefix = "application.jobs")
public class JobProperties {

    /** Total number of processing attempts per job before it becomes FAILED. */
    @Min(1)
    private int maxAttempts = 3;

    /** How long a worker owns a job before another worker may take it over. */
    @Min(1)
    private long leaseSeconds = 300;

    /** Delay before retry n is baseSeconds * 2^(n-1), capped at backoffMaxSeconds. */
    @Min(1)
    private long backoffBaseSeconds = 30;

    @Min(1)
    private long backoffMaxSeconds = 3600;

    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }

    public long getLeaseSeconds() { return leaseSeconds; }
    public void setLeaseSeconds(long leaseSeconds) { this.leaseSeconds = leaseSeconds; }

    public long getBackoffBaseSeconds() { return backoffBaseSeconds; }
    public void setBackoffBaseSeconds(long backoffBaseSeconds) { this.backoffBaseSeconds = backoffBaseSeconds; }

    public long getBackoffMaxSeconds() { return backoffMaxSeconds; }
    public void setBackoffMaxSeconds(long backoffMaxSeconds) { this.backoffMaxSeconds = backoffMaxSeconds; }
}
