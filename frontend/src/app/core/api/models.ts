/** Types des réponses de l'API du référentiel (étape 3). */

export type Lifecycle = 'LEAD' | 'MQL' | 'SQL' | 'OPPORTUNITE' | 'CLIENT';
export type CompanyType = 'ARMATEUR' | 'SHIP_MANAGER' | 'AGENT_MARITIME' | 'TRADER' | 'AFFRETEUR';
export type ActivityType = 'NOTE' | 'CALL' | 'EMAIL' | 'MEETING' | 'SYSTEM';

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface Option {
  code: string;
  label: string;
}

export interface ReferenceData {
  companyTypes: Option[];
  lifecycles: Option[];
  sources: string[];
  stages: { code: string; label: string; probability: number; won: boolean; lost: boolean }[];
  fuelGrades: { code: string; label: string; referencePrice: number }[];
  deliveryLocations: Option[];
  users: UserRef[];
}

export interface UserRef {
  id: string;
  name: string;
  initials: string;
  role: string;
  active: boolean;
}

export interface CompanyRow {
  id: string;
  name: string;
  type: CompanyType;
  country: string | null;
  city: string | null;
  fleetSize: number | null;
  website: string | null;
  ownerId: string | null;
  contactsCount: number;
  openDealsCount: number;
  deliveredVolumeMt: number;
  revenueUsd: number;
  createdAt: string;
}

export interface ContactMini {
  id: string;
  firstName: string;
  lastName: string;
  title: string | null;
  lifecycle: Lifecycle;
}

export interface DealSummary {
  id: string;
  vesselName: string;
  fuelGrade: string;
  quantityMt: number;
  amountUsd: number;
  eta: string | null;
  stage: string;
  stageLabel: string;
  won: boolean;
  lost: boolean;
}

export interface TaskSummary {
  id: string;
  title: string;
  dueDate: string;
  priority: 'HAUTE' | 'MOYENNE' | 'BASSE';
  done: boolean;
  ownerId: string | null;
}

export interface CompanyDetail {
  company: CompanyRow;
  contacts: ContactMini[];
  deals: DealSummary[];
  editable: boolean;
}

export interface CompanyRequest {
  name: string;
  type: CompanyType;
  country: string | null;
  city: string | null;
  fleetSize: number | null;
  website: string | null;
  ownerId: string | null;
}

export interface ContactRow {
  id: string;
  firstName: string;
  lastName: string;
  title: string | null;
  email: string | null;
  phone: string | null;
  companyId: string | null;
  companyName: string | null;
  lifecycle: Lifecycle;
  source: string | null;
  ownerId: string | null;
  score: number;
  lastActivityAt: string;
  createdAt: string;
}

export interface ContactList extends Page<ContactRow> {
  lifecycleCounts: Record<Lifecycle, number>;
  allCount: number;
}

export interface Score {
  total: number;
  parts: { label: string; points: number }[];
}

export interface ContactDetail {
  contact: ContactRow;
  score: Score;
  deals: DealSummary[];
  tasks: TaskSummary[];
  editable: boolean;
}

export interface ContactRequest {
  firstName: string;
  lastName: string;
  title: string | null;
  email: string | null;
  phone: string | null;
  companyId: string | null;
  lifecycle: Lifecycle;
  source: string | null;
  ownerId: string | null;
}

export interface Activity {
  id: string;
  type: ActivityType;
  text: string;
  occurredAt: string;
  authorId: string | null;
  dealId: string | null;
  dealVessel: string | null;
  contactId: string | null;
  contactName: string | null;
  companyId: string | null;
}

export interface ImportReport {
  rows: number;
  created: number;
  skipped: number;
  companiesCreated: number;
  issues: { line: number; message: string }[];
}

export interface SearchResult {
  type: 'contact' | 'company' | 'deal';
  id: string;
  label: string;
  sub: string;
}
