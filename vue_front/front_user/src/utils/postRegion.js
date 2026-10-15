const silhouettes = Object.freeze({
  甘肃: 'cn-gs',
  青海: 'cn-qh',
  广西: 'cn-gx',
  贵州: 'cn-gz',
  重庆: 'cn-cq',
  北京: 'cn-bj',
  福建: 'cn-fj',
  安徽: 'cn-ah',
  广东: 'cn-gd',
  西藏: 'cn-xz',
  新疆: 'cn-xj',
  海南: 'cn-hi',
  宁夏: 'cn-nx',
  陕西: 'cn-sn',
  山西: 'cn-sx',
  湖北: 'cn-hb',
  湖南: 'cn-hn',
  四川: 'cn-sc',
  云南: 'cn-yn',
  河北: 'cn-he',
  河南: 'cn-ha',
  辽宁: 'cn-ln',
  山东: 'cn-sd',
  天津: 'cn-tj',
  江西: 'cn-jx',
  江苏: 'cn-js',
  上海: 'cn-sh',
  浙江: 'cn-zj',
  吉林: 'cn-jl',
  内蒙古: 'cn-nm',
  黑龙江: 'cn-hl',
  美国: 'usa',
  英国: 'gbr',
  台湾: 'twn',
  韩国: 'kor',
  新加坡: 'sgp',
  日本: 'jpn',
  德国: 'deu',
  法国: 'fra',
  澳门: 'mac',
  香港: 'hkg',
  加拿大: 'can',
  澳大利亚: 'aus',
})

export function postRegion(value) {
  const label =
    (value || '')
      .trim()
      .replace(/^(中国大陆|中国)[·\s-]*/, '')
      .replace(/(壮族自治区|回族自治区|维吾尔自治区|自治区|省|市)$/, '') || (value || '').trim()
  return { label, shape: Object.hasOwn(silhouettes, label) ? silhouettes[label] : undefined }
}
