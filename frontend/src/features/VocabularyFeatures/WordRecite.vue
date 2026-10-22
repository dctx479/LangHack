<template>
  <div class="word-recite-container">
    <!-- 词库选择界面 -->
    <div class="vocabulary-selection" v-if="!selectedVocabulary">
      <div class="page-header">
        <h2 class="page-title">选择词库</h2>
        <p class="page-subtitle">请选择要背诵的单词库</p>
      </div>

      <div class="vocabularies-grid">
        <div 
          v-for="vocab in vocabularies" 
          :key="vocab.id"
          class="vocabulary-card"
          @click="selectVocabulary(vocab)"
        >
          <h3 class="vocab-name">{{ vocab.name }}</h3>
          <p class="vocab-desc">{{ vocab.description }}</p>
          <p class="vocab-count">{{ vocab.wordCount }} 个单词</p>
        </div>
      </div>
    </div>

    <!-- 是否加入文章生词选择界面 -->
    <div class="article-choice-selection" v-else-if="includeArticleWords === null">
      <div class="page-header">
        <h2 class="page-title">是否加入文章生词？</h2>
        <p class="page-subtitle">把生词本里筛选过的文章词加进每日清单一起背诵</p>
      </div>

      <div class="article-choice-options">
        <div class="choice-card" @click="selectIncludeArticle(true)">
          <span class="choice-icon">📚</span>
          <span class="choice-title">加入文章生词</span>
          <span class="choice-desc">每日清单 = 自选文章词数 + {{ currentVocabularyName }}</span>
          <span class="choice-extra" v-if="articleWordsCount !== null">
            当前可用 {{ articleWordsCount }} 个
            <button class="refresh-mini-btn" @click.stop="refreshArticleWordsCount" :disabled="articleWordsLoading">
              {{ articleWordsLoading ? '刷新中…' : '刷新' }}
            </button>
          </span>
        </div>
        <div class="choice-card" @click="selectIncludeArticle(false)">
          <span class="choice-icon">📖</span>
          <span class="choice-title">仅背诵词库</span>
          <span class="choice-desc">每日清单 = {{ currentVocabularyName }}</span>
        </div>
      </div>

      <div class="daily-footer">
        <button @click="backToSelection" class="text-btn">返回</button>
      </div>
    </div>

    <!-- 每日文章生词数量选择界面 -->
    <div class="daily-count-selection" v-else-if="includeArticleWords === true && articleDailyCount === null">
      <div class="page-header">
        <h2 class="page-title">每天背几个文章生词？</h2>
        <p class="page-subtitle">从生词本里挑选若干文章词加入每日清单，剩余名额自动用 {{ currentVocabularyName }} 补齐</p>
      </div>

      <div class="progress-info">
        <span>可用文章生词：{{ articleWordsCount }} 个</span>
      </div>

      <div class="count-options">
        <div
          v-for="option in filteredArticleCountOptions"
          :key="option.value"
          class="count-card"
          :class="{ insufficient: option.insufficient }"
          @click="selectArticleDailyCount(option)"
        >
          <span class="count-number">{{ option.display }}</span>
          <span class="count-label" v-if="option.label">个/天</span>
          <span class="count-hint" v-if="option.hint">{{ option.hint }}</span>
        </div>
      </div>

      <div class="daily-footer">
        <button @click="backToIncludeChoice" class="text-btn">返回</button>
      </div>
    </div>

    <!-- 每日复习数量选择界面 -->
    <div class="daily-count-selection" v-else-if="!dailyCountSelected">
      <div class="page-header">
        <h2 class="page-title">设置每日复习量</h2>
        <p class="page-subtitle">选择每天要复习的单词数量</p>
      </div>

      <div class="progress-info">
        <span>剩余单词：{{ remainingWords }} 个</span>
      </div>

      <div class="count-options">
        <div 
          v-for="count in dailyCountOptions" 
          :key="count"
          class="count-card"
          @click="selectDailyCount(count)"
        >
          <span class="count-number">{{ count }}</span>
          <span class="count-label">个/天</span>
          <span class="count-days">{{ calculateDays(count) }}天完成</span>
        </div>
      </div>

      <!-- 底部返回按钮 -->
      <div class="daily-footer">
        <button @click="backToSelection" class="text-btn">返回</button>
      </div>
    </div>

    <!-- 背诵界面 -->
    <div class="recitation-view" v-else>
      <!-- 顶部概览 -->
      <div class="recite-header-bar">
        <div class="page-header recite-page-header">
          <h2 class="page-title">{{ currentVocabularyName }}</h2>
          <p class="page-subtitle">每日复习 {{ dailyCount + (includeArticleWords ? (articleDailyCount || 0) : 0) }} 个单词</p>
        </div>
        <button class="settings-gear-btn" @click="openSettingsDialog" title="修改每日背诵设置">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="12" cy="12" r="3"/>
            <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"/>
          </svg>
        </button>
      </div>

      <!-- 统计卡片 -->
      <div class="stats-cards">
        <div class="stat-card">
          <span class="stat-label">待复习</span>
          <span class="stat-value">{{ pendingWords.length }}</span>
        </div>
        <div class="stat-card">
          <span class="stat-label">已掌握</span>
          <span class="stat-value">{{ masteredWords }}</span>
        </div>
        <div class="stat-card">
          <span class="stat-label">今日复习</span>
          <span class="stat-value">{{ todayReviewed }}</span>
        </div>
      </div>

      <!-- 单词卡片 -->
      <div class="word-card" v-if="currentWord">
        <!-- 自动播放开关 -->
        <div class="auto-speak-toggle" v-if="!isMobile">
          <label class="toggle-label">
            <input type="checkbox" v-model="autoSpeakEnabled" @change="onAutoSpeakChange" class="toggle-checkbox">
            <span class="toggle-text">{{ autoSpeakEnabled ? '自动播放已开启' : '自动播放已关闭' }}</span>
          </label>
        </div>

        <div class="word-content">
          <div class="word-header">
            <h3 class="word-text clickable-word" @click="toggleMeaning">{{ currentWord.word }}</h3>
            <button
              v-if="!isMobile"
              @click="speakWordManually"
              class="speak-btn"
              title="点击播放发音"
            >
              <!-- 播放图标 -->
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5"/>
                <path d="M19.07 4.93a10 10 0 0 1 0 14.14M15.54 8.46a5 5 0 0 1 0 7.07"/>
              </svg>
            </button>
          </div>
          <div class="word-meaning" v-if="showMeaning">
            <p>{{ currentWord.meaning }}</p>
            <!-- 单词详细信息 -->
            <div class="word-details" v-if="wordDetails">
              <div class="detail-section" v-if="wordDetails.phonetic">
                <span class="detail-label">音标：</span>
                <span class="detail-value">{{ wordDetails.phonetic }}</span>
              </div>
              <div class="detail-section" v-if="wordDetails.origin">
                <span class="detail-label">词源：</span>
                <span class="detail-value">{{ wordDetails.origin }}</span>
              </div>
              <div class="meanings-section" v-for="(meaning, index) in wordDetails.meanings" :key="index">
                <div class="part-of-speech">{{ meaning.partOfSpeech }}</div>
                <div class="definitions">
                  <div v-for="(def, idx) in meaning.definitions.slice(0, 2)" :key="idx" class="definition-item">
                    <p class="definition">{{ def.definition }}</p>
                    <p class="definition-translation" v-if="def.definitionTranslation">{{ def.definitionTranslation }}</p>
                    <p class="definition-translation loading" v-else-if="def.translating">翻译中...</p>
                    <div v-if="def.example" class="example-wrapper">
                      <p class="example">例句：{{ def.example }}</p>
                      <p class="example-translation" v-if="def.exampleTranslation">{{ def.exampleTranslation }}</p>
                    </div>
                  </div>
                </div>
              </div>
            </div>
            <!-- 例句:优先 AI 生成,AI 不可用时用词典 API 自带例句兜底 -->
            <div class="ai-examples-section" v-if="aiExamples.length > 0 || aiExamplesLoading || dictExamples.length > 0">
              <div class="ai-examples-title">例句</div>
              <!-- AI 例句加载中 -->
              <p class="ai-examples-loading" v-if="aiExamplesLoading && aiExamples.length === 0 && dictExamples.length === 0">例句生成中...</p>
              <!-- AI 例句 -->
              <div v-for="(ex, i) in aiExamples" :key="'ai-'+i" class="ai-example-item">
                <p class="ai-example-en">{{ ex.sentence }}</p>
                <p class="ai-example-zh" v-if="ex.translation">{{ ex.translation }}</p>
                <p class="ai-example-zh loading" v-else-if="ex.translating">翻译中...</p>
              </div>
              <!-- 词典 API 例句兜底(AI 无结果时) -->
              <template v-if="!aiExamplesLoading && aiExamples.length === 0">
                <div v-for="(ex, i) in dictExamples" :key="'dict-'+i" class="ai-example-item">
                  <p class="ai-example-en">{{ ex.sentence }}</p>
                  <p class="ai-example-zh" v-if="ex.translation">{{ ex.translation }}</p>
                </div>
              </template>
            </div>
          </div>
        </div>

        <div class="quiz-options" v-if="showQuiz && !showMeaning && quizOptions.length">
          <button
            v-for="(option, index) in quizOptions"
            :key="index"
            @click="selectQuizOption(option)"
            class="btn quiz-btn"
            :class="{ 'quiz-btn--wrong': wrongOption === option }"
            :disabled="isHandlingWordAction || wrongOption !== null"
          >
            {{ option }}
          </button>
        </div>

        <!-- 答错后显示单词释义 -->
        <div class="wrong-word-hint" v-if="wrongOption !== null">
          <div class="hint-header">
            <span class="hint-icon">💡</span>
            <span class="hint-title">正确答案</span>
          </div>
          <div class="hint-content">
            <p class="hint-word">{{ currentWord.word }}</p>
            <p class="hint-meaning">{{ currentWord.meaning }}</p>
          </div>
        </div>

        <!-- 认识 / 不认识操作 -->
        <div class="action-buttons" v-if="!showMeaning">
          <button
            class="btn action-btn action-btn--unknown"
            :disabled="isHandlingWordAction"
            @click="handleDontKnowWord"
          >
            不认识
          </button>
          <button
            class="btn action-btn action-btn--known"
            :disabled="isHandlingWordAction"
            @click="handleKnowWord"
          >
            认识
          </button>
        </div>
      </div>

      <!-- 无单词提示 -->
      <div class="empty-state" v-else>
        <p>🎉 恭喜！今日复习任务已完成</p>
        <p class="empty-subtitle">今日复习：{{ todayReviewed }} 个单词</p>
        <div class="empty-actions">
          <button @click="loadMoreWords" class="btn btn-primary">再来一组</button>
          <button @click="startDeepRecite" class="btn btn-warning">深度背诵</button>
          <button @click="takeBreak" class="btn btn-info">休息一会</button>
          <button @click="backToSettings" class="btn btn-secondary">返回设置</button>
        </div>
      </div>
    </div>

    <!-- 深度背诵界面 -->
    <div class="deep-recite-view" v-if="showDeepRecite">
      <div class="page-header">
        <h2 class="page-title">深度背诵</h2>
        <p class="page-subtitle">根据释义输入正确的单词</p>
      </div>

      <!-- 深度背诵卡片 -->
      <div class="word-card" v-if="currentDeepWord">
        <div class="word-content">
          <div class="word-meaning">
            <p>{{ currentDeepWord.meaning }}</p>
          </div>
          <input 
            v-model="userInput" 
            @keyup.enter="checkDeepAnswer" 
            placeholder="请输入单词"
            class="word-input"
            :class="{ 'correct': answerStatus === 'correct', 'wrong': answerStatus === 'wrong' }"
            ref="inputRef"
          />
          <div class="answer-feedback" v-if="answerStatus">
            <span v-if="answerStatus === 'correct'" class="correct-text">✓ 正确！</span>
            <span v-if="answerStatus === 'wrong'" class="wrong-text">✗ 错误！</span>
          </div>
          <div class="deep-options" v-if="showDeepOptions">
            <p class="options-title">请选择正确答案：</p>
            <div class="options-grid">
              <button 
                v-for="(option, index) in deepOptions" 
                :key="index"
                @click="selectDeepOption(option)"
                class="btn option-btn"
              >
                {{ option }}
              </button>
            </div>
            <button @click="retryDeepInput" class="btn btn-secondary retry-btn">再来一遍</button>
          </div>
          <button @click="checkDeepAnswer" class="btn btn-primary submit-btn" v-if="!showDeepOptions">提交</button>
        </div>
      </div>

      <!-- 深度背诵完成 -->
      <div class="empty-state" v-else>
        <p>🎉 深度背诵完成！</p>
        <p class="empty-subtitle">正确率：{{ deepReciteScore }}%</p>
        <button @click="exitDeepRecite" class="btn btn-primary">返回</button>
      </div>

      <!-- 底部退出按钮 -->
      <div class="deep-footer">
        <button @click="exitDeepRecite" class="text-btn">退出</button>
      </div>
    </div>

    <!-- 修改设置弹窗：齿轮按钮和完成态"返回设置"都打开这个 -->
    <el-dialog
      v-model="settingsDialogVisible"
      title="修改每日背诵设置"
      width="420px"
      :close-on-click-modal="false"
      class="settings-dialog"
    >
      <div class="settings-form">
        <div class="form-row">
          <label class="form-label">CET 每日词数</label>
          <el-input-number
            v-model="draftDailyCount"
            :min="1"
            :max="500"
            :step="5"
            controls-position="right"
            style="width: 160px"
          />
        </div>

        <div class="form-row">
          <label class="form-label">加入文章生词</label>
          <el-switch v-model="draftIncludeArticle" />
        </div>

        <div class="form-row" v-if="draftIncludeArticle">
          <label class="form-label">文章每日词数</label>
          <el-input-number
            v-model="draftArticleDailyCount"
            :min="0"
            :max="articleWordsCount || 0"
            :step="5"
            controls-position="right"
            style="width: 160px"
          />
          <span class="form-hint">当前可用 {{ articleWordsCount || 0 }} 个</span>
        </div>

        <div class="form-row form-tip">
          <span>保存后会按新数量重新挑选待复习单词，今日已复习的计数不变。</span>
        </div>
      </div>

      <template #footer>
        <div class="dialog-footer-row">
          <el-button type="warning" plain @click="goChangeVocabFromDialog">换词库</el-button>
          <span class="dialog-footer-spacer"></span>
          <el-button @click="cancelSettings">取消</el-button>
          <el-button type="primary" @click="saveSettings">保存</el-button>
        </div>
      </template>
    </el-dialog>

  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { calculateSM2 } from '@/utils/sm2'
