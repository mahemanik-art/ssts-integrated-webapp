package org.sstamilschool.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ssts_user_profiles")
public class SstsUserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private SstsUser user;

    @Column(length = 20)
    private String phone;

    @Column(length = 100)
    private String alternateEmail;

    @Column(length = 255)
    private String addressLine1;

    @Column(length = 255)
    private String addressLine2;

    @Column(length = 100)
    private String city;

    @Column(length = 50)
    private String state;

    @Column(length = 20)
    private String zipCode;

    @Column(length = 50)
    private String country = "USA";

    @Column(columnDefinition = "TEXT")
    private String bio;

    @Column(length = 500)
    private String avatarUrl;

    @Column(columnDefinition = "TEXT")
    private String priorEducation;

    @Column(columnDefinition = "TEXT")
    private String priorTamilExperience;

    @Column(columnDefinition = "TEXT")
    private String priorTeachingExperience;

    @Column(columnDefinition = "TEXT")
    private String priorVolunteerExperience;

    @Column(columnDefinition = "TEXT")
    private String certifications;

    @Column(columnDefinition = "TEXT")
    private String interests;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public SstsUserProfile() {
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

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getAlternateEmail() { return alternateEmail; }
    public void setAlternateEmail(String alternateEmail) { this.alternateEmail = alternateEmail; }

    public String getAddressLine1() { return addressLine1; }
    public void setAddressLine1(String addressLine1) { this.addressLine1 = addressLine1; }

    public String getAddressLine2() { return addressLine2; }
    public void setAddressLine2(String addressLine2) { this.addressLine2 = addressLine2; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public String getZipCode() { return zipCode; }
    public void setZipCode(String zipCode) { this.zipCode = zipCode; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    @Column(length = 100)
    private String occupation;

    @Column(length = 100)
    private String employer;

    @Column
    private Integer yearsInCommunity = 0;

    public String getOccupation() { return occupation; }
    public void setOccupation(String occupation) { this.occupation = occupation; }

    public String getEmployer() { return employer; }
    public void setEmployer(String employer) { this.employer = employer; }

    public Integer getYearsInCommunity() { return yearsInCommunity; }
    public void setYearsInCommunity(Integer yearsInCommunity) { this.yearsInCommunity = yearsInCommunity; }

    public String getPriorEducation() { return priorEducation; }
    public void setPriorEducation(String priorEducation) { this.priorEducation = priorEducation; }

    public String getPriorTamilExperience() { return priorTamilExperience; }
    public void setPriorTamilExperience(String priorTamilExperience) { this.priorTamilExperience = priorTamilExperience; }

    public String getPriorTeachingExperience() { return priorTeachingExperience; }
    public void setPriorTeachingExperience(String priorTeachingExperience) { this.priorTeachingExperience = priorTeachingExperience; }

    public String getPriorVolunteerExperience() { return priorVolunteerExperience; }
    public void setPriorVolunteerExperience(String priorVolunteerExperience) { this.priorVolunteerExperience = priorVolunteerExperience; }

    public String getCertifications() { return certifications; }
    public void setCertifications(String certifications) { this.certifications = certifications; }

    public String getInterests() { return interests; }
    public void setInterests(String interests) { this.interests = interests; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}