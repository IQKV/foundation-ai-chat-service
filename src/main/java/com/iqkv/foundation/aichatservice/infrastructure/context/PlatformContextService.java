/*
 * Copyright 2026 iQKV Foundation Team.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.iqkv.foundation.aichatservice.infrastructure.context;

import java.util.List;

import com.iqkv.foundation.aichatservice.infrastructure.security.JwtClaimNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Fetches user-visible platform data from downstream services for LLM function calling.
 *
 * <p>Only publicly accessible / non-sensitive data is exposed:
 * <ul>
 *   <li>User display name (from JWT claims — no network call)</li>
 *   <li>Current plan code (from JWT claims — no network call)</li>
 *   <li>Available billing plans (from Billing internal plans endpoint)</li>
 * </ul>
 *
 * <p>The LLM calls these functions automatically when the user's question
 * requires plan or profile context. No data is pre-fetched; functions are
 * invoked lazily by the model.
 */
@Service
public class PlatformContextService {

  private static final Logger log = LoggerFactory.getLogger(PlatformContextService.class);

  private final RestClient billingClient;

  public PlatformContextService(@Value("${iqkv.billing.service-url}") final String billingServiceUrl) {
    this.billingClient = RestClient.builder()
        .baseUrl(billingServiceUrl)
        .build();
  }

  // ─── Response records ─────────────────────────────────────────────────────

  /**
   * User profile information.
   *
   * @param firstName user first name
   * @param lastName  user last name
   * @param fullName  user full display name
   * @param planCode  active billing plan code or null
   */
  public record UserProfile(String firstName, String lastName, String fullName, String planCode) {
  }

  public record PlanFeatures(
      Integer maxUsers,
      Integer maxProjects,
      boolean advancedAnalytics,
      boolean prioritySupport) {
  }

  public record Plan(
      String planCode,
      String displayName,
      String billingPeriod,
      String pricingModel,
      Integer priceMinor,
      String currency,
      PlanFeatures features) {
  }

  public record PlanListResponse(List<Plan> plans) {
  }

  // ─── Internal Billing API response shape ──────────────────────────────────

  record BillingPlanEntitlement(
      Integer maxUsers,
      Integer maxProjects,
      java.util.Map<String, Object> features) {
  }

  record BillingPlan(
      String planCode,
      String displayName,
      String billingPeriod,
      String pricingModel,
      Integer priceMinor,
      String currency,
      BillingPlanEntitlement entitlement) {
  }

  record BillingPlanListResponse(List<BillingPlan> plans) {
  }

  // ─── Function implementations ─────────────────────────────────────────────

  /**
   * Returns available billing plans from the Billing service.
   * No auth required — the internal plans endpoint is public on the internal network.
   */
  public PlanListResponse getAvailablePlans() {
    try {
      final var response = billingClient.get()
          .uri("/api/v1/billing/internal/plans")
          .retrieve()
          .body(BillingPlanListResponse.class);

      if (response == null || response.plans() == null) {
        return new PlanListResponse(List.of());
      }

      final var plans = response.plans().stream()
          .map(p -> {
            final var ent = p.entitlement();
            final boolean advancedAnalytics = ent != null
                && ent.features() != null
                && Boolean.TRUE.equals(ent.features().get("advanced_analytics"));
            final boolean prioritySupport = ent != null
                && ent.features() != null
                && Boolean.TRUE.equals(ent.features().get("priority_support"));
            return new Plan(
                p.planCode(),
                p.displayName(),
                p.billingPeriod(),
                p.pricingModel(),
                p.priceMinor(),
                p.currency(),
                new PlanFeatures(
                    ent != null ? ent.maxUsers() : null,
                    ent != null ? ent.maxProjects() : null,
                    advancedAnalytics,
                    prioritySupport
                )
            );
          })
          .toList();

      return new PlanListResponse(plans);
    } catch (final RestClientException e) {
      log.warn("Failed to fetch available plans from Billing service: {}", e.getMessage());
      return new PlanListResponse(List.of());
    }
  }

  /**
   * Returns basic profile details of the currently authenticated user from JWT claims.
   * No network call required.
   *
   * @return current user profile or empty profile if unauthenticated
   */
  public UserProfile getCurrentUserProfile() {
    final var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof final Jwt jwt) {
      final var firstName = jwt.getClaimAsString(JwtClaimNames.FIRST_NAME);
      final var lastName = jwt.getClaimAsString(JwtClaimNames.LAST_NAME);
      final var planCode = jwt.getClaimAsString(JwtClaimNames.PLAN_CODE);
      final var fullName = buildDisplayName(firstName, lastName);
      return new UserProfile(firstName, lastName, fullName, planCode);
    }
    return new UserProfile(null, null, null, null);
  }

  private String buildDisplayName(final String firstName, final String lastName) {
    final var first = firstName != null ? firstName.strip() : "";
    final var last = lastName != null ? lastName.strip() : "";
    final var full = (first + " " + last).strip();
    return full.isEmpty() ? null : full;
  }
}
