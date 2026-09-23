package org.sstamilschool.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ssts_login_attempts")
public class SstsLoginAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String email;

    @Column(length = 45)
    private String ipAddress;

    @Column(nullable = false)
    private boolean success = false;

    @Column(length = 255)
    private String failureReason;

    @Column(nullable = false, updatable = false)
    private LocalDateTime attemptedAt;

    public SstsLoginAttempt() {
    }

    @PrePersist
    protected void onCreate() {
        this.attemptedAt = LocalDateTime.now();
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

    public LocalDateTime getAttemptedAt() { return attemptedAt; }
    public void setAttemptedAt(LocalDateTime attemptedAt) { this.attemptedAt = attemptedAt; }
}