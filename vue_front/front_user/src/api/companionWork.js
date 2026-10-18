import { companionBlob } from './companion'
import { contentApi } from './content'
import { PostCategory, PostEditor } from '@/constants/content'

// 私人作品只通过本人内容上传入口转存，不把 companion 的私人地址放进帖子。
export async function createCompanionDraft(work) {
  let image = null
  if (work.kind === 'IMAGE') {
    const blob = await companionBlob(work.id)
    image = await contentApi.upload(new File([blob], work.title + '.png', { type: 'image/png' }))
  }
  return contentApi.saveDraft(null, {
    category: PostCategory.CREATION,
    title: Array.from(work.title).slice(0, PostEditor.TITLE_MAX).join(''),
    content: image ? '' : work.body,
    tags: [],
    imageIds: image ? [image.id] : [],
    coverImageId: null,
  })
}
