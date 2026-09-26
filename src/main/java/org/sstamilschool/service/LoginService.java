package org.sstamilschool.service;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.sstamilschool.dto.RegisterRequest;
import org.sstamilschool.repository.SstsRoleRepository;
import org.sstamilschool.repository.SstsUserRepository;
import org.sstamilschool.repository.SstsUserProfileRepository;
import org.sstamilschool.model.SstsRole;
import org.sstamilschool.model.SstsUser;
import org.sstamilschool.model.SstsUserProfile;

import java.time.LocalDateTime;

@Service
public class LoginService {

    private final SstsUserRepository userRepository;
    private final SstsRoleRepository roleRepository;
    private final SstsUserProfileRepository profileRepository;
    private final PasswordEncoder passwordEncoder;

    public LoginService(SstsUserRepository userRepository, SstsRoleRepository roleRepository,
                        SstsUserProfileRepository profileRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.profileRepository = profileRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public boolean authenticate(String usernameOrEmail, String password) {
        var userOpt = userRepository.findByEmailOrUsername(usernameOrEmail, usernameOrEmail);
        if (userOpt.isEmpty()) return false;

        var user = userOpt.get();
        if (!user.isActive()) return false;
        if (!passwordEncoder.matches(password, user.getPasswordHash())) return false;

        userRepository.updateLastLogin(user.getId(), LocalDateTime.now());

        var auth = new UsernamePasswordAuthenticationToken(user, password, user.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
        return true;
    }

    public String redirectBasedOnUserType(Long userId) {
        var user = userRepository.findById(userId).orElse(null);
        if (user == null) return "/";

        return switch (user.getUserType().toLowerCase()) {
            case "admin", "staff" -> "/admin/dashboard";
            case "volunteer" -> "/volunteer/dashboard";
            default -> "/parent/dashboard";
        };
    }

    @Transactional
    public SstsUser register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("That username is already taken.");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("That email is already registered.");
        }

        SstsRole parentRole = roleRepository.findByName("read_only")
            .orElseThrow(() -> new IllegalStateException("Default role 'read_only' is not configured."));

        SstsUser user = new SstsUser();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setFullName(request.getFullName());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole(parentRole);
        user.setUserType("parent");
        user.setActive(true);
        user.setEmailVerified(false);
        user.setReceiveNewsletter(request.isReceiveNewsletter());
        user.setReceiveVolunteerUpdates(request.isReceiveVolunteerUpdates());

        SstsUser saved = userRepository.save(user);

        SstsUserProfile profile = new SstsUserProfile();
        profile.setUser(saved);
        profile.setPhone(request.getPhone());
        profile.setAlternateEmail(request.getAlternateEmail());
        profile.setAddressLine1(request.getAddressLine1());
        profile.setAddressLine2(request.getAddressLine2());
        profile.setCity(request.getCity());
        profile.setState(request.getState());
        profile.setZipCode(request.getZipCode());
        profile.setCountry(request.getCountry());
        profile.setBio(request.getBio());
        profile.setOccupation(request.getOccupation());
        profile.setEmployer(request.getEmployer());
        profile.setYearsInCommunity(request.getYearsInCommunity());
        profile.setPriorEducation(request.getPriorEducation());
        profile.setPriorTamilExperience(request.getPriorTamilExperience());
        profile.setPriorTeachingExperience(request.getPriorTeachingExperience());
        profile.setPriorVolunteerExperience(request.getPriorVolunteerExperience());
        profile.setCertifications(request.getCertifications());
        profile.setInterests(request.getInterests());
        profileRepository.save(profile);

        return saved;
    }
}