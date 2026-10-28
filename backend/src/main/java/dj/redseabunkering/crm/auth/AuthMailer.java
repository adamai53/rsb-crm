package dj.redseabunkering.crm.auth;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import jakarta.mail.MessagingException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import dj.redseabunkering.crm.common.AppProperties;
import dj.redseabunkering.crm.user.User;

/**
 * E-mails transactionnels du parcours de compte. Un échec d'envoi est journalisé (sans le lien)
 * mais ne bloque pas l'opération : l'administrateur peut renvoyer la vérification.
 */
@Service
public class AuthMailer {

    private static final Logger log = LoggerFactory.getLogger(AuthMailer.class);

    private final JavaMailSender sender;
    private final AppProperties app;

    public AuthMailer(JavaMailSender sender, AppProperties app) {
        this.sender = sender;
        this.app = app;
    }

    public void sendEmailVerification(User user, String token) {
        String link = link("/verify-email", token);
        send(user.getEmail(), "Confirmez votre adresse e-mail",
                "Bonjour " + user.getFirstName() + ",",
                "Pour activer votre compte sur le CRM Red Sea Bunkering, confirmez votre adresse e-mail. "
                        + "Ce lien est valable 24 heures et ne peut servir qu'une fois.",
                "Confirmer mon adresse", link);
    }

    public void sendAccountAlreadyExists(User user) {
        send(user.getEmail(), "Vous avez déjà un compte",
                "Bonjour " + user.getFirstName() + ",",
                "Une inscription vient d'être tentée avec votre adresse, mais vous avez déjà un compte. "
                        + "Si vous avez oublié votre mot de passe, vous pouvez le réinitialiser.",
                "Réinitialiser mon mot de passe", app.publicUrl() + "/forgot-password");
    }

    public void sendPasswordReset(User user, String token) {
        send(user.getEmail(), "Réinitialisation de votre mot de passe",
                "Bonjour " + user.getFirstName() + ",",
                "Vous avez demandé à réinitialiser votre mot de passe. Ce lien est valable 1 heure. "
                        + "Si vous n'êtes pas à l'origine de cette demande, ignorez cet e-mail.",
                "Choisir un nouveau mot de passe", link("/reset-password", token));
    }

    public void sendApprovalRequest(User admin, User newUser) {
        send(admin.getEmail(), "Nouveau compte à valider : " + newUser.fullName(),
                "Bonjour " + admin.getFirstName() + ",",
                newUser.fullName() + " (" + newUser.getEmail() + ") a confirmé son adresse e-mail "
                        + "et attend la validation de son compte.",
                "Ouvrir l'administration", app.publicUrl() + "/admin");
    }

    public void sendAccountApproved(User user) {
        send(user.getEmail(), "Votre compte est activé",
                "Bonjour " + user.getFirstName() + ",",
                "Votre compte sur le CRM Red Sea Bunkering a été validé. Vous pouvez maintenant vous connecter.",
                "Se connecter", app.publicUrl() + "/login");
    }

    private String link(String path, String token) {
        return app.publicUrl() + path + "?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }

    private void send(String to, String subject, String greeting, String text, String cta, String url) {
        try {
            var message = sender.createMimeMessage();
            var helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(app.mailFrom());
            helper.setTo(to);
            helper.setSubject(subject + " — Red Sea Bunkering CRM");
            helper.setText(greeting + "\n\n" + text + "\n\n" + cta + " : " + url + "\n\nRed Sea Bunkering",
                    html(greeting, text, cta, url));
            sender.send(message);
        } catch (MessagingException | MailException e) {
            log.warn("Envoi de l'e-mail « {} » à {} impossible : {}", subject, to, e.getMessage());
        }
    }

    private static String html(String greeting, String text, String cta, String url) {
        String u = HtmlUtils.htmlEscape(url);
        return """
                <div style="font-family:Arial,sans-serif;background:#f3f6f8;padding:24px">
                  <div style="max-width:520px;margin:auto;background:#fff;border-radius:12px;border:1px solid #dde4e8;\
                overflow:hidden">
                    <div style="height:4px;background:#00b7f1"></div>
                    <div style="padding:24px;color:#16191c;font-size:14px;line-height:1.5">
                      <p style="font-family:Georgia,serif;font-weight:700;letter-spacing:.02em;margin:0 0 16px">\
                Red Sea Bunkering</p>
                      <p>%s</p>
                      <p>%s</p>
                      <p style="margin:24px 0"><a href="%s" style="background:#d8141c;color:#fff;padding:10px 18px;\
                border-radius:8px;text-decoration:none;font-weight:600">%s</a></p>
                      <p style="color:#56606a;font-size:12px">Si le bouton ne fonctionne pas, copiez ce lien :<br>%s</p>
                    </div>
                  </div>
                </div>"""
                .formatted(HtmlUtils.htmlEscape(greeting), HtmlUtils.htmlEscape(text), u, HtmlUtils.htmlEscape(cta), u);
    }
}
