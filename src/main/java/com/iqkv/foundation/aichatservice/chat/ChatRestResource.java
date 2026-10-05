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

import jakarta.validation.Valid;
import java.util.UUID;

import com.iqkv.foundation.aichatservice.chat.dto.ChatDtos;
import com.iqkv.foundation.aichatservice.infrastructure.security.JwtClaimNames;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/aichat/chat")
@Tag(name = "Chat", description = "AI chat operations")
@SecurityRequirement(name = "bearerAuth")
public class ChatRestResource {

  private final ChatService chatService;

  public ChatRestResource(final ChatService chatService) {
    this.chatService = chatService;
  }

  @PostMapping
  @Operation(summary = "Send a message and receive an AI reply")
  public ResponseEntity<ChatDtos.ChatResponse> chat(
      @Valid @RequestBody final ChatDtos.SendMessageRequest request,
      @AuthenticationPrincipal final Jwt jwt) {
    final var userId = UUID.fromString((String) jwt.getClaim(JwtClaimNames.USER_ID));
    return ResponseEntity.ok(chatService.chat(userId, request));
  }

  @GetMapping("/sessions")
  @Operation(summary = "List chat sessions for the authenticated user")
  public ResponseEntity<ChatDtos.SessionListResponse> getSessions(
      @RequestParam(defaultValue = "20") final int limit,
      @RequestParam(defaultValue = "0") final int offset,
      @AuthenticationPrincipal final Jwt jwt) {
    final var userId = UUID.fromString((String) jwt.getClaim(JwtClaimNames.USER_ID));
    return ResponseEntity.ok(chatService.getSessions(userId, limit, offset));
  }

  @GetMapping("/sessions/{sessionId}/messages")
  @Operation(summary = "List messages in a chat session")
  public ResponseEntity<ChatDtos.MessageListResponse> getMessages(
      @PathVariable final UUID sessionId,
      @RequestParam(defaultValue = "50") final int limit,
      @RequestParam(defaultValue = "0") final int offset,
      @AuthenticationPrincipal final Jwt jwt) {
    final var userId = UUID.fromString((String) jwt.getClaim(JwtClaimNames.USER_ID));
    return ResponseEntity.ok(chatService.getMessages(sessionId, userId, limit, offset));
  }

  @DeleteMapping("/sessions/{sessionId}")
  @Operation(summary = "Delete a chat session")
  public ResponseEntity<Void> deleteSession(
      @PathVariable final UUID sessionId,
      @AuthenticationPrincipal final Jwt jwt) {
    final var userId = UUID.fromString((String) jwt.getClaim(JwtClaimNames.USER_ID));
    chatService.deleteSession(sessionId, userId);
    return ResponseEntity.noContent().build();
  }
}