import { updateMasteryLevel, addWordToVocabularyByWord, getVocabularyList } from '@/api/student'
import { getWordExplanation } from '@/api/wordExplanation'
import { useUserStore } from '@/store/user'

const router = useRouter()
const userStore = useUserStore()

// 获取当前用户 ID
const currentUserId = computed(() => userStore.userInfo?.id || 'anonymous')
const isMobile = ref(typeof window !== 'undefined' ? window.innerWidth < 768 : false)

// 词库配置
const vocabularies = ref([
  {
    id: 'cet6',
    name: '六级核心词汇',
    description: '大学英语六级核心词汇',
    wordCount: 1500,
    file: '/data/六级核心词汇.csv'
  },
  {
    id: 'cet4',
    name: '四级核心词汇',
    description: '大学英语四级核心词汇',
    wordCount: 5038,
    file: '/data/四级核心词汇.csv'
  },
  {
    id: 'gaokao',
    name: '高考英语必背词汇',
    description: '高考英语核心词汇',
    wordCount: 3603,
    file: '/data/高考英语必背词汇.csv'
  }
])

const selectedVocabulary = ref(null)
const showMeaning = ref(false)
const currentWordIndex = ref(0)
const pendingWords = ref([])
const masteredWords = ref(0)
const todayReviewed = ref(0)
const allWords = ref([])
const wordImageUrl = ref('')
const quizOptions = ref([])
const showQuiz = ref(false)
const wordDetails = ref(null)
// AI 生成的例句列表:{ sentence, translation, translating } — 用于覆盖 dict-api 没例句的词
const aiExamples = ref([])
const aiExamplesLoading = ref(false)

// 词典 API 自带例句兜底:AI 不可用时展示已有的 dict-api 例句
const dictExamples = computed(() => {
  if (!wordDetails.value?.meanings) return []
  const seen = new Set()
  const out = []
  for (const m of wordDetails.value.meanings) {
    for (const d of (m.definitions || [])) {
      if (d.example && !seen.has(d.example)) {
        seen.add(d.example)
        out.push({ sentence: d.example, translation: d.exampleTranslation || '', translating: false })
      }
    }
  }
  return out.slice(0, 5)
})
const quizError = ref(false)
const wrongOption = ref(null)
const isHandlingWordAction = ref(false)
const dailyCount = ref(20)
const dailyCountSelected = ref(false)
const dailyCountOptions = ref([10, 20, 30, 50, 100])
// 是否加入文章生词：null=未选 / true / false
const includeArticleWords = ref(null)
const articleWords = ref([])
const articleWordsCount = ref(null)
const articleWordsLoading = ref(false)
// quiz 干扰项专用池：合并三本 CSV，避开后端 words 表里的占位释义（source='basic_auto'）
const distractorPool = ref([])
// 每日要背的文章生词数量（null=未选；超过可用数量时自动取全部）
const articleDailyCount = ref(null)
const articleDailyCountOptions = ref([10, 20, 30, 50, 100])
// 修改设置弹窗：编辑过程中改 draft，保存才覆盖真实状态 + 写 localStorage
const settingsDialogVisible = ref(false)
const draftDailyCount = ref(20)
const draftIncludeArticle = ref(false)
const draftArticleDailyCount = ref(0)
// 深度背诵状态
const showDeepRecite = ref(false)
const deepReciteWords = ref([])
const currentDeepWordIndex = ref(0)
const deepReciteInput = ref('')
const showDeepOptions = ref(false)
const deepOptions = ref([])
const deepReciteScore = ref(0)

const userInput = ref('')
const answerStatus = ref(null)
const deepReciteCorrect = ref(0)
const deepReciteTotal = ref(0)
const inputRef = ref(null)
// 自动发音开关：默认开启；用户在背诵页点喇叭按钮可切换静音，状态持久化到 localStorage
// key 不与某词库绑定 —— 这是个全局偏好，跨词库一致
const autoSpeakEnabled = ref(!isMobile.value && localStorage.getItem('autoSpeakEnabled') !== '0')

const currentVocabularyName = computed(() => {
  const vocab = vocabularies.value.find(v => v.id === selectedVocabulary.value)
  return vocab ? vocab.name : ''
})

const currentWord = computed(() => pendingWords.value[currentWordIndex.value] || null)

// 背诵索引的 localStorage key：包含"是否加入文章词"维度，避免切换文章生词开关后复用错误的索引
const vocabIndexKey = () => {
  const includeFlag = includeArticleWords.value ? 'A' : 'N'
  return `vocab_index_${currentUserId.value}_${selectedVocabulary.value}_${includeFlag}`
}

// 清掉某词库下所有维度（A/N）的索引 key
// 用于"改设置 / 换词库 / loadMoreWords"——这些场景下旧索引指向的位置已经无意义，留着会让下次进入跳到陌生位置
const clearAllVocabIndexKeys = (vocabId = selectedVocabulary.value) => {
  if (!vocabId) return
  localStorage.removeItem(`vocab_index_${currentUserId.value}_${vocabId}_A`)
  localStorage.removeItem(`vocab_index_${currentUserId.value}_${vocabId}_N`)
}

// 跨会话设置持久化的 localStorage key 集中管理
// 让"选过一次设置之后，下次直接进背诵页"成为可能；同时配合"换词库/重置设置"统一清理
const selectedVocabKey = () => `selectedVocabulary_${currentUserId.value}`
const dailyCountKey = (vocabId = selectedVocabulary.value) => `daily_count_${currentUserId.value}_${vocabId}`
const articleDailyCountKey = (vocabId = selectedVocabulary.value) => `article_daily_count_${currentUserId.value}_${vocabId}`
const includeArticleKey = (vocabId = selectedVocabulary.value) => `include_article_${currentUserId.value}_${vocabId}`

const currentDeepWord = computed(() => deepReciteWords.value[currentDeepWordIndex.value] || null)

const remainingWords = computed(() => {
  return allWords.value.filter(w => !w.mastered).length
})

const calculateDays = (dailyCount) => {
  const remaining = remainingWords.value
  if (remaining === 0) return 0
  return Math.ceil(remaining / dailyCount)
}

// 加载单词图片
const loadWordImage = () => {
  // 使用 Picsum Photos 生成随机图片作为占位符
  wordImageUrl.value = `https://picsum.photos/400/300?random=${Math.random()}`
}

// 图片加载失败处理
const imageLoadError = () => {
  wordImageUrl.value = ''
}

// 单词发音
// 静音状态下直接 return，避免任何路径（自动 watch / 将来其他位置调用）意外播放
const speakWord = () => {
  if (typeof window === 'undefined' || !window.speechSynthesis) return
  if (!currentWord.value) return
  if (isMobile.value) return
  if (!autoSpeakEnabled.value) return

  // 先停止上一个单词的发音，避免快速切换时声音重叠
  speechSynthesis.cancel()

  // 使用浏览器 Web Speech API
  const utterance = new SpeechSynthesisUtterance(currentWord.value.word)
  utterance.lang = 'en-US'
  utterance.rate = 0.8
  utterance.pitch = 1
  speechSynthesis.speak(utterance)
}

// 手动播放单词发音（点击按钮时）
const speakWordManually = () => {
  if (!currentWord.value) return
  if (isMobile.value) return

  // 先停止当前正在播放的语音，立即播放新单词
  speechSynthesis.cancel()

  // 使用浏览器 Web Speech API
  const utterance = new SpeechSynthesisUtterance(currentWord.value.word)
  utterance.lang = 'en-US'
  utterance.rate = 0.8
  utterance.pitch = 1
  speechSynthesis.speak(utterance)
}

// 自动播放开关变化时的处理
const onAutoSpeakChange = () => {
  if (isMobile.value) {
    autoSpeakEnabled.value = false
    return
  }
  localStorage.setItem('autoSpeakEnabled', autoSpeakEnabled.value ? '1' : '0')
  if (!autoSpeakEnabled.value) {
    // 关闭自动播放时，停止当前播放
    speechSynthesis.cancel()
  } else {
    // 开启自动播放时，立即播放当前单词
    speakWord()
  }
}

