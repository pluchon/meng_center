// 纸飞机图标的尖头朝右上，图标中心到尖头的距离按 20px 图标算
const NOSE_TURN = 45
const NOSE_LENGTH = 10.6
const STEPS = 28
const MID_SCALE = 1.25
const END_SCALE = 0.75

// 沿二次贝塞尔曲线取关键帧，机头始终朝前进方向，最后尖头正好扎进目标点
export function flightFrames(start, end, arc) {
  const control = {
    x: start.x + (end.x - start.x) * 0.55,
    y: Math.min(start.y, end.y) - arc,
  }
  const finalX = end.x - control.x,
    finalY = end.y - control.y,
    finalLength = Math.hypot(finalX, finalY) || 1
  const stop = {
    x: end.x - (finalX / finalLength) * NOSE_LENGTH * END_SCALE,
    y: end.y - (finalY / finalLength) * NOSE_LENGTH * END_SCALE,
  }
  const frames = []
  let previous = null
  for (let step = 0; step <= STEPS; step++) {
    const u = step / STEPS,
      rest = 1 - u
    const x = rest * rest * start.x + 2 * rest * u * control.x + u * u * stop.x,
      y = rest * rest * start.y + 2 * rest * u * control.y + u * u * stop.y
    const heading =
      (Math.atan2(2 * rest * (control.y - start.y) + 2 * u * (stop.y - control.y),
        2 * rest * (control.x - start.x) + 2 * u * (stop.x - control.x)) *
        180) /
        Math.PI +
      NOSE_TURN
    // 角度跨过 ±180° 时接着转，不让飞机整圈甩回去
    const turn =
      previous === null ? heading : previous + ((((heading - previous + 180) % 360) + 360) % 360) - 180
    previous = turn
    const scale = u < 0.5 ? 1 + (MID_SCALE - 1) * u * 2 : MID_SCALE - (MID_SCALE - END_SCALE) * (u - 0.5) * 2
    frames.push({
      transform: `translate(${x}px, ${y}px) rotate(${turn}deg) scale(${scale})`,
      opacity: u > 0.96 ? 0.4 : 1,
    })
  }
  return frames
}
