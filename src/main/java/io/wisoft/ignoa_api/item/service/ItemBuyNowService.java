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
            log.debug(
                    "즉시구매 예약 거절: itemId={}, buyNowPrice={}, reason=예약 조건 불충족",
                    itemId,
                    buyNowPrice
            );
            throw new BusinessException(ErrorCode.BUY_NOW_CONFLICT);
        }
    }

    public void complete(Long itemId, User buyer) {
        if (itemRepository.completeBuyNowIfPending(itemId, buyer) == 0) {
            // 예약 단계에서 다른 구매자의 진입을 막았으므로, 결제 완료 후 상품 마감 실패는 상태 확인이 필요하다.
            log.error(
                    "즉시구매 상품 마감 실패: itemId={}, buyerId={}, action=상품 상태 수동 확인",
                    itemId,
                    buyer.getId()
            );
            return;
        }

        int lostBids = bidRepository.markLosingBids(itemId);
        chatRoomService.createChatRoom(itemId);

        log.debug(
                "즉시구매 상품 마감 완료: itemId={}, buyerId={}, lostBids={}",
                itemId,
                buyer.getId(),
                lostBids
        );
    }

    public void cancel(Long itemId) {
        if (itemRepository.cancelBuyNowIfPending(itemId) == 0) {
            log.debug("즉시구매 예약 해제 생략: itemId={}, reason=결제 대기 상태 아님", itemId);
            return;
        }

        log.debug("즉시구매 예약 해제: itemId={}", itemId);
    }
}
