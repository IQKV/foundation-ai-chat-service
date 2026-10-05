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
import java.util.UUID;

import com.iqkv.foundation.aichatservice.chat.dto.ChatDtoMapper;
import com.iqkv.foundation.aichatservice.chat.dto.ChatDtos;
import com.iqkv.foundation.aichatservice.shared.exception.ChatSessionNotFoundException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class ChatServiceImpl implements ChatService {

  private static final String DEFAULT_MODEL = "llama3.2";

  private final ChatSessionMapper chatSessionMapper;
  private final ChatMessageMapper chatMessageMapper;
  private final ChatClient chatClient;

  public ChatServiceImpl(final ChatSessionMapper chatSessionMapper,
                         final ChatMessageMapper chatMessageMapper,
                         final ChatClient chatClient) {
    this.chatSessionMapper = chatSessionMapper;
    this.chatMessageMapper = chatMessageMapper;
    this.chatClient = chatClient;
  }

  @Override
  public ChatDtos.ChatResponse chat(final UUID actorId, final ChatDtos.SendMessageRequest request) {
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

    final var userMessage = new ChatMessage();
    userMessage.setId(UUID.randomUUID());
    userMessage.setSessionId(session.getId());
    userMessage.setRole(MessageRole.USER);
    userMessage.setContent(request.content());
    userMessage.setCreatedAt(Instant.now());
    chatMessageMapper.insert(userMessage);

    final var reply = chatClient.prompt()
        .user(request.content())
        .call()
        .content();

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
          request.content().length() > 60 ? request.content().substring(0, 60) : request.content()
      );
    }
    chatSessionMapper.update(session);

    return new ChatDtos.ChatResponse(session.getId(), session.getTitle(), reply, session.getModel(), Instant.now());
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
}
