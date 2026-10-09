import type { Task } from '../types/model';
import { dateOf, lastDay, parseDay, weekday } from './rules';
export const matrixDates = ['ALL','TODAY','TOMORROW','THIS_WEEK','NEXT_WEEK','THIS_MONTH','NEXT_MONTH','RANGE'] as const;
export type MatrixDate = typeof matrixDates[number];
export interface MatrixCard {
  title: string; custom: boolean; list: string; tag: string; priority: string;
  lists?: string[]; tags?: string[]; priorities?: string[]; date?: MatrixDate;
  from: string; to: string;
}
export function matrixRange(c: MatrixCard, today: number): [number,number] | null {
  const monday = today-weekday(today)+1, d = dateOf(today);
  const month = (offset: number) => Date.UTC(d.getUTCFullYear(), d.getUTCMonth()+offset,1)/86400000;
  switch(c.date ?? (c.from || c.to ? 'RANGE':'ALL')) {
    case 'ALL': return null;
    case 'TODAY': return [today,today];
    case 'TOMORROW': return [today+1,today+1];
    case 'THIS_WEEK': return [monday,monday+6];
    case 'NEXT_WEEK': return [monday+7,monday+13];
    case 'THIS_MONTH': return [month(0),month(1)-1];
    case 'NEXT_MONTH': return [month(1),month(2)-1];
    case 'RANGE': return [parseDay(c.from) ?? Infinity,parseDay(c.to) ?? -Infinity];
  }
}
export function customMatrixMatch(t: Task, c: MatrixCard, tags: Set<string>, today: number) {
  const lists = c.lists ?? (c.list ? [c.list]:[]), wantedTags = c.tags ?? (c.tag ? [c.tag]:[]), priorities = c.priorities ?? (c.priority ? [c.priority]:[]);
  if (lists.length && !lists.includes(t.list_id) || wantedTags.length && !wantedTags.some(id=>tags.has(id)) || priorities.length && !priorities.includes(String(t.priority))) return false;
  const range = matrixRange(c,today);
  return !range || (t.due_day!==null && t.due_day<=range[1] && lastDay(t)!>=range[0]);
}
