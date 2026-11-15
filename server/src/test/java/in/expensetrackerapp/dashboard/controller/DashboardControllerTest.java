package in.expensetrackerapp.dashboard.controller;

import in.expensetrackerapp.auth.security.JwtAuthenticationEntryPoint;
import in.expensetrackerapp.auth.security.JwtAuthenticationFilter;
import in.expensetrackerapp.auth.security.JwtService;
import in.expensetrackerapp.auth.security.SecurityConfig;
import in.expensetrackerapp.common.exception.BadRequestException;
import in.expensetrackerapp.common.exception.GlobalExceptionHandler;
import in.expensetrackerapp.config.CorsConfig;
import in.expensetrackerapp.dashboard.dto.DashboardSummaryResponse;
import in.expensetrackerapp.dashboard.service.DashboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DashboardController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class,
        CorsConfig.class, GlobalExceptionHandler.class})
class DashboardControllerTest {

    private static final String TOKEN = "valid.jwt.token";
    private static final String USER_ID = "user-123";
    private static final String AUTH = "Bearer " + TOKEN;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardService dashboardService;

    @MockitoBean
    private JwtService jwtService;

    @BeforeEach
    void authenticate() {
        when(jwtService.extractUserId(TOKEN)).thenReturn(USER_ID);
        when(jwtService.isTokenValid(TOKEN, USER_ID)).thenReturn(true);
    }

    private static DashboardSummaryResponse summary() {
        BigDecimal z = new BigDecimal("0.00");
        return new DashboardSummaryResponse("2026-10", "INR", new BigDecimal("70000.00"), new BigDecimal("1780.65"),
                new BigDecimal("300.00"), new BigDecimal("68519.35"), z, z, z, z,
                List.of(new DashboardSummaryResponse.CategoryExpense("Food", new BigDecimal("570.55"), 2)),
                List.of(), 12, 3, 1, 0);
    }

    @Test
    void summaryReturnsTheFiguresForThePrincipalOnly() throws Exception {
        when(dashboardService.summary(USER_ID, null)).thenReturn(summary());

        mockMvc.perform(get("/api/dashboard/summary").param("userId", "attacker-999").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalIncome").value(70000.00))
                .andExpect(jsonPath("$.netCashFlow").value(68519.35))
                .andExpect(jsonPath("$.categoryExpenses[0].category").value("Food"))
                .andExpect(jsonPath("$.pendingReviewCount").value(3))
                .andExpect(jsonPath("$.excludedTransferCount").value(1));
        verify(dashboardService).summary(USER_ID, null);
    }

    @Test
    void monthParameterIsPassedThrough() throws Exception {
        when(dashboardService.summary(USER_ID, "2026-09")).thenReturn(summary());

        mockMvc.perform(get("/api/dashboard/summary").param("month", "2026-09").header("Authorization", AUTH))
                .andExpect(status().isOk());
        verify(dashboardService).summary(USER_ID, "2026-09");
    }

    @Test
    void invalidMonthIs400() throws Exception {
        when(dashboardService.summary(USER_ID, "nonsense")).thenThrow(new BadRequestException("month must be in yyyy-MM format"));

        mockMvc.perform(get("/api/dashboard/summary").param("month", "nonsense").header("Authorization", AUTH))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("month must be in yyyy-MM format"));
    }

    @Test
    void withoutTokenIs401() throws Exception {
        mockMvc.perform(get("/api/dashboard/summary")).andExpect(status().isUnauthorized());
    }
}
