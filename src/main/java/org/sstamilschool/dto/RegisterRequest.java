package org.sstamilschool.dto;

public class RegisterRequest {

    private String fullName;
    private String username;
    private String email;
    private String password;
    private String confirmPassword;

    private boolean receiveNewsletter = false;
    private boolean receiveVolunteerUpdates = false;

    // --- Family-owned (ssts_families), not user-owned -------------------
    // These were previously written onto ssts_users. See ParentService: a
    // parent's phone and address belong to the household, and UserAdminService
    // already refuses to write them for user_type='parent'.
    private String phone;
    private String phone2;
    private String parent2FullName;
    private String parent2Email;
    private String emergencyContact;

    // --- Person-owned (ssts_users) --------------------------------------
    private String alternateEmail;
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String state;
    private String zipCode;
    private String country = "USA";

    private String bio;
    private String occupation;
    private String employer;
    private Integer yearsInCommunity;
    private String priorEducation;
    private String priorTamilExperience;
    private String priorVolunteerExperience;
    private String interests;

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getConfirmPassword() { return confirmPassword; }
    public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }

    public boolean isReceiveNewsletter() { return receiveNewsletter; }
    public void setReceiveNewsletter(boolean receiveNewsletter) { this.receiveNewsletter = receiveNewsletter; }

    public boolean isReceiveVolunteerUpdates() { return receiveVolunteerUpdates; }
    public void setReceiveVolunteerUpdates(boolean receiveVolunteerUpdates) { this.receiveVolunteerUpdates = receiveVolunteerUpdates; }

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

    public String getPriorVolunteerExperience() { return priorVolunteerExperience; }
    public void setPriorVolunteerExperience(String priorVolunteerExperience) { this.priorVolunteerExperience = priorVolunteerExperience; }

    public String getInterests() { return interests; }
    public void setInterests(String interests) { this.interests = interests; }

    public String getPhone2() { return phone2; }
    public void setPhone2(String phone2) { this.phone2 = phone2; }

    public String getParent2FullName() { return parent2FullName; }
    public void setParent2FullName(String parent2FullName) { this.parent2FullName = parent2FullName; }

    public String getParent2Email() { return parent2Email; }
    public void setParent2Email(String parent2Email) { this.parent2Email = parent2Email; }

    public String getEmergencyContact() { return emergencyContact; }
    public void setEmergencyContact(String emergencyContact) { this.emergencyContact = emergencyContact; }
}
