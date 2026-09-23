package org.sstamilschool.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ssts_staff_details")
public class SstsStaffDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private SstsUser user;

    @Column(length = 50, unique = true)
    private String employeeId;

    @Column(length = 100)
    private String department;

    @Column(length = 100)
    private String jobTitle;

    @Column(precision = 12, scale = 2)
    private java.math.BigDecimal salary;

    @Column(length = 20)
    private String employmentType;

    @Column(columnDefinition = "TEXT")
    private String qualification;

    @Column(length = 255)
    private String teachingCertification;

    @Column(columnDefinition = "TEXT")
    private String availability;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public SstsStaffDetails() {
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

    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public String getJobTitle() { return jobTitle; }
    public void setJobTitle(String jobTitle) { this.jobTitle = jobTitle; }

    public java.math.BigDecimal getSalary() { return salary; }
    public void setSalary(java.math.BigDecimal salary) { this.salary = salary; }

    public String getEmploymentType() { return employmentType; }
    public void setEmploymentType(String employmentType) { this.employmentType = employmentType; }

    public String getQualification() { return qualification; }
    public void setQualification(String qualification) { this.qualification = qualification; }

    public String getTeachingCertification() { return teachingCertification; }
    public void setTeachingCertification(String teachingCertification) { this.teachingCertification = teachingCertification; }

    public String getAvailability() { return availability; }
    public void setAvailability(String availability) { this.availability = availability; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}