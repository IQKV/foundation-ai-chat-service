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

package com.iqkv.foundation.aichatservice.ping;

import java.time.Instant;

import com.iqkv.foundation.aichatservice.infrastructure.security.JwtClaimNames;
import com.iqkv.foundation.tenancy.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exemplary REST resource demonstrating the three security tiers used by all foundation services.
 *
 * <h2>Endpoint overview</h2>
 * <ol>
 *   <li><b>Public ping</b> {@code GET /api/v1/aichat/public/ping} — no authentication, no tenant
 *       context required. A lightweight reachability probe for infrastructure tooling and smoke tests.
 *       Returns a {@link PingDtos.PublicPongResponse}.</li>
 *   <li><b>Tenant-scoped ping</b> {@code GET /api/v1/aichat/ping} — requires a valid JWT and a
 *       resolved tenant context (via {@code X-Tenant-ID} header or JWT {@code tenant_id} claim).
 *       Echoes the resolved tenant key back so clients can verify tenant resolution.
 *       Returns a {@link PingDtos.TenantPongResponse}.</li>
 *   <li><b>Admin ping</b> {@code GET /api/v1/aichat/admin/ping} — requires {@code PLATFORM_ADMIN}
 *       authority. No tenant context is expected or set — admin endpoints are cross-tenant by design.
 *       Echoes the caller's {@code user_id} claim so operators can verify token decoding and authority
 *       assignment. Returns a {@link PingDtos.AdminPongResponse}.</li>
 * </ol>
 *
 * <h2>What this file demonstrates</h2>
 * <ul>
 *   <li>Class-level {@code @RequestMapping} combined with method-level path segments</li>
 *   <li>{@code @SecurityRequirement} applied per-endpoint (omitted on the public one)</li>
 *   <li>{@code @PreAuthorize} on an admin method and its absence on tenant/public methods
 *       where HTTP security rules in {@code SecurityConfig} are sufficient</li>
 *   <li>Reading tenant context from {@link TenantContext} (set by {@code TenantExtractionFilter})</li>
 *   <li>Reading JWT claims via {@code @AuthenticationPrincipal Jwt}</li>
 *   <li>Full OpenAPI annotation set: {@code @Tag}, {@code @Operation}, {@code @ApiResponses},
 *       {@code @Parameter} (header), {@code @Content} with {@code @Schema}</li>
 *   <li>Immutable record DTOs grouped in a sibling {@link PingDtos} class</li>
 * </ul>
 *
 * <p>When scaffolding a new service, replace {@code aichat} with the actual service slug
 * in the {@code @RequestMapping} paths and delete (or keep and expand) this controller.
 */
@RestController
@RequestMapping("/api/v1/aichat")
@Tag(name = "Ping", description = "Reachability probes covering the three security tiers — "
                                  + "public, tenant-authenticated, and platform-admin")
public class PingRestResource {

  // ── Public ping ──────────────────────────────────────────────────────────────
  // No @SecurityRequirement — SecurityConfig permits this path without a token.
  // TenantExtractionFilter is bypassed via the /public/ skip rule.

  @GetMapping("/public/ping")
  @Operation(
      summary = "Public ping",
      description = "Unauthenticated reachability probe. No JWT and no tenant header required. "
                    + "Suitable for infrastructure health checks and smoke tests that run before auth is available.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Service reachable",
                   content = @Content(schema = @Schema(implementation = PingDtos.PublicPongResponse.class)))
  })
  public ResponseEntity<PingDtos.PublicPongResponse> publicPing() {
    return ResponseEntity.ok(new PingDtos.PublicPongResponse("pong", Instant.now()));
  }

  // ── Tenant-scoped ping ────────────────────────────────────────────────────────
  // TenantExtractionFilter resolves the tenant from X-Tenant-ID or JWT tenant_id
  // and sets TenantContext before this method is reached.
  // No @PreAuthorize needed: SecurityConfig already requires authentication for
  // all paths not explicitly listed as permitAll.

  @GetMapping("/ping")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(
      summary = "Tenant ping",
      description = "Authenticated, tenant-scoped reachability probe. "
                    + "Requires a valid JWT and a resolvable tenant identifier "
                    + "(via the X-Tenant-ID header or the JWT tenant_id claim). "
                    + "Echoes the resolved tenant key so clients can verify that tenant resolution is working.")
  @Parameter(name = "X-Tenant-ID", in = ParameterIn.HEADER, required = false,
             description = "8-char alphanumeric tenant key (e.g. xk7f2b9a). "
                           + "Can be omitted when the JWT carries a tenant_id claim.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Service reachable; tenant context resolved",
                   content = @Content(schema = @Schema(implementation = PingDtos.TenantPongResponse.class))),
      @ApiResponse(responseCode = "400", description = "Tenant ID could not be resolved", content = @Content),
      @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content)
  })
  public ResponseEntity<PingDtos.TenantPongResponse> tenantPing() {
    final String tenantKey = TenantContext.getCurrentTenant();
    return ResponseEntity.ok(new PingDtos.TenantPongResponse("pong", tenantKey, Instant.now()));
  }

  // ── Admin ping ────────────────────────────────────────────────────────────────
  // Path is under /admin/ — SecurityConfig enforces PLATFORM_ADMIN, and
  // TenantExtractionFilter skips it entirely (no tenant context is set or expected).

  @GetMapping("/admin/ping")
  @PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(
      summary = "Admin ping",
      description = "Platform-admin reachability probe. "
                    + "Requires a JWT carrying the PLATFORM_ADMIN authority. "
                    + "No X-Tenant-ID header is required or used — admin endpoints are cross-tenant. "
                    + "Echoes the caller's user_id claim so operators can verify token decoding and authority assignment.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Service reachable; PLATFORM_ADMIN authority confirmed",
                   content = @Content(schema = @Schema(implementation = PingDtos.AdminPongResponse.class))),
      @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content),
      @ApiResponse(responseCode = "403", description = "Access denied — PLATFORM_ADMIN authority required", content = @Content)
  })
  public ResponseEntity<PingDtos.AdminPongResponse> adminPing(
      @AuthenticationPrincipal final Jwt jwt) {
    final String actorId = jwt.getClaimAsString(JwtClaimNames.USER_ID);
    return ResponseEntity.ok(new PingDtos.AdminPongResponse("pong", actorId, Instant.now()));
  }
}
