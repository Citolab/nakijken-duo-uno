export type SortingMode = 'scan' | 'grading';

export interface AssessmentItem {
  identifier: string;
  title?: string;
  href: string;
  interactionType?: string;
  maxScore?: number;
  manualScoringRequired?: boolean;
  metadata?: Record<string, string>;
}

export interface Assessment {
  id: string;
  name: string;
  assessmentHref: string;
  packageBaseHref: string;
  items: AssessmentItem[];
}

export interface Delivery {
  id: string;
  code: string;
  name: string;
  assessmentId: string;
  state?: string;
  studentsStarted?: number;
  studentsFinished?: number;
}

export interface UniqueResponse {
  id: string;
  responseIdentifier: string;
  value: string;
  count: number;
  sessionIds: string[];
  responseIds: string[];
  confirmed?: boolean;
  scoreAI?: number | null;
  teacherScore?: number | null;
  effectiveAiScore?: number | null;
  scoreExternal?: number | null;
  scoreSource?: string | null;
  isEmptyResponse?: boolean;
  explanationAI?: string | null;
  issueLabel?: string | null;
  sortIndex?: number | null;
  familyId?: string | null;
  flagged?: boolean;
  flagComment?: string | null;
}

export type ItemInfo = AssessmentItem;

export interface ItemStatistics {
  itemId: string;
  itemIdentifier?: string;
  itemTitle?: string;
  interactionType?: string;
  maxScore?: number;
  count?: number;
  numberCorrect?: number;
  responses: UniqueResponse[];
}

export interface ItemStatsResponse {
  itemStatistics: ItemStatistics[];
}

export interface ScoringDefinition {
  itemIdentifier: string;
  maxScore?: number;
  question?: string;
  answerModel?: string;
}

export interface TeacherScoreEntry {
  responseId: string;
  teacherScore: number;
}

export interface ReviewItemView {
  stats: ItemStatistics;
  item: AssessmentItem | undefined;
  isManualScoring: boolean;
}

export interface ResponseCluster {
  familyId: string | null;
  responses: UniqueResponse[];
}
