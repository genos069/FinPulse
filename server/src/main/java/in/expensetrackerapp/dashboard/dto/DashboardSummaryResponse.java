package in.expensetrackerapp.dashboard.dto;

import in.expensetrackerapp.transaction.dto.TransactionResponse;

import java.math.BigDecimal;
import java.util.List;

/**
 * Dashboard figures. Counting rules (also in API_DOCUMENTATION.md):
 * - Only verified rows count: needsReview=true rows are excluded and reported in pendingReviewCount.
 * - Category "Transfer" (both directions) is excluded from income and expenses; see excludedTransferCount.
 * - Refunds/cashback are credits that are NOT income. They are shown separately and DO count towards net cash flow.
 * - netCashFlow = totalIncome + totalRefunds - totalExpenses.
 * - Only the configured currency (or rows without a currency) is summed; others are counted in otherCurrencyCount.
 * - "currentMonth*" figures and categoryExpenses refer to {@code month} (default: the current calendar month).
 */
public record DashboardSummaryResponse(
        String month,
        String currency,
        BigDecimal totalIncome,
        BigDecimal totalExpenses,
        BigDecimal totalRefunds,
        BigDecimal netCashFlow,
        BigDecimal currentMonthIncome,
        BigDecimal currentMonthExpenses,
        BigDecimal currentMonthRefunds,
        BigDecimal currentMonthNetCashFlow,
        List<CategoryExpense> categoryExpenses,
        List<TransactionResponse> recentTransactions,
        long transactionCount,
        long pendingReviewCount,
        long excludedTransferCount,
        long otherCurrencyCount) {

    public record CategoryExpense(String category, BigDecimal total, long transactionCount) {
    }
}
