package org.sstamilschool.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ssts_parent_details")
public class SstsParentDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private SstsUser user;

    @Column(length = 100)
    private String studentName;

    @Column(length = 20)
    private String studentGrade;

    @Column(length = 50)
    private String studentClass;

    @Column(length = 20)
    private String emergencyContact;

    @Column(columnDefinition = "TEXT")
    private String pickupAuthorized;

    @Column(columnDefinition = "TEXT")
    private String medicalConditions;

    @Column(columnDefinition = "TEXT")
    private String allergies;

    @Column(columnDefinition = "TEXT")
    private String insuranceInfo;

    @Column(length = 20)
    private String parentType;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public SstsParentDetails() {
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

    public SstsUser getUser() { return user; }
    public void setUser(SstsUser user) { this.user = user; }

    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }

    public String getStudentGrade() { return studentGrade; }
    public void setStudentGrade(String studentGrade) { this.studentGrade = studentGrade; }

    public String getStudentClass() { return studentClass; }
    public void setStudentClass(String studentClass) { this.studentClass = studentClass; }

    public String getEmergencyContact() { return emergencyContact; }
    public void setEmergencyContact(String emergencyContact) { this.emergencyContact = emergencyContact; }

    public String getPickupAuthorized() { return pickupAuthorized; }
    public void setPickupAuthorized(String pickupAuthorized) { this.pickupAuthorized = pickupAuthorized; }

    public String getMedicalConditions() { return medicalConditions; }
    public void setMedicalConditions(String medicalConditions) { this.medicalConditions = medicalConditions; }

    public String getAllergies() { return allergies; }
    public void setAllergies(String allergies) { this.allergies = allergies; }

    public String getInsuranceInfo() { return insuranceInfo; }
    public void setInsuranceInfo(String insuranceInfo) { this.insuranceInfo = insuranceInfo; }

    public String getParentType() { return parentType; }
    public void setParentType(String parentType) { this.parentType = parentType; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}