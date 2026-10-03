package io.wisoft.ignoa_api.item.service;

import io.wisoft.ignoa_api.bid.repository.BidRepository;
import io.wisoft.ignoa_api.chat.service.ChatRoomService;
import io.wisoft.ignoa_api.global.exception.BusinessException;
import io.wisoft.ignoa_api.global.exception.ErrorCode;
import io.wisoft.ignoa_api.item.repository.ItemRepository;
import io.wisoft.ignoa_api.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ItemBuyNowService {

    private final ItemRepository itemRepository;
    private final BidRepository bidRepository;
    private final ChatRoomService chatRoomService;

    public void reserve(Long itemId, Long buyNowPrice, LocalDateTime now) {
        if (itemRepository.reserveBuyNowIfActive(itemId, buyNowPrice, now) == 0) {
            log.debug("즉시구매 예약 실패: itemId={}, reason=판매 중 아님 또는 즉시구매가 변경", itemId);
            throw new BusinessException(ErrorCode.BUY_NOW_CONFLICT);
        }
    }

    public void complete(Long itemId, User buyer) {
        if (itemRepository.completeBuyNowIfPending(itemId, buyer) == 0) {
            // 결제는 완료됐는데 상품이 결제 중 상태가 아니다. 상품이 계속 판매될 수 있다
            log.error("즉시구매 상품 마감 실패: itemId={}, buyerId={}, action=상품 상태 수동 확인", itemId, buyer.getId());
            return;
        }

        int lostBids = bidRepository.markLosingBids(itemId);
        chatRoomService.createChatRoom(itemId);
        log.info("즉시구매 상품 마감 완료: itemId={}, buyerId={}, lostBids={}", itemId, buyer.getId(), lostBids);
    }

    public void cancel(Long itemId) {
        if (itemRepository.cancelBuyNowIfPending(itemId) == 0) {
            log.warn("즉시구매 예약 해제 생략: itemId={}, reason=결제 중 상태 아님", itemId);
            return;
        }

        log.info("즉시구매 예약 해제: itemId={}", itemId);
    }
}
