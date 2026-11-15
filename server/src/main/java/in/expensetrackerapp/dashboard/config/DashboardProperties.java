package in.expensetrackerapp.dashboard.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "application.dashboard")
public class DashboardProperties {

    /** Only transactions in this currency (or with no currency) are summed; amounts in different currencies cannot be added. */
    @NotBlank
    private String currency = "INR";

    /** Zone used to decide which calendar month is "current". Statement dates are plain calendar dates. */
    @NotBlank
    private String zone = "Asia/Kolkata";

    @Min(1)
    @Max(50)
    private int recentCount = 5;

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getZone() { return zone; }
    public void setZone(String zone) { this.zone = zone; }

    public int getRecentCount() { return recentCount; }
    public void setRecentCount(int recentCount) { this.recentCount = recentCount; }
}
