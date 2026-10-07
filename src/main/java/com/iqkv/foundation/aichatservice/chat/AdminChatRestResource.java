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

import java.util.UUID;

import com.iqkv.foundation.aichatservice.chat.dto.ChatDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/aichat/admin")
@Tag(name = "Admin Chat", description = "Admin operations for chat")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
public class AdminChatRestResource {

  private final ChatService chatService;

  public AdminChatRestResource(final ChatService chatService) {
    this.chatService = chatService;
  }

  @GetMapping("/sessions")
  @Operation(summary = "List all chat sessions (platform admin)")
  public ResponseEntity<ChatDtos.SessionListResponse> getAllSessions(
      @RequestParam(defaultValue = "100") final int limit,
      @RequestParam(defaultValue = "0") final int offset) {
    return ResponseEntity.ok(chatService.getAllSessions(limit, offset));
  }

  @GetMapping("/sessions/{sessionId}/messages")
  @Operation(summary = "Get messages for any session (platform admin)",
             description = "Returns all messages for the given session regardless of owner. "
                           + "Returns 404 if the session does not exist.")
  public ResponseEntity<ChatDtos.MessageListResponse> getMessages(
      @Parameter(description = "Session UUID") @PathVariable final UUID sessionId,
      @RequestParam(defaultValue = "200") final int limit,
      @RequestParam(defaultValue = "0") final int offset) {
    return ResponseEntity.ok(chatService.getAdminMessages(sessionId, limit, offset));
  }
}
