import { AbstractControl, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';

/** Même règle que l'API : 10 caractères minimum, une majuscule, un chiffre. */
export const PASSWORD_RULE = '10 caractères minimum, dont une majuscule et un chiffre.';
export const strongPassword: ValidatorFn[] = [Validators.required, Validators.pattern(/^(?=.*[A-Z])(?=.*\d).{10,128}$/)];

/** Validateur de groupe : les deux champs doivent être identiques. */
export function sameAs(field: string, confirm: string): ValidatorFn {
  return (group: AbstractControl): ValidationErrors | null => {
    const a = group.get(field)?.value;
    const b = group.get(confirm)?.value;
    return a && b && a !== b ? { mismatch: true } : null;
  };
}

/** Message d'erreur d'un champ : erreur serveur prioritaire, sinon validation locale une fois le champ touché. */
export function controlError(control: AbstractControl | null, server?: string, label = 'Ce champ'): string | null {
  if (server) {
    return server;
  }
  if (!control || !control.touched || !control.errors) {
    return null;
  }
  const e = control.errors;
  if (e['required']) return `${label} est obligatoire.`;
  if (e['email']) return 'Adresse e-mail invalide.';
  if (e['pattern']) return PASSWORD_RULE;
  if (e['maxlength']) return `${e['maxlength'].requiredLength} caractères maximum.`;
  return 'Valeur invalide.';
}
