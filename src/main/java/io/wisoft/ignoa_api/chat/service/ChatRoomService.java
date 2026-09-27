package io.wisoft.ignoa_api.chat.service;

import io.wisoft.ignoa_api.chat.dto.response.ChatRoomIdResponse;
import io.wisoft.ignoa_api.chat.dto.response.ChatRoomPreview;
import io.wisoft.ignoa_api.chat.entity.ChatRoom;
import io.wisoft.ignoa_api.chat.repository.ChatRoomRepository;
import io.wisoft.ignoa_api.global.exception.BusinessException;
import io.wisoft.ignoa_api.global.exception.ErrorCode;
import io.wisoft.ignoa_api.global.infra.storage.MediaUrlResolver;
import io.wisoft.ignoa_api.item.entity.Item;
import io.wisoft.ignoa_api.item.service.ItemMediaService;
import io.wisoft.ignoa_api.item.service.ItemReader;
import io.wisoft.ignoa_api.user.entity.User;
import io.wisoft.ignoa_api.user.service.UserQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatRoomService {

    private final ChatRoomRepository chatRoomRepository;
    private final ItemReader itemReader;
    private final MediaUrlResolver mediaUrlResolver;
    private final UserQueryService userQueryService;
    private final ItemMediaService itemMediaService;

    @Transactional
    public ChatRoomIdResponse openChatRoom(Long buyerId, Long itemId) {
        Item item = itemReader.getById(itemId);

        if (item.isSeller(buyerId)) {
            throw new BusinessException(ErrorCode.SELF_CHAT_NOT_ALLOWED);
        }

        ChatRoom chatRoom = chatRoomRepository.findByItemIdAndBuyerId(itemId, buyerId)
                .orElseGet(() -> {
                    if (!item.isActive()) {
                        throw new BusinessException(ErrorCode.AUCTION_ALREADY_CLOSED);
                    }

                    User buyer = userQueryService.findById(buyerId);
                    return chatRoomRepository.save(
                            ChatRoom.create(item, item.getSeller(), buyer)
                    );
                });

        return new ChatRoomIdResponse(chatRoom.getId());
    }

    @Transactional
    public void createChatRoom(Long itemId) {
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
        List<ChatRoom> chatRooms = chatRoomRepository.findAllByParticipantId(userId);

        List<Long> itemIds = chatRooms.stream()
                .map(chatRoom -> chatRoom.getItem().getId())
                .toList();

        Map<Long, String> itemImageUrls = itemMediaService.getFirstMediaUrl(itemIds);

        return chatRooms.stream()
                .map(chatRoom -> toPreview(
                                chatRoom,
                                userId,
                                itemImageUrls.get(chatRoom.getItem().getId())
                        )
                ).toList();
    }

    public ChatRoomPreview getChatRoom(Long chatRoomId, Long userId) {
        ChatRoom chatRoom = chatRoomRepository.findByIdWithParticipants(chatRoomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        if (!chatRoom.isParticipant(userId)) {
            throw new BusinessException(ErrorCode.CHAT_ROOM_ACCESS_DENIED);
        }

        Long itemId = chatRoom.getItem().getId();
        String itemImageUrl = itemMediaService.getFirstMediaUrl(List.of(itemId)).get(itemId);

        return toPreview(chatRoom, userId, itemImageUrl);
    }

    private ChatRoomPreview toPreview(ChatRoom chatRoom, Long userId, String itemImageUrl) {
        boolean isSeller = chatRoom.isSeller(userId);
        User partner = chatRoom.getPartner(userId);

        String partnerProfileImageUrl = mediaUrlResolver.toUrl(
                partner.getProfileImageReference(),
                partner.getProfileImageSource()
        );

        return ChatRoomPreview.from(
                chatRoom,
                itemImageUrl,
                partner,
                partnerProfileImageUrl,
                isSeller
        );
    }
}
