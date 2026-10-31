package dj.redseabunkering.crm.campaign;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dj.redseabunkering.crm.auth.ClientInfo;
import dj.redseabunkering.crm.campaign.MarketingDtos.PublicLeadRequest;
import dj.redseabunkering.crm.common.ApiException;
import dj.redseabunkering.crm.contact.Sources;
import dj.redseabunkering.crm.lead.LeadDtos.LeadRequest;
import dj.redseabunkering.crm.lead.LeadService;

/** Point d'entrée du formulaire « Demande de cotation » du site : arrive dans les leads entrants. */
@RestController
public class PublicLeadController {

    private static final String DEFAULT_SOURCE = "Site web";

    private final PublicFormGuard guard;
    private final CaptchaVerifier captcha;
    private final LeadService leads;

    public PublicLeadController(PublicFormGuard guard, CaptchaVerifier captcha, LeadService leads) {
        this.guard = guard;
        this.captcha = captcha;
        this.leads = leads;
    }

    @PostMapping("/api/public/leads")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> submit(@RequestHeader(name = "X-Api-Key", required = false) String apiKey,
            @Valid @RequestBody PublicLeadRequest req, HttpServletRequest request) {
        String ip = ClientInfo.from(request).ip();
        guard.check(apiKey, ip);
        // Champ piège rempli : un robot, à qui on répond comme à tout le monde pour ne rien lui apprendre
        if (req.website() != null && !req.website().isBlank()) {
            return Map.of("status", "received");
        }
        if (!captcha.verify(req.captchaToken(), ip)) {
            throw ApiException.badRequest("Vérification anti-robot échouée. Réessayez.");
        }
        String source = req.source() != null && Sources.ALL.contains(req.source()) ? req.source() : DEFAULT_SOURCE;
        leads.create(new LeadRequest(req.name(), req.company(), req.email(), req.phone(), req.vessel(), req.imo(),
                req.fuelGrade(), req.quantityMt(), req.eta(), null, source, req.message()));
        return Map.of("status", "received");
    }
}
