const BASE = '/api';
const j = (r) => r.json();

export function fetchRecommendations(userId, topN = 10) {
  return fetch(`${BASE}/recommendations?userId=${userId}&topN=${topN}`)
    .then(r => {
      if (!r.ok) return r.text().then(t => { throw new Error(`HTTP ${r.status}: ${t.substring(0, 200)}`) });
      return r.json();
    });
}

export function recordCourseClick(userId, courseIndex) {
  return fetch(`${BASE}/interactions`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ userId, courseIndex }) }).then(j);
}

export function fetchCourses(limit = 50) {
  return fetch(`${BASE}/courses?limit=${limit}`).then(j);
}

export function fetchCoursesPaged(page, size, keyword = '', mode = 'name') {
  let url = `${BASE}/courses?page=${page}&size=${size}&mode=${mode}`;
  if (keyword) url += `&keyword=${encodeURIComponent(keyword)}`;
  return fetch(url).then(j);
}

export function fetchEvaluation(topK = 10, maxUsers = 1000) {
  return fetch(`${BASE}/evaluation?topK=${topK}&maxUsers=${maxUsers}`).then(j);
}

export function register(username, password) {
  return fetch(`${BASE}/auth/register`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username, password }) }).then(j);
}

export function login(username, password) {
  return fetch(`${BASE}/auth/login`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username, password }) })
    .then(r => r.json().then(data => ({ ...data, _status: r.status })));
}

export function updateMajor(userId, majorTypeId) {
  return fetch(`${BASE}/user/major`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ userId, majorTypeId }) }).then(j);
}

export function updateProfile(userId, username, majorTypeId) {
  return fetch(`${BASE}/user/profile`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ userId, username, majorTypeId }) }).then(j);
}

export function changePassword(userId, oldPassword, newPassword) {
  return fetch(`${BASE}/user/password`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ userId, oldPassword, newPassword }) }).then(j);
}

export function uploadAvatar(userId, file) {
  const fd = new FormData();
  fd.append('userId', userId);
  fd.append('file', file);
  return fetch(`${BASE}/user/avatar`, { method: 'POST', body: fd }).then(j);
}

export function fetchCourseTypes(exclude = '') {
  let url = `${BASE}/course-types`;
  if (exclude) url += `?exclude=${encodeURIComponent(exclude)}`;
  return fetch(url).then(j);
}

export function fetchQuestions(courseId, limit = 5) {
  return fetch(`${BASE}/questions?courseId=${courseId}&limit=${limit}&random=true`).then(j);
}

export function fetchQuestionsByCategory(category, userId, limit = 10) {
  return fetch(`${BASE}/questions/by-category?category=${category}&userId=${userId}&limit=${limit}`).then(j);
}

export function fetchPlans(userId, status = '', sort = 'desc') {
  let url = `${BASE}/plans?userId=${userId}&sort=${sort}`;
  if (status) url += `&status=${encodeURIComponent(status)}`;
  return fetch(url).then(j);
}

export function createPlan(plan) {
  return fetch(`${BASE}/plans`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(plan) }).then(j);
}

export function updatePlan(id, userId, plan) {
  return fetch(`${BASE}/plans/${id}?userId=${userId}`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(plan) }).then(j);
}

export function deletePlan(id, userId) {
  return fetch(`${BASE}/plans/${id}?userId=${userId}`, { method: 'DELETE' }).then(j);
}

export function fetchWrongQuestions(userId, keyword = '', courseId = null) {
  let url = `${BASE}/wrong-questions?userId=${userId}`;
  if (keyword) url += `&keyword=${encodeURIComponent(keyword)}`;
  if (courseId) url += `&courseId=${courseId}`;
  return fetch(url).then(j);
}

export function fetchWrongQuestionsPaged(userId, category = 'all', keyword = '', page = 1, size = 5) {
  let url = `${BASE}/wrong-questions?userId=${userId}&page=${page}&size=${size}`;
  if (category && category !== 'all') url += `&category=${category}`;
  if (keyword) url += `&keyword=${encodeURIComponent(keyword)}`;
  return fetch(url).then(j);
}

