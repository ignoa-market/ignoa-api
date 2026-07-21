package io.wisoft.ignoa_api.auction.service;

import io.wisoft.ignoa_api.auction.dto.response.AuctionExtensionResponse;
import io.wisoft.ignoa_api.auction.event.AuctionRegisteredEvent;
import io.wisoft.ignoa_api.bid.service.BidService;
import io.wisoft.ignoa_api.global.exception.BusinessException;
import io.wisoft.ignoa_api.global.exception.ErrorCode;
import io.wisoft.ignoa_api.item.entity.Item;
import io.wisoft.ignoa_api.item.repository.ItemRepository;
import io.wisoft.ignoa_api.item.service.ItemReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;


@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionService {

    private final BidService bidService;
    private final ItemReader itemReader;
    private final ItemRepository itemRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void closeAuction(Long itemId) {
        int updatedRows = itemRepository.closeIfActive(itemId, LocalDateTime.now());

        if (updatedRows == 0) {
            log.info("[중복 마감 방지] 이미 마감된 경매 itemId={}", itemId);
            return;
        }

        boolean result = bidService.markBidResults(itemId);
        log.info(result ? "[낙찰] 경매 마감 itemId={}"
                : "[유찰] 입찰 없이 마감된 경매 itemId={}", itemId);
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

        eventPublisher.publishEvent(
                new AuctionRegisteredEvent(extendedItem.getId(), extendedItem.getEndAt()));

        return new AuctionExtensionResponse(
                extendedItem.getId(),
                extendedItem.getEndAt(),
                extendedItem.getExtensionCount()
        );
    }
}
