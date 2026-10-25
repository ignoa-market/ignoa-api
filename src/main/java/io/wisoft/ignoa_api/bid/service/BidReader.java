package io.wisoft.ignoa_api.bid.service;

import io.wisoft.ignoa_api.bid.repository.BidRepository;
import io.wisoft.ignoa_api.bid.repository.dto.MyBidItem;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BidReader {

    private final BidRepository bidRepository;

    public Slice<MyBidItem> findBidderItems(Long bidderId, Long cursor, Pageable pageable) {
        return bidRepository.findBidderItems(bidderId, cursor, pageable);
    }
}

