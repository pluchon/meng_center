// 声音全部用 Web Audio 现场合成，不加载音频文件：
// 背景音乐是稀疏的钢琴短句，按区域和昼夜换音阶；环境声有风、海浪、白天的鸟和夜里的虫；外加脚步和各种音效

const NOTE = (n) => 440 * Math.pow(2, (n - 69) / 12)

// 不同心情用的音阶（MIDI 音高）
const SCALES = Object.freeze({
  day: [60, 62, 64, 67, 69, 72, 74, 76, 79],
  night: [57, 60, 62, 64, 67, 69, 72, 74],
  forest: [57, 59, 60, 64, 65, 69, 71, 72],
  mountain: [53, 55, 57, 59, 60, 64, 65, 67, 71],
  village: [60, 64, 65, 67, 69, 72, 76, 77],
})

export function createAudio(ctx) {
  const master = ctx.createGain()
  master.gain.value = 0.7
  master.connect(ctx.destination)
  const bus = (level) => {
    const g = ctx.createGain()
    g.gain.value = level
    g.connect(master)
    return g
  }
  const sfx = bus(0.7)
  const ambience = bus(0.55)
  const musicBus = bus(0.42)

  // 混响：一段衰减的噪声当冲激响应，音乐和音效共用
  const reverb = ctx.createConvolver()
  const impulse = ctx.createBuffer(2, ctx.sampleRate * 2.6, ctx.sampleRate)
  for (let c = 0; c < 2; c++) {
    const d = impulse.getChannelData(c)
    for (let i = 0; i < d.length; i++) d[i] = (Math.random() * 2 - 1) * Math.pow(1 - i / d.length, 3)
  }
  reverb.buffer = impulse
  const wet = ctx.createGain()
  wet.gain.value = 0.32
  reverb.connect(wet)
  wet.connect(master)

  const noiseBuffer = ctx.createBuffer(1, ctx.sampleRate * 2, ctx.sampleRate)
  const nd = noiseBuffer.getChannelData(0)
  for (let i = 0; i < nd.length; i++) nd[i] = Math.random() * 2 - 1

  function noise(loop = false) {
    const src = ctx.createBufferSource()
    src.buffer = noiseBuffer
    src.loop = loop
    return src
  }

  // 一个包络：从 0 起，attack 秒升到 peak，再按 decay 秒指数衰减
  function envelope(target, at, peak, attack, decay) {
    target.gain.setValueAtTime(0.0001, at)
    target.gain.exponentialRampToValueAtTime(peak, at + attack)
    target.gain.exponentialRampToValueAtTime(0.0001, at + attack + decay)
  }

  function tone(freq, at, { type = 'sine', peak = 0.2, attack = 0.005, decay = 0.3, out = sfx, sendReverb = 0.3, glide } = {}) {
    const osc = ctx.createOscillator()
    osc.type = type
    osc.frequency.setValueAtTime(freq, at)
    if (glide) osc.frequency.exponentialRampToValueAtTime(glide, at + attack + decay)
    const g = ctx.createGain()
    envelope(g, at, peak, attack, decay)
    osc.connect(g)
    g.connect(out)
    if (sendReverb) {
      const s = ctx.createGain()
      s.gain.value = sendReverb
      g.connect(s)
      s.connect(reverb)
    }
    osc.start(at)
    osc.stop(at + attack + decay + 0.05)
  }

  function burst(at, { filter = 'lowpass', freq = 1200, q = 0.7, peak = 0.2, attack = 0.004, decay = 0.08, sweep, out = sfx }) {
    const src = noise()
    const f = ctx.createBiquadFilter()
    f.type = filter
    f.frequency.setValueAtTime(freq, at)
    if (sweep) f.frequency.exponentialRampToValueAtTime(sweep, at + attack + decay)
    f.Q.value = q
    const g = ctx.createGain()
    envelope(g, at, peak, attack, decay)
    src.connect(f)
    f.connect(g)
    g.connect(out)
    src.start(at, Math.random() * 1.5)
    src.stop(at + attack + decay + 0.05)
  }

  // 钢琴音色：几个泛音叠在一起，高的衰减得快
  function piano(midi, at, velocity = 0.5) {
    const f = NOTE(midi)
    ;[
      [1, 1, 3.2],
      [2, 0.38, 1.6],
      [3, 0.14, 0.9],
      [4.01, 0.06, 0.5],
    ].forEach(([mult, amp, decay]) => tone(f * mult, at, { peak: 0.12 * velocity * amp, attack: 0.006, decay, out: musicBus, sendReverb: 0.6 }))
  }

  // ---- 环境声
  function loop(filterType, freq, q, level) {
    const src = noise(true)
    const f = ctx.createBiquadFilter()
    f.type = filterType
    f.frequency.value = freq
    f.Q.value = q
    const g = ctx.createGain()
    g.gain.value = 0
    src.connect(f)
    f.connect(g)
    g.connect(ambience)
    src.start()
    return { gain: g, filter: f, level }
  }
  const wind = loop('bandpass', 520, 0.5, 0.18)
  const sea = loop('lowpass', 420, 0.4, 0.3)
  let nextBird = 0
  let nextCricket = 0

  // ---- 音乐
  let mood = 'day'
  let nextPhrase = ctx.currentTime + 3
  function phrase(at) {
    const scale = SCALES[mood]
    let i = Math.floor(Math.random() * (scale.length - 3)) + 1
    const count = 3 + Math.floor(Math.random() * 5)
    let t = at
    // 偶尔先垫一个低音
    if (Math.random() < 0.55) piano(scale[0] - 12, t, 0.45)
    for (let k = 0; k < count; k++) {
      i = Math.max(0, Math.min(scale.length - 1, i + Math.floor(Math.random() * 5) - 2))
      piano(scale[i], t, 0.35 + Math.random() * 0.3)
      if (Math.random() < 0.2) piano(scale[Math.max(0, i - 2)], t, 0.25)
      t += [0.3, 0.45, 0.6, 0.9][Math.floor(Math.random() * 4)]
    }
    return t
  }

  // 每帧调用：高度和速度决定风声，离海近决定浪声，白天有鸟、夜里有虫，隔一阵弹一句
  function update({ altitude, speed, nearSea, night, region, paused }) {
    const now = ctx.currentTime
    const k = paused ? 0.3 : 1
    wind.gain.gain.setTargetAtTime((0.04 + Math.min(1, altitude / 120) * 0.5 + Math.min(1, speed / 12) * 0.35) * wind.level * k, now, 0.4)
    wind.filter.frequency.setTargetAtTime(420 + speed * 40, now, 0.5)
    sea.gain.gain.setTargetAtTime((0.15 + 0.85 * nearSea) * (0.7 + 0.3 * Math.sin(now * 0.7)) * sea.level * k, now, 0.3)
    mood = night > 0.5 ? 'night' : region === 'forest' ? 'forest' : region === 'mountain' || region === 'plateau' ? 'mountain' : region === 'village' ? 'village' : 'day'
    if (paused) return
    if (now > nextPhrase) nextPhrase = phrase(now + 0.05) + 6 + Math.random() * 9
    if (night < 0.4 && now > nextBird) {
      nextBird = now + 2 + Math.random() * 6
      const base = 2400 + Math.random() * 1800
      for (let i = 0; i < 2 + Math.floor(Math.random() * 3); i++)
        tone(base, now + i * 0.11, { peak: 0.03, attack: 0.01, decay: 0.07, glide: base * 1.3, out: ambience, sendReverb: 0.2 })
    }
    if (night > 0.6 && now > nextCricket) {
      nextCricket = now + 0.8 + Math.random() * 2
      for (let i = 0; i < 6; i++) burst(now + i * 0.05, { filter: 'bandpass', freq: 4600, q: 8, peak: 0.025, decay: 0.03, out: ambience })
    }
  }

  // ---- 音效
  const at = () => ctx.currentTime + 0.01
  const FOOT = {
    grass: { filter: 'lowpass', freq: 900, decay: 0.07, peak: 0.12 },
    sand: { filter: 'lowpass', freq: 600, decay: 0.12, peak: 0.12 },
    path: { filter: 'bandpass', freq: 1800, q: 1.2, decay: 0.05, peak: 0.12 },
    rock: { filter: 'bandpass', freq: 2600, q: 2, decay: 0.04, peak: 0.14 },
    snow: { filter: 'lowpass', freq: 1400, decay: 0.13, peak: 0.1 },
    wood: { filter: 'bandpass', freq: 700, q: 3, decay: 0.06, peak: 0.16 },
  }
  const effects = {
    step(surface, loud) {
      const f = FOOT[surface] ?? FOOT.grass
      burst(at(), { ...f, peak: f.peak * (loud ? 1.4 : 1) * (0.8 + Math.random() * 0.4), freq: f.freq * (0.85 + Math.random() * 0.3) })
    },
    land(strength) {
      burst(at(), { filter: 'lowpass', freq: 260, decay: 0.2, peak: Math.min(0.5, 0.15 + strength * 0.015) })
    },
    jump() {
      tone(330, at(), { peak: 0.06, decay: 0.12, glide: 560, sendReverb: 0.1 })
    },
    glide(open) {
      burst(at(), { filter: 'bandpass', freq: open ? 400 : 1200, sweep: open ? 1600 : 300, q: 1.2, attack: 0.03, decay: 0.3, peak: 0.12 })
      if (open) tone(880, at(), { peak: 0.04, decay: 0.3, type: 'triangle', glide: 1320 })
    },
    splash() {
      burst(at(), { filter: 'lowpass', freq: 2400, sweep: 300, attack: 0.01, decay: 0.45, peak: 0.25 })
    },
    grab() {
      burst(at(), { filter: 'bandpass', freq: 1500, q: 3, decay: 0.03, peak: 0.1 })
    },
    exhausted() {
      tone(392, at(), { peak: 0.08, type: 'triangle', decay: 0.25 })
      tone(311, at() + 0.18, { peak: 0.08, type: 'triangle', decay: 0.35 })
    },
    coin() {
      tone(NOTE(88), at(), { peak: 0.08, type: 'triangle', decay: 0.09 })
      tone(NOTE(95), at() + 0.07, { peak: 0.09, type: 'triangle', decay: 0.35 })
    },
    chest() {
      ;[72, 76, 79, 84].forEach((n, i) => tone(NOTE(n), at() + i * 0.09, { peak: 0.09, type: 'triangle', decay: 0.5, sendReverb: 0.5 }))
    },
    star() {
      ;[72, 76, 79, 83, 84, 88, 91, 96].forEach((n, i) => piano(n, at() + i * 0.11, 0.9))
    },
    shrine() {
      ;[60, 67, 72, 76, 79, 84].forEach((n, i) => piano(n, at() + i * 0.22, 1))
      ;[48, 55, 60].forEach((n) => piano(n, at() + 1.4, 0.9))
    },
    talk() {
      ;[0, 1, 2].forEach((i) => tone(NOTE(79 + i * 2), at() + i * 0.07, { peak: 0.05, type: 'square', decay: 0.05, sendReverb: 0.1 }))
    },
    hit() {
      burst(at(), { filter: 'bandpass', freq: 900, q: 1, decay: 0.12, peak: 0.25 })
      tone(220, at(), { peak: 0.08, decay: 0.15, glide: 110, sendReverb: 0.1 })
    },
    hurt() {
      tone(520, at(), { peak: 0.1, type: 'square', decay: 0.18, glide: 260, sendReverb: 0.1 })
    },
    pop() {
      tone(600, at(), { peak: 0.1, decay: 0.15, glide: 1200, sendReverb: 0.2 })
      burst(at(), { filter: 'highpass', freq: 2000, decay: 0.08, peak: 0.1 })
    },
  }

  return {
    update,
    ...effects,
    setVolume(v) {
      master.gain.setTargetAtTime(v, ctx.currentTime, 0.1)
    },
    resume() {
      if (ctx.state === 'suspended') ctx.resume()
    },
    dispose() {
      ctx.close()
    },
  }
}
