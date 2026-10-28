package dj.redseabunkering.crm.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;

import dj.redseabunkering.crm.common.AppProperties;
import dj.redseabunkering.crm.user.User;

/** Construit réellement l'e-mail (texte + HTML) pour détecter les erreurs de MIME. */
class AuthMailerTests {

    @Test
    void verificationEmailContainsLinkInTextAndHtml() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenAnswer(i -> new MimeMessage(Session.getInstance(new Properties())));
        var app = new AppProperties("https://crm.example", List.of(), true, false, "crm@redseabunkering.com");
        var mailer = new AuthMailer(sender, app);

        mailer.sendEmailVerification(new User("fa@rsb.dj", "x", "Fatouma", "Ali"), "abc_123");

        var captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(captor.capture());
        MimeMessage message = captor.getValue();
        assertThat(message.getSubject()).contains("Confirmez votre adresse e-mail");
        var texts = new ArrayList<String>();
        collect(message, texts);
        assertThat(texts).hasSize(2)
                .allSatisfy(t -> assertThat(t).contains("https://crm.example/verify-email?token=abc_123"));
    }

    /** Parcourt récursivement les parties MIME et garde les corps texte/HTML décodés. */
    private static void collect(Part part, List<String> out) throws Exception {
        Object content = part.getContent();
        if (content instanceof Multipart mp) {
            for (int i = 0; i < mp.getCount(); i++) {
                collect(mp.getBodyPart(i), out);
            }
        } else if (content instanceof String text) {
            out.add(text);
        }
    }
}
