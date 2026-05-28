package io.wisoft.ignoa_api.user.service;

import io.wisoft.ignoa_api.bid.repository.BidRepository;
import io.wisoft.ignoa_api.global.exception.BusinessException;
import io.wisoft.ignoa_api.global.exception.ErrorCode;
import io.wisoft.ignoa_api.global.outbox.service.OutboxAppender;
import io.wisoft.ignoa_api.item.entity.enums.ItemStatus;
import io.wisoft.ignoa_api.item.repository.ItemRepository;
import io.wisoft.ignoa_api.user.dto.request.UpdateUserRequest;
import io.wisoft.ignoa_api.user.dto.response.MyProfile;
import io.wisoft.ignoa_api.user.entity.User;
import io.wisoft.ignoa_api.user.event.ProfileImageDeletedEvent;
import io.wisoft.ignoa_api.user.repository.UserRepository;
import io.wisoft.ignoa_api.wish.repository.WishRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class UserCommandService {

    private final UserQueryService userQueryService;
    private final OutboxAppender outboxAppender;
    private final ApplicationEventPublisher eventPublisher;

    private final UserRepository userRepository;
    private final ItemRepository itemRepository;
    private final BidRepository bidRepository;
    private final WishRepository wishRepository;

    public void saveProfileImage(User user, String oldImageUrl) {
        userRepository.save(user);

        if (oldImageUrl != null) {
            eventPublisher.publishEvent(new ProfileImageDeletedEvent(oldImageUrl));
        }
    }

    public void deleteProfileImage(Long userId) {
        User user = userQueryService.findById(userId);
        String profileImageUrl = user.getProfileImageUrl();

        if (profileImageUrl == null) {
            throw new BusinessException(ErrorCode.PROFILE_IMAGE_NOT_FOUND);
        }

        user.updateProfileImage(null);
        eventPublisher.publishEvent(new ProfileImageDeletedEvent(profileImageUrl));
    }

    public MyProfile updateProfile(Long userId, UpdateUserRequest request) {
        User user = userQueryService.findById(userId);

        if (request.nickname() != null && userRepository.existsByNickname(request.nickname())) {
            throw new BusinessException(ErrorCode.DUPLICATE_NAME);
        }

        user.updateProfile(request.nickname(), request.address());

        return MyProfile.from(user);
    }

    public void withdraw(Long userId) {
        User user = userQueryService.findById(userId);

        if (itemRepository.existsBySellerIdAndStatus(userId, ItemStatus.ACTIVE)) {
            throw new BusinessException(ErrorCode.HAS_ACTIVE_AUCTION);
        }

        if (bidRepository.existsByBidderIdAndItemActive(userId)) {
            throw new BusinessException(ErrorCode.HAS_ACTIVE_BID);
        }

        user.withdraw();
    }

    public void purgeUser(User user) {
        String profileImageUrl = user.getProfileImageUrl();

        wishRepository.deleteAllByUserId(user.getId());
        user.purgePersonalData();
        userRepository.save(user);

        if (profileImageUrl != null) {
            outboxAppender.save(user.getId(), profileImageUrl);
        }
    }
}
