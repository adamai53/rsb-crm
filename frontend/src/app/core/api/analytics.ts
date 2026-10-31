import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { forkJoin } from 'rxjs';

import { Activity } from './models';
import { DealRow } from './sales';

/** Types et accès API du tableau de bord, des rapports et du marketing (étape 6). */

export interface LabelCount {
  label: string;
  count: number;
}

export interface Dashboard {
  today: string;
  weightedPipelineUsd: number;
  openAmountUsd: number;
  openCount: number;
  requests30Days: number;
  newLeads: number;
  conversionRatePct: number;
  wonCount: number;
  lostCount: number;
  deliveredMt90Days: number;
  byStage: { code: string; label: string; count: number; amountUsd: number }[];
  bySource: LabelCount[];
  nextCalls: DealRow[];
  recentActivity: Activity[];
}

export interface Reports {
  revenue: { month: string; revenueUsd: number }[];
  funnel: { code: string; label: string; count: number }[];
  volumes: { code: string; label: string; volumeMt: number }[];
  lostReasons: LabelCount[];
  performance: {
    userId: string;
    name: string;
    initials: string;
    delivered: number;
    revenueUsd: number;
    conversionRatePct: number;
    openAmountUsd: number;
  }[];
  cycle: { averageDays: number | null; delivered: number };
}

export interface CampaignRow {
  id: string;
  name: string;
  channel: string;
  spendUsd: number;
  impressions: number;
  clicks: number;
  periodStart: string | null;
  periodEnd: string | null;
  leads: number;
  won: number;
  revenueUsd: number;
  costPerLeadUsd: number | null;
  revenuePerUsd: number | null;
}

export interface MarketingSummary {
  totals: {
    spendUsd: number;
    leads: number;
    won: number;
    revenueUsd: number;
    costPerLeadUsd: number | null;
    revenuePerUsd: number | null;
    leadToClientPct: number;
  };
  campaigns: CampaignRow[];
  channels: {
    channel: string;
    spendUsd: number;
    leads: number;
    won: number;
    revenueUsd: number;
    costPerLeadUsd: number | null;
    revenuePerUsd: number | null;
  }[];
}

export interface CampaignRequest {
  name: string;
  channel: string;
  spendUsd: number;
  impressions: number;
  clicks: number;
  periodStart: string | null;
  periodEnd: string | null;
}

export interface FormConfig {
  endpoint: string;
  apiKey: string;
  captchaSiteKey: string;
  captchaEnabled: boolean;
  allowedOrigins: string[];
}

@Injectable({ providedIn: 'root' })
export class AnalyticsApi {
  private readonly http = inject(HttpClient);

  dashboard() {
    return this.http.get<Dashboard>('/api/dashboard');
  }

  reports() {
    return forkJoin({
      revenue: this.http.get<Reports['revenue']>('/api/reports/monthly-revenue'),
      funnel: this.http.get<Reports['funnel']>('/api/reports/funnel'),
      volumes: this.http.get<Reports['volumes']>('/api/reports/product-volumes'),
      lostReasons: this.http.get<Reports['lostReasons']>('/api/reports/lost-reasons'),
      performance: this.http.get<Reports['performance']>('/api/reports/sales-performance'),
      cycle: this.http.get<Reports['cycle']>('/api/reports/sales-cycle'),
    });
  }

  marketing() {
    return this.http.get<MarketingSummary>('/api/marketing/summary');
  }
  formConfig() {
    return this.http.get<FormConfig>('/api/marketing/form-config');
  }
  createCampaign(body: CampaignRequest) {
    return this.http.post<{ id: string }>('/api/campaigns', body);
  }
  updateCampaign(id: string, body: CampaignRequest) {
    return this.http.put<void>(`/api/campaigns/${id}`, body);
  }
  deleteCampaign(id: string) {
    return this.http.delete<void>(`/api/campaigns/${id}`);
  }
}
