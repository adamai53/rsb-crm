/** Formats français du prototype : espaces insécables, USD et MT, dates relatives. */

const NUM = new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 0 });
const DATE = new Intl.DateTimeFormat('fr-FR', { day: '2-digit', month: 'short' });
const DATE_Y = new Intl.DateTimeFormat('fr-FR', { day: 'numeric', month: 'long', year: 'numeric' });

export const fmtN = (n: number | null | undefined) => NUM.format(Math.round(n ?? 0));
export const fmtUSD = (n: number | null | undefined) => `${fmtN(n)} USD`;
export const fmtMT = (n: number | null | undefined) => `${fmtN(n)} MT`;

/** Montant compact : 612 k USD, 1,25 M USD. */
export function fmtK(n: number | null | undefined): string {
  const v = n ?? 0;
  if (v >= 1e6) return `${(v / 1e6).toFixed(2).replace('.', ',')} M USD`;
  if (v >= 1e3) return `${Math.round(v / 1e3)} k USD`;
  return fmtUSD(v);
}

/** Les dates seules (AAAA-MM-JJ) sont lues en heure locale, pas en UTC. */
function parse(iso: string): Date {
  return iso.length === 10 ? new Date(`${iso}T00:00:00`) : new Date(iso);
}

export const fmtDate = (iso: string | null | undefined) => (iso ? DATE.format(parse(iso)) : '—');
export const fmtDateY = (iso: string | null | undefined) => (iso ? DATE_Y.format(parse(iso)) : '—');

function startOfDay(d: Date): number {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
}

/** Écart en jours entre une date et aujourd'hui (négatif = passé). */
export function daysFrom(iso: string, now = new Date()): number {
  return Math.round((startOfDay(parse(iso)) - startOfDay(now)) / 864e5);
}

/** « aujourd'hui », « hier », « il y a 3 j », « dans 2 j ». */
export function rel(iso: string | null | undefined, now = new Date()): string {
  if (!iso) return '—';
  const n = daysFrom(iso, now);
  if (n === 0) return "aujourd'hui";
  if (n === -1) return 'hier';
  if (n === 1) return 'demain';
  return n < 0 ? `il y a ${-n} j` : `dans ${n} j`;
}

/** Télécharge un fichier reçu de l'API. */
export function saveBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
