import type { Task } from '../types/model';
export interface TimedItem { id: string; start: number; end: number }
export function taskSegment(t: Task, day: number): TimedItem | null {
  if (t.due_day === null || t.minute_of_day === null) return null;
  const start=(t.due_day-day)*1440+t.minute_of_day, end=start+(t.duration_minutes??30);
  return start<1440 && end>0 ? {id:t.id,start:Math.max(0,start),end:Math.min(1440,end)}:null;
}
/** Interval partitioning, with the width shared by every event in an overlapping group. */
export function layoutTimeline(items: TimedItem[]) {
  const sorted=[...items].sort((a,b)=>a.start-b.start || a.end-b.end || a.id.localeCompare(b.id));
  const result: (TimedItem & {lane:number;columns:number})[]=[];
  let group: typeof result=[], ends:number[]=[], groupEnd=-1;
  const flush=()=> { for(const item of group) item.columns=ends.length; group=[];ends=[]; };
  for(const item of sorted) {
    if(item.start>=groupEnd) flush();
    let lane=ends.findIndex(end=>end<=item.start);
    if(lane<0) lane=ends.length;
    ends[lane]=item.end;
    const entry={...item,lane,columns:1};group.push(entry);result.push(entry);
    groupEnd=Math.max(groupEnd,item.end);
  }
  flush(); return result;
}
export const snapMinute=(y:number,height:number)=>Math.min(1425,Math.max(0,Math.round(y/height*1440/15)*15));
