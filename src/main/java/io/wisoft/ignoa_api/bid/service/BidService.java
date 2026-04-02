package io.wisoft.ignoa_api.bid.service;

import io.wisoft.ignoa_api.bid.dto.request.BidCreateRequest;
import io.wisoft.ignoa_api.bid.dto.request.BidListRequest;
import io.wisoft.ignoa_api.bid.dto.response.BidSummary;
import io.wisoft.ignoa_api.bid.dto.response.BidResponse;
import io.wisoft.ignoa_api.bid.entity.Bid;
import io.wisoft.ignoa_api.bid.event.BidPlaceEvent;
import io.wisoft.ignoa_api.global.dto.SliceResponse;
import io.wisoft.ignoa_api.product.entity.Product;
import io.wisoft.ignoa_api.bid.repository.BidRepository;
import io.wisoft.ignoa_api.product.repository.ProductRepository;
import io.wisoft.ignoa_api.user.entity.User;
import io.wisoft.ignoa_api.user.repository.UserRepository;
import io.wisoft.ignoa_api.global.exception.BusinessException;
import io.wisoft.ignoa_api.global.exception.ErrorCode;
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
public class BidService {

    private final ApplicationEventPublisher eventPublisher;
    private final BidRepository bidRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    @Transactional
    public BidResponse placeBid(Long productId, Long bidderId, BidCreateRequest request) {
        User bidder = userRepository.findById(bidderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Product product = productRepository.findByIdWithLock(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        if (product.isSeller(bidderId)) {
            throw new BusinessException(ErrorCode.SELF_BID_NOT_ALLOWED);
        }

        if (!product.isOnSale()) {
            throw new BusinessException(ErrorCode.AUCTION_CLOSED);
        }

        if (!product.isValidBidPrice(request.price())) {
            throw new BusinessException(ErrorCode.INVALID_BID_PRICE);
        }

        product.raisePriceTo(request.price());

        Bid bid = Bid.place(product, bidder, request.price());
        bidRepository.save(bid);

        eventPublisher.publishEvent(BidPlaceEvent.of(bid, product, bidder));

        return BidResponse.from(bid);
    }

    public SliceResponse<BidSummary> getBids(Long productId, BidListRequest request) {
        if (!productRepository.existsById(productId)) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        Slice<Bid> bidSlice = bidRepository.findByProductIdWithBidder(productId, PageRequest.of(request.page(), request.size()));

        List<BidSummary> bidSummaries = bidSlice.getContent().stream()
                .map(BidSummary::from)
                .toList();

        return SliceResponse.of(bidSummaries, bidSlice.hasNext());
    }
}