package io.wisoft.ignoa_api.user.service;

import io.wisoft.ignoa_api.bid.repository.BidRepository;
import io.wisoft.ignoa_api.global.exception.BusinessException;
import io.wisoft.ignoa_api.global.exception.ErrorCode;
import io.wisoft.ignoa_api.global.infra.storage.MediaUrlResolver;
import io.wisoft.ignoa_api.global.outbox.entity.OutboxEventType;
import io.wisoft.ignoa_api.global.outbox.service.OutboxAppender;
import io.wisoft.ignoa_api.item.entity.enums.ItemStatus;
import io.wisoft.ignoa_api.item.repository.ItemRepository;
import io.wisoft.ignoa_api.user.dto.request.UpdateUserRequest;
import io.wisoft.ignoa_api.user.dto.response.MyProfile;
import io.wisoft.ignoa_api.user.entity.ProfileImageSource;
import io.wisoft.ignoa_api.user.entity.User;
import io.wisoft.ignoa_api.user.repository.UserRepository;
import io.wisoft.ignoa_api.wish.repository.WishRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class UserCommandService {

    private final UserQueryService userQueryService;
    private final MediaUrlResolver mediaUrlResolver;
    private final OutboxAppender outboxAppender;

    private final UserRepository userRepository;
    private final ItemRepository itemRepository;
    private final BidRepository bidRepository;
    private final WishRepository wishRepository;

    public User replaceProfileImage(Long userId, String newObjectKey) {
        User user = userQueryService.findById(userId);

        ProfileImageSource oldMediaSource = user.getProfileImageSource();
        String oldMediaReference = user.getProfileImageReference();

        user.updateProfileImage(newObjectKey, ProfileImageSource.MANAGED);

        if (oldMediaSource == ProfileImageSource.MANAGED) {
            outboxAppender.save(
                    userId.toString(),
                    "USER",
                    oldMediaReference,
                    OutboxEventType.DELETE_PROFILE_IMAGE
            );
        }

        return user;
    }

    public void deleteProfileImage(Long userId) {
        User user = userQueryService.findById(userId);

        String mediaReference = user.getProfileImageReference();
        ProfileImageSource mediaSource = user.getProfileImageSource();

        if (mediaReference == null) {
            throw new BusinessException(ErrorCode.PROFILE_IMAGE_NOT_FOUND);
        }

        user.updateProfileImage(null, null);

        if (mediaSource == ProfileImageSource.MANAGED) {
            outboxAppender.save(
                    userId.toString(),
                    "USER",
                    mediaReference,
                    OutboxEventType.DELETE_PROFILE_IMAGE
            );
        }
    }

    public MyProfile updateProfile(Long userId, UpdateUserRequest request) {
        User user = userQueryService.findById(userId);

        if (request.nickname() != null && userRepository.existsByNickname(request.nickname())) {
            throw new BusinessException(ErrorCode.DUPLICATE_NAME);
        }

        user.updateProfile(request.nickname(), request.address());
        String profileImageUrl = mediaUrlResolver.toUrl(user.getProfileImageReference(), user.getProfileImageSource());

        return MyProfile.from(user, profileImageUrl);
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
        ProfileImageSource mediaSource = user.getProfileImageSource();
        String mediaReference = user.getProfileImageReference();

        wishRepository.deleteAllByUserId(user.getId());
        user.purgePersonalData();
        userRepository.save(user);

        if (mediaSource == ProfileImageSource.MANAGED) {
            outboxAppender.save(
                    user.getId().toString(),
                    "USER",
                    mediaReference,
                    OutboxEventType.PURGE_PERSONAL_DATA);
        }
    }
}
