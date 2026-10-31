import { FormConfig } from '../../core/api/analytics';

export interface SnippetGrade {
  code: string;
  label: string;
}

function attr(s: string): string {
  return s.replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

/** Valeur insérée dans le script du snippet : JSON, et « < » échappé pour ne jamais fermer la balise script. */
function js(value: string): string {
  return JSON.stringify(value).replace(/</g, '\\u003c');
}

/**
 * Code à coller dans un bloc « HTML personnalisé » de WordPress. La source de la demande est déduite du paramètre
 * utm_source de la page d'arrivée, ce qui relie le lead à sa campagne.
 */
export function buildSnippet(config: FormConfig, grades: SnippetGrade[]): string {
  const options = grades.map((g) => `      <option value="${attr(g.code)}">${attr(g.label)}</option>`).join('\n');
  const turnstileScript = config.captchaEnabled
    ? '<script src="https://challenges.cloudflare.com/turnstile/v0/api.js" async defer></script>\n'
    : '';
  const turnstileWidget = config.captchaEnabled
    ? `\n  <div class="cf-turnstile" data-sitekey="${attr(config.captchaSiteKey)}"></div>`
    : '';
  return `<!-- Formulaire « Demande de cotation » Red Sea Bunkering -->
${turnstileScript}<form id="rsb-quote-form" novalidate>
  <p><label>Nom<br><input name="name" required maxlength="160"></label></p>
  <p><label>Société<br><input name="company" required maxlength="160"></label></p>
  <p><label>E-mail<br><input name="email" type="email" required maxlength="254"></label></p>
  <p><label>Téléphone<br><input name="phone" maxlength="40"></label></p>
  <p><label>Navire<br><input name="vessel" required maxlength="120"></label></p>
  <p><label>Numéro IMO<br><input name="imo" pattern="[0-9]{7}" maxlength="7"></label></p>
  <p><label>Produit<br><select name="fuelGrade" required>
${options}
    </select></label></p>
  <p><label>Quantité (MT)<br><input name="quantityMt" type="number" min="1" max="100000" required></label></p>
  <p><label>ETA Djibouti<br><input name="eta" type="date"></label></p>
  <p><label>Message<br><textarea name="message" rows="3" maxlength="5000"></textarea></label></p>
  <p style="position:absolute;left:-9999px" aria-hidden="true"><label>Site web<input name="website" tabindex="-1" autocomplete="off"></label></p>${turnstileWidget}
  <p><button type="submit">Envoyer la demande</button></p>
  <p id="rsb-quote-status" role="status"></p>
</form>
<script>
(function () {
  var ENDPOINT = ${js(config.endpoint)};
  var API_KEY = ${js(config.apiKey)};
  var CHANNELS = { google: 'Google Ads', linkedin: 'LinkedIn', facebook: 'Meta Ads', instagram: 'Meta Ads', meta: 'Meta Ads', newsletter: 'E-mail direct', email: 'E-mail direct', salon: 'Salon / événement' };
  var form = document.getElementById('rsb-quote-form');
  var status = document.getElementById('rsb-quote-status');
  var utm = (new URLSearchParams(location.search).get('utm_source') || '').toLowerCase();
  try {
    if (utm) sessionStorage.setItem('rsb_utm', utm); else utm = sessionStorage.getItem('rsb_utm') || '';
  } catch (e) {}
  form.addEventListener('submit', function (event) {
    event.preventDefault();
    if (!form.reportValidity()) return;
    var button = form.querySelector('button[type=submit]');
    var data = Object.fromEntries(new FormData(form).entries());
    data.captchaToken = data['cf-turnstile-response'] || null;
    delete data['cf-turnstile-response'];
    data.source = CHANNELS[utm] || 'Site web';
    data.eta = data.eta || null;
    button.disabled = true;
    status.textContent = 'Envoi en cours…';
    fetch(ENDPOINT, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-Api-Key': API_KEY },
      body: JSON.stringify(data)
    }).then(function (response) {
      if (response.ok) {
        form.reset();
        status.textContent = 'Merci, votre demande a bien été reçue. Nous revenons vers vous rapidement.';
      } else if (response.status === 429) {
        status.textContent = 'Trop de demandes envoyées. Réessayez plus tard.';
      } else {
        status.textContent = 'Votre demande n’a pas pu être envoyée. Vérifiez le formulaire et réessayez.';
      }
    }).catch(function () {
      status.textContent = 'Serveur injoignable. Réessayez dans quelques instants.';
    }).finally(function () {
      button.disabled = false;
      if (window.turnstile) window.turnstile.reset();
    });
  });
})();
</script>`;
}
