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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.iqkv.foundation.aichatservice.chat.dto.ChatDtoMapper;
import com.iqkv.foundation.aichatservice.chat.dto.ChatDtos;
import com.iqkv.foundation.aichatservice.infrastructure.config.AiChatProperties;
import com.iqkv.foundation.aichatservice.shared.exception.ChatSessionNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

@Service
public class ChatServiceImpl implements ChatService {

  private static final Logger log = LoggerFactory.getLogger(ChatServiceImpl.class);
  private static final String DEFAULT_MODEL = "llama3.1:8b";

  private final ChatSessionMapper chatSessionMapper;
  private final ChatMessageMapper chatMessageMapper;
  private final ChatClient chatClient;
  private final AiChatProperties aiProps;
  private final List<ToolCallback> platformFunctions;

  public ChatServiceImpl(final ChatSessionMapper chatSessionMapper,
                         final ChatMessageMapper chatMessageMapper,
                         final ChatClient chatClient,
                         final AiChatProperties aiProps,
                         final List<ToolCallback> platformFunctions) {
    this.chatSessionMapper = chatSessionMapper;
    this.chatMessageMapper = chatMessageMapper;
    this.chatClient = chatClient;
    this.aiProps = aiProps;
    this.platformFunctions = platformFunctions != null ? platformFunctions : List.of();
  }

  @Override
  public ChatDtos.ChatResponse chat(final UUID actorId, final String firstName,
                                    final String lastName, final String planCode,
                                    final ChatDtos.SendMessageRequest request) {
    final ChatSession session;

    if (request.sessionId() == null) {
      session = new ChatSession();
      session.setId(UUID.randomUUID());
      session.setUserId(actorId);
      session.setModel(request.model() != null ? request.model() : DEFAULT_MODEL);
      session.setTitle(null);
      session.setCreatedAt(Instant.now());
      session.setUpdatedAt(Instant.now());
      chatSessionMapper.insert(session);
    } else {
      session = chatSessionMapper.findById(request.sessionId())
          .orElseThrow(() -> new ChatSessionNotFoundException(request.sessionId()));
    }

    // Guard: truncate user input to configured max to avoid context blowout
    final String userContent = request.content().length() > aiProps.maxInputChars()
        ? request.content().substring(0, aiProps.maxInputChars())
        : request.content();

    final var userMessage = new ChatMessage();
    userMessage.setId(UUID.randomUUID());
    userMessage.setSessionId(session.getId());
    userMessage.setRole(MessageRole.USER);
    userMessage.setContent(userContent);
    userMessage.setCreatedAt(Instant.now());
    chatMessageMapper.insert(userMessage);

    // Build enriched system prompt with user context from JWT
    final String systemPrompt = buildSystemPrompt(firstName, lastName, planCode);

    // Build per-request options: model, temperature, output token limit
    var promptSpec = chatClient.prompt()
        .system(systemPrompt)
        .user(userContent)
        .options(OllamaChatOptions.builder()
            .model(session.getModel())
            .temperature(aiProps.temperature())
            .numPredict(aiProps.maxOutputTokens()));

    if (!platformFunctions.isEmpty()) {
      promptSpec = promptSpec.tools(platformFunctions);
    }

    final var reply = promptSpec
        .call()
        .content();

    if (reply == null) {
      throw new NonTransientAiException("LLM returned empty response");
    }

    final var assistantMessage = new ChatMessage();
    assistantMessage.setId(UUID.randomUUID());
    assistantMessage.setSessionId(session.getId());
    assistantMessage.setRole(MessageRole.ASSISTANT);
    assistantMessage.setContent(reply);
    assistantMessage.setCreatedAt(Instant.now());
    chatMessageMapper.insert(assistantMessage);

    session.setUpdatedAt(Instant.now());
    if (session.getTitle() == null) {
      session.setTitle(
          userContent.length() > 60 ? userContent.substring(0, 60) : userContent
      );
    }
    chatSessionMapper.update(session);

    return new ChatDtos.ChatResponse(session.getId(), session.getTitle(), reply, session.getModel(), Instant.now());
  }