// 切换自动发音开关：写 localStorage；切到静音时立刻打断正在播放的声音
const toggleAutoSpeak = () => {
  autoSpeakEnabled.value = !autoSpeakEnabled.value
  localStorage.setItem('autoSpeakEnabled', autoSpeakEnabled.value ? '1' : '0')
  if (!autoSpeakEnabled.value) {
    speechSynthesis.cancel()
  }
}

// 生成测验选项
const generateQuizOptions = () => {
  if (!currentWord.value) return

  const correctMeaning = currentWord.value.meaning
  // 所有干扰项统一从三本 CSV 合并池抽取（高考/四级/六级），不再使用当前 allWords 或 articleWords
  // 这样能彻底避开后端 words 表里 source='basic_auto' 的占位释义
  const otherWords = distractorPool.value.filter(
    w => w.word !== currentWord.value.word && w.meaning !== correctMeaning
  )

  // 随机选择3个错误选项
  const shuffled = otherWords.sort(() => Math.random() - 0.5)
  const wrongOptions = shuffled.slice(0, 3).map(w => w.meaning)

  // 组合所有选项并打乱顺序
  const allOptions = [correctMeaning, ...wrongOptions]
  quizOptions.value = allOptions.sort(() => Math.random() - 0.5)
  showQuiz.value = true
}

// 处理选择题选项
const selectQuizOption = async (selectedMeaning) => {
  if (!currentWord.value || isHandlingWordAction.value || wrongOption.value !== null) return

  if (selectedMeaning === currentWord.value.meaning) {
    // 选对：标记为掌握，进入下一个单词
    // 注意：不要在 await rateWord 后手动 showQuiz=false——rateWord 推进 currentWordIndex 后，
    // watch(currentWord) 会在 microtask 里把 showQuiz / wrongOption 重置并重新生成选项。
    // 旧代码在 await 之后再写 false，会把 watcher 刚刚设的 true 又压回 false，
    // 导致下一个词出现时选择题选项消失。
    isHandlingWordAction.value = true
    try {
      await rateWord(5)
    } finally {
      isHandlingWordAction.value = false
    }
  } else {
    // 选错：累计不认识次数 + 持久化，但不结束本词、不计入今日完成数
    // 不立刻回写后端：避免与后续"选对"时的 MASTERED 同步发生竞争（晚到的 LEARNING 会把 MASTERED 覆写）
    // 标记 synced=false 后由下次进入页面的 resyncPendingWords 兜底补传
    currentWord.value.unknownCount = (currentWord.value.unknownCount || 0) + 1
    currentWord.value.lastReviewTime = new Date().toISOString()
    if (currentWord.value.source !== 'article') {
      currentWord.value.synced = false
      saveProgress()
    }
    // 标记错误选项为红色，并显示正确答案提示
    wrongOption.value = selectedMeaning
    setTimeout(() => {
      wrongOption.value = null
    }, 3000) // 延长到3秒，给用户足够时间记忆
  }
}

const handleKnowWord = async () => {
  if (!currentWord.value || isHandlingWordAction.value) return
  isHandlingWordAction.value = true
  try {
    await rateWord(5)
  } finally {
    isHandlingWordAction.value = false
  }
}

const handleDontKnowWord = () => {
  if (!currentWord.value || isHandlingWordAction.value) return
  showMeaning.value = true
  isHandlingWordAction.value = true
  // 显示释义 3 秒后自动标记为不认识并进入下一个单词
  setTimeout(async () => {
    try {
      await rateWord(0)
    } finally {
      showMeaning.value = false
      isHandlingWordAction.value = false
    }
  }, 3000)
}

// 获取单词详细信息
// 防止"用户连续点认识/不认识 → 多个 fetch 并发 → 慢请求覆盖快请求"导致显示错位
// 每次发起前自增 token，回写 wordDetails 之前比对，旧请求直接丢弃
let fetchWordDetailsToken = 0

const fetchWordDetails = async (word) => {
  const myToken = ++fetchWordDetailsToken
  try {
    const response = await fetch(`https://api.dictionaryapi.dev/api/v2/entries/en/${word}`)
    if (!response.ok) throw new Error('API请求失败')
    const data = await response.json()
    if (myToken !== fetchWordDetailsToken) return
    if (data && data.length > 0) {
      const entry = data[0]
      // 预初始化翻译字段，避免 Vue 对后续动态新增属性的响应式追踪失效
      // 同时这里就裁到模板要显示的 2 条/词性，避免少见的多义词触发 batch 200 条上限
      const meanings = (entry.meanings || []).map(m => ({
        partOfSpeech: m.partOfSpeech,
        definitions: (m.definitions || []).slice(0, 2).map(d => ({
          definition: d.definition,
          example: d.example || '',
          definitionTranslation: '',
          exampleTranslation: '',
          translating: false
        }))
      }))
      wordDetails.value = {
        phonetic: entry.phonetic || entry.phonetics?.[0]?.text || '',
        meanings,
        origin: entry.origin || ''
      }

      // 翻译所有释义和例句
      translateDefinitionsAndExamples()
    } else {
      wordDetails.value = null
    }
  } catch (error) {
    if (myToken !== fetchWordDetailsToken) return
    console.error('获取单词详情失败:', error)
    wordDetails.value = null
  }
}

// 拉 AI 例句:dict-api 经常缺例句(如 latitude),走后端 WordAiExplanationService 兜底
// 同样用 token 防并发覆盖;最多取 3 句翻译展示
let fetchAiExamplesToken = 0

const fetchAiExamples = async (word) => {
  const myToken = ++fetchAiExamplesToken
  aiExamples.value = []
  aiExamplesLoading.value = true
  try {
    const resp = await getWordExplanation(word, false)
    if (myToken !== fetchAiExamplesToken) return
    const examples = Array.isArray(resp?.data?.usageExamples) ? resp.data.usageExamples : []
    // 过滤掉演示模式下的占位例句
    const real = examples.filter(s => s && typeof s === 'string'
      && !s.startsWith('This is an example sentence with ')
      && !s.startsWith('Learn to use '))
    const picks = real.slice(0, 3).map(s => ({ sentence: s, translation: '', translating: true }))
    aiExamples.value = picks
    if (picks.length === 0) {
      aiExamplesLoading.value = false
      return
    }

    // 批量翻译这几条例句
    try {
      const transResp = await fetch('/api/translate/batch', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          texts: picks.map(p => p.sentence),
          direction: 'en-to-zh'
        })
      })
      const transJson = await transResp.json()
      if (myToken !== fetchAiExamplesToken) return
      if (transJson.code === 200 && Array.isArray(transJson.data)) {
        picks.forEach((p, i) => {
          p.translation = transJson.data[i] || ''
          p.translating = false
        })
      } else {
        picks.forEach(p => { p.translating = false })
      }
    } catch (e) {
      if (myToken !== fetchAiExamplesToken) return
      console.error('AI 例句翻译失败:', e)
      picks.forEach(p => { p.translating = false })
    }
  } catch (error) {
    if (myToken !== fetchAiExamplesToken) return
    console.error('获取 AI 例句失败:', error)
    aiExamples.value = []
  } finally {
    if (myToken === fetchAiExamplesToken) aiExamplesLoading.value = false
  }
}

// 翻译释义和例句
const translateDefinitionsAndExamples = async () => {
  if (!wordDetails.value || !wordDetails.value.meanings) return

  const textsToTranslate = []
  const itemsMap = [] // 记录每个文本对应的 def 和类型（definition 或 example）

  wordDetails.value.meanings.forEach(meaning => {
    meaning.definitions?.forEach(def => {
      // 标记翻译中
      def.translating = true

      // 添加释义到翻译列表
      textsToTranslate.push(def.definition)
      itemsMap.push({ def, type: 'definition' })

      // 如果有例句，也添加到翻译列表
      if (def.example) {
        textsToTranslate.push(def.example)
        itemsMap.push({ def, type: 'example' })
      }
    })
  })

  if (textsToTranslate.length === 0) return

  // 批量翻译所有释义和例句
  try {
    const response = await fetch('/api/translate/batch', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        texts: textsToTranslate,
        direction: 'en-to-zh'
      })
    })
    const result = await response.json()

    if (result.code === 200 && result.data) {
      itemsMap.forEach((item, index) => {
        const translation = result.data[index] || ''
        if (item.type === 'definition') {
          item.def.definitionTranslation = translation
        } else {
          item.def.exampleTranslation = translation
        }
        item.def.translating = false
      })
    } else {
      itemsMap.forEach(item => {
        item.def.translating = false
      })
    }
  } catch (error) {
    console.error('翻译失败:', error)
    itemsMap.forEach(item => {
      item.def.translating = false
    })
  }
}

// 切换单词意思显示
const toggleMeaning = () => {
  showMeaning.value = !showMeaning.value
}

// 深度背诵相关函数
const startDeepRecite = () => {
  if (showDeepRecite.value) exitDeepRecite()
  // 获取今天背诵过的单词：CET 词来自 allWords，文章词来自 pendingWords（未持久化到 allWords）
  const isToday = (t) => t && new Date(t).toDateString() === new Date().toDateString()
  const cetTodayWords = allWords.value.filter(w => isToday(w.lastReviewTime))
  const articleTodayWords = pendingWords.value.filter(w => w.source === 'article' && isToday(w.lastReviewTime))
  const todayWords = [...cetTodayWords, ...articleTodayWords]
  if (todayWords.length === 0) {
    ElMessage.warning('今天还没有背诵过单词')
    return
  }
  // 随机打乱
  deepReciteWords.value = todayWords.sort(() => Math.random() - 0.5)
  currentDeepWordIndex.value = 0
  userInput.value = ''
  answerStatus.value = null
  deepReciteCorrect.value = 0
  deepReciteTotal.value = 0
  deepReciteScore.value = 0
  showDeepOptions.value = false
  deepOptions.value = []
  showDeepRecite.value = true
  // 自动聚焦输入框
  setTimeout(() => {
    if (inputRef.value) inputRef.value.focus()
  }, 100)
}

const checkDeepAnswer = async () => {
  if (!currentDeepWord.value || !userInput.value.trim()) return

  const isCorrect = userInput.value.trim().toLowerCase() === currentDeepWord.value.word.toLowerCase()
  deepReciteTotal.value++

  if (isCorrect) {
    answerStatus.value = 'correct'
    deepReciteCorrect.value++
    deepReciteScore.value = Math.round((deepReciteCorrect.value / deepReciteTotal.value) * 100)

    // 深度背诵答对：标记为已掌握
    const word = currentDeepWord.value
    const isArticleWord = word.source === 'article'

    if (isArticleWord) {
      // 文章词：标记为已掌握
      try {
        await updateMasteryLevel(word.wordId, { masteryLevel: 'MASTERED', isCorrect: true })
        word.mastered = true
        masteredWords.value++
      } catch (err) {
        console.error('文章词标记已掌握失败:', err)
      }
    } else {
      // CET 词：标记为已掌握
      word.mastered = true
      word.lastReviewTime = new Date().toISOString()
      syncWordToDatabase(word, 'MASTERED')
      saveProgress()
      masteredWords.value++
    }

    // 延迟后进入下一个单词
    setTimeout(() => {
      userInput.value = ''
      answerStatus.value = null
      showDeepOptions.value = false
      if (currentDeepWordIndex.value < deepReciteWords.value.length - 1) {
        currentDeepWordIndex.value++
        if (inputRef.value) inputRef.value.focus()
      } else {
        // 完成
      }
    }, 1500)
  } else {
    answerStatus.value = 'wrong'
    // 增加单词出现频率
    currentDeepWord.value.unknownCount = (currentDeepWord.value.unknownCount || 0) + 1
    if (currentDeepWord.value.source !== 'article') {
      saveProgress()
    }
    // 生成选项提示
    generateDeepOptions()
    showDeepOptions.value = true
  }
}

