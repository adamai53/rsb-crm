package dj.redseabunkering.crm.campaign;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dj.redseabunkering.crm.campaign.MarketingDtos.CampaignRequest;
import dj.redseabunkering.crm.campaign.MarketingDtos.CampaignRow;
import dj.redseabunkering.crm.campaign.MarketingDtos.FormConfig;
import dj.redseabunkering.crm.campaign.MarketingDtos.Summary;
import dj.redseabunkering.crm.common.ApiException;
import dj.redseabunkering.crm.common.AppProperties;
import dj.redseabunkering.crm.common.CurrentUser;

/** Campagnes (lecture pour tous, modification par MANAGER et ADMIN), synthèse marketing et réglages du formulaire. */
@RestController
@RequestMapping("/api")
public class MarketingController {

    private final MarketingService service;
    private final PublicFormProperties form;
    private final AppProperties app;

    public MarketingController(MarketingService service, PublicFormProperties form, AppProperties app) {
        this.service = service;
        this.form = form;
        this.app = app;
    }

    @GetMapping("/campaigns")
    public List<CampaignRow> list() {
        return service.list();
    }

    @GetMapping("/marketing/summary")
    public Summary summary() {
        return service.summary();
    }

    @PostMapping("/campaigns")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, UUID> create(@Valid @RequestBody CampaignRequest req, CurrentUser me) {
        requireManager(me);
        return Map.of("id", service.create(req, me));
    }

    @PutMapping("/campaigns/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void update(@PathVariable UUID id, @Valid @RequestBody CampaignRequest req, CurrentUser me) {
        requireManager(me);
        service.update(id, req, me);
    }

    @DeleteMapping("/campaigns/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, CurrentUser me) {
        requireManager(me);
        service.delete(id, me);
    }

    @GetMapping("/marketing/form-config")
    public FormConfig formConfig(CurrentUser me) {
        requireManager(me);
        String base = app.publicUrl().replaceAll("/+$", "");
        return new FormConfig(base + "/api/public/leads", form.apiKey(), form.captchaSiteKey(), form.captchaEnabled(),
                form.allowedOrigins());
    }

    private static void requireManager(CurrentUser me) {
        if (!me.canManageAll()) {
            throw ApiException.forbidden("Réservé aux managers et aux administrateurs.");
        }
    }
}
