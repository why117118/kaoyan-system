"""
seed_politics_and_questions.py  —  Seed course_questions with sample questions.
Run after load_data.py to populate the practice question bank.
"""
import pymysql
import json

DB = {
    'host': '127.0.0.1',
    'port': 3306,
    'user': 'root',
    'password': '123456',
    'database': 'grad_project',
    'charset': 'utf8mb4',
}

# Sample questions keyed by type keyword
QUESTIONS = {
    '数学': [
        {
            'question': '函数 f(x) = x² + 2x + 1 的最小值是多少？',
            'options': json.dumps(['0', '1', '-1', '2']),
            'answer': '0',
            'explanation': 'f(x) = (x+1)²，当 x = -1 时取最小值 0'
        },
        {
            'question': '矩阵 A 是 3×3 单位矩阵，则 det(A) = ?',
            'options': json.dumps(['0', '1', '3', '-1']),
            'answer': '1',
            'explanation': '单位矩阵的行列式等于 1'
        },
        {
            'question': '∫₀¹ x dx = ?',
            'options': json.dumps(['0', '1/2', '1', '2']),
            'answer': '1/2',
            'explanation': '∫₀¹ x dx = [x²/2]₀¹ = 1/2'
        },
    ],
    '英语': [
        {
            'question': 'Choose the correct word: The project was _____ successful.',
            'options': json.dumps(['high', 'highly', 'higher', 'highest']),
            'answer': 'highly',
            'explanation': 'highly 是副词，用于修饰形容词 successful'
        },
        {
            'question': 'Which is correct? "He suggested that she _____ the meeting."',
            'options': json.dumps(['attend', 'attends', 'attended', 'attending']),
            'answer': 'attend',
            'explanation': 'suggest 后跟虚拟语气，用动词原形'
        },
    ],
    '政治': [
        {
            'question': '马克思主义哲学的根本特征是什么？',
            'options': json.dumps(['实践性', '科学性', '革命性', '阶级性']),
            'answer': '实践性',
            'explanation': '实践性是马克思主义哲学区别于其他哲学的最显著特征'
        },
        {
            'question': '中国特色社会主义最本质的特征是什么？',
            'options': json.dumps(['人民当家作主', '中国共产党的领导', '改革开放', '依法治国']),
            'answer': '中国共产党的领导',
            'explanation': '十九大报告明确指出：中国共产党的领导是中国特色社会主义最本质的特征'
        },
        {
            'question': '新发展理念包括哪些？',
            'options': json.dumps(['创新、协调、绿色、开放、共享', '创新、和谐、绿色、开放、共享', '创新、协调、生态、开放、共享', '创新、协调、绿色、改革、共享']),
            'answer': '创新、协调、绿色、开放、共享',
            'explanation': '新发展理念是创新、协调、绿色、开放、共享'
        },
    ],
}


def main():
    conn = pymysql.connect(**DB)
    cur = conn.cursor(pymysql.cursors.DictCursor)

    inserted = 0
    for type_keyword, questions in QUESTIONS.items():
        # Find courses matching this type
        cur.execute(
            "SELECT c.course_index, c.name FROM courses c "
            "LEFT JOIN course_types t ON c.type_id = t.type_id "
            "WHERE t.type_name LIKE %s LIMIT 5",
            ('%' + type_keyword + '%',)
        )
        courses = cur.fetchall()
        if not courses:
            print(f"[WARN] No courses found for type keyword '{type_keyword}', skipping.")
            continue

        for q in questions:
            # Assign to first matching course
            c = courses[0]
            cur.execute(
                "INSERT INTO course_questions (course_id, course_name, question, options, answer, explanation) "
                "VALUES (%s, %s, %s, %s, %s, %s)",
                (c['course_index'], c['name'], q['question'], q['options'], q['answer'], q['explanation'])
            )
            inserted += 1

    conn.commit()
    cur.close()
    conn.close()
    print(f"Inserted {inserted} sample questions.")


if __name__ == '__main__':
    main()
