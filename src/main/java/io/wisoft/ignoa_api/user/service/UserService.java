package io.wisoft.ignoa_api.user.service;

import io.wisoft.ignoa_api.auth.service.RefreshTokenService;
import io.wisoft.ignoa_api.bid.repository.BidRepository;
import io.wisoft.ignoa_api.global.exception.BusinessException;
import io.wisoft.ignoa_api.global.exception.ErrorCode;
import io.wisoft.ignoa_api.item.entity.enums.ItemStatus;
import io.wisoft.ignoa_api.item.repository.ItemRepository;
import io.wisoft.ignoa_api.global.infra.storage.StorageService;
import io.wisoft.ignoa_api.user.dto.request.UpdateUserRequest;
import io.wisoft.ignoa_api.user.dto.response.UserMeResponse;
import io.wisoft.ignoa_api.user.entity.User;
import io.wisoft.ignoa_api.user.event.ProfileImageDeletedEvent;
import io.wisoft.ignoa_api.user.repository.UserRepository;
import io.wisoft.ignoa_api.wish.repository.WishRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final RefreshTokenService refreshTokenService;
    private final StorageService storageService;
    private final ApplicationEventPublisher eventPublisher;
    private final UserRepository userRepository;
    private final ItemRepository itemRepository;
    private final BidRepository bidRepository;
    private final WishRepository wishRepository;

    public void checkDuplicateEmail(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
    }

    public void checkDuplicateNickname(String nickname) {
        if (userRepository.existsByNickname(nickname)) {
            throw new BusinessException(ErrorCode.DUPLICATE_NAME);
        }
    }

    public UserMeResponse getMe(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        return UserMeResponse.from(user);
    }

    @Transactional
    public UserMeResponse updateProfileImage(Long userId, MultipartFile image) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        String oldImageUrl = user.getProfileImageUrl();
        String imageUrl = storageService.upload(image);
        user.updateProfileImage(imageUrl);

        if (oldImageUrl != null) {
            eventPublisher.publishEvent(new ProfileImageDeletedEvent(oldImageUrl));
        }

        return UserMeResponse.from(user);
    }

    @Transactional
    public void deleteProfileImage(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (user.getProfileImageUrl() == null) {
            throw new BusinessException(ErrorCode.PROFILE_IMAGE_NOT_FOUND);
        }

        String imageUrl = user.getProfileImageUrl();
        user.updateProfileImage(null);
        eventPublisher.publishEvent(new ProfileImageDeletedEvent(imageUrl));
    }

    @Transactional
    public UserMeResponse patchMe(Long userId, UpdateUserRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (request.nickname() != null) {
            if (userRepository.existsByNickname(request.nickname())) {
                throw new BusinessException(ErrorCode.DUPLICATE_NAME);
            }
            user.updateNickname(request.nickname());
        }
        if (request.address() != null) user.updateAddress(request.address());

        return UserMeResponse.from(user);
    }

    @Transactional
    public void deleteMe(Long userId, String refreshToken) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (itemRepository.existsBySellerIdAndStatus(userId, ItemStatus.ACTIVE)) {
            throw new BusinessException(ErrorCode.HAS_ACTIVE_AUCTION);
        }

        if (bidRepository.existsByBidderIdAndItemActive(userId)) {
            throw new BusinessException(ErrorCode.HAS_ACTIVE_BID);
        }

        wishRepository.deleteAllByUserId(userId);
        refreshTokenService.delete(refreshToken);

        if (user.getProfileImageUrl() != null) {
            eventPublisher.publishEvent(new ProfileImageDeletedEvent(user.getProfileImageUrl()));
        }

        user.withdraw();
    }
}
