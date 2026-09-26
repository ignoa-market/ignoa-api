package io.wisoft.ignoa_api.chat.controller;

import io.wisoft.ignoa_api.chat.dto.request.ChatMessageSendRequest;
import io.wisoft.ignoa_api.chat.dto.response.ChatMessageResponse;
import io.wisoft.ignoa_api.chat.service.ChatMessageService;
import io.wisoft.ignoa_api.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/chat-rooms")
public class ChatMessageController {

    private final ChatMessageService chatMessageService;

    @PostMapping("/{chatRoomId}/messages")
    public ResponseEntity<ApiResponse<ChatMessageResponse>> sendMessage(
            @PathVariable Long chatRoomId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody ChatMessageSendRequest request
    ) {
        ChatMessageResponse data = chatMessageService.sendMessage(chatRoomId, userId, request.content());
        ApiResponse<ChatMessageResponse> response = ApiResponse.of(data, "메시지를 전송했습니다.");
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
