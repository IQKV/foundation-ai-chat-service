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

public interface ChatService {

  ChatDtos.ChatResponse chat(UUID actorId, ChatDtos.SendMessageRequest request);

  ChatDtos.SessionListResponse getSessions(UUID userId, int limit, int offset);

  ChatDtos.MessageListResponse getMessages(UUID sessionId, UUID actorId, int limit, int offset);

  void deleteSession(UUID sessionId, UUID actorId);

  ChatDtos.SessionListResponse getAllSessions(int limit, int offset);
}
