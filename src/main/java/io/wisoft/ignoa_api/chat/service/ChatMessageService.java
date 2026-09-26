package io.wisoft.ignoa_api.chat.service;

import io.wisoft.ignoa_api.chat.dto.request.ChatMessagePageRequest;
import io.wisoft.ignoa_api.chat.dto.response.ChatMessageResponse;
import io.wisoft.ignoa_api.chat.entity.ChatMessage;
import io.wisoft.ignoa_api.chat.entity.ChatRoom;
import io.wisoft.ignoa_api.chat.event.ChatMessageSendEvent;
import io.wisoft.ignoa_api.chat.repository.ChatMessageRepository;
import io.wisoft.ignoa_api.chat.repository.ChatRoomRepository;
import io.wisoft.ignoa_api.global.common.SliceResponse;
import io.wisoft.ignoa_api.global.exception.BusinessException;
import io.wisoft.ignoa_api.global.exception.ErrorCode;
import io.wisoft.ignoa_api.user.entity.User;
import io.wisoft.ignoa_api.user.service.UserQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatMessageService {

    private final UserQueryService userQueryService;

    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomRepository chatRoomRepository;

    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ChatMessageResponse sendMessage(Long chatRoomId, Long userId, String content) {
        ChatRoom chatRoom = chatRoomRepository.findByIdWithParticipants(chatRoomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        if (!chatRoom.isParticipant(userId)) {
            throw new BusinessException(ErrorCode.CHAT_ROOM_ACCESS_DENIED);
        }

        User sender = userQueryService.findById(userId);
        ChatMessage chatMessage = chatMessageRepository.save(ChatMessage.create(chatRoom, sender, content));

        chatRoomRepository.updateLastMessageIfNewer(chatRoom.getId(), chatMessage, chatMessage.getId());

        ChatMessageResponse response = ChatMessageResponse.from(chatMessage);
        eventPublisher.publishEvent(ChatMessageSendEvent.of(chatRoom, response));

        return response;
    }

    public SliceResponse<ChatMessageResponse> getMessages(Long chatRoomId, Long userId, ChatMessagePageRequest request) {
        ChatRoom chatRoom = chatRoomRepository.findByIdWithParticipants(chatRoomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_ROOM_NOT_FOUND));

        if (!chatRoom.isParticipant(userId)) {
            throw new BusinessException(ErrorCode.CHAT_ROOM_ACCESS_DENIED);
        }

        Slice<ChatMessage> messages = chatMessageRepository.findMessages(
                chatRoomId,
                request.beforeMessageId(),
                PageRequest.of(0, request.size())
        );

        List<ChatMessageResponse> responses = messages.getContent().stream()
                .map(ChatMessageResponse::from)
                .toList();

        return SliceResponse.of(responses, messages.hasNext());
    }
}