const generateDeepOptions = () => {
  if (!currentDeepWord.value) return
  
  const correctWord = currentDeepWord.value.word
  const otherWords = allWords.value.filter(w => w.word !== correctWord)
  const shuffled = otherWords.sort(() => Math.random() - 0.5)
  const wrongOptions = shuffled.slice(0, 3).map(w => w.word)
  const allOptions = [correctWord, ...wrongOptions]
  deepOptions.value = allOptions.sort(() => Math.random() - 0.5)
}

const selectDeepOption = async (option) => {
  if (option === currentDeepWord.value.word) {
    answerStatus.value = 'correct'
    deepReciteCorrect.value++
    deepReciteScore.value = Math.round((deepReciteCorrect.value / deepReciteTotal.value) * 100)

    // 选择正确选项后：标记为已掌握
    const word = currentDeepWord.value
    const isArticleWord = word.source === 'article'

    if (isArticleWord) {
      // 文章词：标记为已掌握
      try {
        await updateMasteryLevel(word.wordId, { masteryLevel: 'MASTERED', isCorrect: true })
        word.mastered = true
        masteredWords.value++
      } catch (err) {
        console.error('文章词标记已掌握失败:', err)
      }
    } else {
      // CET 词：标记为已掌握
      word.mastered = true
      word.lastReviewTime = new Date().toISOString()
      syncWordToDatabase(word, 'MASTERED')
      saveProgress()
      masteredWords.value++
    }

    setTimeout(() => {
      userInput.value = ''
      answerStatus.value = null
      showDeepOptions.value = false
      deepOptions.value = []
      if (currentDeepWordIndex.value < deepReciteWords.value.length - 1) {
        currentDeepWordIndex.value++
        if (inputRef.value) inputRef.value.focus()
      }
    }, 1000)
  } else {
    ElMessage.error('选择错误，请重新输入')
    showDeepOptions.value = false
    deepOptions.value = []
    userInput.value = ''
    if (inputRef.value) inputRef.value.focus()
  }
}

const retryDeepInput = () => {
  showDeepOptions.value = false
  deepOptions.value = []
  answerStatus.value = null
  userInput.value = ''
  if (inputRef.value) inputRef.value.focus()
}

const exitDeepRecite = () => {
  showDeepRecite.value = false
  deepReciteWords.value = []
  currentDeepWordIndex.value = 0
  userInput.value = ''
  answerStatus.value = null
  deepReciteCorrect.value = 0
  deepReciteTotal.value = 0
  deepReciteScore.value = 0
  showDeepOptions.value = false
  deepOptions.value = []
}

// 监听当前单词变化，自动加载图片、发音、详细信息，并生成选项
watch(currentWord, (newWord) => {
  if (newWord) {
    // 如果当前词是占位释义,从 CSV 池查真正释义替换(兜底补救)
    const m = newWord.meaning || ''
    if (m.includes('待完善') || m.includes('基础词汇')) {
      const realDef = distractorPool.value.find(w => w.word.toLowerCase() === newWord.word.toLowerCase())
      if (realDef && realDef.meaning) {
        newWord.meaning = realDef.meaning
        console.log(`[补救] ${newWord.word} 占位释义已替换为: ${realDef.meaning}`)
      } else {
        console.warn(`[补救失败] ${newWord.word} 在 CSV 池中也找不到`)
      }
    }

    loadWordImage(newWord.word)
    fetchWordDetails(newWord.word)
    fetchAiExamples(newWord.word)
    showMeaning.value = false
    showQuiz.value = false
    wrongOption.value = null
    generateQuizOptions()
    // 新单词出场就自动发音；speakWord 内部已根据 autoSpeakEnabled 做静音 guard
    speakWord()
  }
})

// 选择词库
const selectVocabulary = async (vocab) => {
  try {
    await tryAutoEnterRecite(vocab.id)
  } catch (error) {
    ElMessage.error('加载词库失败：' + error.message)
  }
}

// 拉取生词本中未掌握的文章生词（按 SM-2 到期/逾期优先）
const loadArticleWords = async () => {
  articleWordsLoading.value = true
  try {
    const res = await getVocabularyList({ page: 1, size: 999, masteryLevel: '', sourceType: 'ARTICLE' })
    const records = Array.isArray(res?.data?.records) ? res.data.records : []
    const list = records
      .filter(w => w.masteryLevel !== 'MASTERED')
      .map(w => ({
        id: `article_${w.wordId || w.id}`,
        wordId: w.wordId || w.id,
        word: w.word,
        meaning: w.definition,
        source: 'article',
        nextReviewTime: w.nextReviewTime || null,
        reviewCount: w.reviewCount || 0,
        interval: 0,
        easinessFactor: 2.5,
        lastReviewTime: null,
        mastered: false,
        unknownCount: 0,
        synced: true
      }))
      .sort((a, b) => {
        const ta = a.nextReviewTime ? new Date(a.nextReviewTime) : new Date(0)
        const tb = b.nextReviewTime ? new Date(b.nextReviewTime) : new Date(0)
        return ta - tb
      })
    articleWords.value = list
    articleWordsCount.value = list.length
  } catch (error) {
    console.error('拉取文章生词失败:', error)
    articleWords.value = []
    articleWordsCount.value = 0
  } finally {
    articleWordsLoading.value = false
  }
}

const loadMasteredVocabulary = async () => {
  const records = []
  const pageSize = 100
  let page = 1
  let hasMore = true

  try {
    while (hasMore) {
      // CET entries are also stored in vocabulary_books, so leave sourceType unset.
      const res = await getVocabularyList({ page, size: pageSize })
      const data = res?.data
      const batch = Array.isArray(data?.records) ? data.records : []
      if (page > 1 && batch.length === 0 && records.length > 0 && Number(data?.total) === 0) {
        throw new Error('Mastered vocabulary pagination ended before all records were loaded')
      }
      records.push(...batch)

      const total = data?.total == null ? Number.NaN : Number(data.total)
      if (Number.isFinite(total) && total >= 0) {
        hasMore = records.length < total && batch.length > 0
      } else if (batch.length < pageSize) {
        hasMore = false
      }
      if (hasMore) page++
    }

    const recordsByWord = new Map(records
      .map(record => [String(record.word || '').trim().toLowerCase(), record])
      .filter(([word]) => word))
    allWords.value = allWords.value.map(word => {
      const backendRecord = recordsByWord.get(String(word.word || '').trim().toLowerCase())
      if (!backendRecord) return word
      if (backendRecord.masteryLevel === 'MASTERED') return { ...word, mastered: true, synced: true }
      return word.synced === true ? { ...word, mastered: false, synced: true } : word
    })

    const progressKey = `vocab_progress_${currentUserId.value}_${selectedVocabulary.value}`
    localStorage.setItem(progressKey, JSON.stringify(allWords.value.map(word => ({
      word: word.word,
      reviewCount: word.reviewCount,
      interval: word.interval,
      easinessFactor: word.easinessFactor,
      lastReviewTime: word.lastReviewTime,
      mastered: word.mastered,
      unknownCount: word.unknownCount,
      synced: word.synced
    }))))
  } catch (error) {
    console.warn('Unable to refresh mastered vocabulary; using local progress.', error)
  }
}

// 手动刷新"当前可用文章生词"的数量
// 用户在生词本里把已掌握的词重置为学习中后，点击这里可以让计数立即同步
const refreshArticleWordsCount = async () => {
  await loadArticleWords()
  ElMessage.success(`已同步，当前可用 ${articleWordsCount.value} 个`)
}

// 一站式装载：选择 / 恢复词库后，能跳过哪步设置就跳过哪步
// 复用入口：onMounted 时根据 selectedVocabKey() 自动恢复；用户在词库选择卡点击时也会走这里
// 三项设置（includeArticleWords / articleDailyCount / dailyCount）都已持久化时，直接进入背诵页；否则停在缺失的那一步
const tryAutoEnterRecite = async (vocabId) => {
  const vocab = vocabularies.value.find(v => v.id === vocabId)
  if (!vocab) {
    // 词库列表里找不到这个 id（比如配置变更），清掉旧记录回到词库选择
    localStorage.removeItem(selectedVocabKey())
    return
  }

  // 进入新词库前重置所有向导状态；下面会根据 localStorage 把能恢复的恢复回来
  selectedVocabulary.value = vocabId
  dailyCountSelected.value = false
  includeArticleWords.value = null
  articleDailyCount.value = null
  localStorage.setItem(selectedVocabKey(), vocabId)

  // 只解析 CSV + 恢复 vocab_progress；loadVocabularyData 故意不内部调 loadWords()
  // 由本函数末尾或 selectDailyCount 在所有依赖项就绪后统一调一次，避免 watch(currentWord) 重复触发
  await loadVocabularyData(vocab.file)

  const savedDailyCountRaw = localStorage.getItem(dailyCountKey())

  // 未登录：不存在文章生词概念
  if (!userStore.token) {
    includeArticleWords.value = false
    articleWords.value = []
    articleWordsCount.value = 0
    articleDailyCount.value = 0
    if (savedDailyCountRaw) {
      dailyCount.value = parseInt(savedDailyCountRaw) || dailyCount.value
      dailyCountSelected.value = true
      loadWords()
    }
    return
  }

  // 登录用户：拉文章词数（既作为选择卡片展示，也用于钳制 articleDailyCount）
  await loadArticleWords()

  const savedIncludeArticleRaw = localStorage.getItem(includeArticleKey())
  const savedArticleDailyCountRaw = localStorage.getItem(articleDailyCountKey())

  // 恢复"是否加入文章生词"
  if (savedIncludeArticleRaw === '1') {
    includeArticleWords.value = true
    if (savedArticleDailyCountRaw !== null) {
      const saved = parseInt(savedArticleDailyCountRaw) || 0
      const available = articleWordsCount.value || 0
      // 文章词被用户在生词本里掌握掉之后总数会下降；用 min 钳制避免越界
      const clamped = Math.min(saved, available)
      articleDailyCount.value = clamped
      // 只有 available > 0 时才回写——避免 loadArticleWords 网络失败导致 articleWordsCount=0 时，
      // 把用户真实偏好（比如 10）永久覆写成 0；下次加载成功也救不回来
      if (clamped !== saved && available > 0) {
        localStorage.setItem(articleDailyCountKey(), clamped.toString())
      }
    }
  } else if (savedIncludeArticleRaw === '0') {
    includeArticleWords.value = false
    // include=false 时不参与选词，但仍恢复上次的数字
    // 这样后续打开"修改设置"弹窗，再切回开启时能直接看到上次的值
    if (savedArticleDailyCountRaw !== null) {
      articleDailyCount.value = parseInt(savedArticleDailyCountRaw) || 0
    } else {
      articleDailyCount.value = 0
    }
  }
  // savedIncludeArticleRaw 为 null：includeArticleWords 保持 null，向导停在"是否加入"

  // 恢复 CET 每日词数
  if (savedDailyCountRaw) {
    const parsed = parseInt(savedDailyCountRaw)
    if (!isNaN(parsed) && parsed > 0) dailyCount.value = parsed
  }

  // 三项全齐：直接落地到背诵页
  const includeChosen = includeArticleWords.value !== null
  const articleCountChosen = includeArticleWords.value === false || articleDailyCount.value !== null
  const dailyCountSaved = !!savedDailyCountRaw
  if (includeChosen && articleCountChosen && dailyCountSaved) {
    dailyCountSelected.value = true
    loadWords()
  }
}

