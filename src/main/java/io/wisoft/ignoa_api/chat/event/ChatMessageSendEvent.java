package io.wisoft.ignoa_api.chat.event;

import io.wisoft.ignoa_api.chat.dto.response.ChatMessageResponse;
import io.wisoft.ignoa_api.chat.entity.ChatRoom;

public record ChatMessageSendEvent(
        Long sellerId,
        Long buyerId,
        ChatMessageResponse chatMessage
) {

    public static ChatMessageSendEvent of(ChatRoom chatRoom, ChatMessageResponse chatMessage) {
        return new ChatMessageSendEvent(
                chatRoom.getSeller().getId(),
                chatRoom.getBuyer().getId(),
                chatMessage
        );
    }
}
