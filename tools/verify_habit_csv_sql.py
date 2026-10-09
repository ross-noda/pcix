"""Compare additive migration 11→12 to Room schema and preserve existing habit rows."""
import json, re, sqlite3
from pathlib import Path
root = Path(__file__).resolve().parents[1]
base = root / 'app/schemas/com.example.pix.data.PixDatabase'
def create(version):
    schema = json.loads((base / f'{version}.json').read_text())['database']
    db = sqlite3.connect(':memory:')
    db.execute('PRAGMA foreign_keys=ON')
    for e in schema['entities']:
        db.execute(e['createSql'].replace('${TABLE_NAME}', e['tableName']))
        for index in e.get('indices', []):
            db.execute(index['createSql'].replace('${TABLE_NAME}', e['tableName']))
    return db, schema
old, _ = create(11)
expected, schema = create(12)
old.execute("INSERT INTO habits VALUES('h','Water','REPEAT',0,NULL,'',1,0,480,1,1)")
old.execute("INSERT INTO habit_logs VALUES('l','h',20000,4,0,1,1)")
source = (root / 'app/src/main/java/com/example/pix/data/PixDatabase.kt').read_text()
block = source[source.index('val MIGRATION_11_12'):source.index('val MIGRATION_10_11')]
for sql in re.findall(r'db.execSQL\("([^"\n]*)"\)', block): old.execute(sql)
for e in schema['entities']:
    table = e['tableName']
    # Column order is immaterial to Room; ALTER TABLE appends the new columns.
    def columns(db): return sorted(tuple(row[1:]) for row in db.execute(f'PRAGMA table_info({table})'))
    assert columns(old) == columns(expected), table
    for pragma in ('foreign_key_list','index_list'):
        assert old.execute(f'PRAGMA {pragma}({table})').fetchall() == expected.execute(f'PRAGMA {pragma}({table})').fetchall(), (table, pragma)
assert old.execute('SELECT name,unit,csvId,reminderMinute FROM habits').fetchone() == ('Water','rep',None,480)
assert old.execute('SELECT count,sourceStatus FROM habit_logs').fetchone() == (4,None)
old.execute("UPDATE habit_logs SET sourceStatus=''")
assert old.execute('SELECT sourceStatus FROM habit_logs').fetchone() == ('',)
assert not old.execute('PRAGMA foreign_key_check').fetchall()
print('PASS: migration 11→12 equals Room schema; history, reminder, nullable original state and default unit preserved')