const selectIncludeArticle = (include) => {
  includeArticleWords.value = include
  // 持久化选择，方便下次进入直接跳过
  localStorage.setItem(includeArticleKey(), include ? '1' : '0')
  // 选择不加入时直接跳过文章数量选择，并把 articleDailyCount 也持久化为 0
  if (!include) {
    articleDailyCount.value = 0
    localStorage.setItem(articleDailyCountKey(), '0')
  }
}

// 打开"修改设置"弹窗：把当前真实值复制到草稿
// 用 draft 是为了取消时不影响 pendingWords / dailyCount 等真实状态
const openSettingsDialog = async () => {
  draftDailyCount.value = dailyCount.value
  draftIncludeArticle.value = includeArticleWords.value === true
  draftArticleDailyCount.value = articleDailyCount.value || 0
  // 先把弹窗打开，避免点击后视觉无反馈；接着异步同步最新文章生词数
  settingsDialogVisible.value = true
  if (userStore.token) {
    await loadArticleWords()
    // 拉到最新可用数后，如果草稿值越界就钳到新 max；防止 el-input-number 显示越界值
    const available = articleWordsCount.value || 0
    if (draftArticleDailyCount.value > available) {
      draftArticleDailyCount.value = available
    }
  }
}

// 保存设置：钳制 + 写真实状态 + 写 localStorage + 重算 pendingWords
// 显式不清零 todayReviewed —— 用户改设置不应该把今天的进度抹掉
// 即使本次关闭了"加入文章生词"，也保留草稿里的数字并持久化，下次再开启时不必重输
const saveSettings = () => {
  const cet = Math.max(1, Math.min(500, parseInt(draftDailyCount.value) || dailyCount.value))
  const available = articleWordsCount.value || 0
  // 注意：不再用 draftIncludeArticle 决定是否归零；归零会丢掉用户上一次输入
  // 关闭文章生词时由 loadWords 里的 `includeArticleWords ? ... : 0` 决定真实选词数量
  const article = Math.max(0, Math.min(parseInt(draftArticleDailyCount.value) || 0, available))

  dailyCount.value = cet
  includeArticleWords.value = draftIncludeArticle.value
  articleDailyCount.value = article
  dailyCountSelected.value = true

  localStorage.setItem(dailyCountKey(), cet.toString())
  localStorage.setItem(includeArticleKey(), draftIncludeArticle.value ? '1' : '0')
  localStorage.setItem(articleDailyCountKey(), article.toString())

  // 先清掉两个维度（A/N）的索引 key，再 loadWords 让它走 "没保存索引" 分支自然落到 0
  // 之所以清两份：includeArticleWords 切换会让 vocabIndexKey 的后缀变；只清当前后缀那一份，反向切回去时还会读到陈旧值
  clearAllVocabIndexKeys()
  loadWords()
  currentWordIndex.value = 0

  settingsDialogVisible.value = false
  ElMessage.success('设置已更新')
}

const cancelSettings = () => {
  settingsDialogVisible.value = false
}

// 从弹窗里跳"换词库"：清除所有持久化设置回到词库选择页
const goChangeVocabFromDialog = () => {
  settingsDialogVisible.value = false
  backToSelection()
}

// 选择每日文章生词数量
const selectArticleDailyCount = (option) => {
  const availableCount = articleWordsCount.value || 0
  const requestedCount = option.value
  const actualCount = Math.min(requestedCount, availableCount)

  if (requestedCount > availableCount && availableCount > 0) {
    ElMessage.warning(`当前文章生词不足，最多可加入 ${availableCount} 个，已为你加入 ${availableCount} 个`)
  } else if (availableCount === 0) {
    ElMessage.info('没有可加入的文章生词，将只背诵词库中的单词')
  }

  articleDailyCount.value = actualCount
  localStorage.setItem(articleDailyCountKey(), actualCount.toString())
}

// 文章数量选项（数量不足时仍可点击，点击后自动按最大可用数量加入）
const filteredArticleCountOptions = computed(() => {
  const count = articleWordsCount.value || 0
  return articleDailyCountOptions.value.map(opt => ({
    value: opt,
    display: opt.toString(),
    label: true,
    insufficient: opt > count,
    hint: opt > count ? `最多可加 ${count} 个` : ''
  }))
})

// 从文章数量选择返回到"是否加入"步骤
const backToIncludeChoice = () => {
  includeArticleWords.value = null
  articleDailyCount.value = null
}

// 选择每日复习数量
const selectDailyCount = (count) => {
  dailyCount.value = count
  dailyCountSelected.value = true
  localStorage.setItem(dailyCountKey(), count.toString())
  loadWords()
}

// 返回词库选择
// 同步清空"已选词库 / 是否加文章词 / 文章词数 / CET 词数 / 当前游标"五个 key，
// 让用户重进时确实从头再选一遍（否则 onMounted 又会把人扔回背诵页）
const backToSelection = () => {
  // 先用旧的 vocabId 计算出三个相关 key，再把 selectedVocabulary 置 null
  const vocabIdForKeys = selectedVocabulary.value
  if (vocabIdForKeys) {
    localStorage.removeItem(includeArticleKey(vocabIdForKeys))
    localStorage.removeItem(articleDailyCountKey(vocabIdForKeys))
    localStorage.removeItem(dailyCountKey(vocabIdForKeys))
    clearAllVocabIndexKeys(vocabIdForKeys)
  }
  localStorage.removeItem(selectedVocabKey())

  selectedVocabulary.value = null
  pendingWords.value = []
  allWords.value = []
  currentWordIndex.value = 0
  dailyCountSelected.value = false
  includeArticleWords.value = null
  articleWords.value = []
  articleWordsCount.value = null
}

// 完成态的"返回设置"按钮：复用修改设置弹窗
// 概念上和顶栏的齿轮一致，避免另搞一个返回向导的入口
const backToSettings = () => {
  if (showDeepRecite.value) exitDeepRecite()
  openSettingsDialog()
}

// 解析 CSV 文件
const parseCSV = (csvText) => {
  const lines = csvText.split('\n')
  const words = []

  // 跳过表头
  for (let i = 1; i < lines.length; i++) {
    const line = lines[i].trim()
    if (!line) continue

    // 简单的 CSV 解析（处理带引号的字段）
    const match = line.match(/^"([^"]+)","([^"]+)"$/)
    if (match) {
      words.push({
        id: i,
        word: match[1],
        meaning: match[2],
        reviewCount: 0,
        interval: 0,
        easinessFactor: 2.5,
        lastReviewTime: null,
        mastered: false,
        unknownCount: 0,
        synced: false
      })
    }
  }

  return words
}

// 加载 quiz 干扰项池：并行抓三本 CSV，合并、按 word 去重、过滤空/占位释义
// 失败的 CSV 静默跳过，保证至少有现存内容可用
const loadDistractorPool = async () => {
  const files = vocabularies.value.map(v => v.file)
  try {
    const results = await Promise.allSettled(
      files.map(async (file) => {
        const res = await fetch(file)
        if (!res.ok) throw new Error(`加载失败: ${file}`)
        return parseCSV(await res.text())
      })
    )
    const merged = []
    const seen = new Set()
    for (const r of results) {
      if (r.status !== 'fulfilled') continue
      for (const w of r.value) {
        if (!w || !w.word || !w.meaning) continue
        const m = w.meaning.trim()
        if (!m || m.includes('待完善') || m.includes('基础词汇')) continue
        if (seen.has(w.word)) continue
        seen.add(w.word)
        merged.push({ word: w.word, meaning: m })
      }
    }
    distractorPool.value = merged
  } catch (error) {
    console.error('加载干扰项池失败:', error)
  }
}

// 加载词库数据
// 故意不内部调用 loadWords()：tryAutoEnterRecite 会在最后一步统一 loadWords()，
// 避免 pendingWords 被先以"includeArticleWords=null"算一次、再以恢复后值算一次，
// 触发 watch(currentWord) → fetchWordDetails 跑两次出现 last-write-wins 错位
const loadVocabularyData = async (file) => {
  try {
    const response = await fetch(file)
    if (!response.ok) throw new Error('文件加载失败')
    const text = await response.text()
    allWords.value = parseCSV(text)

    // 从 localStorage 加载复习进度（自动检测词库更新）
    const progressKey = `vocab_progress_${currentUserId.value}_${selectedVocabulary.value}`
    const savedCountKey = `vocab_count_${currentUserId.value}_${selectedVocabulary.value}`
    const currentCount = allWords.value.length
    const savedProgress = localStorage.getItem(progressKey)
    if (savedProgress) {
      const progress = JSON.parse(savedProgress)
      const progressByWord = new Map(progress.map(item => [item.word, item]))
      allWords.value = allWords.value.map(word => {
        const savedWord = progressByWord.get(word.word)
        return savedWord ? { ...word, ...savedWord } : word
      })
    }
    // 保存当前词数，用于下次检测
    localStorage.setItem(savedCountKey, currentCount.toString())

    if (userStore.token) await loadMasteredVocabulary()

    // 顺便也算一次 masteredWords，让用户在向导阶段如果观察到统计也能看到正确值
    masteredWords.value = allWords.value.filter(w => w.mastered).length

    // 登录后台补偿同步：将本地已评分但未成功写入 vocabulary_books 的单词重推一次
    // 保证"背过的单词"即使当时网络失败也会最终出现在单词页统计中
    resyncPendingWords()
  } catch (error) {
    ElMessage.error('加载词库数据失败：' + error.message)
  }
}

// 补偿扫描：查找 localStorage 中已评分但未同步到后端的单词，串行补发一次
// 串行控制并发避免同时发起大量请求；每条请求失败不阻断后续
const resyncPendingWords = async () => {
  if (!userStore.token) return
  // 仅同步 CET 词；文章词不进 allWords 且不回写后端
  const pending = allWords.value.filter(w => w.source !== 'article' && w.lastReviewTime && w.synced !== true)
  if (pending.length === 0) return

  for (const word of pending) {
    const level = word.mastered ? 'MASTERED' : 'LEARNING'
    // eslint-disable-next-line no-await-in-loop
    await syncWordToDatabase(word, level)
  }
}

