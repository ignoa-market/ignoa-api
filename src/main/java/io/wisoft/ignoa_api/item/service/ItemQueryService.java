package io.wisoft.ignoa_api.item.service;

import io.wisoft.ignoa_api.global.common.CursorResponse;
import io.wisoft.ignoa_api.global.infra.storage.MediaUrlResolver;
import io.wisoft.ignoa_api.item.dto.request.ItemPreviewCursorRequest;
import io.wisoft.ignoa_api.item.dto.response.ItemDetail;
import io.wisoft.ignoa_api.item.dto.response.ItemMediaUrls;
import io.wisoft.ignoa_api.item.dto.response.ItemPreview;
import io.wisoft.ignoa_api.item.dto.response.SellerProfile;
import io.wisoft.ignoa_api.item.entity.Item;
import io.wisoft.ignoa_api.item.entity.enums.ItemViewType;
import io.wisoft.ignoa_api.item.repository.ItemQueryRepository;
import io.wisoft.ignoa_api.wish.repository.WishRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItemQueryService {

    private final ItemReader itemReader;
    private final ItemPreviewAssembler itemPreviewAssembler;
    private final ItemMediaService itemMediaService;
    private final MediaUrlResolver mediaUrlResolver;

    private final ItemQueryRepository itemQueryRepository;
    private final WishRepository wishRepository;

    public ItemDetail getItem(Long itemId, Long userId) {
        Item item = itemReader.getByIdWithSeller(itemId);

        // 판매자 여부
        boolean isSeller = userId != null && item.isSeller(userId);

        // 판매자 프로필
        String profileImageUrl = mediaUrlResolver.toUrl(
                item.getSeller().getProfileImageReference(), item.getSeller().getProfileImageSource()
        );
        SellerProfile sellerProfile = SellerProfile.from(item.getSeller(), profileImageUrl);

        // 상품 이미지
        List<ItemMediaUrls> mediaUrls = itemMediaService.getMediaUrls(itemId);

        // 찜 (개수, 여부)
        int wishCount = wishRepository.countByItemId(itemId);
        boolean isWished = userId != null
                && wishRepository.existsByUserIdAndItemId(userId, itemId);

        return ItemDetail.of(item, mediaUrls, sellerProfile, isSeller, isWished, wishCount);
    }

    public CursorResponse<ItemPreview> getItems(ItemPreviewCursorRequest request, Long userId) {
        ItemViewType viewType = request.view();
        PageRequest pageable = PageRequest.of(0, request.size());

        Slice<Item> items = getItemsByView(viewType, request.cursor(), pageable);

        List<Item> content = items.getContent();
        List<ItemPreview> itemPreviews = itemPreviewAssembler.assemble(userId, content);

        // 최신순 목록에만 다음 페이지 제공
        boolean hasNext = viewType == ItemViewType.LATEST && items.hasNext();
        Long nextCursor = hasNext
                ? content.get(content.size() - 1).getId()
                : null;

        return CursorResponse.of(itemPreviews, nextCursor, hasNext);
    }

    private Slice<Item> getItemsByView(ItemViewType viewType, Long cursor, Pageable pageable) {
        return switch (viewType) {
            case LATEST -> itemQueryRepository.findLatestItems(cursor, pageable);
            case POPULAR -> itemQueryRepository.findPopularItems(pageable);
            case ENDING_SOON -> itemQueryRepository.findEndingSoonItems(pageable);
        };
    }
}
