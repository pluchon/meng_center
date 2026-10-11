package com.pluchon.meng.content.service;

import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.common.core.error.CommonErrorCode;
import com.pluchon.meng.content.config.ContentProperties;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;

// 内容操作频率和每日配额校验
@Service
public class ContentLimitService {

    // 缓存和操作计数
    @Autowired
    private StringRedisTemplate redis;

    // 当前业务域配置
    @Autowired
    private ContentProperties properties;

    // 业务时钟
    @Autowired
    private Clock clock;

    // 原子递增并设置过期时间的计数脚本
    private final DefaultRedisScript<Long> count =
            new DefaultRedisScript<>(
                    "local n=redis.call('INCR',KEYS[1]);if n==1 then"
                            + " redis.call('EXPIRE',KEYS[1],ARGV[1]) end;return n",
                    Long.class);

    // 原子累计操作次数并设置有效期
    private void charge(String scope, long user, int max, long seconds) {
        Long n =
                redis.execute(
                        count,
                        List.of("meng:content:limit:" + scope + ":" + user),
                        Long.toString(seconds));
        if (n == null || n > max) throw new BusinessException(CommonErrorCode.RATE_LIMITED);
    }

    // 限制每分钟搜索请求次数
    public void search(long user) {
        charge("search", user, 30, 60);
    }

    // 校验发帖间隔与每日配额
    public void post(long user) {
        charge("post-interval", user, 1, properties.postInterval().toSeconds());
        daily("post", user, properties.postsDaily());
    }

    // 校验帖子配图每日上传配额
    public void upload(long user) {
        daily("upload", user, properties.uploadsDaily());
    }

    // 校验评论提交间隔
    public void comment(long user) {
        charge("comment-interval", user, 1, Math.max(1, properties.commentInterval().toSeconds()));
    }

    // 校验举报间隔与每日配额
    public void report(long user) {
        charge("report-interval", user, 1, Math.max(1, properties.reportInterval().toSeconds()));
        daily("report", user, properties.reportsDaily());
    }

    // 校验评论配图每日上传配额
    public void commentUpload(long user) {
        daily("comment-upload", user, properties.commentUploadsDaily());
    }

    // 校验草稿保存间隔
    public void draft(long user) {
        charge("draft-interval", user, 1, Math.max(1, properties.draftInterval().toSeconds()));
    }

    // 累计当前业务日期的操作次数
    private void daily(String scope, long user, int max) {
        var now = clock.instant().atZone(ZoneId.of(properties.timezone()));
        charge(
                scope + ":" + now.toLocalDate(),
                user,
                max,
                Math.max(
                        1,
                        Duration.between(
                                        now,
                                        now.toLocalDate().plusDays(1).atStartOfDay(now.getZone()))
                                .toSeconds()));
    }
}
