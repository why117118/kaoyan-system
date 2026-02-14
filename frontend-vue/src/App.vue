<script setup>
import { ref, reactive, computed, onMounted, watch, nextTick } from 'vue'
import {
  fetchRecommendations, recordCourseClick, fetchCourses, fetchCoursesPaged,
  fetchEvaluation, register, login, updateMajor, updateProfile,
  changePassword, uploadAvatar, fetchCourseTypes, fetchQuestions,
  fetchQuestionsByCategory, fetchPlans, createPlan, updatePlan,
  deletePlan, fetchWrongQuestions, fetchWrongQuestionsPaged, createWrongQuestion,
  deleteWrongQuestion, fetchWrongQuestionsByCategory,
  fetchWrongQuestionCount,
  adminLogin, adminListUsers, adminUpdateUser, adminDeleteUser,
  adminListQuestions, adminAddQuestion, adminUpdateQuestion, adminDeleteQuestion,
  adminListWrongQuestions, adminDeleteWrongQuestion,
  adminListPlans, adminUpdatePlan, adminDeletePlan,
  adminListCourses, adminUpdateCourseUrl
} from './api.js'

/* ==================== 全局状态 ==================== */
const currentUser = ref(null)
const currentAdmin = ref(null)
const page = ref('home')
const msg = ref('')
const msgType = ref('ok')

function showMsg(text, type = 'ok') { msg.value = text; msgType.value = type; setTimeout(() => msg.value = '', 3000) }

/* ==================== Auth ==================== */
const authMode = ref('login')
const loginRole = ref('user')   // 'user' | 'admin'
const authForm = reactive({ username: '', password: '' })
async function doAuth() {
  try {
    if (loginRole.value === 'admin') {
      const data = await adminLogin(authForm.username, authForm.password)
      if (data.error) { showMsg('管理员账号或密码错误', 'err'); return }
      currentAdmin.value = data
      authForm.username = ''; authForm.password = ''
      adminPage.value = 'users'
      showMsg('管理员登录成功')
      loadAdminUsers()
      return
    }
    const fn = authMode.value === 'login' ? login : register
    const data = await fn(authForm.username, authForm.password)
    if (data.error) {
      const errMap = {
        username_exists: '用户名已存在',
        user_not_found: '用户名不存在，请检查后重试',
        wrong_password: '密码错误，请重新输入'
      }
      showMsg(errMap[data.error] || '登录失败，请检查账号密码', 'err')
      return
    }
    currentUser.value = data
    authForm.username = ''; authForm.password = ''
    page.value = 'home'
    showMsg(authMode.value === 'login' ? '登录成功' : '注册成功')
    loadProfile()
  } catch { showMsg('网络错误', 'err') }
}
function logout() { currentUser.value = null; currentAdmin.value = null; page.value = 'home'; loginRole.value = 'user'; authMode.value = 'login' }

/* ==================== 导航 ==================== */
const navItems = [
  { key: 'home', label: '首页', icon: '🏠' },
  { key: 'profile', label: '个人资料', icon: '👤' },
  { key: 'recommend', label: '推荐演示', icon: '🎯' },
  { key: 'courses', label: '课程大厅', icon: '📚' },
  { key: 'plans', label: '学习计划', icon: '📝' },
  { key: 'wrong', label: '错题反馈', icon: '❌' },
  { key: 'practice', label: '题库练习', icon: '✏️' },
]

/* ==================== 个人资料 ==================== */
const profileForm = reactive({ username: '', majorTypeId: null })
const courseTypes = ref([])
const pwdForm = reactive({ oldPassword: '', newPassword: '' })

async function loadProfile() {
  if (!currentUser.value) return
  profileForm.username = currentUser.value.username || ''
  profileForm.majorTypeId = currentUser.value.majorTypeId || null
  try { courseTypes.value = await fetchCourseTypes() } catch {}
}

async function saveProfile() {
  if (!currentUser.value) return
  try {
    const data = await updateProfile(currentUser.value.id, profileForm.username, profileForm.majorTypeId)
    if (data.error) { showMsg(data.error === 'username_exists' ? '用户名已存在' : '更新失败', 'err'); return }
    currentUser.value = data; showMsg('资料已更新')
  } catch { showMsg('网络错误', 'err') }
}

async function doChangePassword() {
  if (!currentUser.value) return
  try {
    const data = await changePassword(currentUser.value.id, pwdForm.oldPassword, pwdForm.newPassword)
    if (data.error) { showMsg('旧密码错误', 'err'); return }
    showMsg('密码已更新'); pwdForm.oldPassword = ''; pwdForm.newPassword = ''
  } catch { showMsg('网络错误', 'err') }
}

async function doUploadAvatar(e) {
  const file = e.target.files[0]; if (!file || !currentUser.value) return
  try {
    const data = await uploadAvatar(currentUser.value.id, file)
    if (data.avatar) { currentUser.value = { ...currentUser.value, avatar: data.avatar }; showMsg('头像已更新') }
  } catch { showMsg('上传失败', 'err') }
}

/* ==================== 推荐 ==================== */
const recList = ref([])
const recTopN = ref(10)
const recLoading = ref(false)
const recError = ref('')
const evalResult = ref(null)
const evalLoading = ref(false)

async function loadRecommendations() {
  if (!currentUser.value) { showMsg('请先登录', 'err'); return }
  recLoading.value = true
  recError.value = ''
  recList.value = []
  try {
    const data = await fetchRecommendations(currentUser.value.id, recTopN.value)
    recList.value = data.recommendations || []
    if (recList.value.length === 0) {
      recError.value = '推荐结果为空（后台返回: ' + JSON.stringify(data).substring(0, 200) + '）'
    }
  } catch (e) {
    recError.value = '请求失败: ' + (e.message || e)
    showMsg('推荐加载失败', 'err')
  } finally { recLoading.value = false }
}

async function loadEvaluation() {
  evalLoading.value = true
  try { evalResult.value = await fetchEvaluation() }
  catch { showMsg('评估加载失败', 'err') }
  finally { evalLoading.value = false }
}

/* ==================== 课程大厅 ==================== */
const courseList = ref([])
const coursePage = ref(1)
const courseSize = ref(12)
const courseTotalPages = ref(1)
const courseKeyword = ref('')
const courseSearchMode = ref('name')
const courseLoading = ref(false)