const loadWords = () => {
  // 过滤掉已掌握的单词，只显示未掌握的
  let words = allWords.value.filter(word => !word.mastered)
  // 优先选今天没复习过的，再按不认识次数降序，同组内随机打乱
  const today = new Date().toDateString()
  words.sort((a, b) => {
    const aToday = a.lastReviewTime && new Date(a.lastReviewTime).toDateString() === today ? 1 : 0
    const bToday = b.lastReviewTime && new Date(b.lastReviewTime).toDateString() === today ? 1 : 0
    if (aToday !== bToday) return aToday - bToday
    return (b.unknownCount || 0) - (a.unknownCount || 0)
  })
  // 在同 unknownCount 组内随机打乱
  const grouped = {}
  for (const w of words) {
    const key = w.unknownCount || 0
    if (!grouped[key]) grouped[key] = []
    grouped[key].push(w)
  }
  const shuffled = []
  const keys = Object.keys(grouped).sort((a, b) => Number(b) - Number(a))
  for (const key of keys) {
    const group = grouped[key]
    for (let i = group.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1));
      [group[i], group[j]] = [group[j], group[i]]
    }
    shuffled.push(...group)
  }

  // 文章词独立按 articleDailyCount 取，CET 词按 dailyCount 取，最终合并相加
  const articleNeed = includeArticleWords.value ? (articleDailyCount.value || 0) : 0
  // 过滤掉占位释义的文章词(源头阻断)
  const validArticleWords = articleWords.value.filter(w => {
    const m = w.meaning || ''
    return !m.includes('待完善') && !m.includes('基础词汇')
  })
  const articlePicks = validArticleWords.slice(0, articleNeed)
  const cetPicks = shuffled.slice(0, dailyCount.value)
  const merged = [...articlePicks, ...cetPicks]
  for (let i = merged.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [merged[i], merged[j]] = [merged[j], merged[i]]
  }
  pendingWords.value = merged

  // 从 localStorage 恢复背诵进度
  const savedIndex = localStorage.getItem(vocabIndexKey())
  if (savedIndex) {
    currentWordIndex.value = parseInt(savedIndex)
    // 确保索引在有效范围内
    if (currentWordIndex.value >= pendingWords.value.length) {
      currentWordIndex.value = 0
    }
  } else {
    currentWordIndex.value = 0
  }

  masteredWords.value = allWords.value.filter(w => w.mastered).length
}

const loadMoreWords = async () => {
  if (showDeepRecite.value) exitDeepRecite()
  // 文章生词：重新拉一次（本地已掌握的会被过滤）
  if (includeArticleWords.value) {
    await loadArticleWords()
  }
  // 过滤掉已掌握的单词，只显示未掌握的
  let words = allWords.value.filter(word => !word.mastered)
  // 优先选今天没复习过的，再按不认识次数降序，同组内随机打乱
  const today = new Date().toDateString()
  words.sort((a, b) => {
    const aToday = a.lastReviewTime && new Date(a.lastReviewTime).toDateString() === today ? 1 : 0
    const bToday = b.lastReviewTime && new Date(b.lastReviewTime).toDateString() === today ? 1 : 0
    if (aToday !== bToday) return aToday - bToday
    return (b.unknownCount || 0) - (a.unknownCount || 0)
  })
  // 在同 unknownCount 组内随机打乱
  const grouped = {}
  for (const w of words) {
    const key = w.unknownCount || 0
    if (!grouped[key]) grouped[key] = []
    grouped[key].push(w)
  }
  const shuffled = []
  const keys = Object.keys(grouped).sort((a, b) => Number(b) - Number(a))
  for (const key of keys) {
    const group = grouped[key]
    for (let i = group.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1));
      [group[i], group[j]] = [group[j], group[i]]
    }
    shuffled.push(...group)
  }
  // 文章词独立按 articleDailyCount 取，CET 词按 dailyCount 取，最终合并相加
  const articleNeed = includeArticleWords.value ? (articleDailyCount.value || 0) : 0
  // 过滤掉占位释义的文章词(源头阻断)
  const validArticleWords = articleWords.value.filter(w => {
    const m = w.meaning || ''
    return !m.includes('待完善') && !m.includes('基础词汇')
  })
  const articlePicks = validArticleWords.slice(0, articleNeed)
  const cetPicks = shuffled.slice(0, dailyCount.value)
  const merged = [...articlePicks, ...cetPicks]
  for (let i = merged.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [merged[i], merged[j]] = [merged[j], merged[i]]
  }
  pendingWords.value = merged
  currentWordIndex.value = 0
  // 清除保存的进度
  localStorage.removeItem(vocabIndexKey())
  masteredWords.value = allWords.value.filter(w => w.mastered).length
  if (pendingWords.value.length === 0) {
    ElMessage.warning('所有单词都已掌握！')
  } else {
    ElMessage.success(`已加载 ${pendingWords.value.length} 个新单词`)
  }
}

const takeBreak = () => {
  if (showDeepRecite.value) exitDeepRecite()
  ElMessage.success('休息一下，记得回来继续学习哦！')
  setTimeout(() => {
    router.push('/student')
  }, 1000)
}

const toBackendMasteryLevel = (quality) => {
  if (quality === 5) return 'MASTERED'
  if (quality === 0) return 'LEARNING'
  return 'LEARNING'
}

const rateWord = async (quality) => {
  if (!currentWord.value) return

  const word = currentWord.value
  const isArticleWord = word.source === 'article'
  // 本次评分产生新状态，标记为未同步；syncWordToDatabase 成功后会置回 true
  // 文章词不回写后端，永远视为已同步以跳过补偿扫描
  word.synced = isArticleWord ? true : false

  if (quality === 5) {
    // 认识：标记为学习中（不再直接标记为已掌握，需要通过深度背诵才能真正掌握）
    if (isArticleWord) {
      // 文章词：更新为学习中状态
      try {
        const response = await updateMasteryLevel(word.wordId, { masteryLevel: 'LEARNING', isCorrect: true })
        if (response?.code !== 200) {
          ElMessage.error('标记失败')
          return
        }
        word.mastered = false  // 不再直接标记为已掌握
        word.lastReviewTime = new Date().toISOString()
      } catch (err) {
        console.error('文章词同步 LEARNING 失败:', err)
        ElMessage.error('标记失败')
        return
      }
    } else {
      // CET 词：标记为学习中
      word.mastered = false  // 不再直接标记为已掌握
      word.lastReviewTime = new Date().toISOString()
      syncWordToDatabase(word, 'LEARNING')  // 改为 LEARNING 状态
    }
  } else if (quality === 0) {
    // 不认识：增加出现频率（增加不认识计数）
    word.unknownCount = (word.unknownCount || 0) + 1
    word.lastReviewTime = new Date().toISOString()
    if (!isArticleWord) {
      syncWordToDatabase(word, toBackendMasteryLevel(quality))
    }
  } else if (quality === 3) {
    // 一般：按 SM-2 算法继续复习
    const result = calculateSM2(quality, {
      reviewCount: word.reviewCount,
      interval: word.interval,
      easinessFactor: word.easinessFactor
    })
    word.reviewCount = result.repetition
    word.interval = result.interval
    word.easinessFactor = result.easinessFactor
    word.lastReviewTime = new Date().toISOString()
    if (!isArticleWord) {
      syncWordToDatabase(word, toBackendMasteryLevel(quality))
    }
  }

  // 保存进度到 localStorage（作为备份）：CET 词走 saveProgress，文章词不入 allWords，无需保存
  if (!isArticleWord) {
    saveProgress()
  }

  todayReviewed.value++

  // 保存每日背诵进度到 localStorage，供 Dashboard 读取
  const today = new Date().toDateString()
  localStorage.setItem(`todayReviewed_${currentUserId.value}`, todayReviewed.value.toString())
  localStorage.setItem(`lastReviewDate_${currentUserId.value}`, today)

  // 不再在这里增加 masteredWords，只有深度背诵答对才算掌握
  // if (quality === 5) {
  //   masteredWords.value++
  // }

  showMeaning.value = false

  // 移动到下一个单词
  if (currentWordIndex.value < pendingWords.value.length - 1) {
    currentWordIndex.value++
    // 保存新的索引（同步代码块内只需写一次）
    localStorage.setItem(vocabIndexKey(), currentWordIndex.value.toString())
  } else {
    pendingWords.value = []
    // 完成背诵，清除保存的索引
    localStorage.removeItem(vocabIndexKey())
    ElMessage.success('复习完成！')
  }
}

const saveProgress = () => {
  const progress = allWords.value.map(word => ({
    word: word.word,
    mastered: word.mastered,
    unknownCount: word.unknownCount,
    reviewCount: word.reviewCount,
    interval: word.interval,
    easinessFactor: word.easinessFactor,
    lastReviewTime: word.lastReviewTime,
    synced: word.synced
  }))
  localStorage.setItem(`vocab_progress_${currentUserId.value}_${selectedVocabulary.value}`, JSON.stringify(progress))
}

// 将单词同步到后端数据库
// 成功时将 word.synced 置 true 并持久化，供 onMounted 时的补偿扫描跳过已同步记录
const syncWordToDatabase = async (word, masteryLevel) => {
  try {
    // 第一步：通过单词字符串添加到生词本（如果不存在会自动创建）
    const addResponse = await addWordToVocabularyByWord({ word: word.word })

    if (addResponse?.code === 200 && addResponse.data && addResponse.data.wordId) {
      const wordId = addResponse.data.wordId

      // 第二步：更新掌握程度（无论单词是新建还是已存在，都刷新本次评分对应的状态）
      const updateResponse = await updateMasteryLevel(wordId, {
        masteryLevel,
        isCorrect: masteryLevel === 'MASTERED'
      })

      if (updateResponse?.code === 200) {
        word.synced = true
        saveProgress()
        return true
      }
    }
    console.warn(`添加单词 ${word.word} 到数据库失败，保留本地状态等待下次补偿同步`)
    return false
  } catch (error) {
    console.error('同步单词到数据库失败:', error)
    // 同步失败不影响 localStorage 保存，仅记录错误
    return false
  }
}

// 当用户从其他标签页/路由切回本页时，如果停留在"是否加入文章生词"步骤上，重新拉一次
// 解决"在生词本里把已掌握的词重置为学习中后，文章生词数量没跟着变"的问题
const handleVisibilityChange = () => {
  if (document.visibilityState !== 'visible') return
  if (!userStore.token) return
  if (!selectedVocabulary.value) return
  if (includeArticleWords.value !== null) return
  loadArticleWords()
}

onMounted(async () => {
  isMobile.value = window.innerWidth < 768
  if (isMobile.value) {
    autoSpeakEnabled.value = false
  }
  // 加载今日背诵进度
  const today = new Date().toDateString()
  const savedDate = localStorage.getItem(`lastReviewDate_${currentUserId.value}`)
  if (savedDate === today) {
    const savedCount = localStorage.getItem(`todayReviewed_${currentUserId.value}`)
    if (savedCount) {
      todayReviewed.value = parseInt(savedCount)
    }
  }

  // 并行加载三本 CSV 作为 quiz 干扰项池，去重，过滤空/占位释义
  loadDistractorPool()

  // 之前选过词库 → 试图按已持久化的设置直接进入背诵
  // 三项设置缺任意一项时，停在缺失那一步的向导
  const savedVocab = localStorage.getItem(selectedVocabKey())
  if (savedVocab) {
    try {
      await tryAutoEnterRecite(savedVocab)
    } catch (error) {
      console.error('自动恢复词库失败:', error)
    }
  }

  document.addEventListener('visibilitychange', handleVisibilityChange)
  window.addEventListener('focus', handleVisibilityChange)
})

