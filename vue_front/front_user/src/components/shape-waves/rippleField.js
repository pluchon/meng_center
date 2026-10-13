// 参考 ShapeWaves 的阻尼波方程：相邻格点传递能量，固定步长避免帧率改变传播速度。
const stepDuration = 1 / 60
export function createRippleField(cols, rows) {
  const count = cols * rows
  const charges = new Float32Array(count)
  let heights = new Float32Array(count),
    previous = new Float32Array(count)
  let active = false,
    backlog = 0
  function reset() {
    heights.fill(0)
    previous.fill(0)
    charges.fill(0)
    active = false
    backlog = 0
  }
  function splash(x, y, radius, strength) {
    const sigma = Math.max(0.5, radius * 0.5),
      reach = Math.ceil(sigma * 2.5)
    for (
      let row = Math.max(0, Math.floor(y - reach));
      row <= Math.min(rows - 1, Math.ceil(y + reach));
      row++
    ) {
      for (
        let col = Math.max(0, Math.floor(x - reach));
        col <= Math.min(cols - 1, Math.ceil(x + reach));
        col++
      ) {
        const dx = col - x,
          dy = row - y,
          index = row * cols + col
        heights[index] = Math.min(
          1.2,
          heights[index] + strength * Math.exp(-(dx * dx + dy * dy) / (2 * sigma * sigma)),
        )
      }
    }
    active = true
  }
  function advance(delta) {
    if (!active) return false
    backlog = Math.min(backlog + delta, stepDuration * 4)
    while (backlog >= stepDuration) {
      backlog -= stepDuration
      let peak = 0
      for (let row = 0; row < rows; row++) {
        const up = (row === 0 ? row : row - 1) * cols,
          down = (row === rows - 1 ? row : row + 1) * cols,
          base = row * cols
        for (let col = 0; col < cols; col++) {
          const index = base + col,
            left = base + (col === 0 ? col : col - 1),
            right = base + (col === cols - 1 ? col : col + 1)
          const height = heights[index]
          const laplacian =
            heights[left] + heights[right] + heights[up + col] + heights[down + col] - 4 * height
          const next = (height + (height - previous[index]) * 0.94 + 0.42 * laplacian) * 0.972
          previous[index] = next
          charges[index] = Math.min(1, Math.max(0, next))
          peak = Math.max(peak, Math.abs(next))
        }
      }
      const swap = heights
      heights = previous
      previous = swap
      if (peak < 0.01) {
        reset()
        break
      }
    }
    return true
  }
  return { charges, splash, advance, reset }
}
