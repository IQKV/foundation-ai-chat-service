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

package com.iqkv.foundation.aichatservice.chat.dto;

import com.iqkv.foundation.aichatservice.chat.ChatMessage;
import com.iqkv.foundation.aichatservice.chat.ChatSession;

public final class ChatDtoMapper {

  private ChatDtoMapper() {
  }

  public static ChatDtos.SessionResponse toSessionResponse(final ChatSession session) {
    return new ChatDtos.SessionResponse(
        session.getId(),
        session.getUserId(),
        session.getTitle(),
        session.getModel(),
        session.getCreatedAt(),
        session.getUpdatedAt()
    );
  }

  public static ChatDtos.MessageResponse toMessageResponse(final ChatMessage message) {
    return new ChatDtos.MessageResponse(
        message.getId(),
        message.getSessionId(),
        message.getRole(),
        message.getContent(),
        message.getCreatedAt()
    );
  }
}