  /**
   * Prepends user-specific context to the configured system prompt.
   * Gives the model the user's name and current plan so it can personalise
   * responses without the user having to repeat themselves.
   */
  private String buildSystemPrompt(final String firstName, final String lastName,
                                   final String planCode) {
    final var sb = new StringBuilder();

    // User identity line
    final String displayName = buildDisplayName(firstName, lastName);
    if (!displayName.isEmpty()) {
      sb.append("The user you are talking to is ").append(displayName).append(".\n");
    }

    // Current plan line
    if (planCode != null && !planCode.isBlank()) {
      sb.append("Their current billing plan is: ").append(planCode).append(".\n");
    } else {
      sb.append("They do not have an active subscription.\n");
    }

    sb.append("You can call getAvailablePlans() to look up available plans and their features.\n");
    sb.append("\n");
    sb.append(aiProps.systemPrompt());
    return sb.toString();
  }

  private String buildDisplayName(final String firstName, final String lastName) {
    if (firstName != null && lastName != null) {
      return firstName.strip() + " " + lastName.strip();
    }
    if (firstName != null) {
      return firstName.strip();
    }
    if (lastName != null) {
      return lastName.strip();
    }
    return "";
  }

  @Override
  public ChatDtos.SessionListResponse getSessions(final UUID userId, final int limit, final int offset) {
    final var sessions = chatSessionMapper.findByUserId(userId, limit, offset);
    final var total = chatSessionMapper.countByUserId(userId);
    final var items = sessions.stream().map(ChatDtoMapper::toSessionResponse).toList();
    return new ChatDtos.SessionListResponse(items, total);
  }

  @Override
  public ChatDtos.MessageListResponse getMessages(final UUID sessionId, final UUID actorId,
                                                   final int limit, final int offset) {
    final var session = chatSessionMapper.findById(sessionId)
        .orElseThrow(() -> new ChatSessionNotFoundException(sessionId));

    if (!session.getUserId().equals(actorId)) {
      throw new ChatSessionNotFoundException(sessionId);
    }

    final var messages = chatMessageMapper.findBySessionId(sessionId, limit, offset);
    final var total = chatMessageMapper.countBySessionId(sessionId);
    return new ChatDtos.MessageListResponse(
        messages.stream().map(ChatDtoMapper::toMessageResponse).toList(),
        total
    );
  }

  @Override
  public void deleteSession(final UUID sessionId, final UUID actorId) {
    final var session = chatSessionMapper.findById(sessionId)
        .orElseThrow(() -> new ChatSessionNotFoundException(sessionId));

    if (!session.getUserId().equals(actorId)) {
      throw new ChatSessionNotFoundException(sessionId);
    }

    chatSessionMapper.delete(sessionId);
  }

  @Override
  public ChatDtos.SessionListResponse getAllSessions(final int limit, final int offset) {
    final var sessions = chatSessionMapper.findAll(limit, offset);
    final var total = chatSessionMapper.countAll();
    return new ChatDtos.SessionListResponse(
        sessions.stream().map(ChatDtoMapper::toSessionResponse).toList(),
        total
    );
  }

  @Override
  public ChatDtos.MessageListResponse getAdminMessages(final UUID sessionId, final int limit, final int offset) {
    // Admin access — no ownership check; verify session exists for a clean 404
    chatSessionMapper.findById(sessionId)
        .orElseThrow(() -> new ChatSessionNotFoundException(sessionId));
    final var messages = chatMessageMapper.findBySessionId(sessionId, limit, offset);
    final var total = chatMessageMapper.countBySessionId(sessionId);
    return new ChatDtos.MessageListResponse(
        messages.stream().map(ChatDtoMapper::toMessageResponse).toList(),
        total
    );
  }
}
