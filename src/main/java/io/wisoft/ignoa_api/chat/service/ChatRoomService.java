package io.wisoft.ignoa_api.chat.service;

import io.wisoft.ignoa_api.chat.dto.response.ChatRoomPreview;
import io.wisoft.ignoa_api.chat.entity.ChatRoom;
import io.wisoft.ignoa_api.chat.repository.ChatRoomRepository;
import io.wisoft.ignoa_api.global.infra.storage.MediaUrlResolver;
import io.wisoft.ignoa_api.item.entity.Item;
import io.wisoft.ignoa_api.item.service.ItemReader;
import io.wisoft.ignoa_api.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatRoomService {

    private final ChatRoomRepository chatRoomRepository;
    private final ItemReader itemReader;
    private final MediaUrlResolver mediaUrlResolver;

    @Transactional
    public void createChat(Long itemId) {
        Item item = itemReader.getById(itemId);
        User buyer = item.getHighestBidder();

        if (buyer == null) {
            return;
        }

        if (chatRoomRepository.existsByItemIdAndBuyerId(item.getId(), buyer.getId())) {
            return;
        }

        ChatRoom chatRoom = ChatRoom.create(item, item.getSeller(), buyer);
        chatRoomRepository.save(chatRoom);
    }

    public List<ChatRoomPreview> getChatRooms(Long userId) {
        return chatRoomRepository.findAllByParticipantId(userId).stream()
                .map(chatRoom -> toPreview(chatRoom, userId))
                .toList();
    }

    private ChatRoomPreview toPreview(ChatRoom chatRoom, Long userId) {
        boolean isSeller = chatRoom.getSeller().getId().equals(userId);
        User partner = isSeller ? chatRoom.getBuyer() : chatRoom.getSeller();

        String partnerProfileImageUrl = mediaUrlResolver.toUrl(
                partner.getProfileImageReference(),
                partner.getProfileImageSource()
        );

        return ChatRoomPreview.from(
                chatRoom,
                partner,
                partnerProfileImageUrl,
                isSeller
        );
    }
}
