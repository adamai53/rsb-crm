package dj.redseabunkering.crm.contact;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dj.redseabunkering.crm.common.ApiException;
import dj.redseabunkering.crm.common.CurrentUser;
import dj.redseabunkering.crm.company.CompanyRepository;
import dj.redseabunkering.crm.contact.ContactDtos.ContactDetail;
import dj.redseabunkering.crm.contact.ContactDtos.ContactPatch;
import dj.redseabunkering.crm.contact.ContactDtos.ContactRequest;
import dj.redseabunkering.crm.deal.RelatedRecords;
import dj.redseabunkering.crm.user.OwnerAssignment;

@Service
public class ContactService {

    private final ContactRepository contacts;
    private final CompanyRepository companies;
    private final ContactQueries queries;
    private final RelatedRecords related;
    private final OwnerAssignment owners;

    public ContactService(ContactRepository contacts, CompanyRepository companies, ContactQueries queries,
            RelatedRecords related, OwnerAssignment owners) {
        this.contacts = contacts;
        this.companies = companies;
        this.queries = queries;
        this.related = related;
        this.owners = owners;
    }

    @Transactional(readOnly = true)
    public ContactDetail detail(UUID id, CurrentUser me) {
        var scored = queries.find(id).orElseThrow(ContactService::notFound);
        return new ContactDetail(scored.row(), scored.score(), related.dealsOfContact(id), related.tasksOfContact(id),
                me.canEdit(scored.row().ownerId()));
    }

    @Transactional
    public UUID create(ContactRequest req, CurrentUser me) {
        requireCompany(req.companyId());
        var contact = new Contact(req.firstName(), req.lastName(), owners.forNewRecord(req.ownerId(), me));
        contact.update(req.firstName(), req.lastName(), req.title(), req.email(), req.phone(), req.companyId(),
                req.lifecycle(), req.source());
        return contacts.save(contact).getId();
    }

    @Transactional
    public void update(UUID id, ContactRequest req, CurrentUser me) {
        Contact contact = get(id);
        me.requireEdit(contact.getOwnerId());
        requireCompany(req.companyId());
        contact.update(req.firstName(), req.lastName(), req.title(), req.email(), req.phone(), req.companyId(),
                req.lifecycle(), req.source());
        contact.setOwnerId(owners.reassign(contact.getOwnerId(), req.ownerId(), me));
    }

    @Transactional
    public void patch(UUID id, ContactPatch patch, CurrentUser me) {
        Contact contact = get(id);
        if (patch.lifecycle() != null && patch.lifecycle() != contact.getLifecycle()) {
            me.requireEdit(contact.getOwnerId());
            contact.setLifecycle(patch.lifecycle());
        }
        contact.setOwnerId(owners.reassign(contact.getOwnerId(), patch.ownerId(), me));
    }

    @Transactional
    public void delete(UUID id, CurrentUser me) {
        Contact contact = get(id);
        me.requireEdit(contact.getOwnerId());
        contacts.delete(contact);
    }

    private Contact get(UUID id) {
        return contacts.findById(id).orElseThrow(ContactService::notFound);
    }

    private void requireCompany(UUID companyId) {
        if (companyId != null && !companies.existsById(companyId)) {
            throw ApiException.badRequest("L'entreprise choisie n'existe pas.");
        }
    }

    private static ApiException notFound() {
        return ApiException.notFound("Contact introuvable.");
    }
}
