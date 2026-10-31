import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

/** Types et accès API du module Devis et e-mails (étape 5). */

export type QuoteStatus = 'BROUILLON' | 'ENVOYE' | 'ACCEPTE' | 'REFUSE' | 'EXPIRE';

export const QUOTE_STATUS: Record<QuoteStatus, { label: string; cls: string }> = {
  BROUILLON: { label: 'Brouillon', cls: '' },
  ENVOYE: { label: 'Envoyé', cls: 'p-sea' },
  ACCEPTE: { label: 'Accepté', cls: 'p-good' },
  REFUSE: { label: 'Refusé', cls: 'p-bad' },
  EXPIRE: { label: 'Expiré', cls: 'p-warn' },
};

export interface Quote {
  id: string;
  number: string;
  dealId: string;
  vesselName: string;
  imo: string | null;
  companyId: string | null;
  companyName: string | null;
  contactId: string | null;
  contactName: string | null;
  contactEmail: string | null;
  fuelGrade: string;
  fuelGradeLabel: string;
  quantityMt: number;
  priceUsdPerMt: number;
  bargeFeeUsd: number;
  productAmountUsd: number;
  totalUsd: number;
  status: QuoteStatus;
  validUntil: string;
  sentAt: string | null;
  createdAt: string;
  eta: string | null;
  deliveryLocationLabel: string | null;
  ownerId: string | null;
  ownerName: string | null;
}

export interface EmailDraft {
  subject: string;
  body: string;
  dealId: string | null;
}

export interface EmailTemplate {
  id: string;
  name: string;
  subject: string;
  body: string;
}

export interface SentEmail {
  id: string;
  subject: string;
  toAddress: string;
  contactId: string | null;
  contactName: string | null;
  status: 'ENVOYE' | 'OUVERT' | 'ERREUR';
  sentAt: string;
  openedAt: string | null;
}

export interface Sequence {
  id: string;
  name: string;
  description: string | null;
  autoSend: boolean;
  steps: { id: string; dayOffset: number; type: 'EMAIL' | 'CALL' | 'TASK'; label: string }[];
  enrolled: { id: string; contactId: string; contactName: string; ownerId: string | null }[];
}

function params(p: Record<string, string | null | undefined>): HttpParams {
  let hp = new HttpParams();
  for (const [k, v] of Object.entries(p)) if (v) hp = hp.set(k, v);
  return hp;
}

@Injectable({ providedIn: 'root' })
export class DocumentsApi {
  private readonly http = inject(HttpClient);

  // Devis
  quotes(dealId?: string | null) {
    return this.http.get<Quote[]>('/api/quotes', { params: params({ dealId }) });
  }
  quote(id: string) {
    return this.http.get<Quote>(`/api/quotes/${id}`);
  }
  createQuote(body: { dealId: string; priceUsdPerMt: number | null; bargeFeeUsd: number | null }) {
    return this.http.post<{ id: string }>('/api/quotes', body);
  }
  quotePdf(id: string) {
    return this.http.get(`/api/quotes/${id}/pdf`, { responseType: 'blob' });
  }
  quoteEmailDraft(id: string) {
    return this.http.get<EmailDraft>(`/api/quotes/${id}/email-draft`);
  }
  sendQuote(id: string, body: { subject: string; body: string }) {
    return this.http.post<void>(`/api/quotes/${id}/send`, body);
  }
  acceptQuote(id: string) {
    return this.http.post<void>(`/api/quotes/${id}/accept`, null);
  }
  refuseQuote(id: string) {
    return this.http.post<void>(`/api/quotes/${id}/refuse`, null);
  }

  // Modèles et envois
  templates() {
    return this.http.get<EmailTemplate[]>('/api/email-templates');
  }
  updateTemplate(id: string, body: { name: string; subject: string; body: string }) {
    return this.http.put<EmailTemplate>(`/api/email-templates/${id}`, body);
  }
  draft(templateId: string, contactId?: string | null, dealId?: string | null) {
    return this.http.get<EmailDraft>('/api/emails/draft', { params: params({ templateId, contactId, dealId }) });
  }
  sendEmail(body: { contactId: string; dealId: string | null; subject: string; body: string }) {
    return this.http.post<void>('/api/emails/send', body);
  }
  sentEmails(limit = 12) {
    return this.http.get<SentEmail[]>('/api/emails/sent', { params: params({ limit: String(limit) }) });
  }

  // Séquences
  sequences() {
    return this.http.get<Sequence[]>('/api/sequences');
  }
  enroll(sequenceId: string, contactId: string) {
    return this.http.post<{ id: string }>(`/api/sequences/${sequenceId}/enroll`, { contactId });
  }
  stopEnrollment(id: string) {
    return this.http.post<void>(`/api/sequences/enrollments/${id}/stop`, null);
  }
  setAutoSend(sequenceId: string, autoSend: boolean) {
    return this.http.patch<void>(`/api/sequences/${sequenceId}`, { autoSend });
  }
}
