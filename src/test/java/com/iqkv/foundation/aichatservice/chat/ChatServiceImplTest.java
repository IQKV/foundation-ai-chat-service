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

package com.iqkv.foundation.aichatservice.chat;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import java.util.UUID;

import com.iqkv.foundation.aichatservice.chat.dto.ChatDtos;
import com.iqkv.foundation.aichatservice.infrastructure.config.AiChatProperties;
import com.iqkv.foundation.aichatservice.shared.exception.EmptyMessageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

@ExtendWith(MockitoExtension.class)
class ChatServiceImplTest {

  @Mock
  private ChatSessionMapper chatSessionMapper;
  @Mock
  private ChatMessageMapper chatMessageMapper;
  @Mock
  private ChatClient chatClient;

  private ChatServiceImpl chatService;

  @BeforeEach
  void setUp() {
    final var props = new AiChatProperties(
        "system prompt", 1000, 100, 0.2, "llama3", new AiChatProperties.Guard(true));
    chatService = new ChatServiceImpl(chatSessionMapper, chatMessageMapper, chatClient, props, List.of());
  }

  @Test
  @DisplayName("Should reject blank content before persisting or calling the model")
  void shouldRejectBlankContent() {
    // Arrange
    final var request = new ChatDtos.SendMessageRequest(null, "   ", null);

    // Act & Assert
    assertThatThrownBy(() -> chatService.chat(UUID.randomUUID(), "Ada", "Lovelace", "PRO", request))
        .isInstanceOf(EmptyMessageException.class);
    verifyNoInteractions(chatSessionMapper, chatMessageMapper, chatClient);
  }

  @Test
  @DisplayName("Should reject content made only of invisible characters")
  void shouldRejectInvisibleOnlyContent() {
    // Arrange: two zero-width spaces carry no visible text
    final var request = new ChatDtos.SendMessageRequest(null, "\u200B\u200B", null);

    // Act & Assert
    assertThatThrownBy(() -> chatService.chat(UUID.randomUUID(), "Ada", "Lovelace", "PRO", request))
        .isInstanceOf(EmptyMessageException.class);
    verifyNoInteractions(chatSessionMapper, chatMessageMapper, chatClient);
  }
}
