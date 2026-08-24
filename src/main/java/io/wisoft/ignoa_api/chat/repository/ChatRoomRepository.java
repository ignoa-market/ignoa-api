package io.wisoft.ignoa_api.chat.repository;

import io.wisoft.ignoa_api.chat.entity.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    boolean existsByItemIdAndBuyerId(Long itemId, Long buyerId);
}
