package io.wisoft.ignoa_api.chat.repository;

import io.wisoft.ignoa_api.chat.entity.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    @Query("""
            SELECT cm
            FROM ChatMessage cm
            WHERE cm.chatRoom.id = :chatRoomId
                AND (:beforeMessageId IS NULL OR cm.id < :beforeMessageId)
            ORDER BY cm.id DESC
            """)
    Slice<ChatMessage> findMessages(@Param("chatRoomId") Long chatRoomId,
                                    @Param("beforeMessageId") Long beforeMessageId,
                                    Pageable pageable);
}
