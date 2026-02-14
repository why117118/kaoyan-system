"""
load_data.py  —  Import data.csv into MySQL grad_project database.
Handles UTF-8 / GBK / GB18030 encoding detection.
"""
import csv
import sys
import pymysql

DB = {
    'host': '127.0.0.1',
    'port': 3306,
    'user': 'root',
    'password': '123456',
    'database': 'grad_project',
    'charset': 'utf8mb4',
}

CSV_PATH = '../data.csv'

def detect_and_read(path):
    for enc in ('utf-8-sig', 'utf-8', 'gbk', 'gb18030'):
        try:
            with open(path, encoding=enc) as f:
                reader = list(csv.DictReader(f))
                print(f"[OK] Opened with encoding: {enc}, rows={len(reader)}")
                return reader
        except (UnicodeDecodeError, KeyError):
            continue
    raise RuntimeError(f"Cannot decode {path}")

def main():
    rows = detect_and_read(CSV_PATH)
    if not rows:
        print("No data."); return

    conn = pymysql.connect(**DB)
    cur = conn.cursor()

    # Collect unique students, course_types, courses
    students = {}
    types = {}
    courses = {}

    for r in rows:
        sid = r['stu_id'].strip()
        if sid not in students:
            students[sid] = sid

        type_id = r.get('type_id', '').strip()
        type_name = r.get('type', '').strip()
        if type_id and type_id not in types:
            types[type_id] = type_name

        cidx = r.get('course_index', '').strip()
        cname = r.get('name', '').strip()
        if cidx and cidx not in courses:
            courses[cidx] = {'name': cname, 'type': type_name, 'type_id': type_id}

    # Insert students
    print(f"Inserting {len(students)} students...")
    for sid in students:
        cur.execute('INSERT IGNORE INTO students (stu_id) VALUES (%s)', (sid,))

    # Insert course_types
    print(f"Inserting {len(types)} course types...")
    for tid, tname in types.items():
        cur.execute('INSERT IGNORE INTO course_types (type_id, type_name) VALUES (%s, %s)', (int(tid), tname))

    # Insert courses
    print(f"Inserting {len(courses)} courses...")
    for cidx, meta in courses.items():
        tid = int(meta['type_id']) if meta['type_id'] else None
        cur.execute(
            'INSERT IGNORE INTO courses (course_index, name, type, type_id) VALUES (%s, %s, %s, %s)',
            (int(cidx), meta['name'], meta['type'], tid)
        )

    # Insert interactions
    print(f"Inserting {len(rows)} interactions...")
    for r in rows:
        cur.execute(
            'INSERT IGNORE INTO interactions (stu_id, time, course_index) VALUES (%s, %s, %s)',
            (r['stu_id'].strip(), r['time'].strip(), int(r['course_index'].strip()))
        )

    conn.commit()
    cur.close()
    conn.close()
    print("Done!")

if __name__ == '__main__':
    main()
