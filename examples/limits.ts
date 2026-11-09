import type { Ionicons } from '@expo/vector-icons';

// Limite de tempo nos apps, com redução gradual.
// A pessoa diz quanto usa hoje e onde quer chegar; o plano desce um degrau por semana.
// Só desce se a semana foi cumprida. Se não foi, o degrau se repete, sem castigo.
//
// Protótipo: o uso é simulado. No Android real, vem do UsageStatsManager e o bloqueio
// aparece pelo Serviço de Acessibilidade; no iPhone, pela API de Tempo de Uso.

export type AppId = 'tiktok' | 'instagram' | 'youtube' | 'telegram' | 'whatsapp' | 'facebook' | 'x' | 'kwai' | 'jogos';

// packages: nomes dos apps no Android (alguns têm mais de uma versão)
export type LimitApp = { id: AppId; name: string; icon: keyof typeof Ionicons.glyphMap; color: string; messaging?: boolean; packages: string[] };

export const APPS: LimitApp[] = [
  { id: 'tiktok', name: 'TikTok', icon: 'logo-tiktok', color: '#111111', packages: ['com.zhiliaoapp.musically', 'com.ss.android.ugc.trill', 'com.zhiliaoapp.musically.go'] },
  { id: 'instagram', name: 'Instagram', icon: 'logo-instagram', color: '#C13584', packages: ['com.instagram.android', 'com.instagram.lite'] },
  { id: 'youtube', name: 'YouTube', icon: 'logo-youtube', color: '#E62117', packages: ['com.google.android.youtube'] },
  { id: 'kwai', name: 'Kwai', icon: 'videocam', color: '#FF7A00', packages: ['com.kwai.video', 'com.kwai.bulldog'] },
  { id: 'telegram', name: 'Telegram', icon: 'paper-plane', color: '#2AABEE', messaging: true, packages: ['org.telegram.messenger', 'org.telegram.messenger.web', 'org.thunderdog.challegram'] },
  { id: 'whatsapp', name: 'WhatsApp', icon: 'logo-whatsapp', color: '#25D366', messaging: true, packages: ['com.whatsapp', 'com.whatsapp.w4b'] },
  { id: 'facebook', name: 'Facebook', icon: 'logo-facebook', color: '#1877F2', packages: ['com.facebook.katana', 'com.facebook.lite'] },
  { id: 'x', name: 'X (Twitter)', icon: 'logo-x', color: '#14171A', packages: ['com.twitter.android'] },
  { id: 'jogos', name: 'Jogos', icon: 'game-controller', color: '#6C4BD8', packages: [] },
];

export const appById = (id: AppId) => APPS.find((a) => a.id === id) ?? APPS[0];
// App da lista a partir do nome do pacote Android (usado quando o bloqueio nativo avisa)
export const appByPackage = (pkg: string) => APPS.find((a) => a.packages.includes(pkg));

export type Pace = 'leve' | 'equilibrado' | 'firme';

export const PACES: Record<Pace, { label: string; step: number; text: string }> = {
  leve: { label: 'Leve', step: 15, text: '15 min a menos por semana' },
  equilibrado: { label: 'Equilibrado', step: 30, text: '30 min a menos por semana' },
  firme: { label: 'Firme', step: 60, text: '1 hora a menos por semana' },
};

export type LimitPlan = {
  apps: AppId[];
  startMinutes: number;
  goalMinutes: number;
  pace: Pace;
  startedAt: number;
  // Degrau atual (0 = primeira semana) e quando ele começou
  step: number;
  stepSince: number;
  // Semanas que precisaram ser repetidas, para o resumo
  repeated: number;
  pause: boolean;
  night: boolean;
  emergencyPerDay: number;
};

export const EMERGENCY_MINUTES = 5;
export const WARN_AT = 0.8;
const DAY = 24 * 60 * 60 * 1000;
const WEEK = 7 * DAY;

