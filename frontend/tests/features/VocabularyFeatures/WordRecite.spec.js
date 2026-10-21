import { beforeEach, describe, expect, it, vi } from 'vitest'
import { shallowMount } from '@vue/test-utils'

const { getVocabularyList } = vi.hoisted(() => ({
  getVocabularyList: vi.fn()
}))

vi.mock('@/api/student', () => ({
  getVocabularyList,
  updateMasteryLevel: vi.fn(),
  addWordToVocabularyByWord: vi.fn()
}))

vi.mock('@/api/wordExplanation', () => ({ getWordExplanation: vi.fn() }))
vi.mock('@/store/user', () => ({
  useUserStore: () => ({ token: 'token', userInfo: { id: 17 } })
}))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }))
vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn() }
}))

import WordRecite from '@/features/VocabularyFeatures/WordRecite.vue'

const csvResponse = (csv) => ({ ok: true, text: async () => csv })

describe('WordRecite vocabulary progress', () => {
  let wrapper

  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    global.fetch = vi.fn().mockResolvedValue(csvResponse('word,meaning\n"alpha","甲"\n"beta","乙"'))
    getVocabularyList.mockResolvedValue({ data: { records: [], total: 0 } })
  })

  const mountPage = async () => {
    wrapper = shallowMount(WordRecite)
    await wrapper.vm.loadVocabularyData('/words.csv')
    return wrapper
  }

  it('filters server-mastered words absent from local progress', async () => {
    const firstPage = Array.from({ length: 100 }, (_, index) => ({
      wordId: index + 1,
      word: `other-${index}`,
      masteryLevel: 'MASTERED'
    }))
    getVocabularyList
      .mockResolvedValueOnce({ data: { records: firstPage, total: 101 } })
      .mockResolvedValueOnce({
        data: { records: [{ wordId: 101, word: 'alpha', masteryLevel: 'MASTERED' }], total: 101 }
      })
    const page = await mountPage()
    page.vm.loadWords()

    expect(getVocabularyList).toHaveBeenNthCalledWith(1, { page: 1, size: 100 })
    expect(getVocabularyList).toHaveBeenNthCalledWith(2, { page: 2, size: 100 })
    expect(page.vm.pendingWords.map(word => word.word)).toEqual(['beta'])
    expect(localStorage.getItem('vocab_progress_17_null')).toContain('"mastered":true')
  })

  it('keeps local progress when the vocabulary API fails', async () => {
    localStorage.setItem('vocab_progress_17_null', JSON.stringify([
      { word: 'alpha', mastered: true, unknownCount: 2, lastReviewTime: '2026-10-20', synced: false }
    ]))
    getVocabularyList.mockRejectedValue(new Error('offline'))
    const page = await mountPage()
    page.vm.loadWords()

    expect(page.vm.pendingWords.map(word => word.word)).toEqual(['beta'])
    expect(page.vm.allWords[0]).toMatchObject({ mastered: true, unknownCount: 2, synced: false })
  })

  it('returns a locally synced word to the daily list after backend mastery is reset', async () => {
    localStorage.setItem('vocab_progress_17_null', JSON.stringify([
      { word: 'alpha', mastered: true, unknownCount: 0, lastReviewTime: null, synced: true },
      { word: 'beta', mastered: true, unknownCount: 0, lastReviewTime: null, synced: false }
    ]))
    getVocabularyList.mockResolvedValue({
      data: {
        records: [
          { wordId: 91, word: 'alpha', masteryLevel: 'LEARNING' },
          { wordId: 92, word: 'beta', masteryLevel: 'LEARNING' }
        ],
        total: 2
      }
    })
    const page = await mountPage()
    page.vm.loadWords()

    expect(page.vm.allWords[0]).toMatchObject({ word: 'alpha', mastered: false, synced: true })
    expect(page.vm.allWords[1]).toMatchObject({ word: 'beta', mastered: true, synced: false })
    expect(page.vm.pendingWords.map(word => word.word)).toContain('alpha')
  })

  it('merges progress by word when the CSV vocabulary changes', async () => {
    localStorage.setItem('vocab_count_17_null', '3')
    localStorage.setItem('vocab_progress_17_null', JSON.stringify([
      { word: 'alpha', mastered: true, unknownCount: 4, lastReviewTime: '2026-10-20T10:00:00Z', synced: true },
      { word: 'removed', mastered: false, unknownCount: 9 }
    ]))
    global.fetch.mockResolvedValue(csvResponse('word,meaning\n"alpha","甲"\n"beta","乙"\n"gamma","丙"'))
    const page = await mountPage()

    expect(page.vm.allWords).toHaveLength(3)
    expect(page.vm.allWords.find(word => word.word === 'alpha')).toMatchObject({
      mastered: true,
      unknownCount: 4,
      lastReviewTime: '2026-10-20T10:00:00Z',
      synced: true
    })
    expect(page.vm.allWords.find(word => word.word === 'beta')).toMatchObject({ mastered: false, unknownCount: 0 })
    expect(page.vm.allWords.some(word => word.word === 'removed')).toBe(false)
    expect(localStorage.getItem('vocab_progress_17_null')).toContain('"unknownCount":4')
  })
})