export function createWrongQuestion(data) {
  return fetch(`${BASE}/wrong-questions`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(data) }).then(j);
}

export function deleteWrongQuestion(id, userId) {
  return fetch(`${BASE}/wrong-questions/${id}?userId=${userId}`, { method: 'DELETE' }).then(j);
}

export function fetchWrongQuestionsByCategory(category, userId, limit = 10) {
  return fetch(`${BASE}/wrong-questions/by-category?category=${category}&userId=${userId}&limit=${limit}`).then(j);
}

export function fetchWrongQuestionCount(userId, questionId = null, questionText = null) {
  let url = `${BASE}/wrong-questions/count?userId=${userId}`;
  if (questionId) url += `&questionId=${questionId}`;
  if (questionText) url += `&questionText=${encodeURIComponent(questionText)}`;
  return fetch(url).then(j);
}

// ==================== 管理员 API ====================

export function adminLogin(username, password) {
  return fetch(`${BASE}/admin/login`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username, password }) }).then(j);
}

// 用户管理
export function adminListUsers(page = 1, size = 20, keyword = '') {
  let url = `${BASE}/admin/users?page=${page}&size=${size}`;
  if (keyword) url += `&keyword=${encodeURIComponent(keyword)}`;
  return fetch(url).then(j);
}
export function adminUpdateUser(id, data) {
  return fetch(`${BASE}/admin/users/${id}`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(data) }).then(j);
}
export function adminDeleteUser(id) {
  return fetch(`${BASE}/admin/users/${id}`, { method: 'DELETE' }).then(j);
}

// 题库管理
export function adminListQuestions(page = 1, size = 20, keyword = '') {
  let url = `${BASE}/admin/questions?page=${page}&size=${size}`;
  if (keyword) url += `&keyword=${encodeURIComponent(keyword)}`;
  return fetch(url).then(j);
}
export function adminAddQuestion(data) {
  return fetch(`${BASE}/admin/questions`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(data) }).then(j);
}
export function adminUpdateQuestion(id, data) {
  return fetch(`${BASE}/admin/questions/${id}`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(data) }).then(j);
}
export function adminDeleteQuestion(id) {
  return fetch(`${BASE}/admin/questions/${id}`, { method: 'DELETE' }).then(j);
}

// 错题管理
export function adminListWrongQuestions(page = 1, size = 20, keyword = '') {
  let url = `${BASE}/admin/wrong-questions?page=${page}&size=${size}`;
  if (keyword) url += `&keyword=${encodeURIComponent(keyword)}`;
  return fetch(url).then(j);
}
export function adminDeleteWrongQuestion(id) {
  return fetch(`${BASE}/admin/wrong-questions/${id}`, { method: 'DELETE' }).then(j);
}

// 学习计划管理
export function adminListPlans(page = 1, size = 20, keyword = '') {
  let url = `${BASE}/admin/plans?page=${page}&size=${size}`;
  if (keyword) url += `&keyword=${encodeURIComponent(keyword)}`;
  return fetch(url).then(j);
}
export function adminUpdatePlan(id, data) {
  return fetch(`${BASE}/admin/plans/${id}`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(data) }).then(j);
}
export function adminDeletePlan(id) {
  return fetch(`${BASE}/admin/plans/${id}`, { method: 'DELETE' }).then(j);
}

// 课程链接管理
export function adminListCourses(page = 1, size = 20, keyword = '') {
  let url = `${BASE}/admin/courses?page=${page}&size=${size}`;
  if (keyword) url += `&keyword=${encodeURIComponent(keyword)}`;
  return fetch(url).then(j);
}
export function adminUpdateCourseUrl(courseIndex, url) {
  return fetch(`${BASE}/admin/courses/${courseIndex}/url`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ url }) }).then(j);
}
