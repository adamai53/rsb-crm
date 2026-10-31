package dj.redseabunkering.crm.demo;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code referenceDate} : jour « J » des données de démonstration, au format AAAA-MM-JJ, ou « today ». */
@ConfigurationProperties(prefix = "app.demo")
public record DemoProperties(String password, String referenceDate) {
}
