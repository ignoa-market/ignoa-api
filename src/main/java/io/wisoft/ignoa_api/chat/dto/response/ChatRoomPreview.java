package io.wisoft.ignoa_api.chat.dto.response;

import io.wisoft.ignoa_api.chat.entity.ChatMessage;
import io.wisoft.ignoa_api.chat.entity.ChatRoom;
import io.wisoft.ignoa_api.item.entity.Item;
import io.wisoft.ignoa_api.user.entity.User;

import java.time.LocalDateTime;

public record ChatRoomPreview(
        Long chatRoomId,
        Long itemId,
        String itemTitle,
        String itemImageUrl,
        Long partnerId,
        String partnerNickname,
        String partnerProfileImageUrl,
        String role,
        String lastMessage,
        LocalDateTime lastMessageAt,
        LocalDateTime createdAt
) {

    public static ChatRoomPreview from(
            ChatRoom chatRoom,
            String itemImageUrl,
            User partner,
            String partnerProfileImageUrl,
            boolean isSeller
    ) {
        Item item = chatRoom.getItem();
        ChatMessage lastMessage = chatRoom.getLastMessage();

        return new ChatRoomPreview(
                chatRoom.getId(),
                item.getId(),
                item.getTitle(),
                itemImageUrl,
                partner.getId(),
                partner.getNickname(),
                partnerProfileImageUrl,
                isSeller ? "SELLER" : "BUYER",
                lastMessage == null ? null : lastMessage.getContent(),
                lastMessage == null ? null : lastMessage.getCreatedAt(),
                chatRoom.getCreatedAt()
        );
    }
}
