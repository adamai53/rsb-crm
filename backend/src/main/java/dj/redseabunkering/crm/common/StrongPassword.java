package dj.redseabunkering.crm.common;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Politique de mot de passe : 10 caractères minimum, au moins une majuscule et un chiffre. */
@NotBlank(message = "Le mot de passe est obligatoire.")
@Pattern(regexp = "^(?=.*[A-Z])(?=.*\\d).{10,128}$",
        message = "Le mot de passe doit contenir au moins 10 caractères, dont une majuscule et un chiffre.")
@Constraint(validatedBy = {})
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface StrongPassword {

    String message() default "Mot de passe trop faible.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
