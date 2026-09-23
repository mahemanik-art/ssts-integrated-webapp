package org.sstamilschool.model;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

@Entity
@Table(name = "ssts_users")
public class SstsUser implements UserDetails, Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false, length = 100)
    private String fullName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    private SstsRole role;

    @Column(nullable = false, length = 20)
    private String userType = "parent";

    @OneToOne(fetch = FetchType.LAZY, cascade = jakarta.persistence.CascadeType.ALL)
    @JoinColumn(name = "profile_id")
    private transient SstsUserProfile profile;

    @Column(length = 100)
    private String designation;

    private LocalDate joiningDate;

    private LocalDate lastDate;

    @Column(nullable = false)
    private boolean receiveNewsletter = true;

    @Column(nullable = false)
    private boolean receiveVolunteerUpdates = true;

    @Column(nullable = false)
    private boolean isActive = true;

    @Column(nullable = false)
    private boolean emailVerified = false;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private LocalDateTime lastLogin;

    public SstsUser() {
    }

    public SstsUser(String username, String email, String passwordHash, String fullName, SstsRole role) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.role = role;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public SstsRole getRole() { return role; }
    public void setRole(SstsRole role) { this.role = role; }

    public String getUserType() { return userType; }
    public void setUserType(String userType) { this.userType = userType; }

    public SstsUserProfile getProfile() { return profile; }
    public void setProfile(SstsUserProfile profile) { this.profile = profile; }

    public String getDesignation() { return designation; }
    public void setDesignation(String designation) { this.designation = designation; }

    public LocalDate getJoiningDate() { return joiningDate; }
    public void setJoiningDate(LocalDate joiningDate) { this.joiningDate = joiningDate; }

    public LocalDate getLastDate() { return lastDate; }
    public void setLastDate(LocalDate lastDate) { this.lastDate = lastDate; }

    public boolean isReceiveNewsletter() { return receiveNewsletter; }
    public void setReceiveNewsletter(boolean receiveNewsletter) { this.receiveNewsletter = receiveNewsletter; }

    public boolean isReceiveVolunteerUpdates() { return receiveVolunteerUpdates; }
    public void setReceiveVolunteerUpdates(boolean receiveVolunteerUpdates) { this.receiveVolunteerUpdates = receiveVolunteerUpdates; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    public boolean isEmailVerified() { return emailVerified; }
    public void setEmailVerified(boolean emailVerified) { this.emailVerified = emailVerified; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public LocalDateTime getLastLogin() { return lastLogin; }
    public void setLastLogin(LocalDateTime lastLogin) { this.lastLogin = lastLogin; }

    @Override
    public Collection<SimpleGrantedAuthority> getAuthorities() {
        return role != null
            ? List.of(new SimpleGrantedAuthority("ROLE_" + role.getName().toUpperCase()))
            : List.of();
    }

    @Override
    public String getPassword() { return passwordHash; }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return isActive; }
}