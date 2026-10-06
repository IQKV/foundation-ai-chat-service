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

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Binds {@code iqkv.ai.*} from {@code application.yml}.
 *
 * <p>Controls prompt framing and token budgets for every chat request:
 * <ul>
 *   <li>{@code system-prompt} — injected as the system message on every call.
 *       Defines the assistant's role, allowed topics, and output style.</li>
 *   <li>{@code max-input-chars} — user message is truncated to this length before
 *       being sent to the LLM to prevent runaway context growth.</li>
 *   <li>{@code max-output-tokens} — maps to {@code ChatOptions.maxTokens()}, limits
 *       the completion length.</li>
 *   <li>{@code temperature} — controls response creativity (0.0 = deterministic,
 *       1.0 = creative). Use lower values for factual / support use cases.</li>
 * </ul>
 */
@Validated
@ConfigurationProperties(prefix = "iqkv.ai")
public record AiChatProperties(
    String systemPrompt,
    int maxInputChars,
    int maxOutputTokens,
    double temperature) {
}
