package io.wisoft.ignoa_api.auction.service;

import io.wisoft.ignoa_api.auction.dto.response.AuctionExtensionResponse;
import io.wisoft.ignoa_api.bid.service.BidService;
import io.wisoft.ignoa_api.chat.service.ChatRoomService;
import io.wisoft.ignoa_api.global.exception.BusinessException;
import io.wisoft.ignoa_api.global.exception.ErrorCode;
import io.wisoft.ignoa_api.item.entity.Item;
import io.wisoft.ignoa_api.item.repository.ItemRepository;
import io.wisoft.ignoa_api.item.service.ItemReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;


@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionService {

    private final BidService bidService;
    private final ChatRoomService chatRoomService;
    private final ItemReader itemReader;
    private final ItemRepository itemRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void closeAuction(Long itemId) {
        int updatedRows = itemRepository.closeIfActive(itemId, LocalDateTime.now());

        if (updatedRows == 0) {
            log.debug("경매 마감 처리 생략: itemId={}, reason=조건 불충족", itemId);
            return;
        }

        boolean hasWinner = bidService.markBidResults(itemId);

        if (!hasWinner) {
            log.debug("경매 유찰 처리 완료: itemId={}", itemId);
            return;
        }

        chatRoomService.createChat(itemId);
        log.debug("경매 낙찰 처리 및 채팅방 생성 완료: itemId={}", itemId);
    }

    @Transactional
    public AuctionExtensionResponse extendAuction(Long itemId, Long sellerId) {
        Item item = itemReader.getByIdWithSeller(itemId);

        if (!item.isSeller(sellerId)) {
            throw new BusinessException(ErrorCode.ITEM_EXTEND_FORBIDDEN);
        }

        int updatedRows = itemRepository.extendEndAtIfActive(itemId, LocalDateTime.now());

        if (updatedRows == 0) {
            throw new BusinessException(ErrorCode.AUCTION_EXTEND_CONFLICT);
        }

        Item extendedItem = itemReader.getById(itemId);

        return new AuctionExtensionResponse(
                extendedItem.getId(),
                extendedItem.getEndAt(),
                extendedItem.getExtensionCount()
        );
    }
}
