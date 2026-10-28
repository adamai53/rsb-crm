package dj.redseabunkering.crm.contact;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import dj.redseabunkering.crm.common.BaseEntity;
import dj.redseabunkering.crm.user.User;

@Entity
@Table(name = "contacts")
public class Contact extends BaseEntity {

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    private String title;

    private String email;

    private String phone;

    @Column(name = "company_id")
    private UUID companyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Lifecycle lifecycle = Lifecycle.LEAD;

    private String source;

    @Column(name = "owner_id")
    private UUID ownerId;

    protected Contact() {
    }

    public Contact(String firstName, String lastName, UUID ownerId) {
        this.firstName = firstName.trim();
        this.lastName = lastName.trim();
        this.ownerId = ownerId;
    }

    public void update(String firstName, String lastName, String title, String email, String phone, UUID companyId,
            Lifecycle lifecycle, String source) {
        this.firstName = firstName.trim();
        this.lastName = lastName.trim();
        this.title = clean(title);
        this.email = email == null || email.isBlank() ? null : User.normalizeEmail(email);
        this.phone = clean(phone);
        this.companyId = companyId;
        this.lifecycle = lifecycle;
        this.source = clean(source);
    }

    private static String clean(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    public String fullName() {
        return firstName + " " + lastName;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getTitle() {
        return title;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public Lifecycle getLifecycle() {
        return lifecycle;
    }

    public void setLifecycle(Lifecycle lifecycle) {
        this.lifecycle = lifecycle;
    }

    public String getSource() {
        return source;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(UUID ownerId) {
        this.ownerId = ownerId;
    }
}
