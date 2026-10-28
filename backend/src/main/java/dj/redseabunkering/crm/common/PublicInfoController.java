package dj.redseabunkering.crm.common;

import java.time.OffsetDateTime;

import org.springframework.boot.info.BuildProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Informations publiques minimales, utilisées par le frontend pour vérifier que l'API répond. */
@RestController
@RequestMapping("/api/public")
public class PublicInfoController {

    private final ObjectProvider<BuildProperties> build;
    private final AppProperties props;

    public PublicInfoController(ObjectProvider<BuildProperties> build, AppProperties props) {
        this.build = build;
        this.props = props;
    }

    @GetMapping("/info")
    public InfoResponse info() {
        BuildProperties b = build.getIfAvailable();
        return new InfoResponse("Red Sea Bunkering CRM", b != null ? b.getVersion() : "dev",
                props.seedDemo(), OffsetDateTime.now());
    }

    public record InfoResponse(String name, String version, boolean demo, OffsetDateTime serverTime) {}
}
