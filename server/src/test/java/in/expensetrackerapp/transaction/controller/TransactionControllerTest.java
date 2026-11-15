package in.expensetrackerapp.transaction.controller;

import in.expensetrackerapp.auth.security.JwtAuthenticationEntryPoint;
import in.expensetrackerapp.auth.security.JwtAuthenticationFilter;
import in.expensetrackerapp.auth.security.JwtService;
import in.expensetrackerapp.auth.security.SecurityConfig;
import in.expensetrackerapp.common.exception.BadRequestException;
import in.expensetrackerapp.common.exception.GlobalExceptionHandler;
import in.expensetrackerapp.common.exception.ResourceNotFoundException;
import in.expensetrackerapp.config.CorsConfig;
import in.expensetrackerapp.transaction.dto.TransactionPageResponse;
import in.expensetrackerapp.transaction.dto.TransactionResponse;
import in.expensetrackerapp.transaction.dto.TransactionUpdateRequest;
import in.expensetrackerapp.transaction.model.TransactionDirection;
import in.expensetrackerapp.transaction.service.TransactionFilter;
import in.expensetrackerapp.transaction.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = TransactionController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class,
        CorsConfig.class, GlobalExceptionHandler.class})
class TransactionControllerTest {

    private static final String TOKEN = "valid.jwt.token";
    private static final String USER_ID = "user-123";
    private static final String AUTH = "Bearer " + TOKEN;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransactionService transactionService;

    @MockitoBean
    private JwtService jwtService;

    @BeforeEach
    void authenticate() {
        when(jwtService.extractUserId(TOKEN)).thenReturn(USER_ID);
        when(jwtService.isTokenValid(TOKEN, USER_ID)).thenReturn(true);
    }

    private static TransactionResponse response() {
        return new TransactionResponse("t1", "doc-1", "job-1", "2026-10-03", new BigDecimal("450.00"), "INR",
                TransactionDirection.DEBIT, "Swiggy", "Food", "UPI/DR/1/SWIGGY", "s.pdf", "427619384729",
                new BigDecimal("1000.50"), false, null, Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void list_returnsPageAndPassesFiltersAndPrincipal() throws Exception {
        when(transactionService.list(eq(USER_ID), any(TransactionFilter.class), anyInt(), anyInt()))
                .thenReturn(new TransactionPageResponse(List.of(response()), 1, 10, 11, 2));

        mockMvc.perform(get("/api/transactions")
                        .param("page", "1").param("size", "10")
                        .param("from", "2026-10-01").param("to", "2026-10-31")
                        .param("category", "Food").param("direction", "DEBIT").param("needsReview", "false")
                        .param("userId", "attacker-999")
                        .header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value("t1"))
                .andExpect(jsonPath("$.content[0].amount").value(450.00))
                .andExpect(jsonPath("$.content[0].direction").value("DEBIT"))
                .andExpect(jsonPath("$.totalElements").value(11))
                .andExpect(jsonPath("$.content[0].userId").doesNotExist())
                .andExpect(jsonPath("$.content[0].sourceHash").doesNotExist());

        ArgumentCaptor<TransactionFilter> filter = ArgumentCaptor.forClass(TransactionFilter.class);
        verify(transactionService).list(eq(USER_ID), filter.capture(), eq(1), eq(10));
        assertThat(filter.getValue()).isEqualTo(new TransactionFilter(LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 31), "Food", TransactionDirection.DEBIT, false));
    }

    @Test
    void list_withMalformedDateIs400NotInternalServerError() throws Exception {
        mockMvc.perform(get("/api/transactions").param("from", "03/10/2026").header("Authorization", AUTH))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(transactionService);
    }

    @Test
    void list_withUnknownDirectionIs400() throws Exception {
        mockMvc.perform(get("/api/transactions").param("direction", "SIDEWAYS").header("Authorization", AUTH))
                .andExpect(status().isBadRequest());
    }

    @Test
    void list_serviceValidationErrorsAre400() throws Exception {
        when(transactionService.list(eq(USER_ID), any(TransactionFilter.class), anyInt(), anyInt()))
                .thenThrow(new BadRequestException("'from' must not be after 'to'"));

        mockMvc.perform(get("/api/transactions").header("Authorization", AUTH))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("'from' must not be after 'to'"));
    }

    @Test
    void list_withoutTokenIs401() throws Exception {
        mockMvc.perform(get("/api/transactions")).andExpect(status().isUnauthorized());
    }

    @Test
    void get_returnsTransactionOrNotFound() throws Exception {
        when(transactionService.get(USER_ID, "t1")).thenReturn(response());
        when(transactionService.get(USER_ID, "other")).thenThrow(new ResourceNotFoundException("Transaction not found"));

        mockMvc.perform(get("/api/transactions/t1").header("Authorization", AUTH))
                .andExpect(status().isOk()).andExpect(jsonPath("$.merchant").value("Swiggy"));
        mockMvc.perform(get("/api/transactions/other").header("Authorization", AUTH))
                .andExpect(status().isNotFound());
    }

    @Test
    void patch_updatesWithPrincipalAndIgnoresOwnershipFieldsInBody() throws Exception {
        when(transactionService.update(eq(USER_ID), eq("t1"), any(TransactionUpdateRequest.class))).thenReturn(response());

        mockMvc.perform(patch("/api/transactions/t1").header("Authorization", AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"Food\",\"userId\":\"attacker-999\",\"sourceHash\":\"x\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<TransactionUpdateRequest> body = ArgumentCaptor.forClass(TransactionUpdateRequest.class);
        verify(transactionService).update(eq(USER_ID), eq("t1"), body.capture());
        assertThat(body.getValue().category()).isEqualTo("Food");
    }

    @Test
    void patch_rejectsInvalidFieldsWith400AndNeverCallsTheService() throws Exception {
        String[] badBodies = {
                "{\"amount\": -5}",
                "{\"amount\": 0}",
                "{\"amount\": 10.123}",
                "{\"date\": \"03-10-2026\"}",
                "{\"merchant\": \"\"}",
                "{\"description\": \"" + "x".repeat(2001) + "\"}",
                "{\"direction\": \"SIDEWAYS\"}",
                "{ not json"
        };
        for (String body : badBodies) {
            mockMvc.perform(patch("/api/transactions/t1").header("Authorization", AUTH)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verify(transactionService, never()).update(any(), any(), any());
    }

    @Test
    void patch_withoutTokenIs401() throws Exception {
        mockMvc.perform(patch("/api/transactions/t1").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void delete_returns204OrNotFound() throws Exception {
        org.mockito.Mockito.doThrow(new ResourceNotFoundException("Transaction not found"))
                .when(transactionService).delete(USER_ID, "other");

        mockMvc.perform(delete("/api/transactions/t1").header("Authorization", AUTH)).andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/transactions/other").header("Authorization", AUTH)).andExpect(status().isNotFound());
        verify(transactionService).delete(USER_ID, "t1");
    }
}
