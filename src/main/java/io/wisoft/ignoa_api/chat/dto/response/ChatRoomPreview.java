package io.wisoft.ignoa_api.chat.dto.response;

import io.wisoft.ignoa_api.chat.entity.ChatRoom;
import io.wisoft.ignoa_api.user.entity.User;

import java.time.LocalDateTime;

public record ChatRoomPreview(
        Long chatRoomId,
        Long itemId,
        String itemTitle,
        Long partnerId,
        String partnerNickname,
        String partnerProfileImageUrl,
        String role,
        LocalDateTime createdAt
) {

    public static ChatRoomPreview from(
            ChatRoom chatRoom,
            User partner,
            String partnerProfileImageUrl,
            boolean isSeller
    ) {
        return new ChatRoomPreview(
                chatRoom.getId(),
                chatRoom.getItem().getId(),
                chatRoom.getItem().getTitle(),
                partner.getId(),
                partner.getNickname(),
                partnerProfileImageUrl,
                isSeller ? "SELLER" : "BUYER",
                chatRoom.getCreatedAt()
        );
    }
}
