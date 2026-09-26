package io.wisoft.ignoa_api.chat.repository;

import io.wisoft.ignoa_api.chat.entity.ChatMessage;
import io.wisoft.ignoa_api.chat.entity.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    boolean existsByItemIdAndBuyerId(Long itemId, Long buyerId);

    Optional<ChatRoom> findByItemIdAndBuyerId(Long itemId, Long buyerId);

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

    @Query("""
            SELECT cr
            FROM ChatRoom cr
            JOIN FETCH cr.item
            JOIN FETCH cr.seller
            JOIN FETCH cr.buyer
            WHERE cr.id = :chatRoomId
            """)
    Optional<ChatRoom> findByIdWithParticipants(@Param("chatRoomId") Long chatRoomId);

    // 마지막 메시지 갱신 조건부 UPDATE
    @Modifying
    @Query("""
            UPDATE ChatRoom cr
            SET cr.lastMessage = :chatMessage
            WHERE cr.id = :chatRoomId
                AND (cr.lastMessage IS NULL OR cr.lastMessage.id < :chatMessageId)
            """)
    int updateLastMessageIfNewer(@Param("chatRoomId") Long chatRoomId,
                                 @Param("chatMessage") ChatMessage chatMessage,
                                 @Param("chatMessageId") Long chatMessageId);
}
