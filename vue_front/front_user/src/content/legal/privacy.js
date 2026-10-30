export default {
  published: false,
  titleKey: 'auth.register.privacy',
  version: '2026-10-09',
  sections: [
    {
      id: 'publication',
      title: '正式政策待发布',
      paragraphs: ['运营者尚未提供正式隐私政策。本页面当前内容不能作为正式隐私政策使用。'],
    },
    {
      id: 'ai-services',
      title: 'AI 服务与联网搜索的数据流',
      paragraphs: [
        '奶糖助手与站内 AI 均通过阿里云百炼处理数据。奶糖说话和图片输入使用百炼上的 DeepSeek 模型，其他判断、写作、向量与图像任务使用 Qwen 模型。',
        '模型仅接收功能所需的消息、图片、昵称、长期记忆、会话摘要、公开帖子及本人的成长数据，不接收邮箱、手机号、IP 或属地。',
        '联网搜索经百炼的搜索服务发送本轮明确询问的问题，不包含昵称、记忆和图片内容；用户本轮明确提供的城市或作品名可以用于查询。',
      ],
    },
    {
      id: 'information',
      title: '待确认信息',
      paragraphs: [
        '正式发布前需确认个人信息处理者、联系方式、处理目的、存储期限、用户权利与适用地区。',
      ],
    },
  ],
}
