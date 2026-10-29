import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

/** Types et accès API du module Ventes (étape 4) : transactions, leads, tâches, escales. */

export type TaskPriority = 'HAUTE' | 'MOYENNE' | 'BASSE';
export type LeadStatus = 'NOUVEAU' | 'CONVERTI' | 'REJETE';

export interface DealRow {
  id: string;
  vesselName: string;
  imo: string | null;
  companyId: string | null;
  companyName: string | null;
  contactId: string | null;
  contactName: string | null;
  fuelGrade: string;
  quantityMt: number;
  priceUsdPerMt: number;
  amountUsd: number;
  stage: string;
  stageLabel: string;
  probability: number;
  won: boolean;
  lost: boolean;
  ownerId: string | null;
  source: string | null;
  eta: string | null;
  deliveryLocation: string | null;
  deliveryLocationLabel: string | null;
  closedAt: string | null;
  lostReason: string | null;
  createdAt: string;
}

export interface DealSummaryStats {
  openCount: number;
  openAmountUsd: number;
  weightedPipelineUsd: number;
  wonCount: number;
  lostCount: number;
  conversionRatePct: number;
}

export interface DealList {
  content: DealRow[];
  summary: DealSummaryStats;
}

export interface TaskItem {
  id: string;
  title: string;
  dueDate: string;
  priority: TaskPriority;
  ownerId: string | null;
  done: boolean;
  doneAt?: string | null;
  dealId?: string | null;
  dealVessel?: string | null;
  contactId?: string | null;
  contactName?: string | null;
}

export interface DealDetail {
  deal: DealRow;
  tasks: TaskItem[];
  editable: boolean;
}

export interface DealRequest {
  vesselName: string;
  imo: string | null;
  companyId: string | null;
  contactId: string | null;
  fuelGrade: string;
  quantityMt: number;
  priceUsdPerMt: number | null;
  stage: string | null;
  source: string | null;
  eta: string | null;
  deliveryLocation: string | null;
  lostReason: string | null;
  ownerId: string | null;
}

export interface Lead {
  id: string;
  name: string;
  companyName: string | null;
  email: string | null;
  phone: string | null;
  vesselName: string | null;
  imo: string | null;
  fuelGrade: string | null;
  quantityMt: number | null;
  eta: string | null;
  deliveryLocation: string | null;
  source: string | null;
  message: string | null;
  status: LeadStatus;
  receivedAt: string;
  convertedDealId: string | null;
  estimatedValueUsd: number | null;
}

export interface LeadRequest {
  name: string;
  companyName: string | null;
  email: string | null;
  phone: string | null;
  vesselName: string;
  imo: string | null;
  fuelGrade: string;
  quantityMt: number | null;
  eta: string | null;
  deliveryLocation: string | null;
  source: string | null;
  message: string | null;
}

export interface ConversionResult {
  dealId: string;
  contactId: string;
  companyId: string | null;
  ownerId: string;
  ownerName: string;
  companyCreated: boolean;
  contactCreated: boolean;
}

export interface TaskRequest {
  title: string;
  dueDate: string;
  priority: TaskPriority;
  ownerId: string | null;
  dealId: string | null;
  contactId: string | null;
  companyId: string | null;
}

export interface UpcomingCalls {
  today: string;
  days: number;
  calls: DealRow[];
}

function params(p: Record<string, string | null | undefined>): HttpParams {
  let hp = new HttpParams();
  for (const [k, v] of Object.entries(p)) if (v) hp = hp.set(k, v);
  return hp;
}

@Injectable({ providedIn: 'root' })
export class SalesApi {
  private readonly http = inject(HttpClient);

  // Transactions
  deals(p: { ownerId?: string | null; q?: string | null; stage?: string | null } = {}) {
    return this.http.get<DealList>('/api/deals', { params: params(p) });
  }
  deal(id: string) {
    return this.http.get<DealDetail>(`/api/deals/${id}`);
  }
  createDeal(body: DealRequest) {
    return this.http.post<{ id: string }>('/api/deals', body);
  }
  updateDeal(id: string, body: DealRequest) {
    return this.http.put<void>(`/api/deals/${id}`, body);
  }
  moveDeal(id: string, stage: string, lostReason?: string | null) {
    return this.http.patch<void>(`/api/deals/${id}/stage`, { stage, lostReason: lostReason ?? null });
  }
  setDealOwner(id: string, ownerId: string) {
    return this.http.patch<void>(`/api/deals/${id}/owner`, { ownerId });
  }
  deleteDeal(id: string) {
    return this.http.delete<void>(`/api/deals/${id}`);
  }
  lostReasons() {
    return this.http.get<string[]>('/api/deals/lost-reasons');
  }

  // Leads
  leads(status?: LeadStatus | null) {
    return this.http.get<Lead[]>('/api/leads', { params: params({ status }) });
  }
  lead(id: string) {
    return this.http.get<Lead>(`/api/leads/${id}`);
  }
  createLead(body: LeadRequest) {
    return this.http.post<{ id: string }>('/api/leads', body);
  }
  convertLead(id: string) {
    return this.http.post<ConversionResult>(`/api/leads/${id}/convert`, null);
  }
  rejectLead(id: string) {
    return this.http.post<void>(`/api/leads/${id}/reject`, null);
  }

  // Tâches
  tasks(ownerId?: string | null) {
    return this.http.get<TaskItem[]>('/api/tasks', { params: params({ ownerId }) });
  }
  createTask(body: TaskRequest) {
    return this.http.post<{ id: string }>('/api/tasks', body);
  }
  setTaskDone(id: string, done: boolean) {
    return this.http.patch<void>(`/api/tasks/${id}/done`, { done });
  }
  deleteTask(id: string) {
    return this.http.delete<void>(`/api/tasks/${id}`);
  }
  dueTasksCount() {
    return this.http.get<{ due: number }>('/api/tasks/due-count');
  }

  // Escales
  upcomingCalls(days = 21) {
    return this.http.get<UpcomingCalls>('/api/calls/upcoming', { params: params({ days: String(days) }) });
  }
}
