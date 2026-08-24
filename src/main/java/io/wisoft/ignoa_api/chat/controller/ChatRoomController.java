package io.wisoft.ignoa_api.chat.controller;

import io.wisoft.ignoa_api.chat.dto.response.ChatRoomPreview;
import io.wisoft.ignoa_api.chat.service.ChatRoomService;
import io.wisoft.ignoa_api.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/chat-rooms")
public class ChatRoomController {

    private final ChatRoomService chatRoomService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ChatRoomPreview>>> getChatRooms(
            @AuthenticationPrincipal Long userId
    ) {
        List<ChatRoomPreview> data = chatRoomService.getChatRooms(userId);
        ApiResponse<List<ChatRoomPreview>> response = ApiResponse.of(data, "채팅방 목록을 조회했습니다.");
        return ResponseEntity.ok(response);
    }
}
