import { computed, onMounted, onUnmounted, ref, watch } from 'vue'

// 滑块高亮：跟随悬停项移动，移开后回到选中项
// 从无到有、布局变化引起的重定位直接跳过去，不播放滑动；只有悬停和选中项变化才滑动
export function useSlider(root, { item, active = '.selected', deps = () => [] }) {
  const box = ref(null),
    visible = ref(false),
    instant = ref(false)
  let hovered = null
  let observer
  let jumpId = 0

  function place(jump = false) {
    const container = root.value
    if (!container) return
    const target = hovered?.isConnected ? hovered : container.querySelector(active)
    // 容器被隐藏时尺寸为 0，不能把滑块定位过去
    if (!target || target.offsetWidth === 0) {
      visible.value = false
      return
    }
    const skipTransition = jump || !visible.value
    const style = getComputedStyle(target)
    box.value = {
      x: target.offsetLeft,
      y: target.offsetTop,
      width: target.offsetWidth,
      height: target.offsetHeight,
      fill: style.getPropertyValue('--slide-fill'),
      edge: style.getPropertyValue('--slide-edge'),
    }
    visible.value = true
    if (skipTransition) {
      const id = ++jumpId
      instant.value = true
      requestAnimationFrame(() =>
        requestAnimationFrame(() => {
          if (id === jumpId) instant.value = false
        }),
      )
    }
  }

  function reset() {
    if (!hovered) return
    hovered = null
    place()
  }

  function onHover(event) {
    // 触屏没有悬停；点击留下的焦点也不算，只认键盘焦点
    if (event.pointerType === 'touch') return
    if (event.type === 'focusin' && !event.target.matches(':focus-visible')) return
    const found = event.target.closest?.(item)
    // 经过项与项之间的空隙时保持在上一项，避免滑块来回跳
    if (!found || !root.value.contains(found)) return
    hovered = found
    place()
  }

  function onLeave(event) {
    if (event.type === 'focusout' && root.value?.contains(event.relatedTarget)) return
    reset()
  }

  // 指针离开事件偶尔丢失（快速移出窗口、切换标签页、元素在指针下被替换），用全局事件兜底
  function onDocumentMove(event) {
    if (hovered && !root.value?.contains(event.target)) reset()
  }

  const settle = () => place(true)
  watch(deps, () => place(), { flush: 'post' })
  onMounted(() => {
    place(true)
    document.fonts?.ready.then(settle)
    observer = new ResizeObserver(settle)
    observer.observe(root.value)
    root.value.querySelectorAll(item).forEach((element) => observer.observe(element))
    document.addEventListener('pointermove', onDocumentMove, { passive: true })
    document.documentElement.addEventListener('mouseleave', reset)
    document.addEventListener('visibilitychange', reset)
    window.addEventListener('blur', reset)
  })
  onUnmounted(() => {
    observer?.disconnect()
    document.removeEventListener('pointermove', onDocumentMove)
    document.documentElement.removeEventListener('mouseleave', reset)
    document.removeEventListener('visibilitychange', reset)
    window.removeEventListener('blur', reset)
  })

  return {
    sliderClass: computed(() => ({ visible: visible.value, instant: instant.value })),
    sliderStyle: computed(() =>
      box.value
        ? {
            width: `${box.value.width}px`,
            height: `${box.value.height}px`,
            transform: `translate(${box.value.x}px, ${box.value.y}px)`,
            '--slide-fill': box.value.fill,
            '--slide-edge': box.value.edge,
          }
        : null,
    ),
    onHover,
    onLeave,
  }
}
