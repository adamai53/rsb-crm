package dj.redseabunkering.crm.company;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dj.redseabunkering.crm.common.ApiException;
import dj.redseabunkering.crm.common.CurrentUser;
import dj.redseabunkering.crm.company.CompanyDtos.CompanyDetail;
import dj.redseabunkering.crm.company.CompanyDtos.CompanyRequest;
import dj.redseabunkering.crm.deal.RelatedRecords;
import dj.redseabunkering.crm.user.OwnerAssignment;

@Service
public class CompanyService {

    private final CompanyRepository companies;
    private final CompanyQueries queries;
    private final RelatedRecords related;
    private final OwnerAssignment owners;

    public CompanyService(CompanyRepository companies, CompanyQueries queries, RelatedRecords related,
            OwnerAssignment owners) {
        this.companies = companies;
        this.queries = queries;
        this.related = related;
        this.owners = owners;
    }

    @Transactional(readOnly = true)
    public CompanyDetail detail(UUID id, CurrentUser me) {
        var row = queries.find(id).orElseThrow(CompanyService::notFound);
        return new CompanyDetail(row, queries.contacts(id), related.dealsOfCompany(id), me.canEdit(row.ownerId()));
    }

    @Transactional
    public UUID create(CompanyRequest req, CurrentUser me) {
        requireUniqueName(req.name(), null);
        var company = new Company(req.name(), req.type(), owners.forNewRecord(req.ownerId(), me));
        company.update(req.name(), req.type(), req.country(), req.city(), req.fleetSize(), req.website());
        return companies.save(company).getId();
    }

    @Transactional
    public void update(UUID id, CompanyRequest req, CurrentUser me) {
        Company company = get(id);
        me.requireEdit(company.getOwnerId());
        requireUniqueName(req.name(), id);
        company.update(req.name(), req.type(), req.country(), req.city(), req.fleetSize(), req.website());
        company.setOwnerId(owners.reassign(company.getOwnerId(), req.ownerId(), me));
    }

    @Transactional
    public void changeOwner(UUID id, UUID ownerId, CurrentUser me) {
        Company company = get(id);
        company.setOwnerId(owners.reassign(company.getOwnerId(), ownerId, me));
    }

    @Transactional
    public void delete(UUID id, CurrentUser me) {
        Company company = get(id);
        me.requireEdit(company.getOwnerId());
        companies.delete(company);
    }

    /** Pour l'import CSV et la conversion des leads : retrouve l'entreprise par nom (casse ignorée) ou la crée. */
    @Transactional
    public Company findOrCreate(String name, UUID ownerId) {
        return companies.findByNameIgnoreCase(name.trim())
                .orElseGet(() -> companies.save(new Company(name, CompanyType.ARMATEUR, ownerId)));
    }

    Company get(UUID id) {
        return companies.findById(id).orElseThrow(CompanyService::notFound);
    }

    private void requireUniqueName(String name, UUID exceptId) {
        companies.findByNameIgnoreCase(name.trim())
                .filter(c -> !c.getId().equals(exceptId))
                .ifPresent(c -> {
                    throw ApiException.conflict("Une entreprise porte déjà ce nom : « " + c.getName() + " ».");
                });
    }

    private static ApiException notFound() {
        return ApiException.notFound("Entreprise introuvable.");
    }
}
