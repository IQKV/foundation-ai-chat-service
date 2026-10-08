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

package com.iqkv.foundation.aichatservice.infrastructure.config;

import com.iqkv.foundation.aichatservice.infrastructure.context.PlatformContextService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AiChatProperties.class)
public class AiChatConfig {

  @Bean
  public ChatClient chatClient(final ChatClient.Builder builder) {
    return builder.build();
  }

  /**
   * Exposes available billing plans to the LLM.
   * Model calls this when the user asks about plans, pricing, or upgrades.
   * No auth needed — the Billing internal plans endpoint is public on the internal network.
   */
  @Bean
  public ToolCallback getAvailablePlansFunction(final PlatformContextService platformContextService) {
    return FunctionToolCallback.builder("getAvailablePlans", platformContextService::getAvailablePlans)
        .description(
            "Retrieves the current list of available billing plans with their features, pricing, and limits. "
            + "Use this to answer questions about plan options, pricing, upgrades, or feature comparisons. "
            + "Present the information naturally in conversation without mentioning this is a function call.")
        .build();
  }

  /**
   * Exposes current user profile information to the LLM.
   * Model calls this when the user asks about their identity, name, or account profile.
   */
  @Bean
  public ToolCallback getCurrentUserProfileFunction(final PlatformContextService platformContextService) {
    return FunctionToolCallback.builder("getCurrentUserProfile", platformContextService::getCurrentUserProfile)
        .description(
            "Retrieves detailed profile information for the current user, including their full name and active billing plan. "
            + "Use this when you need complete profile details to personalize your response or answer account-related questions. "
            + "Incorporate the information naturally - never reveal you're calling a function or accessing a system.")
        .build();
  }
}
