package dj.redseabunkering.crm.demo;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Charge les données de démonstration au démarrage, seulement si APP_SEED_DEMO=true (ou profil Spring « demo »). */
@Component
@ConditionalOnProperty(name = "app.seed-demo", havingValue = "true")
public class DemoSeeder implements ApplicationRunner {

    private final DemoService demo;

    public DemoSeeder(DemoService demo) {
        this.demo = demo;
    }

    @Override
    public void run(ApplicationArguments args) {
        demo.seedIfEmpty();
    }
}
