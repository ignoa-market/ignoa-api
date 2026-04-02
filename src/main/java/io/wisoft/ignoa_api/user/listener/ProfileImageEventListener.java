package io.wisoft.ignoa_api.user.listener;

import io.wisoft.ignoa_api.storage.service.StorageService;
import io.wisoft.ignoa_api.user.event.ProfileImageDeletedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ProfileImageEventListener {

    private final StorageService storageService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProfileImageDeleted(ProfileImageDeletedEvent event) {
        storageService.delete(event.imageUrl());
    }
}