async function loadCourses() {
  courseLoading.value = true
  try {
    const data = await fetchCoursesPaged(coursePage.value, courseSize.value, courseKeyword.value, courseSearchMode.value)
    courseList.value = data.content || data
    courseTotalPages.value = data.totalPages || 1
  } catch { showMsg('课程加载失败', 'err') }
  finally { courseLoading.value = false }
}

function searchCourses() { coursePage.value = 1; loadCourses() }
function gotoPage(p) { coursePage.value = p; loadCourses() }

async function onCourseClick(course) {
  if (!currentUser.value) return
  try { await recordCourseClick(currentUser.value.id, course.course_index) } catch {}
}

/* ==================== 学习计划 ==================== */
const planList = ref([])
const planFilter = ref('')
const planSort = ref('desc')
const planModal = ref(false)
const planEditId = ref(null)
const planForm = reactive({ userId: 0, title: '', description: '', targetDate: '', status: 'pending' })

async function loadPlans() {
  if (!currentUser.value) return
  try { planList.value = await fetchPlans(currentUser.value.id, planFilter.value, planSort.value) } catch {}
}

function openPlanModal(plan = null) {
  planModal.value = true
  if (plan) {
    planEditId.value = plan.id
    Object.assign(planForm, { userId: currentUser.value.id, title: plan.title, description: plan.description || '', targetDate: plan.targetDate || '', status: plan.status })
  } else {
    planEditId.value = null
    Object.assign(planForm, { userId: currentUser.value.id, title: '', description: '', targetDate: '', status: 'pending' })
  }
}

async function savePlan() {
  try {
    if (planEditId.value) { await updatePlan(planEditId.value, currentUser.value.id, planForm) }
    else { await createPlan(planForm) }
    planModal.value = false; showMsg('计划已保存'); loadPlans()
  } catch { showMsg('保存失败', 'err') }
}

async function removePlan(id) {
  if (!confirm('确定删除该计划？')) return
  try { await deletePlan(id, currentUser.value.id); showMsg('已删除'); loadPlans() } catch { showMsg('删除失败', 'err') }
}

/* ==================== 错题反馈 ==================== */
const wrongList = ref([])
const wrongKeyword = ref('')
const wrongCategory = ref('all')
const wrongPage = ref(1)
const wrongTotalPages = ref(1)

async function loadWrongQuestions() {
  if (!currentUser.value) return
  try {
    const data = await fetchWrongQuestionsPaged(currentUser.value.id, wrongCategory.value, wrongKeyword.value, wrongPage.value, 5)
    wrongList.value = data.items || []
    wrongTotalPages.value = data.totalPages || 1
  } catch {}
}

function wrongCategoryChange() {
  wrongPage.value = 1
  loadWrongQuestions()
}



/* ==================== 题库练习 ==================== */
const practiceCategory = ref('math')
const practiceQuestions = ref([])
const practiceAnswers = reactive({})
const practiceResults = reactive({})
const practiceMarked = reactive({})
const practiceLoading = ref(false)

async function loadPractice() {
  if (!currentUser.value) { showMsg('请先登录', 'err'); return }
  practiceLoading.value = true
  Object.keys(practiceAnswers).forEach(k => delete practiceAnswers[k])
  Object.keys(practiceResults).forEach(k => delete practiceResults[k])
  Object.keys(practiceMarked).forEach(k => delete practiceMarked[k])
  try {
    practiceQuestions.value = await fetchQuestionsByCategory(practiceCategory.value, currentUser.value.id)
  } catch { showMsg('题目加载失败', 'err') }
  finally { practiceLoading.value = false }
}

function checkAnswer(q, idx) {
  const userAns = practiceAnswers[idx]
  if (!userAns) return
  const correct = userAns === q.answer
  practiceResults[idx] = correct ? 'correct' : 'wrong'
}

async function markPracticeWrong(q, idx) {
  if (!currentUser.value || practiceMarked[idx]) return
  try {
    const res = await createWrongQuestion({
      userId: currentUser.value.id,
      questionId: q.id || null,
      questionText: q.question,
      courseName: q.courseName || q.course_name || '',
      yourAnswer: practiceAnswers[idx] || '',
      correctAnswer: q.answer || ''
    })
    practiceMarked[idx] = true
    const ec = res.error_count
    if (ec && ec > 1) { showMsg(`已收录错题（第 ${ec} 次做错）`) }
    else { showMsg('已收录错题') }
  } catch { showMsg('收录失败', 'err') }
}

/* ==================== 管理员面板 ==================== */
const adminPage = ref('users')
const adminNavItems = [
  { key: 'users', label: '用户管理', icon: '👥' },
  { key: 'questions', label: '题库管理', icon: '📖' },
  { key: 'wrongQ', label: '错题管理', icon: '❌' },
  { key: 'plans', label: '学习计划', icon: '📋' },
  { key: 'courseUrls', label: '课程链接', icon: '🔗' },
]

/* --- 管理员：用户管理 --- */
const adUsers = ref([])
const adUserPage = ref(1)
const adUserTotal = ref(0)
const adUserTotalPages = ref(1)
const adUserKeyword = ref('')
const adUserModal = ref(false)
const adUserForm = reactive({ id: 0, username: '', majorTypeId: null })
const adCourseTypes = ref([])

async function loadAdminUsers() {
  try {
    const d = await adminListUsers(adUserPage.value, 15, adUserKeyword.value)
    adUsers.value = d.content || []
    adUserTotal.value = d.total || 0
    adUserTotalPages.value = d.totalPages || 1
  } catch {}
  if (!adCourseTypes.value.length) {
    try { adCourseTypes.value = await fetchCourseTypes() } catch {}
  }
}
function openAdminUserEdit(u) {
  adUserForm.id = u.id; adUserForm.username = u.username; adUserForm.majorTypeId = u.majorTypeId
  adUserModal.value = true
}
async function saveAdminUser() {
  try {
    await adminUpdateUser(adUserForm.id, { username: adUserForm.username, majorTypeId: adUserForm.majorTypeId })
    adUserModal.value = false; showMsg('用户已更新'); loadAdminUsers()
  } catch { showMsg('更新失败', 'err') }
}
async function removeAdminUser(id) {
  if (!confirm('确定删除该用户？其学习计划、错题等数据将一并删除。')) return
  try { await adminDeleteUser(id); showMsg('用户已删除'); loadAdminUsers() } catch { showMsg('删除失败', 'err') }
}

