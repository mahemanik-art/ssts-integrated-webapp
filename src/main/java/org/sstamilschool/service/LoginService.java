package org.sstamilschool.service;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.sstamilschool.dto.RegisterRequest;
import org.sstamilschool.repository.SstsRoleRepository;
import org.sstamilschool.repository.SstsUserRepository;
import org.sstamilschool.model.SstsRole;
import org.sstamilschool.model.SstsUser;

import java.time.LocalDateTime;

@Service
public class LoginService {

    private final SstsUserRepository userRepository;
    private final SstsRoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final ParentService parentService;

    public LoginService(SstsUserRepository userRepository, SstsRoleRepository roleRepository,
                        PasswordEncoder passwordEncoder, ParentService parentService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.parentService = parentService;
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

        // The user row carries IDENTITY and PERSON facts only. The contact block
        // (phone, address) is deliberately NOT set here: for user_type='parent'
        // that data belongs to the household in ssts_families, and
        // UserAdminService.copyPerPersonFields already refuses to write it on the
        // admin path. Setting it here too is what let the two paths disagree.
        // Everything below is a property of the PERSON, not the household.
        user.setAlternateEmail(request.getAlternateEmail());
        user.setBio(request.getBio());
        user.setOccupation(request.getOccupation());
        user.setEmployer(request.getEmployer());
        user.setYearsInCommunity(request.getYearsInCommunity());
        user.setPriorEducation(request.getPriorEducation());
        user.setPriorTamilExperience(request.getPriorTamilExperience());
        user.setPriorVolunteerExperience(request.getPriorVolunteerExperience());
        user.setInterests(request.getInterests());

        SstsUser saved = userRepository.save(user);

        // Same transaction: a parent account without a family is the state every
        // previously-registered parent was left in, because nothing created one.
        parentService.createFamilyForNewParent(saved, request);

        return saved;
    }
}
