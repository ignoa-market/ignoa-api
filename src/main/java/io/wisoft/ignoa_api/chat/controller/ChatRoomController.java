package io.wisoft.ignoa_api.chat.controller;

import io.wisoft.ignoa_api.chat.dto.response.ChatRoomIdResponse;
import io.wisoft.ignoa_api.chat.dto.response.ChatRoomPreview;
import io.wisoft.ignoa_api.chat.service.ChatRoomService;
import io.wisoft.ignoa_api.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class ChatRoomController {

    private final ChatRoomService chatRoomService;

    @PostMapping("/items/{itemId}/chat-rooms")
    public ResponseEntity<ApiResponse<ChatRoomIdResponse>> openChatRoom(
            @PathVariable Long itemId,
            @AuthenticationPrincipal Long userId
    ) {
        ChatRoomIdResponse data = chatRoomService.openChatRoom(userId, itemId);
        ApiResponse<ChatRoomIdResponse> response = ApiResponse.of(data, "채팅방을 열었습니다.");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/chat-rooms")
    public ResponseEntity<ApiResponse<List<ChatRoomPreview>>> getChatRooms(
            @AuthenticationPrincipal Long userId
    ) {
        List<ChatRoomPreview> data = chatRoomService.getChatRooms(userId);
        ApiResponse<List<ChatRoomPreview>> response = ApiResponse.of(data, "채팅방 목록을 조회했습니다.");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/chat-rooms/{chatRoomId}")
    public ResponseEntity<ApiResponse<ChatRoomPreview>> getChatRoom(
            @PathVariable Long chatRoomId,
            @AuthenticationPrincipal Long userId
    ) {
        ChatRoomPreview data = chatRoomService.getChatRoom(chatRoomId, userId);
        ApiResponse<ChatRoomPreview> response = ApiResponse.of(data, "채팅방을 조회했습니다.");
        return ResponseEntity.ok(response);
    }
}
