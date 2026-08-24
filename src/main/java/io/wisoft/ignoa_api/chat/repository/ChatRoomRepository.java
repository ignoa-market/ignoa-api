package io.wisoft.ignoa_api.chat.repository;

import io.wisoft.ignoa_api.chat.entity.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    boolean existsByItemIdAndBuyerId(Long itemId, Long buyerId);

    @Query("""
            SELECT cr
            FROM ChatRoom cr
            JOIN FETCH cr.item
            JOIN FETCH cr.seller
            JOIN FETCH cr.buyer
            WHERE cr.seller.id = :userId
               OR cr.buyer.id = :userId
            ORDER BY cr.createdAt DESC
            """)
    List<ChatRoom> findAllByParticipantId(@Param("userId") Long userId);
}
