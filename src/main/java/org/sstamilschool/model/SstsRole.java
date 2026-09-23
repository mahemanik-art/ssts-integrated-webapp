package org.sstamilschool.model;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "ssts_roles")
public class SstsRole implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    @Column(length = 255)
    private String description;

    // User management
    private boolean canCreateUsers = false;
    private boolean canEditUsers = false;
    private boolean canDeleteUsers = false;
    private boolean canViewUsers = false;

    // Content management
    private boolean canManageContent = false;
    private boolean canEditPages = false;
    private boolean canPublishContent = false;

    // Class/Level management
    private boolean canManageLevels = false;
    private boolean canManageClasses = false;

    // Donor management
    private boolean canManageDonors = false;
    private boolean canViewDonors = false;

    // Calendar & Events
    private boolean canManageEvents = false;
    private boolean canViewCalendar = false;

    // Volunteer management
    private boolean canManageVolunteers = false;

    // Financial & Reporting
    private boolean canViewFinancials = false;
    private boolean canViewReports = false;

    // System settings
    private boolean canManageSettings = false;
    private boolean canViewAuditLogs = false;

    // Communication
    private boolean canSendAnnouncements = false;
    private boolean canSendNewsletters = false;

    // Dashboard
    private boolean canViewDashboard = false;

    private boolean isActive = true;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public SstsRole() {
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

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isCanCreateUsers() { return canCreateUsers; }
    public void setCanCreateUsers(boolean canCreateUsers) { this.canCreateUsers = canCreateUsers; }

    public boolean isCanEditUsers() { return canEditUsers; }
    public void setCanEditUsers(boolean canEditUsers) { this.canEditUsers = canEditUsers; }

    public boolean isCanDeleteUsers() { return canDeleteUsers; }
    public void setCanDeleteUsers(boolean canDeleteUsers) { this.canDeleteUsers = canDeleteUsers; }

    public boolean isCanViewUsers() { return canViewUsers; }
    public void setCanViewUsers(boolean canViewUsers) { this.canViewUsers = canViewUsers; }

    public boolean isCanManageContent() { return canManageContent; }
    public void setCanManageContent(boolean canManageContent) { this.canManageContent = canManageContent; }

    public boolean isCanEditPages() { return canEditPages; }
    public void setCanEditPages(boolean canEditPages) { this.canEditPages = canEditPages; }

    public boolean isCanPublishContent() { return canPublishContent; }
    public void setCanPublishContent(boolean canPublishContent) { this.canPublishContent = canPublishContent; }

    public boolean isCanManageLevels() { return canManageLevels; }
    public void setCanManageLevels(boolean canManageLevels) { this.canManageLevels = canManageLevels; }

    public boolean isCanManageClasses() { return canManageClasses; }
    public void setCanManageClasses(boolean canManageClasses) { this.canManageClasses = canManageClasses; }

    public boolean isCanManageDonors() { return canManageDonors; }
    public void setCanManageDonors(boolean canManageDonors) { this.canManageDonors = canManageDonors; }

    public boolean isCanViewDonors() { return canViewDonors; }
    public void setCanViewDonors(boolean canViewDonors) { this.canViewDonors = canViewDonors; }

    public boolean isCanManageEvents() { return canManageEvents; }
    public void setCanManageEvents(boolean canManageEvents) { this.canManageEvents = canManageEvents; }

    public boolean isCanViewCalendar() { return canViewCalendar; }
    public void setCanViewCalendar(boolean canViewCalendar) { this.canViewCalendar = canViewCalendar; }

    public boolean isCanManageVolunteers() { return canManageVolunteers; }
    public void setCanManageVolunteers(boolean canManageVolunteers) { this.canManageVolunteers = canManageVolunteers; }

    public boolean isCanViewFinancials() { return canViewFinancials; }
    public void setCanViewFinancials(boolean canViewFinancials) { this.canViewFinancials = canViewFinancials; }

    public boolean isCanViewReports() { return canViewReports; }
    public void setCanViewReports(boolean canViewReports) { this.canViewReports = canViewReports; }

    public boolean isCanManageSettings() { return canManageSettings; }
    public void setCanManageSettings(boolean canManageSettings) { this.canManageSettings = canManageSettings; }

    public boolean isCanViewAuditLogs() { return canViewAuditLogs; }
    public void setCanViewAuditLogs(boolean canViewAuditLogs) { this.canViewAuditLogs = canViewAuditLogs; }

    public boolean isCanSendAnnouncements() { return canSendAnnouncements; }
    public void setCanSendAnnouncements(boolean canSendAnnouncements) { this.canSendAnnouncements = canSendAnnouncements; }

    public boolean isCanSendNewsletters() { return canSendNewsletters; }
    public void setCanSendNewsletters(boolean canSendNewsletters) { this.canSendNewsletters = canSendNewsletters; }

    public boolean isCanViewDashboard() { return canViewDashboard; }
    public void setCanViewDashboard(boolean canViewDashboard) { this.canViewDashboard = canViewDashboard; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}