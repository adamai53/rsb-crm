import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

import {
  Activity,
  ActivityType,
  CompanyDetail,
  CompanyRequest,
  CompanyRow,
  ContactDetail,
  ContactList,
  ContactRequest,
  ImportReport,
  Lifecycle,
  Page,
  SearchResult,
} from './models';

type Params = Record<string, string | number | boolean | null | undefined>;

function params(p: Params): HttpParams {
  let hp = new HttpParams();
  for (const [k, v] of Object.entries(p)) {
    if (v !== null && v !== undefined && v !== '') hp = hp.set(k, String(v));
  }
  return hp;
}

/** Accès à l'API du référentiel : entreprises, contacts, activités, recherche. */
@Injectable({ providedIn: 'root' })
export class CrmApi {
  private readonly http = inject(HttpClient);

  // Entreprises
  companies(p: { q?: string; ownerId?: string | null; sort?: string; page?: number; size?: number }) {
    return this.http.get<Page<CompanyRow>>('/api/companies', { params: params(p) });
  }
  company(id: string) {
    return this.http.get<CompanyDetail>(`/api/companies/${id}`);
  }
  createCompany(body: CompanyRequest) {
    return this.http.post<{ id: string }>('/api/companies', body);
  }
  updateCompany(id: string, body: CompanyRequest) {
    return this.http.put<void>(`/api/companies/${id}`, body);
  }
  setCompanyOwner(id: string, ownerId: string) {
    return this.http.patch<void>(`/api/companies/${id}/owner`, { ownerId });
  }
  deleteCompany(id: string) {
    return this.http.delete<void>(`/api/companies/${id}`);
  }

  // Contacts
  contacts(p: {
    q?: string;
    ownerId?: string | null;
    companyId?: string | null;
    lifecycle?: Lifecycle | null;
    sort?: string;
    dir?: 'asc' | 'desc';
    page?: number;
    size?: number;
  }) {
    return this.http.get<ContactList>('/api/contacts', { params: params(p) });
  }
  contact(id: string) {
    return this.http.get<ContactDetail>(`/api/contacts/${id}`);
  }
  createContact(body: ContactRequest) {
    return this.http.post<{ id: string }>('/api/contacts', body);
  }
  updateContact(id: string, body: ContactRequest) {
    return this.http.put<void>(`/api/contacts/${id}`, body);
  }
  patchContact(id: string, body: { lifecycle?: Lifecycle; ownerId?: string }) {
    return this.http.patch<void>(`/api/contacts/${id}`, body);
  }
  deleteContact(id: string) {
    return this.http.delete<void>(`/api/contacts/${id}`);
  }
  importContacts(file: File) {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<ImportReport>('/api/contacts/import', form);
  }
  exportContacts(p: { q?: string; ownerId?: string | null; lifecycle?: Lifecycle | null }) {
    return this.http.get('/api/contacts/export', { params: params(p), responseType: 'blob' });
  }

  // Activités
  activities(p: { contactId?: string; companyId?: string; dealId?: string }) {
    return this.http.get<Activity[]>('/api/activities', { params: params(p) });
  }
  logActivity(body: {
    type: ActivityType;
    text: string;
    contactId?: string | null;
    companyId?: string | null;
    dealId?: string | null;
  }) {
    return this.http.post<{ id: string }>('/api/activities', body);
  }

  // Recherche
  search(q: string) {
    return this.http.get<SearchResult[]>('/api/search', { params: params({ q }) });
  }
}
