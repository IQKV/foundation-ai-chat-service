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

package com.iqkv.foundation.aichatservice.infrastructure.messaging;

import java.util.Map;

import com.iqkv.foundation.aichatservice.infrastructure.config.RabbitMQConfig;
import com.iqkv.foundation.tenancy.TenantLiquibaseRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Provisions this service's schema for a newly created tenant.
 *
 * <p>Listens on {@code iqkv.aichat.tenant.provisioning} for {@code tenant.created} events
 * published by the IAM service and runs Liquibase tenant migrations against this service's database.
 * Schema provisioning is independent of IAM — this service does not update tenant status or
 * publish outcome events; those are exclusively IAM's responsibility.
 *
 * <p>When scaffolding a new service from this template, replace every occurrence of
 * {@code aichat} with the actual service name.
 */
@Component
@ConditionalOnProperty(name = "iqkv.messaging.rabbitmq.enabled", havingValue = "true")
@ConditionalOnBean(TenantLiquibaseRunner.class)
public class TenantProvisioningConsumer {

  private static final Logger log = LoggerFactory.getLogger(TenantProvisioningConsumer.class);

  private final TenantLiquibaseRunner tenantLiquibaseRunner;

  public TenantProvisioningConsumer(final TenantLiquibaseRunner tenantLiquibaseRunner) {
    this.tenantLiquibaseRunner = tenantLiquibaseRunner;
  }

  @RabbitListener(queues = RabbitMQConfig.TENANT_PROVISIONING_QUEUE)
  public void handleTenantProvisioning(final Map<String, Object> event) {
    final String tenantKey = (String) event.get("tenantKey");
    if (tenantKey == null || tenantKey.isBlank()) {
      log.warn("Received tenant provisioning event with missing tenantKey, skipping");
      return;
    }
    log.info("Received tenant provisioning event: tenantKey={}", tenantKey);

    try {
      tenantLiquibaseRunner.runMigrationsForTenant(tenantKey);
      log.info("Schema provisioning succeeded: tenantKey={}", tenantKey);
    } catch (final Exception e) {
      log.error("Schema provisioning failed: tenantKey={}", tenantKey, e);
      // Rethrow so the message is nacked and routed to the DLQ for redelivery / inspection.
      throw new RuntimeException("Schema provisioning failed for tenantKey=" + tenantKey, e);
    }
  }
}
