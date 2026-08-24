package io.wisoft.ignoa_api.chat.service;

import io.wisoft.ignoa_api.chat.entity.ChatRoom;
import io.wisoft.ignoa_api.chat.repository.ChatRoomRepository;
import io.wisoft.ignoa_api.item.entity.Item;
import io.wisoft.ignoa_api.item.service.ItemReader;
import io.wisoft.ignoa_api.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatRoomService {

    private final ChatRoomRepository chatRoomRepository;
    private final ItemReader itemReader;

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
}