/* --- 管理员：题库管理 --- */
const adQuestions = ref([])
const adQPage = ref(1)
const adQTotal = ref(0)
const adQTotalPages = ref(1)
const adQKeyword = ref('')
const adQModal = ref(false)
const adQEditId = ref(null)
const adQForm = reactive({ courseId: '', courseName: '', question: '', opt1: '', opt2: '', opt3: '', opt4: '', answer: '', explanation: '' })

async function loadAdminQuestions() {
  try {
    const d = await adminListQuestions(adQPage.value, 15, adQKeyword.value)
    adQuestions.value = d.content || []
    adQTotal.value = d.total || 0
    adQTotalPages.value = d.totalPages || 1
  } catch {}
}
function openAdminQAdd() {
  adQEditId.value = null
  Object.assign(adQForm, { courseId: '', courseName: '', question: '', opt1: '', opt2: '', opt3: '', opt4: '', answer: '', explanation: '' })
  adQModal.value = true
}
function openAdminQEdit(q) {
  adQEditId.value = q.id
  let opts = []
  try { opts = typeof q.options === 'string' ? JSON.parse(q.options) : (q.options || []) } catch {}
  adQForm.courseId = q.courseId
  adQForm.courseName = q.courseName
  adQForm.question = q.question
  adQForm.opt1 = opts[0] || ''; adQForm.opt2 = opts[1] || ''; adQForm.opt3 = opts[2] || ''; adQForm.opt4 = opts[3] || ''
  adQForm.answer = q.answer; adQForm.explanation = q.explanation || ''
  adQModal.value = true
}
async function saveAdminQuestion() {
  const opts = JSON.stringify([adQForm.opt1, adQForm.opt2, adQForm.opt3, adQForm.opt4].filter(o => o))
  try {
    if (adQEditId.value) {
      await adminUpdateQuestion(adQEditId.value, { question: adQForm.question, options: opts, answer: adQForm.answer, explanation: adQForm.explanation })
    } else {
      await adminAddQuestion({ courseId: Number(adQForm.courseId), courseName: adQForm.courseName, question: adQForm.question, options: opts, answer: adQForm.answer, explanation: adQForm.explanation })
    }
    adQModal.value = false; showMsg('题目已保存'); loadAdminQuestions()
  } catch { showMsg('保存失败', 'err') }
}
async function removeAdminQuestion(id) {
  if (!confirm('确定删除该题目？')) return
  try { await adminDeleteQuestion(id); showMsg('已删除'); loadAdminQuestions() } catch { showMsg('删除失败', 'err') }
}

/* --- 管理员：错题管理 --- */
const adWrong = ref([])
const adWPage = ref(1)
const adWTotal = ref(0)
const adWTotalPages = ref(1)
const adWKeyword = ref('')

async function loadAdminWrong() {
  try {
    const d = await adminListWrongQuestions(adWPage.value, 15, adWKeyword.value)
    adWrong.value = d.content || []
    adWTotal.value = d.total || 0
    adWTotalPages.value = d.totalPages || 1
  } catch {}
}
async function removeAdminWrong(id) {
  if (!confirm('确定删除该错题记录？')) return
  try { await adminDeleteWrongQuestion(id); showMsg('已删除'); loadAdminWrong() } catch { showMsg('删除失败', 'err') }
}

/* --- 管理员：学习计划管理 --- */
const adPlans = ref([])
const adPPage = ref(1)
const adPTotal = ref(0)
const adPTotalPages = ref(1)
const adPKeyword = ref('')
const adPModal = ref(false)
const adPForm = reactive({ id: 0, title: '', description: '', targetDate: '', status: 'pending' })

async function loadAdminPlans() {
  try {
    const d = await adminListPlans(adPPage.value, 15, adPKeyword.value)
    adPlans.value = d.content || []
    adPTotal.value = d.total || 0
    adPTotalPages.value = d.totalPages || 1
  } catch {}
}
function openAdminPlanEdit(p) {
  Object.assign(adPForm, { id: p.id, title: p.title, description: p.description || '', targetDate: p.targetDate || '', status: p.status })
  adPModal.value = true
}
async function saveAdminPlan() {
  try {
    await adminUpdatePlan(adPForm.id, { title: adPForm.title, description: adPForm.description, targetDate: adPForm.targetDate, status: adPForm.status })
    adPModal.value = false; showMsg('计划已更新'); loadAdminPlans()
  } catch { showMsg('更新失败', 'err') }
}
async function removeAdminPlan(id) {
  if (!confirm('确定删除该学习计划？')) return
  try { await adminDeletePlan(id); showMsg('已删除'); loadAdminPlans() } catch { showMsg('删除失败', 'err') }
}

/* --- 管理员：课程链接管理 --- */
const adCourses = ref([])
const adCPage = ref(1)
const adCTotal = ref(0)
const adCTotalPages = ref(1)
const adCKeyword = ref('')
const adCModal = ref(false)
const adCForm = reactive({ courseIndex: 0, name: '', url: '' })

async function loadAdminCourses() {
  try {
    const d = await adminListCourses(adCPage.value, 15, adCKeyword.value)
    adCourses.value = d.content || []
    adCTotal.value = d.total || 0
    adCTotalPages.value = d.totalPages || 1
  } catch {}
}
function openAdminCourseEdit(c) {
  adCForm.courseIndex = c.courseIndex; adCForm.name = c.name; adCForm.url = c.url || ''
  adCModal.value = true
}
async function saveAdminCourseUrl() {
  try {
    await adminUpdateCourseUrl(adCForm.courseIndex, adCForm.url)
    adCModal.value = false; showMsg('链接已更新'); loadAdminCourses()
  } catch { showMsg('更新失败', 'err') }
}

/* ==================== 管理员页面切换 ==================== */
watch(adminPage, (v) => {
  if (v === 'users') loadAdminUsers()
  if (v === 'questions') loadAdminQuestions()
  if (v === 'wrongQ') loadAdminWrong()
  if (v === 'plans') loadAdminPlans()
  if (v === 'courseUrls') loadAdminCourses()
})

/* ==================== 页面切换加载 ==================== */
watch(page, (v) => {
  if (v === 'profile') loadProfile()
  if (v === 'courses') loadCourses()
  if (v === 'plans') loadPlans()
  if (v === 'wrong') loadWrongQuestions()
})
</script>

