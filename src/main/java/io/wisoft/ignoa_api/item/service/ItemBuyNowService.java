package io.wisoft.ignoa_api.item.service;

import io.wisoft.ignoa_api.bid.repository.BidRepository;
import io.wisoft.ignoa_api.chat.service.ChatRoomService;
import io.wisoft.ignoa_api.global.exception.BusinessException;
import io.wisoft.ignoa_api.global.exception.ErrorCode;
import io.wisoft.ignoa_api.item.repository.ItemRepository;
import io.wisoft.ignoa_api.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ItemBuyNowService {

    private final ItemRepository itemRepository;
    private final BidRepository bidRepository;
    private final ChatRoomService chatRoomService;

    public void reserve(Long itemId, Long buyNowPrice, LocalDateTime now) {
        if (itemRepository.reserveBuyNowIfActive(itemId, buyNowPrice, now) == 0) {
            throw new BusinessException(ErrorCode.BUY_NOW_CONFLICT);
        }
    }

    public void complete(Long itemId, User buyer) {
        itemRepository.completeBuyNowIfPending(itemId, buyer);
        bidRepository.markLosingBids(itemId);
        chatRoomService.createChatRoom(itemId);
    }

    public void cancel(Long itemId) {
        itemRepository.cancelBuyNowIfPending(itemId);
    }
}
