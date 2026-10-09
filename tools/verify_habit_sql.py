"""Execute the actual additive migration against exported Room 10/11 schemas."""
import json, re, sqlite3
from pathlib import Path
root = Path(__file__).resolve().parents[1]
base = root / 'app/schemas/com.example.pix.data.PixDatabase'
def database(version):
    schema = json.loads((base / f'{version}.json').read_text())['database']
    db = sqlite3.connect(':memory:')
    db.execute('PRAGMA foreign_keys=ON')
    for e in schema['entities']:
        db.execute(e['createSql'].replace('${TABLE_NAME}', e['tableName']))
        for i in e.get('indices', []):
            db.execute(i['createSql'].replace('${TABLE_NAME}', e['tableName']))
    return db, schema
old, _ = database(10)
expected, schema = database(11)
old.execute("INSERT INTO lists VALUES('list','Preserved','x',1,0,0,0)")
old.execute("INSERT INTO sync_outbox VALUES('pending','lists','list','UPSERT','{}',1,2,NULL)")
source = (root / 'app/src/main/java/com/example/pix/data/HabitMigration.kt').read_text()
for sql in re.findall(r'db.execSQL\("([^"\n]*)"\)', source):
    old.execute(sql)
for e in schema['entities']:
    table = e['tableName']
    for pragma in ('table_info', 'foreign_key_list', 'index_list'):
        assert old.execute(f'PRAGMA {pragma}({table})').fetchall() == expected.execute(f'PRAGMA {pragma}({table})').fetchall(), (table, pragma)
assert old.execute('SELECT name FROM lists').fetchone() == ('Preserved',)
assert old.execute('SELECT attemptCount FROM sync_outbox').fetchone() == (2,)
old.execute("INSERT INTO habit_groups VALUES('g','Health',0,1,1)")
old.execute("INSERT INTO habits VALUES('h','Water','REPEAT',0,'g','',1,0,480,1,1)")
old.execute("INSERT INTO habit_rules VALUES('r','h',20000,20000,NULL,1,8,1,127,1,1,1)")
old.execute("INSERT INTO habit_logs VALUES('log','h',20000,4,0,1,1)")
old.execute("DELETE FROM habit_groups WHERE id='g'")
assert old.execute('SELECT groupId FROM habits').fetchone() == (None,)
assert old.execute('SELECT count FROM habit_logs').fetchone() == (4,)
try:
    old.execute("INSERT INTO habit_logs VALUES('duplicate','h',20000,7,0,1,1)")
    raise AssertionError('Duplicate day accepted')
except sqlite3.IntegrityError:
    pass
old.execute("DELETE FROM habits WHERE id='h'")
assert old.execute('SELECT COUNT(*) FROM habit_logs').fetchone() == (0,)
assert old.execute('SELECT COUNT(*) FROM habit_rules').fetchone() == (0,)
assert not old.execute('PRAGMA foreign_key_check').fetchall()
print('PASS: Room 10→11 migration equals exported schema; existing rows preserved; group SET NULL; unique daily logs; habit CASCADE; foreign keys clean')