// Degraus do plano: do uso atual até a meta, sem passar dela
export function ladder(plan: Pick<LimitPlan, 'startMinutes' | 'goalMinutes' | 'pace'>) {
  const step = PACES[plan.pace].step;
  const first = Math.max(plan.goalMinutes, plan.startMinutes - step);
  const steps = [first];
  while (steps[steps.length - 1] > plan.goalMinutes) {
    steps.push(Math.max(plan.goalMinutes, steps[steps.length - 1] - step));
  }
  return steps;
}

export function weeksToGoal(plan: Pick<LimitPlan, 'startMinutes' | 'goalMinutes' | 'pace'>) {
  return ladder(plan).length;
}

export function limitToday(plan: LimitPlan) {
  const steps = ladder(plan);
  return steps[Math.min(plan.step, steps.length - 1)];
}

export function dayInStep(plan: LimitPlan, now = Date.now()) {
  return Math.min(7, Math.floor((now - plan.stepSince) / DAY) + 1);
}

export function reachedGoal(plan: LimitPlan) {
  return limitToday(plan) <= plan.goalMinutes;
}

// Se a semana do degrau acabou: avança quando a média ficou dentro do limite, senão repete
export function weekResult(plan: LimitPlan, history: Record<string, number>, now = Date.now()) {
  if (now - plan.stepSince < WEEK) return null;
  const limit = limitToday(plan);
  // Só conta os dias medidos de verdade. Sem medição (acesso ao uso desligado), não avança o degrau:
  // a semana sem dados não pode virar um limite mais apertado.
  const days = Array.from({ length: 7 }, (_, i) => history[dayKey(plan.stepSince + i * DAY)]).filter((m): m is number => typeof m === 'number');
  if (days.length < 4) return { met: false, avg: 0, noData: true };
  const avg = days.reduce((s, m) => s + m, 0) / days.length;
  return { met: avg <= limit, avg, noData: false };
}

// Dia local (no Brasil, toISOString viraria o dia às 21h)
export const dayKey = (t = Date.now()) => {
  const d = new Date(t);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
};

export function hm(minutes: number) {
  const m = Math.max(0, Math.round(minutes));
  const h = Math.floor(m / 60);
  const r = m % 60;
  if (!h) return `${r}min`;
  if (!r) return `${h}h`;
  return `${h}h${String(r).padStart(2, '0')}`;
}

// ---------- Simulação (protótipo) ----------

// Divide um total de minutos entre os apps escolhidos, com pesos fixos por app
const WEIGHTS: Record<AppId, number> = { tiktok: 5, instagram: 4, youtube: 3, kwai: 3, telegram: 2, whatsapp: 2, facebook: 2, x: 2, jogos: 3 };

export function splitUsage(apps: AppId[], total: number) {
  const sum = apps.reduce((s, a) => s + WEIGHTS[a], 0) || 1;
  const out: Partial<Record<AppId, number>> = {};
  apps.forEach((a) => {
    out[a] = Math.round((total * WEIGHTS[a]) / sum);
  });
  return out;
}

// Uso de hoje "até agora": cresce ao longo do dia, ficando um pouco abaixo do limite
export function simulatedToday(apps: AppId[], limit: number, now = new Date()) {
  const hour = now.getHours() + now.getMinutes() / 60;
  const awake = Math.min(1, Math.max(0, (hour - 7) / 16));
  return splitUsage(apps, limit * 0.9 * awake);
}

// Últimos dias antes do plano: perto do uso que a pessoa contou, com variação
export function simulatedHistory(startMinutes: number, now = Date.now()) {
  const out: Record<string, number> = {};
  for (let d = 6; d >= 1; d--) {
    const wobble = [0.08, -0.05, 0.12, -0.1, 0.04, -0.02][d - 1];
    out[dayKey(now - d * DAY)] = Math.round(startMinutes * (1 + wobble));
  }
  return out;
}
