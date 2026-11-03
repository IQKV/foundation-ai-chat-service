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

package com.iqkv.foundation.aichatservice.shared.exception;

/**
 * Thrown when a chat message is rejected by the prompt-injection guard.
 *
 * <p>Caught by {@code GlobalExceptionHandler}, which maps it to HTTP 400 with a
 * generic RFC-7807 problem detail body. The matched rule is carried for logging
 * only and is deliberately never echoed back to the caller.
 */
public class PromptInjectionException extends RuntimeException {

  private final String rule;

  public PromptInjectionException(final String rule) {
    super("Chat message rejected by prompt-injection guard: rule=" + rule);
    this.rule = rule;
  }

  public String getRule() {
    return rule;
  }
}