onBeforeUnmount(() => {
  document.removeEventListener('visibilitychange', handleVisibilityChange)
  window.removeEventListener('focus', handleVisibilityChange)
})
</script>

<style scoped>
.word-recite-container {
  max-width: 100%;
  margin: 0 auto;
  padding: 16px;
  min-height: 100vh;
  background: var(--bg-secondary);
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

.page-header {
  text-align: center;
  margin-bottom: 24px;
  padding: 20px 0;
}

.page-title {
  font-size: 24px;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0 0 8px 0;
  letter-spacing: -0.5px;
}

.page-subtitle {
  font-size: 14px;
  color: var(--text-tertiary);
  margin: 0;
}

/* 背诵界面：标题区 + 右上角齿轮按钮 */
.recite-header-bar {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
}

.recite-page-header {
  flex: 1;
}

.settings-gear-btn {
  position: absolute;
  right: 8px;
  top: 50%;
  transform: translateY(-50%);
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: var(--bg-primary);
  border: 1px solid var(--border-light);
  color: var(--text-secondary);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s;
  box-shadow: var(--shadow-sm);
}

.settings-gear-btn:hover {
  color: var(--primary-color);
  border-color: var(--primary-color);
  transform: translateY(-50%) rotate(30deg);
}

.settings-gear-btn:active {
  transform: translateY(-50%) rotate(60deg);
}

/* 修改设置弹窗 */
.settings-form {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.form-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.form-label {
  flex: 0 0 110px;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.form-hint {
  font-size: 12px;
  color: var(--text-tertiary);
}

.form-tip {
  font-size: 12px;
  color: var(--text-tertiary);
  background: var(--bg-tertiary);
  padding: 10px 12px;
  border-radius: 8px;
  line-height: 1.5;
}

.dialog-footer-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.dialog-footer-spacer {
  flex: 1;
}

/* 每日复习数量选择界面 */
.daily-count-selection {
  max-width: 600px;
  margin: 0 auto;
}

/* 是否加入文章生词选择界面 */
.article-choice-selection {
  max-width: 720px;
  margin: 0 auto;
}

.article-choice-options {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
  margin-bottom: 16px;
}

.choice-card {
  background: var(--bg-primary);
  border-radius: 16px;
  padding: 28px 20px;
  box-shadow: var(--shadow-sm);
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  border: 2px solid var(--border-light);
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.choice-card:hover {
  transform: translateY(-3px);
  border-color: var(--brand-primary, #6366f1);
  box-shadow: 0 12px 28px rgba(99, 102, 241, 0.12);
}

.choice-icon {
  font-size: 36px;
}

.choice-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-primary);
}

.choice-desc {
  font-size: 13px;
  color: var(--text-tertiary);
  line-height: 1.5;
}

.choice-extra {
  font-size: 12px;
  color: var(--brand-primary, #6366f1);
  font-weight: 600;
  margin-top: 4px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.refresh-mini-btn {
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 10px;
  border: 1px solid var(--brand-primary, #6366f1);
  background: white;
  color: var(--brand-primary, #6366f1);
  cursor: pointer;
  transition: background 0.15s;
}

.refresh-mini-btn:hover:not(:disabled) {
  background: var(--brand-primary, #6366f1);
  color: white;
}

.refresh-mini-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

@media (max-width: 600px) {
  .article-choice-options {
    grid-template-columns: 1fr;
  }
}

.count-options {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

.count-card {
  background: var(--bg-primary);
  border-radius: 16px;
  padding: 32px;
  box-shadow: var(--shadow-sm);
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  border: 2px solid var(--border-light);
  text-align: center;
  position: relative;
  overflow: hidden;
}

.count-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 4px;
  background: var(--gradient-brand);
  transform: scaleX(0);
  transition: transform 0.3s ease;
}

.count-card:hover {
  border-color: var(--primary-color);
  transform: translateY(-4px);
  box-shadow: var(--shadow-lg);
}

.count-card:hover::before {
  transform: scaleX(1);
}

.count-number {
  font-size: 32px;
  font-weight: 700;
  color: var(--primary-color);
  display: block;
  margin-bottom: 8px;
  letter-spacing: -1px;
}

.count-label {
  font-size: 14px;
  color: var(--text-secondary);
  font-weight: 500;
}

.count-days {
  font-size: 12px;
  color: var(--text-tertiary);
  display: block;
  margin-top: 4px;
}

.count-card.insufficient {
  border-color: #f8d7da;
}

.count-card.insufficient .count-hint {
  color: var(--warning-color);
}

.progress-info {
  text-align: center;
  margin-bottom: 20px;
  padding: 16px;
  background: var(--bg-primary);
  border-radius: 12px;
  font-size: 14px;
  color: var(--text-secondary);
  border: 1px solid var(--border-light);
  box-shadow: var(--shadow-sm);
}

/* 词库选择界面 */
.vocabularies-grid {
  display: grid;
  gap: 16px;
}

.vocabulary-card {
  background: var(--bg-primary);
  border-radius: 16px;
  padding: 24px;
  box-shadow: var(--shadow-sm);
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  border: 2px solid var(--border-light);
  position: relative;
  overflow: hidden;
}

.vocabulary-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 4px;
  background: var(--gradient-brand);
  transform: scaleX(0);
  transition: transform 0.3s ease;
}

.vocabulary-card:hover {
  border-color: var(--primary-color);
  transform: translateY(-4px);
  box-shadow: var(--shadow-lg);
}

.vocabulary-card:hover::before {
  transform: scaleX(1);
}

.vocab-name {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0 0 8px 0;
  letter-spacing: -0.5px;
}

.vocab-desc {
  font-size: 14px;
  color: var(--text-secondary);
  margin: 0 0 12px 0;
  line-height: 1.5;
}

.vocab-count {
  font-size: 13px;
  color: var(--text-tertiary);
  margin: 0;
  font-weight: 500;
  background: var(--bg-tertiary);
  padding: 4px 12px;
  border-radius: 12px;
  display: inline-block;
}

/* 统计卡片 */
.stats-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-bottom: 24px;
}

.stat-card {
  background: var(--bg-primary);
  border-radius: 16px;
  padding: 20px;
  text-align: center;
  box-shadow: var(--shadow-sm);
  border: 1px solid var(--border-light);
  transition: all 0.3s ease;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
}

.stat-label {
  font-size: 12px;
  color: var(--text-secondary);
  margin-bottom: 8px;
  font-weight: 500;
}

.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: var(--primary-color);
  letter-spacing: -0.5px;
}

/* 单词卡片 */
.word-card {
  background: var(--bg-primary);
  border-radius: 20px;
  padding: 32px;
  box-shadow: var(--shadow-md);
  text-align: center;
  border: 1px solid var(--border-light);
  position: relative;
  overflow: hidden;
}

.word-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 4px;
  background: var(--gradient-brand);
}

/* 自动播放开关 */
.auto-speak-toggle {
  position: absolute;
  top: 16px;
  right: 16px;
  z-index: 10;
}

.toggle-label {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  user-select: none;
}

.toggle-checkbox {
  appearance: none;
  width: 44px;
  height: 24px;
  background: #dcdfe6;
  border-radius: 12px;
  position: relative;
  cursor: pointer;
  transition: background-color 0.3s;
}

.toggle-checkbox:checked {
  background: #409eff;
}

.toggle-checkbox::before {
  content: '';
  position: absolute;
  top: 2px;
  left: 2px;
  width: 20px;
  height: 20px;
  background: white;
  border-radius: 50%;
  transition: transform 0.3s;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.2);
}

.toggle-checkbox:checked::before {
  transform: translateX(20px);
}

.toggle-text {
  font-size: 13px;
  color: #606266;
  font-weight: 500;
}

.toggle-checkbox:checked + .toggle-text {
  color: #409eff;
}

/* 单词图片 */
.word-image-container {
  margin-bottom: 20px;
  border-radius: 8px;
  overflow: hidden;
  background: #f5f5f5;
  min-height: 200px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.word-image {
  width: 100%;
  height: 200px;
  object-fit: cover;
}

.word-header {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin-bottom: 24px;
}

.word-text {
  font-size: 32px;
  font-weight: 600;
  margin: 0;
}

.clickable-word {
  cursor: pointer;
  transition: color 0.2s;
}

.clickable-word:hover {
  color: #409eff;
}

.speak-btn {
  background: #409eff;
  border: none;
  border-radius: 50%;
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s;
  color: white;
  flex-shrink: 0;
}

.speak-btn:hover {
  background: #66b1ff;
  transform: scale(1.1);
}

.speak-btn:active {
  transform: scale(0.95);
}

.word-content {
  padding: 20px;
  border-radius: 16px;
  margin-bottom: 24px;
}

.word-meaning p {
  font-size: 18px;
  color: var(--text-primary);
  margin: 0 0 16px 0;
  font-weight: 500;
  line-height: 1.6;
}

/* 单词详细信息 */
.word-details {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid var(--border-light);
  text-align: left;
}

.detail-section {
  margin-bottom: 8px;
  font-size: 14px;
}

.detail-label {
  font-weight: 600;
  color: var(--text-secondary);
}

.detail-value {
  color: var(--text-primary);
}

.meanings-section {
  margin-top: 12px;
}

.part-of-speech {
  font-weight: 600;
  color: var(--primary-color);
  margin-bottom: 8px;
  font-size: 15px;
}

.definitions {
  margin-left: 8px;
}

.definition-item {
  margin-bottom: 12px;
}

.definition {
  font-size: 14px;
  color: var(--text-primary);
  margin: 0 0 4px 0;
}

.example-wrapper {
  margin: 8px 0;
}

.example {
  font-size: 13px;
  color: var(--text-secondary);
  margin: 0 0 4px 0;
  font-style: italic;
}

.definition-translation {
  font-size: 13px;
  color: #67c23a;
  margin: 4px 0 0 0;
  font-style: normal;
}

.definition-translation.loading {
  color: #909399;
  font-style: italic;
}

.example-translation {
  font-size: 13px;
  color: #67c23a;
  margin: 0;
  padding-left: 16px;
}

/* AI 兜底例句区 */
.ai-examples-section {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px dashed var(--border-light);
  text-align: left;
}

.ai-examples-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--primary-color);
  margin-bottom: 10px;
}

.ai-examples-loading {
  font-size: 13px;
  color: var(--text-tertiary);
  font-style: italic;
  margin: 0;
}

.ai-examples-empty {
  font-size: 13px;
  color: var(--text-tertiary);
  margin: 0;
}

.ai-example-item {
  margin-bottom: 12px;
  padding: 10px 12px;
  background: var(--bg-tertiary);
  border-radius: 8px;
}

.ai-example-item:last-child {
  margin-bottom: 0;
}

.ai-example-en {
  font-size: 14px;
  color: var(--text-primary);
  margin: 0 0 4px 0;
  font-style: italic;
  line-height: 1.5;
}

.ai-example-zh {
  font-size: 13px;
  color: #67c23a;
  margin: 0;
  line-height: 1.5;
}

.ai-example-zh.loading {
  color: var(--text-tertiary);
  font-style: italic;
}

.quiz-options {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  width: 100%;
  margin-bottom: 20px;
}

.quiz-btn {
  min-height: 56px;
  padding: 12px 14px;
  font-size: 14px;
  text-align: left;
  white-space: normal;
  word-break: break-word;
  line-height: 1.5;
  background: var(--bg-tertiary);
  color: var(--text-primary);
  border: 1px solid var(--border-light);
  transition: all 0.3s ease;
}

.quiz-btn:hover {
  background: var(--bg-secondary);
  border-color: var(--border-color);
}

.quiz-btn--wrong {
  background: rgba(245, 108, 108, 0.1) !important;
  border-color: var(--error-color) !important;
  color: var(--error-color) !important;
  animation: shake 0.5s ease-in-out;
}

.wrong-word-hint {
  margin: 16px 0;
  padding: 16px;
  background: linear-gradient(135deg, #fff7ed 0%, #ffedd5 100%);
  border: 2px solid #fdba74;
  border-radius: 12px;
  box-shadow: 0 4px 12px rgba(249, 115, 22, 0.15);
  animation: slideDown 0.3s ease-out;
}

@keyframes slideDown {
  from {
    opacity: 0;
    transform: translateY(-10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.hint-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.hint-icon {
  font-size: 20px;
}

.hint-title {
  font-size: 14px;
  font-weight: 600;
  color: #c2410c;
}

.hint-content {
  padding-left: 28px;
}

.hint-word {
  font-size: 24px;
  font-weight: 700;
  color: #ea580c;
  margin: 0 0 8px 0;
  letter-spacing: 0.5px;
}

.hint-meaning {
  font-size: 16px;
  color: #9a3412;
  margin: 0;
  line-height: 1.6;
}

@keyframes shake {
  0%, 100% { transform: translateX(0); }
  10%, 50%, 90% { transform: translateX(-6px); }
  30%, 70% { transform: translateX(6px); }
}

.action-buttons {
  display: flex;
  gap: 16px;
  justify-content: center;
  flex-wrap: wrap;
  margin-top: 8px;
  padding-top: 12px;
  border-top: 1px dashed var(--border-light);
}

.action-btn {
  min-width: 100px;
  padding: 14px 24px;
  font-size: 16px;
  font-weight: 800;
  flex: 1;
  min-height: 58px;
  border-radius: 16px;
  letter-spacing: 1px;
}

.action-btn--unknown {
  background: linear-gradient(135deg, #fff7ed 0%, #ffedd5 100%);
  color: #c2410c;
  border: 2px solid #fdba74;
  box-shadow: 0 8px 18px rgba(249, 115, 22, 0.12);
}

.action-btn--unknown:hover {
  background: linear-gradient(135deg, #ffedd5 0%, #fed7aa 100%);
  transform: translateY(-2px);
  box-shadow: 0 10px 22px rgba(249, 115, 22, 0.18);
}

.action-btn--known {
  background: linear-gradient(135deg, #2563eb 0%, #1d4ed8 100%);
  color: white;
  border: 2px solid #1d4ed8;
  box-shadow: 0 10px 24px rgba(37, 99, 235, 0.28);
}

.action-btn--known:hover {
  background: linear-gradient(135deg, #1d4ed8 0%, #1e40af 100%);
  transform: translateY(-2px);
  box-shadow: 0 12px 28px rgba(37, 99, 235, 0.34);
}

.action-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
}

.next-word-action {
  display: flex;
  justify-content: center;
  margin-top: 8px;
  padding-top: 12px;
  border-top: 1px dashed var(--border-light);
}

.next-word-btn {
  width: 100%;
  padding: 16px 24px;
  font-size: 17px;
  font-weight: 800;
  min-height: 60px;
  background: linear-gradient(135deg, #7c3aed 0%, #6d28d9 100%);
  color: white;
  border: 2px solid #6d28d9;
  border-radius: 16px;
  box-shadow: 0 10px 28px rgba(124, 58, 237, 0.28);
  letter-spacing: 1px;
}

.next-word-btn:hover {
  background: linear-gradient(135deg, #6d28d9 0%, #5b21b6 100%);
  transform: translateY(-2px);
  box-shadow: 0 12px 32px rgba(124, 58, 237, 0.34);
}

.next-word-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
}

.cancel-btn {
  width: 100%;
}

.btn {
  padding: 12px 24px;
  border: none;
  border-radius: 12px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  letter-spacing: -0.3px;
}

.btn-primary {
  background: var(--primary-color);
  color: white;
  box-shadow: 0 4px 12px rgba(0, 102, 204, 0.3);
}

.btn-primary:hover {
  background: var(--primary-dark);
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(0, 102, 204, 0.4);
}

.btn-success {
  background: var(--success-color);
  color: white;
  box-shadow: 0 4px 12px rgba(103, 194, 58, 0.3);
}

.btn-success:hover {
  background: var(--success-dark);
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(103, 194, 58, 0.4);
}

.btn-info {
  background: var(--info-color);
  color: white;
  box-shadow: 0 4px 12px rgba(144, 147, 153, 0.3);
}

.btn-info:hover {
  background: var(--info-dark);
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(144, 147, 153, 0.4);
}

.btn-danger {
  background: var(--error-color);
  color: white;
  box-shadow: 0 4px 12px rgba(245, 108, 108, 0.3);
}

.btn-danger:hover {
  background: var(--error-dark);
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(245, 108, 108, 0.4);
}

.btn-secondary {
  background: var(--bg-tertiary);
  color: var(--text-primary);
  border: 1px solid var(--border-light);
}

.btn-secondary:hover {
  background: var(--bg-secondary);
  border-color: var(--border-color);
  transform: translateY(-2px);
}

.btn-warning {
  background: var(--warning-color);
  color: white;
  box-shadow: 0 4px 12px rgba(230, 162, 60, 0.3);
}

.btn-warning:hover {
  background: var(--warning-dark);
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(230, 162, 60, 0.4);
}

.empty-state {
  text-align: center;
  padding: 48px;
  color: var(--text-secondary);
}

.empty-subtitle {
  font-size: 14px;
  color: var(--text-tertiary);
  margin: 8px 0 24px 0;
}

.empty-actions {
  display: flex;
  gap: 12px;
  justify-content: center;
  flex-wrap: wrap;
}

/* 深度背诵界面 */
.deep-recite-view {
  max-width: 600px;
  margin: 0 auto;
}

.word-input {
  width: 100%;
  padding: 16px;
  font-size: 18px;
  border: 2px solid var(--border-color);
  border-radius: 12px;
  margin: 20px 0;
  text-align: center;
  transition: all 0.3s;
  background: var(--bg-primary);
  color: var(--text-primary);
  font-weight: 500;
}

.word-input:focus {
  outline: none;
  border-color: var(--primary-color);
  box-shadow: 0 0 0 3px rgba(0, 102, 204, 0.1);
}

.word-input.correct {
  border-color: var(--success-color);
  background: rgba(103, 194, 58, 0.1);
}

.word-input.wrong {
  border-color: var(--error-color);
  background: rgba(245, 108, 108, 0.1);
}

.answer-feedback {
  margin-bottom: 20px;
  text-align: center;
  font-size: 16px;
  font-weight: 600;
}

.correct-text {
  color: var(--success-color);
}

.wrong-text {
  color: var(--error-color);
}

.submit-btn {
  width: 100%;
}

.deep-options {
  margin-top: 20px;
}

.options-title {
  text-align: center;
  font-weight: 600;
  margin-bottom: 12px;
  color: var(--text-primary);
}

.options-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  margin-bottom: 12px;
}

.option-btn {
  padding: 12px;
  font-size: 14px;
  background: var(--bg-tertiary);
  border: 1px solid var(--border-light);
  transition: all 0.2s ease;
}

.option-btn:hover {
  background: var(--bg-secondary);
  border-color: var(--border-color);
  transform: translateY(-1px);
}

.retry-btn {
  width: 100%;
}

/* 深度背诵底部 */
.deep-footer {
  margin-top: 24px;
  text-align: center;
  padding: 16px;
}

/* 每日复习量底部 */
.daily-footer {
  margin-top: 24px;
  text-align: center;
  padding: 16px;
}

.text-btn {
  background: transparent;
  border: none;
  color: var(--text-tertiary);
  font-size: 14px;
  font-weight: 500;
  padding: 8px 16px;
  cursor: pointer;
  transition: color 0.2s;
}

.text-btn:hover {
  color: var(--text-primary);
}

.text-btn:active {
  color: var(--primary-color);
}

/* 响应式布局 */
@media (max-width: 768px) {
  .word-recite-container {
    padding: 12px;
  }

  .page-header {
    margin-bottom: 16px;
    padding: 16px 0;
  }

  .page-title {
    font-size: 20px;
  }

  .page-subtitle {
    font-size: 13px;
  }

  /* 词库选择卡片 */
  .vocabularies-grid {
    gap: 12px;
  }

  .vocabulary-card {
    padding: 20px;
  }

  .vocab-name {
    font-size: 16px;
  }

  .vocab-desc {
    font-size: 13px;
  }

  /* 每日复习量选择 */
  .daily-count-selection {
    max-width: 100%;
  }

  .count-options {
    grid-template-columns: repeat(2, 1fr);
    gap: 12px;
  }

  .count-card {
    padding: 24px 16px;
  }

  .count-number {
    font-size: 28px;
  }

  /* 统计卡片 */
  .stats-cards {
    grid-template-columns: repeat(3, 1fr);
    gap: 8px;
  }

  .stat-card {
    padding: 12px 8px;
  }

  .stat-label {
    font-size: 11px;
  }

  .stat-value {
    font-size: 18px;
  }

  /* 单词卡片 */
  .word-card {
    padding: 20px 16px;
    border-radius: 16px;
  }

  .word-content {
    padding: 16px;
    margin-bottom: 16px;
  }

  .word-header {
    margin-bottom: 16px;
  }

  .word-text {
    font-size: 24px;
  }

  .speak-btn {
    width: 36px;
    height: 36px;
  }

  .word-meaning p {
    font-size: 16px;
  }

  .action-buttons {
    gap: 8px;
  }

  .action-buttons .btn {
    width: 100%;
  }

  .quiz-options {
    grid-template-columns: 1fr;
    gap: 8px;
  }

  .quiz-btn {
    padding: 14px 12px;
    font-size: 13px;
  }

  /* 深度背诵 */
  .deep-recite-view {
    max-width: 100%;
  }

  .word-input {
    font-size: 16px;
    padding: 14px;
  }

  .options-grid {
    grid-template-columns: 1fr;
  }

  /* 空状态 */
  .empty-state {
    padding: 32px 16px;
  }

  .empty-actions {
    flex-direction: column;
  }

  .empty-actions .btn {
    width: 100%;
  }

  /* 返回按钮 */
  .back-btn {
    padding: 8px 16px;
    font-size: 14px;
  }
}

@media (max-width: 480px) {
  .count-options {
    grid-template-columns: 1fr;
  }

  .stats-cards {
    grid-template-columns: 1fr;
    gap: 8px;
  }

  .stat-card {
    padding: 16px;
  }

  .stat-value {
    font-size: 20px;
  }

  .word-text {
    font-size: 20px;
  }

  .action-buttons {
    flex-direction: column;
  }

  .action-buttons .btn {
    width: 100%;
  }

  .quiz-options {
    grid-template-columns: 1fr;
  }
}

@media (min-width: 769px) and (max-width: 1024px) {
  .count-options {
    grid-template-columns: repeat(3, 1fr);
    gap: 12px;
  }

  .word-card {
    padding: 28px;
  }

  .word-text {
    font-size: 28px;
  }
}
</style>
