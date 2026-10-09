import sqlite3,json,re
from pathlib import Path
r=Path(__file__).resolve().parents[1]
base=r/'app/schemas/com.example.pix.data.PixDatabase'
old=json.loads((base/'9.json').read_text())['database'];new=json.loads((base/'10.json').read_text())['database']
def create(schema):
 db=sqlite3.connect(':memory:');db.execute('PRAGMA foreign_keys=ON')
 for e in schema['entities']:
  db.execute(e['createSql'].replace('${TABLE_NAME}',e['tableName']))
  for i in e.get('indices',[]):db.execute(i['createSql'].replace('${TABLE_NAME}',e['tableName']))
 return db
a=create(old);b=create(new)
a.execute("INSERT INTO google_calendar_accounts VALUES('a','a@example.test')")
a.execute("INSERT INTO google_calendars VALUES('a','c','Calendar',123,NULL,0,'reader')")
s=(r/'app/src/main/java/com/example/pix/data/PixDatabase.kt').read_text()
block=s[s.index('val MIGRATION_9_10'):s.index('val MIGRATION_8_9')]
for sql in re.findall(r'db.execSQL\("([^"\n]*)"\)',block): a.execute(sql)
for entity in new['entities']:
 table=entity['tableName']
 for pragma in ['table_info','foreign_key_list','index_list']:
  assert a.execute(f'PRAGMA {pragma}({table})').fetchall()==b.execute(f'PRAGMA {pragma}({table})').fetchall(),(table,pragma)
assert a.execute('SELECT colorArgb,enabled,localColorArgb FROM google_calendars').fetchone()==(123,0,None)
assert not a.execute('PRAGMA foreign_key_check').fetchall()
# Execute the actual repository SQL on parent + child fixtures.
a.execute("INSERT INTO lists VALUES('l','List','x',1,0,0,0)")
fields=next(e['fields'] for e in new['entities'] if e['tableName']=='tasks')
for tid,parent in [('parent',None),('child','parent')]:
 vals={f['columnName']:None for f in fields}
 vals.update(id=tid,title=tid,notes='',parentTaskId=parent,listId='l',dueDay=22000,priority=0,isCompleted=0,isTemplate=0,isSkipped=0,sortOrder=1,createdAt=1,updatedAt=1)
 cols=','.join(vals);marks=','.join('?' for _ in vals)
 a.execute(f'INSERT INTO tasks ({cols}) VALUES ({marks})',list(vals.values()))
sql=re.search(r'@Query\("([^"\n]*)"\)\s*fun observeWidgetRange',s)[1]
assert [row[0] for row in a.execute(sql,{'start':22000,'end':22042})]==['parent']
sql=re.search(r'@Query\(\s*"""(.*?)"""\s*\)\s*fun observeTasks',s,re.S)[1]
params=dict(rootsOnly=1,listId=None,tagId=None,showCompleted=0,mode='ALL',today=22000,weekEnd=22007,minute=0,search='',manual=0)
assert [row[0] for row in a.execute(sql,params)]==['parent']
params['rootsOnly']=0
assert len(a.execute(sql,params).fetchall())==2
print('PASS: migration 9->10 matches exported Room schema; selection/color preserved; FK checks clean; real widget queries exclude children; app query retains children')

# Coincident calendar/event IDs must remain scoped to distinct Google accounts.
a.execute("INSERT INTO google_calendar_accounts VALUES('b','b@example.test')")
a.execute("UPDATE google_calendars SET enabled=1 WHERE accountId='a'")
a.execute("INSERT INTO google_calendars VALUES('b','c','Second',456,NULL,1,'reader',NULL)")
fields=next(e['fields'] for e in new['entities'] if e['tableName']=='google_events')
for account,color in [('a',123),('b',456)]:
 vals={f['columnName']:None for f in fields}
 vals.update(accountId=account,calendarId='c',eventId='same',title=account,description='',location='',startDay=1,endDay=2,allDay=1,status='confirmed',cancelled=0,updatedAt=0,colorArgb=color)
 a.execute(f"INSERT INTO google_events ({','.join(vals)}) VALUES ({','.join('?' for _ in vals)})",list(vals.values()))
sql=re.search(r'@Query\("([^"\n]*)"\)\s*fun observeAllEvents',s)[1]
params={'start':0,'end':3}
assert len(a.execute(sql,params).fetchall())==2
a.execute("UPDATE google_calendars SET enabled=0 WHERE accountId='a'")
assert len(a.execute(sql,params).fetchall())==1
a.execute("DELETE FROM google_calendar_accounts WHERE id='a'")
assert a.execute("SELECT accountId,colorArgb FROM google_events").fetchall()==[('b',456)]
assert not a.execute('PRAGMA foreign_key_check').fetchall()
print('PASS: combined Google events, isolated visibility, identical IDs and isolated cascade deletion')
