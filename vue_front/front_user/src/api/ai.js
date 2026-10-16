import request from './request'
export const aiApi = {
  tags: (body, config = {}) =>
    request.post('/content/ai/tag-suggestions', body, { timeout: 10000, ...config }),
  inspirations: (body, config = {}) =>
    request.post('/content/ai/inspirations', body, { timeout: 18000, ...config }),
  covers: (body, config = {}) =>
    request.post('/content/ai/covers', body, { timeout: 65000, ...config }),
  growth: (days, config = {}) =>
    request.post('/growth/ai-summaries', { days }, { timeout: 18000, ...config }),
  creator: (days, config = {}) =>
    request.post('/content/creator/ai-reviews', { days }, { timeout: 18000, ...config }),
}