<template>
  <!-- ========== 未登录：Auth页面 ========== -->
  <div v-if="!currentUser && !currentAdmin" class="auth-wrap">
    <div class="auth-card">
      <h2>{{ loginRole === 'admin' ? '管理员登录' : (authMode === 'login' ? '欢迎回来' : '创建账号') }}</h2>
      <p class="subtitle">考研智能推荐系统</p>
      <div v-if="msg" :class="['msg', msgType]" style="position:fixed;top:16px;left:50%;transform:translateX(-50%);z-index:9999;min-width:260px;text-align:center">{{ msg }}</div>
      <!-- 角色切换 -->
      <div class="role-toggle">
        <button :class="['role-btn', { active: loginRole === 'user' }]" @click="loginRole = 'user'; authMode = 'login'">用户登录</button>
        <button :class="['role-btn', { active: loginRole === 'admin' }]" @click="loginRole = 'admin'">管理员登录</button>
      </div>
      <input v-model="authForm.username" :placeholder="loginRole === 'admin' ? '管理员账号' : '用户名'" @keyup.enter="doAuth" />
      <input v-model="authForm.password" type="password" placeholder="密码" @keyup.enter="doAuth" />
      <button class="btn" @click="doAuth">{{ loginRole === 'admin' ? '管理员登录' : (authMode === 'login' ? '登 录' : '注 册') }}</button>
      <p v-if="loginRole === 'user'" class="toggle" @click="authMode = authMode === 'login' ? 'register' : 'login'">
        {{ authMode === 'login' ? '没有账号？' : '已有账号？' }}
        <span>{{ authMode === 'login' ? '去注册' : '去登录' }}</span>
      </p>
      <p v-if="loginRole === 'admin'" class="toggle" style="color:#86868b;font-size:.8rem">默认账号: admin / admin123</p>
    </div>
  </div>

  <!-- ========== 管理员面板 ========== -->
  <div v-else-if="currentAdmin">
    <div v-if="msg" :class="['msg', msgType]" style="position:fixed;top:16px;left:50%;transform:translateX(-50%);z-index:9999;min-width:260px;text-align:center">{{ msg }}</div>
    <div class="dashboard-wrap">
      <aside class="dashboard-left admin-sidebar">
        <div class="brand">🛠️ 管理后台</div>
        <ul class="nav-list">
          <li v-for="n in adminNavItems" :key="n.key" :class="['nav-item', { active: adminPage === n.key }]" @click="adminPage = n.key">
            <span>{{ n.icon }}</span><span>{{ n.label }}</span>
          </li>
        </ul>
        <div style="padding:12px 20px;border-top:1px solid #f0f0f0">
          <div style="font-size:.85rem;color:#6e6e73">管理员：{{ currentAdmin.username }}</div>
          <div style="font-size:.78rem;color:#ff3b30;cursor:pointer;margin-top:4px" @click="logout">退出登录</div>
        </div>
      </aside>
      <main class="main-panel">

        <!-- ===== 用户管理 ===== -->
        <template v-if="adminPage === 'users'">
          <h2 class="section-title">👥 用户管理 <span class="total-badge">共 {{ adUserTotal }} 人</span></h2>
          <div class="card">
            <div class="admin-toolbar">
              <input v-model="adUserKeyword" placeholder="搜索用户名..." @keyup.enter="adUserPage = 1; loadAdminUsers()" />
              <button class="btn-blue btn-sm" @click="adUserPage = 1; loadAdminUsers()">搜索</button>
            </div>
            <table v-if="adUsers.length" class="admin-table">
              <thead><tr><th>ID</th><th>用户名</th><th>专业课</th><th>注册时间</th><th>操作</th></tr></thead>
              <tbody>
                <tr v-for="u in adUsers" :key="u.id">
                  <td>{{ u.id }}</td>
                  <td>{{ u.username }}</td>
                  <td>{{ u.typeName || '-' }}</td>
                  <td>{{ u.createdAt ? u.createdAt.substring(0, 10) : '-' }}</td>
                  <td>
                    <button class="btn-outline btn-sm" @click="openAdminUserEdit(u)">编辑</button>
                    <button class="btn-outline btn-sm btn-danger" style="margin-left:4px" @click="removeAdminUser(u.id)">删除</button>
                  </td>
                </tr>
              </tbody>
            </table>
            <div v-else class="empty-state">暂无用户</div>
            <div class="pagination" v-if="adUserTotalPages > 1">
              <button @click="adUserPage > 1 && (adUserPage--, loadAdminUsers())">&laquo;</button>
              <select :value="adUserPage" @change="adUserPage = +$event.target.value; loadAdminUsers()">
                <option v-for="p in adUserTotalPages" :key="p" :value="p">第 {{ p }} 页</option>
              </select>
              <span class="page-info">/ {{ adUserTotalPages }}</span>
              <button @click="adUserPage < adUserTotalPages && (adUserPage++, loadAdminUsers())">&raquo;</button>
            </div>
          </div>
          <!-- 编辑用户弹窗 -->
          <div v-if="adUserModal" class="plan-modal-overlay" @click.self="adUserModal = false">
            <div class="plan-modal">
              <h3>编辑用户</h3>
              <label>用户名</label><input v-model="adUserForm.username" />
              <label>专业课</label>
              <select v-model="adUserForm.majorTypeId">
                <option :value="null">-- 未选择 --</option>
                <option v-for="ct in adCourseTypes" :key="ct.type_id" :value="ct.type_id">{{ ct.type_name }}</option>
              </select>
              <div class="actions"><button class="btn-outline" @click="adUserModal = false">取消</button><button class="btn-blue" @click="saveAdminUser">保存</button></div>
            </div>
          </div>
        </template>

        <!-- ===== 题库管理 ===== -->
        <template v-if="adminPage === 'questions'">
          <h2 class="section-title">📖 题库管理 <span class="total-badge">共 {{ adQTotal }} 题</span></h2>
          <div class="card">
            <div class="admin-toolbar">
              <input v-model="adQKeyword" placeholder="搜索题目或课程名..." @keyup.enter="adQPage = 1; loadAdminQuestions()" />
              <button class="btn-blue btn-sm" @click="adQPage = 1; loadAdminQuestions()">搜索</button>
              <button class="btn-blue btn-sm" style="margin-left:auto" @click="openAdminQAdd()">+ 新增题目</button>
            </div>
            <table v-if="adQuestions.length" class="admin-table">
              <thead><tr><th>ID</th><th>课程</th><th>题目</th><th>答案</th><th>操作</th></tr></thead>
              <tbody>
                <tr v-for="q in adQuestions" :key="q.id">
                  <td>{{ q.id }}</td>
                  <td class="cell-short">{{ q.courseName }}</td>
                  <td class="cell-long">{{ q.question && q.question.length > 40 ? q.question.substring(0, 40) + '...' : q.question }}</td>
                  <td class="cell-short">{{ q.answer }}</td>
                  <td>
                    <button class="btn-outline btn-sm" @click="openAdminQEdit(q)">编辑</button>
                    <button class="btn-outline btn-sm btn-danger" style="margin-left:4px" @click="removeAdminQuestion(q.id)">删除</button>
                  </td>
                </tr>
              </tbody>
            </table>
            <div v-else class="empty-state">暂无题目</div>
            <div class="pagination" v-if="adQTotalPages > 1">
              <button @click="adQPage > 1 && (adQPage--, loadAdminQuestions())">&laquo;</button>
              <select :value="adQPage" @change="adQPage = +$event.target.value; loadAdminQuestions()">
                <option v-for="p in adQTotalPages" :key="p" :value="p">第 {{ p }} 页</option>
              </select>
              <span class="page-info">/ {{ adQTotalPages }}</span>
              <button @click="adQPage < adQTotalPages && (adQPage++, loadAdminQuestions())">&raquo;</button>
            </div>
          </div>
          <!-- 题目编辑弹窗 -->
          <div v-if="adQModal" class="plan-modal-overlay" @click.self="adQModal = false">
            <div class="plan-modal" style="width:520px">
              <h3>{{ adQEditId ? '编辑题目' : '新增题目' }}</h3>
              <template v-if="!adQEditId">
                <label>课程ID</label><input v-model="adQForm.courseId" placeholder="课程的course_index" />
                <label>课程名称</label><input v-model="adQForm.courseName" />
              </template>
              <label>题目</label><textarea v-model="adQForm.question" rows="2"></textarea>
              <label>选项A</label><input v-model="adQForm.opt1" />
              <label>选项B</label><input v-model="adQForm.opt2" />
              <label>选项C</label><input v-model="adQForm.opt3" />
              <label>选项D</label><input v-model="adQForm.opt4" />
              <label>正确答案（填写选项文本）</label><input v-model="adQForm.answer" />
              <label>解析</label><textarea v-model="adQForm.explanation" rows="2"></textarea>
              <div class="actions"><button class="btn-outline" @click="adQModal = false">取消</button><button class="btn-blue" @click="saveAdminQuestion">保存</button></div>
            </div>
          </div>
        </template>

        <!-- ===== 错题管理 ===== -->
        <template v-if="adminPage === 'wrongQ'">
          <h2 class="section-title">❌ 错题管理 <span class="total-badge">共 {{ adWTotal }} 条</span></h2>
          <div class="card">
            <div class="admin-toolbar">
              <input v-model="adWKeyword" placeholder="搜索题目、课程或用户名..." @keyup.enter="adWPage = 1; loadAdminWrong()" />
              <button class="btn-blue btn-sm" @click="adWPage = 1; loadAdminWrong()">搜索</button>
            </div>
            <table v-if="adWrong.length" class="admin-table">
              <thead><tr><th>ID</th><th>用户</th><th>课程</th><th>题目</th><th>错误次数</th><th>操作</th></tr></thead>
              <tbody>
                <tr v-for="w in adWrong" :key="w.id">
                  <td>{{ w.id }}</td>
                  <td>{{ w.username || w.userId }}</td>
                  <td class="cell-short">{{ w.courseName || '-' }}</td>
                  <td class="cell-long">{{ w.questionText && w.questionText.length > 30 ? w.questionText.substring(0, 30) + '...' : w.questionText }}</td>
                  <td>{{ w.errorCount }}</td>
                  <td><button class="btn-outline btn-sm btn-danger" @click="removeAdminWrong(w.id)">删除</button></td>
                </tr>
              </tbody>
            </table>
            <div v-else class="empty-state">暂无错题记录</div>
            <div class="pagination" v-if="adWTotalPages > 1">
              <button @click="adWPage > 1 && (adWPage--, loadAdminWrong())">&laquo;</button>
              <select :value="adWPage" @change="adWPage = +$event.target.value; loadAdminWrong()">
                <option v-for="p in adWTotalPages" :key="p" :value="p">第 {{ p }} 页</option>
              </select>
              <span class="page-info">/ {{ adWTotalPages }}</span>
              <button @click="adWPage < adWTotalPages && (adWPage++, loadAdminWrong())">&raquo;</button>
            </div>
          </div>
        </template>

        <!-- ===== 学习计划管理 ===== -->
        <template v-if="adminPage === 'plans'">
          <h2 class="section-title">📋 学习计划管理 <span class="total-badge">共 {{ adPTotal }} 条</span></h2>
          <div class="card">
            <div class="admin-toolbar">
              <input v-model="adPKeyword" placeholder="搜索计划标题或用户名..." @keyup.enter="adPPage = 1; loadAdminPlans()" />
              <button class="btn-blue btn-sm" @click="adPPage = 1; loadAdminPlans()">搜索</button>
            </div>
            <table v-if="adPlans.length" class="admin-table">
              <thead><tr><th>ID</th><th>用户</th><th>标题</th><th>目标日期</th><th>状态</th><th>操作</th></tr></thead>
              <tbody>
                <tr v-for="p in adPlans" :key="p.id">
                  <td>{{ p.id }}</td>
                  <td>{{ p.username || p.userId }}</td>
                  <td>{{ p.title }}</td>
                  <td>{{ p.targetDate || '-' }}</td>
                  <td><span :class="['status-badge', p.status]">{{ p.status === 'pending' ? '待开始' : p.status === 'in_progress' ? '进行中' : '已完成' }}</span></td>
                  <td>
                    <button class="btn-outline btn-sm" @click="openAdminPlanEdit(p)">编辑</button>
                    <button class="btn-outline btn-sm btn-danger" style="margin-left:4px" @click="removeAdminPlan(p.id)">删除</button>
                  </td>
                </tr>
              </tbody>
            </table>
            <div v-else class="empty-state">暂无学习计划</div>
            <div class="pagination" v-if="adPTotalPages > 1">
              <button @click="adPPage > 1 && (adPPage--, loadAdminPlans())">&laquo;</button>
              <select :value="adPPage" @change="adPPage = +$event.target.value; loadAdminPlans()">
                <option v-for="p in adPTotalPages" :key="p" :value="p">第 {{ p }} 页</option>
              </select>
              <span class="page-info">/ {{ adPTotalPages }}</span>
              <button @click="adPPage < adPTotalPages && (adPPage++, loadAdminPlans())">&raquo;</button>
            </div>
          </div>
          <!-- 编辑计划弹窗 -->
          <div v-if="adPModal" class="plan-modal-overlay" @click.self="adPModal = false">
            <div class="plan-modal">
              <h3>编辑学习计划</h3>
              <label>标题</label><input v-model="adPForm.title" />
              <label>描述</label><textarea v-model="adPForm.description"></textarea>
              <label>目标日期</label><input v-model="adPForm.targetDate" type="date" />
              <label>状态</label>
              <select v-model="adPForm.status">
                <option value="pending">待开始</option>
                <option value="in_progress">进行中</option>
                <option value="done">已完成</option>
              </select>
              <div class="actions"><button class="btn-outline" @click="adPModal = false">取消</button><button class="btn-blue" @click="saveAdminPlan">保存</button></div>
            </div>
          </div>
        </template>

        <!-- ===== 课程链接管理 ===== -->
        <template v-if="adminPage === 'courseUrls'">
          <h2 class="section-title">🔗 课程链接管理 <span class="total-badge">共 {{ adCTotal }} 门课程</span></h2>
          <div class="card">
            <div class="admin-toolbar">
              <input v-model="adCKeyword" placeholder="搜索课程名..." @keyup.enter="adCPage = 1; loadAdminCourses()" />
              <button class="btn-blue btn-sm" @click="adCPage = 1; loadAdminCourses()">搜索</button>
            </div>
            <table v-if="adCourses.length" class="admin-table">
              <thead><tr><th>ID</th><th>课程名</th><th>类别</th><th>链接</th><th>操作</th></tr></thead>
              <tbody>
                <tr v-for="c in adCourses" :key="c.courseIndex">
                  <td>{{ c.courseIndex }}</td>
                  <td>{{ c.name }}</td>
                  <td class="cell-short">{{ c.typeName || '-' }}</td>
                  <td class="cell-long">
                    <a v-if="c.url" :href="c.url" target="_blank" class="link-text">{{ c.url.length > 40 ? c.url.substring(0, 40) + '...' : c.url }}</a>
                    <span v-else style="color:#86868b">未设置</span>
                  </td>
                  <td><button class="btn-outline btn-sm" @click="openAdminCourseEdit(c)">编辑链接</button></td>
                </tr>
              </tbody>
            </table>
            <div v-else class="empty-state">暂无课程</div>
            <div class="pagination" v-if="adCTotalPages > 1">
              <button @click="adCPage > 1 && (adCPage--, loadAdminCourses())">&laquo;</button>
              <select :value="adCPage" @change="adCPage = +$event.target.value; loadAdminCourses()">
                <option v-for="p in adCTotalPages" :key="p" :value="p">第 {{ p }} 页</option>
              </select>
              <span class="page-info">/ {{ adCTotalPages }}</span>
              <button @click="adCPage < adCTotalPages && (adCPage++, loadAdminCourses())">&raquo;</button>
            </div>
          </div>
          <!-- 编辑链接弹窗 -->
          <div v-if="adCModal" class="plan-modal-overlay" @click.self="adCModal = false">
            <div class="plan-modal">
              <h3>编辑课程链接</h3>
              <p style="font-size:.88rem;color:#6e6e73;margin-bottom:12px">{{ adCForm.name }}</p>
              <label>网页链接 (URL)</label>
              <input v-model="adCForm.url" placeholder="https://..." />
              <div class="actions"><button class="btn-outline" @click="adCModal = false">取消</button><button class="btn-blue" @click="saveAdminCourseUrl">保存</button></div>
            </div>
          </div>
        </template>

      </main>
    </div>
  </div>

  <!-- ========== 已登录用户：Dashboard ========== -->
  <div v-else>
    <!-- 消息条 -->
    <div v-if="msg" :class="['msg', msgType]" style="position:fixed;top:16px;left:50%;transform:translateX(-50%);z-index:9999;min-width:260px;text-align:center">{{ msg }}</div>

    <div class="dashboard-wrap">
      <!-- 左侧导航 -->
      <aside class="dashboard-left">
        <div class="brand">📘 考研推荐系统</div>
        <ul class="nav-list">
          <li v-for="n in navItems" :key="n.key" :class="['nav-item', { active: page === n.key }]" @click="page = n.key">
            <span>{{ n.icon }}</span><span>{{ n.label }}</span>
          </li>
        </ul>
        <div style="padding:12px 20px;border-top:1px solid #f0f0f0">
          <div style="font-size:.85rem;color:#6e6e73">{{ currentUser.username }}</div>
          <div style="font-size:.78rem;color:#0071e3;cursor:pointer;margin-top:4px" @click="logout">退出登录</div>
        </div>
      </aside>

      <!-- 主内容区 -->
      <main class="main-panel">

        <!-- ===== 首页 ===== -->
        <template v-if="page === 'home'">
          <div class="hero" style="border-radius:18px;margin-bottom:28px">
            <h1>你好，{{ currentUser.username }} 👋</h1>
            <p>欢迎使用考研智能推荐系统</p>
          </div>
          <div class="welcome-grid">
            <div class="welcome-card" @click="page = 'recommend'">
              <div class="icon">🎯</div>
              <h3>智能推荐</h3>
              <p>基于协同过滤算法，为你推荐最适合的课程</p>
            </div>
            <div class="welcome-card" @click="page = 'courses'">
              <div class="icon">📚</div>
              <h3>课程大厅</h3>
              <p>浏览全部课程资源，按类别或关键词搜索</p>
            </div>
            <div class="welcome-card" @click="page = 'plans'">
              <div class="icon">📝</div>
              <h3>学习计划</h3>
              <p>制定考研复习计划，跟踪学习进度</p>
            </div>
            <div class="welcome-card" @click="page = 'practice'">
              <div class="icon">✏️</div>
              <h3>题库练习</h3>
              <p>按科目分类练习，自动收录错题</p>
            </div>
            <div class="welcome-card">
              <div class="icon">🔗</div>
              <h3>考研资讯</h3>
              <p>获取最新考研信息与资源链接</p>
              <div class="subcard-grid">
                <a class="sub-card" href="https://yz.chsi.com.cn/" target="_blank">考研资讯</a>
                <a class="sub-card" href="https://www.icourse163.org/" target="_blank">信息库</a>
                <a class="sub-card" href="https://kaoyan.eol.cn/" target="_blank">硕士</a>
              </div>
            </div>
          </div>
        </template>

        <!-- ===== 个人资料 ===== -->
        <template v-if="page === 'profile'">
          <h2 class="section-title">个人资料</h2>
          <div class="card">
            <div class="profile-grid">
              <div class="avatar-col">
                <img v-if="currentUser.avatar" :src="currentUser.avatar" class="avatar-img" />
                <div v-else class="avatar-placeholder">{{ (currentUser.username || '?')[0] }}</div>
                <label style="display:block;margin-top:10px;font-size:.82rem;color:#0071e3;cursor:pointer">
                  更换头像 <input type="file" accept="image/*" style="display:none" @change="doUploadAvatar" />
                </label>
              </div>
              <div class="profile-form">
                <label>用户名</label>
                <input v-model="profileForm.username" />
                <label>考研专业课</label>
                <select v-model="profileForm.majorTypeId">
                  <option :value="null">-- 未选择 --</option>
                  <option v-for="ct in courseTypes" :key="ct.type_id" :value="ct.type_id">{{ ct.type_name }}</option>
                </select>
                <button class="btn-blue" @click="saveProfile">保存修改</button>
              </div>
            </div>
          </div>
          <div class="card">
            <h3 style="font-size:1rem;font-weight:600;margin-bottom:12px">修改密码</h3>
            <div class="profile-form">
              <label>旧密码</label>
              <input v-model="pwdForm.oldPassword" type="password" />
              <label>新密码</label>
              <input v-model="pwdForm.newPassword" type="password" />
              <button class="btn-blue" @click="doChangePassword">更新密码</button>
            </div>
          </div>
        </template>

        <!-- ===== 推荐演示 ===== -->
        <template v-if="page === 'recommend'">
          <h2 class="section-title">智能推荐</h2>
          <div class="card">
            <div style="display:flex;gap:10px;align-items:center;margin-bottom:16px">
              <label style="font-size:.9rem">推荐数量：</label>
              <input v-model.number="recTopN" type="number" min="1" max="50" style="width:80px;padding:8px 12px;border:1px solid #d2d2d7;border-radius:12px" />
              <button class="btn-blue" @click="loadRecommendations" :disabled="recLoading">{{ recLoading ? '加载中...' : '获取推荐' }}</button>
              <button class="btn-outline" @click="loadEvaluation" :disabled="evalLoading">{{ evalLoading ? '评估中...' : '模型评估' }}</button>
            </div>
            <div v-if="evalResult" class="eval-grid">
              <div class="eval-cell" v-for="(v, k) in evalResult" :key="k">
                <div class="label">{{ k }}</div>
                <div class="value">{{ typeof v === 'number' ? v.toFixed(4) : v }}</div>
              </div>
            </div>
          </div>
          <div v-if="recLoading" class="empty-state" style="color:#007aff">正在加载推荐，首次可能需要1-2分钟...</div>
          <div v-else-if="recError" class="empty-state" style="color:#ff3b30;border:1px solid #ff3b30;padding:16px;border-radius:12px;font-size:.85rem;word-break:break-all">{{ recError }}</div>
          <div v-else-if="recList.length" class="course-grid">
            <div v-for="r in recList" :key="r.course_index" class="course-card">
              <h4>{{ r.name }}</h4>
              <div class="meta">{{ r.type_name || '' }}</div>
              <div class="meta">预测评分：{{ (r.predicted_score || 0).toFixed(2) }}</div>
              <span v-if="r.reason" class="reason-tag">{{ r.reason }}</span>
            </div>
          </div>
          <div v-else class="empty-state">点击"获取推荐"开始</div>
        </template>

        <!-- ===== 课程大厅 ===== -->
        <template v-if="page === 'courses'">
          <h2 class="section-title">课程大厅</h2>
          <div class="card">
            <div class="course-search">
              <input v-model="courseKeyword" placeholder="搜索课程..." @keyup.enter="searchCourses" />
              <select v-model="courseSearchMode">
                <option value="name">按名称</option>
                <option value="type">按类别</option>
              </select>
              <button class="btn-blue btn-sm" @click="searchCourses">搜索</button>
            </div>
          </div>
          <div v-if="courseLoading" class="empty-state">加载中...</div>
          <div v-else-if="courseList.length" class="course-grid">
            <div v-for="c in courseList" :key="c.course_index" class="course-card" @click="onCourseClick(c)" style="cursor:pointer">
              <h4>{{ c.name }}</h4>
              <div class="meta">{{ c.type_name || '' }}</div>
              <a v-if="c.url" :href="c.url" target="_blank" class="course-link" @click.stop>🔗 课程链接</a>
            </div>
          </div>
          <div v-else class="empty-state">暂无课程</div>
          <div class="pagination" v-if="courseTotalPages > 1">
            <button @click="coursePage > 1 && gotoPage(coursePage - 1)">&laquo;</button>
            <select :value="coursePage" @change="gotoPage(+$event.target.value)">
              <option v-for="p in courseTotalPages" :key="p" :value="p">第 {{ p }} 页</option>
            </select>
            <span class="page-info">/ {{ courseTotalPages }}</span>
            <button @click="coursePage < courseTotalPages && gotoPage(coursePage + 1)">&raquo;</button>
          </div>
        </template>

        <!-- ===== 学习计划 ===== -->
        <template v-if="page === 'plans'">
          <h2 class="section-title">学习计划</h2>
          <div class="card">
            <div class="plan-toolbar">
              <select v-model="planFilter" @change="loadPlans">
                <option value="">全部状态</option>
                <option value="pending">待开始</option>
                <option value="in_progress">进行中</option>
                <option value="done">已完成</option>
              </select>
              <select v-model="planSort" @change="loadPlans">
                <option value="desc">最新在前</option>
                <option value="asc">最早在前</option>
              </select>
              <button class="btn-blue btn-sm" @click="openPlanModal()">+ 新计划</button>
            </div>
            <table v-if="planList.length" class="plan-table">
              <thead><tr><th>标题</th><th>目标日期</th><th>状态</th><th>操作</th></tr></thead>
              <tbody>
                <tr v-for="plan in planList" :key="plan.id">
                  <td>{{ plan.title }}</td>
                  <td>{{ plan.targetDate || '-' }}</td>
                  <td><span :class="['status-badge', plan.status]">{{ plan.status === 'pending' ? '待开始' : plan.status === 'in_progress' ? '进行中' : '已完成' }}</span></td>
                  <td>
                    <button class="btn-outline btn-sm" @click="openPlanModal(plan)">编辑</button>
                    <button class="btn-outline btn-sm btn-danger" style="margin-left:6px" @click="removePlan(plan.id)">删除</button>
                  </td>
                </tr>
              </tbody>
            </table>
            <div v-else class="empty-state">暂无学习计划</div>
          </div>
          <!-- 计划弹窗 -->
          <div v-if="planModal" class="plan-modal-overlay" @click.self="planModal = false">
            <div class="plan-modal">
              <h3>{{ planEditId ? '编辑计划' : '新建计划' }}</h3>
              <label>标题</label>
              <input v-model="planForm.title" />
              <label>描述</label>
              <textarea v-model="planForm.description"></textarea>
              <label>目标日期</label>
              <input v-model="planForm.targetDate" type="date" />
              <label>状态</label>
              <select v-model="planForm.status">
                <option value="pending">待开始</option>
                <option value="in_progress">进行中</option>
                <option value="done">已完成</option>
              </select>
              <div class="actions">
                <button class="btn-outline" @click="planModal = false">取消</button>
                <button class="btn-blue" @click="savePlan">保存</button>
              </div>
            </div>
          </div>
        </template>

        <!-- ===== 错题反馈 ===== -->
        <template v-if="page === 'wrong'">
          <h2 class="section-title">错题反馈</h2>
          <div class="card">
            <div style="display:flex;gap:10px;align-items:center;margin-bottom:16px">
              <select v-model="wrongCategory" @change="wrongCategoryChange" style="padding:10px 16px;border:1px solid #d2d2d7;border-radius:12px">
                <option value="all">全部科目</option>
                <option value="math">数学</option>
                <option value="english">英语</option>
                <option value="politics">政治</option>
                <option value="major">专业课</option>
              </select>
              <input v-model="wrongKeyword" placeholder="搜索错题..." style="flex:1;padding:10px 16px;border:1px solid #d2d2d7;border-radius:12px" @keyup.enter="wrongPage = 1; loadWrongQuestions()" />
              <button class="btn-blue btn-sm" @click="wrongPage = 1; loadWrongQuestions()">搜索</button>
            </div>
            <div v-if="wrongList.length" class="wrong-list">
              <div v-for="w in wrongList" :key="w.id" class="wrong-row">
                <div class="info">
                  <h4>
                    {{ w.questionText || w.question_text }}
                    <span v-if="w.error_count && w.error_count > 1" class="error-count">× {{ w.error_count }}</span>
                  </h4>
                  <p>课程：{{ w.courseName || w.course_name || '-' }} | 你的答案：{{ w.yourAnswer || w.your_answer || '-' }} | 正确答案：{{ w.correctAnswer || w.correct_answer || '-' }}</p>
                </div>
              </div>
            </div>
            <div v-else class="empty-state">暂无错题记录</div>
            <div class="pagination" v-if="wrongTotalPages > 1">
              <button @click="wrongPage > 1 && (wrongPage--, loadWrongQuestions())">&laquo;</button>
              <select :value="wrongPage" @change="wrongPage = +$event.target.value; loadWrongQuestions()">
                <option v-for="p in wrongTotalPages" :key="p" :value="p">第 {{ p }} 页</option>
              </select>
              <span class="page-info">/ {{ wrongTotalPages }}</span>
              <button @click="wrongPage < wrongTotalPages && (wrongPage++, loadWrongQuestions())">&raquo;</button>
            </div>
          </div>
        </template>

        <!-- ===== 题库练习 ===== -->
        <template v-if="page === 'practice'">
          <h2 class="section-title">题库练习</h2>
          <div class="card">
            <div style="display:flex;gap:10px;align-items:center;margin-bottom:16px">
              <select v-model="practiceCategory" style="padding:10px 16px;border:1px solid #d2d2d7;border-radius:12px">
                <option value="math">数学</option>
                <option value="english">英语</option>
                <option value="politics">政治</option>
                <option value="major">专业课</option>
              </select>
              <button class="btn-blue" @click="loadPractice" :disabled="practiceLoading">{{ practiceLoading ? '加载中...' : '开始练习' }}</button>
            </div>
          </div>
          <div v-if="practiceQuestions.length">
            <div v-for="(q, idx) in practiceQuestions" :key="idx" class="practice-area">
              <div class="q-text">{{ idx + 1 }}. {{ q.question }}</div>
              <div class="options" v-if="q.options">
                <label v-for="opt in (typeof q.options === 'string' ? JSON.parse(q.options) : q.options)" :key="opt">
                  <input type="radio" :name="'q' + idx" :value="opt" v-model="practiceAnswers[idx]" :disabled="!!practiceResults[idx]" />
                  {{ opt }}
                </label>
              </div>
              <div style="margin-top:10px;display:flex;gap:8px">
                <button class="btn-outline btn-sm" @click="checkAnswer(q, idx)" v-if="!practiceResults[idx]">提交答案</button>
                <button class="btn-outline btn-sm btn-danger" @click="markPracticeWrong(q, idx)" v-if="practiceResults[idx] === 'wrong' && !practiceMarked[idx]">收录错题</button>
                <span v-if="practiceMarked[idx]" style="font-size:.82rem;color:#34c759;font-weight:600">✓ 已收录</span>
              </div>
              <div v-if="practiceResults[idx]" :class="['practice-result', practiceResults[idx]]">
                {{ practiceResults[idx] === 'correct' ? '✅ 回答正确！' : '❌ 回答错误，正确答案：' + q.answer }}
              </div>
            </div>
          </div>
          <div v-else-if="!practiceLoading" class="empty-state">选择科目并点击"开始练习"</div>
        </template>

      </main>
    </div>
  </div>
</template>
