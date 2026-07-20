package com.seedrank.auth.signup;

import java.time.Clock;
import java.time.Instant;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seedrank.member.User;
import com.seedrank.member.UserRepository;
import com.seedrank.point.PointLedger;
import com.seedrank.point.PointLedgerRepository;
import com.seedrank.point.PointWallet;
import com.seedrank.point.PointWalletRepository;

@Service
class SignupService {

    private final UserRepository userRepository;
    private final PointWalletRepository pointWalletRepository;
    private final PointLedgerRepository pointLedgerRepository;
    private final PasswordEncoder passwordEncoder;
    private final SignupPolicy signupPolicy;
    private final Clock clock;

    SignupService(
            UserRepository userRepository,
            PointWalletRepository pointWalletRepository,
            PointLedgerRepository pointLedgerRepository,
            PasswordEncoder passwordEncoder,
            SignupPolicy signupPolicy,
            Clock clock) {
        this.userRepository = userRepository;
        this.pointWalletRepository = pointWalletRepository;
        this.pointLedgerRepository = pointLedgerRepository;
        this.passwordEncoder = passwordEncoder;
        this.signupPolicy = signupPolicy;
        this.clock = clock;
    }

    @Transactional
    SignupResponse signup(SignupRequest request) {
        String email = signupPolicy.normalizeAndValidateEmail(request.email());
        signupPolicy.validatePassword(request.password());
        signupPolicy.validateProfileId(request.profileId());

        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException();
        }

        Instant now = clock.instant();
        User user = User.create(email, passwordEncoder.encode(request.password()), request.profileId(), now);
        saveUser(user);

        pointWalletRepository.saveAndFlush(PointWallet.signupWallet(user, now));
        pointLedgerRepository.saveAndFlush(PointLedger.signupBonus(user, now));
        return SignupResponse.from(user);
    }

    private void saveUser(User user) {
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new EmailAlreadyExistsException();
        }
    }
}
