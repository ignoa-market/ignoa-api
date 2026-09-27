package io.wisoft.ignoa_api.chat.listener;

import io.wisoft.ignoa_api.chat.event.ChatMessageSendEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ChatMessageEventListener {

    private static final String CHAT_QUEUE = "/queue/chat";

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onChatMessageSent(ChatMessageSendEvent event) {
        messagingTemplate.convertAndSendToUser(
                String.valueOf(event.sellerId()), CHAT_QUEUE, event.chatMessage());

        messagingTemplate.convertAndSendToUser(
                String.valueOf(event.buyerId()), CHAT_QUEUE, event.chatMessage());
    }
}
