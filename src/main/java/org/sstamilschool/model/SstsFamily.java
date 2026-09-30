package org.sstamilschool.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * One row per FAMILY, not per person -- the row holds BOTH parents (the
 * parent1_/parent2_ and phone1/phone2 column pairs) plus their shared address.
 * Do not "simplify" this into one row per parent: that is what forced the
 * per-child duplication ssts_students.family_id exists to avoid.
 *
 * userId is NULLABLE so a family can exist before any parent registers a
 * portal account, and the column is ON DELETE SET NULL so it also survives the
 * account going away afterwards. Do not "tidy" that into ON DELETE CASCADE: a
 * CASCADE on a family with enrolled students collides with
 * ssts_students.family_id ON DELETE RESTRICT and turns every parent-account
 * deletion into a foreign-key error. There is no `cascade` here on purpose --
 * the database constraint owns this, not the entity.
 */
@Entity
@Table(name = "ssts_families")
public class SstsFamily {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true)
    private SstsUser user;

    @Column(length = 20)
    private String emergencyContact;





    // Names are pinned explicitly because Spring's implicit naming strategy
    // mishandles the digit boundary: parent1Email maps to `parent1email`
    // (no underscore) while parent1FullName maps to `parent1full_name`. The
    // inconsistency is silent under ddl-auto=update and only shows up as
    // "missing column" under validate, so do not drop these @Column names.
    @Column(name = "parent1_full_name", length = 100)
    private String parent1FullName;

    @Column(name = "parent1_email", length = 100)
    private String parent1Email;

    @Column(name = "parent2_full_name", length = 100)
    private String parent2FullName;

    @Column(name = "parent2_email", length = 100)
    private String parent2Email;

    @Column(length = 255)
    private String street;

    @Column(length = 100)
    private String city;

    @Column(length = 50)
    private String state;

    @Column(length = 50)
    private String country;

    @Column(length = 20)
    private String zip;

    @Column(length = 20)
    private String phone1;

    @Column(length = 20)
    private String phone2;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public SstsFamily() {
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

    public String getEmergencyContact() { return emergencyContact; }
    public void setEmergencyContact(String emergencyContact) { this.emergencyContact = emergencyContact; }





    public String getParent1FullName() { return parent1FullName; }
    public void setParent1FullName(String parent1FullName) { this.parent1FullName = parent1FullName; }

    public String getParent1Email() { return parent1Email; }
    public void setParent1Email(String parent1Email) { this.parent1Email = parent1Email; }

    public String getParent2FullName() { return parent2FullName; }
    public void setParent2FullName(String parent2FullName) { this.parent2FullName = parent2FullName; }

    public String getParent2Email() { return parent2Email; }
    public void setParent2Email(String parent2Email) { this.parent2Email = parent2Email; }

    public String getStreet() { return street; }
    public void setStreet(String street) { this.street = street; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public String getZip() { return zip; }
    public void setZip(String zip) { this.zip = zip; }

    public String getPhone1() { return phone1; }
    public void setPhone1(String phone1) { this.phone1 = phone1; }

    public String getPhone2() { return phone2; }
    public void setPhone2(String phone2) { this.phone2 = phone2; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
