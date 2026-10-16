import { computed, ref, markRaw, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { contentApi, contentImage } from '@/api/content'
import { PostEditor } from '@/constants/content'
const imageTypes = new Set(['image/jpeg', 'image/png', 'image/webp', 'image/gif'])
async function prepare(file) {
  if (file.type === 'image/gif') return file
  const bitmap = await createImageBitmap(file)
  try {
    if (Math.max(bitmap.width, bitmap.height) <= PostEditor.IMAGE_EDGE) return file
    const ratio = PostEditor.IMAGE_EDGE / Math.max(bitmap.width, bitmap.height),
      canvas = document.createElement('canvas')
    canvas.width = Math.round(bitmap.width * ratio)
    canvas.height = Math.round(bitmap.height * ratio)
    canvas.getContext('2d').drawImage(bitmap, 0, 0, canvas.width, canvas.height)
    const blob = await new Promise((resolve) => canvas.toBlob(resolve, file.type, 0.9))
    if (!blob) throw new Error('decode')
    return new File([blob], file.name, { type: blob.type })
  } finally {
    bitmap.close()
  }
}
export function useEditorUploads(uploadImage = contentApi.upload) {
  const { t } = useI18n(),
    images = ref([]),
    cover = ref(null),
    notice = ref(''),
    queue = []
  let active = 0,
    disposed = false
  const entries = computed(() => [...images.value, ...(cover.value ? [cover.value] : [])])
  const blocked = computed(() => entries.value.some((item) => item.status !== 'ready'))
  function dispose(item) {
    if (!item) return
    item.cancelled = true
    item.controller?.abort()
    if (item.preview?.startsWith('blob:')) URL.revokeObjectURL(item.preview)
  }
  function pump() {
    if (disposed) return
    while (active < PostEditor.UPLOAD_CONCURRENCY && queue.length) {
      const item = queue.shift()
      if (item.cancelled) continue
      active++
      void upload(item).finally(() => {
        active--
        pump()
      })
    }
  }
  async function upload(item) {
    item.status = 'uploading'
    item.progress = 0
    item.error = ''
    item.controller = markRaw(new AbortController())
    try {
      const file = await prepare(item.file)
      if (item.cancelled) return
      const result = await uploadImage(file, {
        signal: item.controller.signal,
        onUploadProgress: (event) => {
          if (event.total)
            item.progress = Math.min(99, Math.round((event.loaded / event.total) * 100))
        },
      })
      if (item.cancelled) return
      if (item.preview.startsWith('blob:')) URL.revokeObjectURL(item.preview)
      Object.assign(item, result, {
        preview: contentImage(result.thumbUrl),
        status: 'ready',
        progress: 100,
      })
    } catch (error) {
      if (!item.cancelled) {
        item.status = 'failed'
        item.error =
          error.message === 'decode'
            ? t('editor.error.decode')
            : error.message || t('editor.error.decode')
      }
    }
  }
  function add(files, asCover = false) {
    notice.value = ''
    for (const file of Array.from(files)) {
      if (!imageTypes.has(file.type) || file.size > PostEditor.IMAGE_BYTES) {
        notice.value = t('editor.error.file')
        continue
      }
      if (!asCover && images.value.length >= PostEditor.IMAGE_MAX) {
        notice.value = t('editor.error.images', { max: PostEditor.IMAGE_MAX })
        break
      }
      const item = {
        key: crypto.randomUUID(),
        file,
        preview: URL.createObjectURL(file),
        status: 'queued',
        progress: 0,
        error: '',
        cancelled: false,
      }
      if (asCover) {
        dispose(cover.value)
        cover.value = item
        queue.push(cover.value)
        break
      }
      images.value.push(item)
      queue.push(images.value[images.value.length - 1])
    }
    pump()
  }
  function remove(item) {
    dispose(item)
    if (item === cover.value) cover.value = null
    else images.value = images.value.filter((image) => image.key !== item.key)
  }
  function retry(item) {
    if (item.status !== 'failed' || !item.file) return
    item.status = 'queued'
    item.cancelled = false
    queue.push(item)
    pump()
  }
  function hydrate(list, coverImage) {
    entries.value.forEach(dispose)
    queue.length = 0
    const ready = (image) => ({
      ...image,
      key: crypto.randomUUID(),
      preview: contentImage(image.thumbUrl),
      status: 'ready',
      progress: 100,
    })
    images.value = list.map(ready)
    cover.value = coverImage ? ready(coverImage) : null
  }
  onUnmounted(() => {
    disposed = true
    entries.value.forEach(dispose)
    queue.length = 0
  })
  function setCover(image) {
    dispose(cover.value)
    cover.value = {
      ...image,
      key: crypto.randomUUID(),
      preview: contentImage(image.thumbUrl),
      status: 'ready',
      progress: 100,
    }
  }
  return { images, cover, notice, blocked, add, remove, retry, hydrate, setCover }
}
