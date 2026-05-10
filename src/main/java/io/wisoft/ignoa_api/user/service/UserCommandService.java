package io.wisoft.ignoa_api.user.service;

import io.wisoft.ignoa_api.user.entity.User;
import io.wisoft.ignoa_api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class UserCommandService {
    private final UserRepository userRepository;

    public void save(User user) {
        userRepository.save(user);
    }
}

