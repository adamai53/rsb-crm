package dj.redseabunkering.crm.email;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import dj.redseabunkering.crm.common.BaseEntity;

@Entity
@Table(name = "email_templates")
public class EmailTemplate extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false)
    private String body;

    protected EmailTemplate() {
    }

    public EmailTemplate(String name, String subject, String body) {
        update(name, subject, body);
    }

    public final void update(String name, String subject, String body) {
        this.name = name.trim();
        this.subject = subject.trim();
        this.body = body;
    }

    public String getName() {
        return name;
    }

    public String getSubject() {
        return subject;
    }

    public String getBody() {
        return body;
    }
}
