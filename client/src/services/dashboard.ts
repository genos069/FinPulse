import { z } from "zod";
import { authApiUrl } from "./auth";

const amount = z.coerce.number().finite();
const categorySchema = z.object({
  category: z.string(),
  total: amount,
  transactionCount: z.coerce.number().int().nonnegative(),
});
const recentTransactionSchema = z.object({
  id: z.string(),
  date: z.string(),
  amount,
  currency: z.string().optional().default("INR"),
  direction: z.string(),
  merchant: z.string().nullable().optional(),
  category: z.string().nullable().optional(),
  description: z.string().nullable().optional(),
  source: z.string().nullable().optional(),
  needsReview: z.boolean().optional().default(false),
});
export const dashboardSummarySchema = z.object({
  month: z.string(),
  currency: z.string(),
  totalIncome: amount,
  totalExpenses: amount,
  totalRefunds: amount,
  netCashFlow: amount,
  currentMonthIncome: amount,
  currentMonthExpenses: amount,
  currentMonthRefunds: amount,
  currentMonthNetCashFlow: amount,
  categoryExpenses: z.array(categorySchema),
  recentTransactions: z.array(recentTransactionSchema),
  transactionCount: z.coerce.number().int().nonnegative(),
  pendingReviewCount: z.coerce.number().int().nonnegative(),
  excludedTransferCount: z.coerce.number().int().nonnegative(),
  otherCurrencyCount: z.coerce.number().int().nonnegative(),
});
export type DashboardSummary = z.infer<typeof dashboardSummarySchema>;
export type DashboardTransaction = z.infer<typeof recentTransactionSchema>;

export async function getDashboardSummary(
  accessToken: string,
  month: string,
): Promise<DashboardSummary> {
  if (!authApiUrl) throw new Error("The dashboard server is not configured.");
  const url = new URL(authApiUrl);
  if (
    url.protocol !== "https:" &&
    !(typeof __DEV__ !== "undefined" && __DEV__ && url.protocol === "http:")
  ) {
    throw new Error("The dashboard server must use HTTPS.");
  }

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 15000);
  try {
    const response = await fetch(
      `${authApiUrl}/api/dashboard/summary?month=${encodeURIComponent(month)}`,
      {
        headers: { Authorization: `Bearer ${accessToken}` },
        signal: controller.signal,
      },
    );
    if (response.status === 401 || response.status === 403) {
      throw new Error("Your session expired. Please sign in again.");
    }
    if (!response.ok) {
      throw new Error("Could not load your dashboard summary. Please try again.");
    }
    const parsed = dashboardSummarySchema.safeParse(await response.json());
    if (!parsed.success) {
      throw new Error("The dashboard server returned an unexpected response.");
    }
    return parsed.data;
  } catch (error) {
    if (
      error instanceof Error &&
      (error.name === "AbortError" || error instanceof TypeError)
    ) {
      throw new Error("Could not reach the dashboard server.");
    }
    throw error;
  } finally {
    clearTimeout(timer);
  }
}
