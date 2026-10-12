import { cleanupPostReadModels } from './read-model-cleanup.mjs';
import fs from "node:fs/promises";
import path from "node:path";
import crypto from "node:crypto";
import assert from "node:assert/strict";
import mysql from "mysql2/promise";
import { createClient } from "redis";
import { chromium } from "playwright";
const origin = "http://127.0.0.1:5173",
  base = "http://127.0.0.1:8080",
  nonce = crypto.randomBytes(3).toString("hex"),
  users = [],
  cookies = [],
  keys = [],
  checks = [];
const connect = (domain) =>
  mysql.createConnection({
    host: "127.0.0.1",
    port: Number(process.env.MENG_MYSQL_PORT),
    user: `meng_${domain}_app`,
    password: process.env[`MENG_MYSQL_${domain.toUpperCase()}_PASSWORD`],
    database: `meng_${domain}`,
  });
const redis = createClient({
  url: `redis://127.0.0.1:${process.env.MENG_REDIS_PORT}`,
  password: process.env.MENG_REDIS_PASSWORD,
});
let userDb, contentDb, growthDb, browser;
async function api(method, url, body, index = 0) {
  const response = await fetch(base + url, {
    method,
    headers: {
      Origin: origin,
      ...(cookies[index] ? { Cookie: cookies[index] } : {}),
      ...(body && !(body instanceof FormData)
        ? { "Content-Type": "application/json" }
        : {}),
    },
    body:
      body instanceof FormData ? body : body ? JSON.stringify(body) : undefined,
  });
  return { status: response.status, ...(await response.json()) };
}
function ok(result, status = 200) {
  assert.equal(result.status, status, JSON.stringify(result));
  return result.data;
}
async function check(name, run) {
  await run();
  checks.push(name);
  console.log("PASS " + name);
}
const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
async function clearDraftRate(index = 0) {
  await redis.del("meng:content:limit:draft-interval:" + users[index]);
}
async function clearPostRate(index = 0) {
  await redis.del("meng:content:limit:post-interval:" + users[index]);
}
const basePost = {
  category: "DAILY",
  title: "Editor verification " + nonce,
  content: "# Markdown\n\nA **safe** post.",
  imageIds: [],
  tags: [],
};
try {
  for (let i = 0; i < 80; i++) {
    const health = await fetch("http://127.0.0.1:8102/actuator/health").catch(
      () => null,
    );
    if (health?.status === 200) break;
    await wait(500);
  }
  await redis.connect();
  userDb = await connect("user");
  contentDb = await connect("content");
  growthDb = await connect("growth");
  const [[admin]] = await userDb.execute(
    "SELECT password_hash FROM user WHERE username=?",
    [process.env.MENG_ADMIN_USERNAME],
  );
  assert.ok(admin);
  for (let i = 0; i < 2; i++) {
    const username = "verify_detail_" + nonce + "_" + i;
    const [insert] = await userDb.execute(
      "INSERT INTO user(username,password_hash,role,status,level) VALUES(?,?,'USER','NORMAL',1)",
      [username, admin.password_hash],
    );
    users.push(insert.insertId);
    const ticket = crypto.randomUUID(),
      key = "meng:captcha-ticket:" + ticket;
    keys.push(key);
    await redis.set(
      key,
      crypto
        .createHmac(
          "sha256",
          Buffer.from(process.env.MENG_PII_HASH_KEY, "base64"),
        )
        .update("127.0.0.1")
        .digest("hex"),
      { EX: 120 },
    );
    const response = await fetch(base + "/user/sessions", {
      method: "POST",
      headers: { Origin: origin, "Content-Type": "application/json" },
      body: JSON.stringify({
        username,
        password: process.env.MENG_ADMIN_PASSWORD,
        ticket,
      }),
    });
    assert.equal(response.status, 200);
    cookies.push(response.headers.get("set-cookie").split(";")[0]);
  }
  const postBody={category:'DAILY',title:'今天的云像棉花糖一样',content:'放学路上抬头看到的，忍不住拍了好几张。大家那边的天空是什么样的呀～\n\n## 拍的时候\n\n- 傍晚五点多，太阳刚要下去\n- 手机直出，**没有调色**\n\n> 云是天空写给地面的信。\n\n想看更多可以去[这个相册](https://example.com)，参数用 `HDR` 模式。\n\n```text\nISO 100\n1/500s\nf/2.8\n```',imageIds:[],tags:['detail'+nonce]};
  let post, rootComment, reply, reply2, image;
  async function comment(body,index=0){await redis.del('meng:content:limit:comment-interval:'+users[index]);return ok(await api('POST','/content/posts/'+post+'/comments',body,index));}
  await check('Create post and count one unique view per person',async()=>{
    const f=new FormData();f.append('file',new Blob([await fs.readFile(new URL('../../vue_front/front_user/src/assets/forget_password_avatar.png',import.meta.url))],{type:'image/png'}),'image.png');
    image=ok(await api('POST','/content/images',f));postBody.imageIds=[image.id];
    for(let i=0;i<7;i++){const extra=ok(await api('POST','/content/images',f));postBody.imageIds.push(extra.id)}
    await userDb.execute('UPDATE user SET nickname=? WHERE id=?',['云朵收藏家',users[0]]);
    await userDb.execute('UPDATE user SET nickname=? WHERE id=?',['薄荷糖',users[1]]);
    post=ok(await api('POST','/content/posts',postBody));
    const first=ok(await api('GET','/content/posts/'+post));const second=ok(await api('GET','/content/posts/'+post));assert.equal(first.viewCount,second.viewCount);
    assert.equal(ok(await api('GET','/content/posts/'+post,null,1)).viewCount,first.viewCount+1);
  });
  await check('Comments validate text, tokens, ownership and image kinds',async()=>{
    ok(await api('POST','/content/posts/'+post+'/comments',{}),400);
    ok(await api('POST','/content/posts/'+post+'/comments',{content:'[表情:水滴]'.repeat(11)}),400);
    ok(await api('POST','/content/posts/'+post+'/comments',{content:'😀'.repeat(501)}),400);
    ok(await api('POST','/content/posts/'+post+'/comments',{imageIds:[image.id]}),403);
    const f=new FormData();f.append('file',new Blob([await fs.readFile(new URL('../../vue_front/front_user/src/assets/user_avatar.png',import.meta.url))],{type:'image/png'}),'image.png');
    const ci=ok(await api('POST','/content/comment-images',f));
    ok(await api('POST','/content/posts/'+post+'/comments',{content:'[表情:水滴]'.repeat(10),imageIds:[ci.id]}),400);
    await redis.del('meng:content:limit:comment-interval:'+users[1]);
    ok(await api('POST','/content/posts/'+post+'/comments',{imageIds:[ci.id]},1),403);
    rootComment=await comment({content:'今天也是好天气 [表情:萌萌] <script>bad()</script>',imageIds:[ci.id]});
    assert.equal(rootComment.postAuthor,true);assert.equal(rootComment.images.length,1);
    reply=await comment({content:'这张图真可爱',replyToCommentId:rootComment.id},1);
    reply2=await comment({content:'谢谢你～',replyToCommentId:reply.id});
    assert.equal(reply2.replyToUserId,users[1]);
    const list=ok(await api('GET','/content/posts/'+post+'/comments'));assert.equal(list.records[0].replyCount,2);assert.equal(list.records[0].replies,undefined);
    assert.deepEqual(ok(await api('GET','/content/comments/'+rootComment.id+'/replies')).records.map(c=>c.id),[reply.id,reply2.id]);
    await api('PUT','/content/comments/'+reply.id+'/like');await api('PUT','/content/comments/'+reply.id+'/like');
    assert.equal(ok(await api('GET','/content/comments/'+rootComment.id+'/replies')).records[0].likeCount,1);
    await api('DELETE','/content/comments/'+reply.id+'/like');
    ok(await api('DELETE','/content/comments/'+reply.id),403);
  });
  await check('Reports reject own content and duplicate submissions',async()=>{
    const report={targetType:'POST',targetId:post,reason:'OTHER',note:'验证'};
    ok(await api('POST','/content/reports',report),400);ok(await api('POST','/content/reports',report,1));
    assert.equal((await api('POST','/content/reports',report,1)).code,'CONTENT_REPORT_DUPLICATE');
  });
  await check('Comment pagination and replies use server totals',async()=>{
    for(let i=0;i<12;i++)await comment({content:'分页评论 '+i},1);
    for(let i=0;i<11;i++)await comment({content:'楼内回复 '+i,replyToCommentId:rootComment.id},1);
    const list=ok(await api('GET','/content/posts/'+post+'/comments?page=1&size=10'));assert.equal(list.total,13);assert.equal(list.records.length,10);
    assert.equal(ok(await api('GET','/content/posts/'+post+'/comments?page=2&size=10')).records.length,3);
    assert.equal(ok(await api('GET','/content/comments/'+rootComment.id+'/replies')).total,13);
  });
  await check('Detail and comment reads withstand concurrent requests',async()=>{
    const samples=[];let completed=0;
    for(let i=0;i<16;i++) ok(await api('GET','/content/posts/'+post));
    const started=performance.now(),deadline=started+8000;
    await Promise.all(Array.from({length:16},async()=>{
      while(performance.now()<deadline){
        const before=performance.now();
        ok(await api('GET',completed%2?'/content/posts/'+post:'/content/posts/'+post+'/comments'));
        samples.push(performance.now()-before);completed++;
      }
    }));
    samples.sort((a,b)=>a-b);
    console.log(JSON.stringify({name:'详情与评论混合读取',concurrency:16,requests:completed,
      perSecond:Math.round(completed*1000/(performance.now()-started)),
      p95:Math.round(samples[Math.floor(samples.length*.95)]),errors:0}));
  });
  browser=await chromium.launch({channel:'msedge',headless:true});
  const context=await browser.newContext({viewport:{width:1440,height:1000}});
  const cookieParts=cookies[0].split('=');await context.addCookies([{name:cookieParts[0],value:cookieParts.slice(1).join('='),url:origin,httpOnly:true}]);
  const page=await context.newPage(),errors=[];page.on('pageerror',e=>errors.push(e.message));
  const out=new URL('../test-output/detail/',import.meta.url);await fs.mkdir(out,{recursive:true});
  await check('Reference design renders',async()=>{
    if(process.env.MENG_DETAIL_REFERENCE_URL){await page.goto(process.env.MENG_DETAIL_REFERENCE_URL);await page.waitForTimeout(1500);await page.screenshot({path:path.join(out.pathname.replace(/^\/(\w:)/,'$1'),'reference.png'),fullPage:true});await page.screenshot({path:path.join(out.pathname.replace(/^\/(\w:)/,'$1'),'reference-top.png')});}
  });
  await check('Initial comments run in parallel, mount once and retry independently',async()=>{
    const starts=[];
    const capture=request=>{
      if(request.method()==='GET'&&request.url().includes('/content/posts/'+post))
        starts.push({url:request.url(),time:performance.now()});
    };
    const pattern=new RegExp('/content/posts/'+post+'(?:\\?|$|/comments)');
    await page.route(pattern,async route=>{
      await wait(350);await route.continue();
    });
    page.on('request',capture);
    try{
      await page.goto(origin+'/posts/'+post);
      await page.locator('.comment-list .comment-item').first().waitFor();
      const detail=starts.find(item=>!item.url.includes('/comments'));
      const comments=starts.filter(item=>item.url.includes('/comments'));
      assert.equal(comments.length,1);
      assert.ok(Math.abs(comments[0].time-detail.time)<100);
      assert.equal(await page.locator('.comment-composer').count(),0);
    }finally{
      page.off('request',capture);await page.unroute(pattern);
    }
    const commentsPattern='**/content/posts/'+post+'/comments?*';
    await page.route(commentsPattern,route=>route.fulfill({status:503,contentType:'application/json',body:JSON.stringify({code:'UNAVAILABLE',message:'Unavailable'})}),{times:1});
    await page.goto(origin+'/posts/'+post);
    await page.locator('.detail-card h1').waitFor();
    await page.locator('.post-comments .page-state').getByRole('button',{name:'重试',exact:true}).click();
    await page.locator('.comment-list .comment-item').first().waitFor();
    assert.equal(await page.locator('.detail-state').count(),0);
  });
  await check('Desktop detail, composer, pagination and safe emotion rendering',async()=>{
    await page.goto(origin+'/posts/'+post+'?category=DAILY&sort=LATEST&page=2');await page.locator('.comments-heading').waitFor();
    await page.evaluate(()=>document.fonts.ready);await page.screenshot({path:path.join(out.pathname.replace(/^\/(\w:)/,'$1'),'desktop.png'),fullPage:true});
    await page.screenshot({path:path.join(out.pathname.replace(/^\/(\w:)/,'$1'),'desktop-top.png')});assert.equal(await page.locator('.detail-card').count(),1);
    assert.equal(await page.locator('.post-comments .feed-pagination.compact').count(),1);
    assert.equal(await page.locator('.post-comments .back-top').count(),0);
    await page.getByRole('button',{name:'第 2 页',exact:true}).click();await page.getByText('今天也是好天气',{exact:false}).waitFor();
    assert.equal(await page.locator('.comment-body img').count(),1);assert.equal(await page.locator('.comment-body script').count(),0);
    await page.getByRole('button',{name:'13 条回复',exact:true}).click();await page.locator('.replies .comment-item').first().waitFor();
    await page.getByRole('button',{name:'还有 3 条回复',exact:true}).click();await page.waitForFunction(()=>document.querySelectorAll('.replies .comment-item').length===13);
    await page.locator('.replies .comment-item').first().getByRole('button',{name:'回复',exact:true}).click();
    assert.match(await page.locator('.composer-target').innerText(),/@/);
    await page.getByRole('textbox',{name:'写评论',exact:true}).fill('浏览器回复');
    await page.getByRole('button',{name:'表情',exact:true}).click();
    await page.getByRole('button',{name:'水滴',exact:true}).click();
    assert.equal(await page.locator('.composer-images img').count(),1);
    assert.equal(await page.getByRole('textbox',{name:'写评论',exact:true}).inputValue(),'浏览器回复');
    await page.locator('.comment-composer input[type=file]').setInputFiles(path.resolve('vue_front/front_user/src/assets/user_avatar.png'));
    await page.waitForFunction(()=>!document.querySelector('.composer-image small'));
    for(let i=0;i<8;i++) await page.getByRole('button',{name:'水滴',exact:true}).click();
    await page.getByRole('button',{name:'水滴',exact:true}).click();
    assert.equal(await page.locator('.composer-images img').count(),10);
    assert.match(await page.locator('.composer-error').innerText(),/合计最多 10/);
    assert.equal(await page.getByRole('button',{name:'图片',exact:true}).isDisabled(),true);
    await page.locator('.comment-composer').screenshot({path:path.join(out.pathname.replace(/^\/(\w:)/,'$1'),'composer-desktop.png')});
    await page.setViewportSize({width:390,height:844});
    assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
    await page.locator('.comment-composer').screenshot({path:path.join(out.pathname.replace(/^\/(\w:)/,'$1'),'composer-mobile.png')});
    await page.setViewportSize({width:1440,height:1000});
    assert.equal(await page.locator('.detail-decor').evaluate(el=>getComputedStyle(el).zIndex), '2');
    assert.equal(await page.locator('.detail-scroll-content').evaluate(el=>getComputedStyle(el).zIndex), '0');
    for(let i=0;i<9;i++) await page.locator('.composer-images').getByRole('button',{name:'移除图片',exact:true}).last().click();
    const layout = await page.evaluate(()=>{
      const preview=document.querySelector('.composer-images').getBoundingClientRect();
      const field=document.querySelector('.composer-field').getBoundingClientRect();
      return preview.bottom<=field.top;
    });
    assert.equal(layout,true);

    await redis.del('meng:content:limit:comment-interval:'+users[0]);
    const sending=page.waitForResponse(response=>response.url().endsWith('/comments')&&response.request().method()==='POST');
    await page.getByRole('button',{name:'发送',exact:true}).click();assert.equal((await sending).status(),200);await page.getByText('浏览器回复',{exact:false}).waitFor();
    await page.getByRole('button',{name:'写评论',exact:true}).click();await page.getByRole('textbox',{name:'写评论',exact:true}).fill('未发送的内容');
    await page.getByRole('button',{name:'收起',exact:true}).click();assert.equal(await page.getByRole('textbox',{name:'写评论',exact:true}).count(),0);await page.getByRole('button',{name:'写评论',exact:true}).click();assert.equal(await page.getByRole('textbox',{name:'写评论',exact:true}).inputValue(),'未发送的内容');
    await page.locator('.detail-back').click();await page.waitForURL(/category=DAILY.*page=2/);
  });
  await check('Author editing hydrates attachments and saves without a draft',async()=>{
    await page.goto(origin+'/posts/'+post);await page.getByRole('link',{name:'编辑',exact:true}).click();await page.getByRole('button',{name:'保存修改',exact:true}).waitFor();
    assert.equal(await page.getByRole('button',{name:'保存为草稿',exact:true}).count(),0);
    await page.getByPlaceholder('起个标题吧').fill('修改后的标题 '+nonce);
    await page.getByRole('button',{name:'保存修改',exact:true}).click();await page.waitForURL(new RegExp('/posts/'+post+'$'));
    assert.equal(ok(await api('GET','/content/posts/'+post)).title,'修改后的标题 '+nonce);
    ok(await api('PUT','/content/posts/'+post,postBody,1),403);
  });
  await check('Other readers see reporting and duplicate reports close the dialog',async()=>{
    const parts=cookies[1].split('=');await context.addCookies([{name:parts[0],value:parts.slice(1).join('='),url:origin,httpOnly:true}]);
    await page.goto(origin+'/posts/'+post);await page.locator('.comments-heading').waitFor();
    assert.equal(await page.locator('.detail-moderation').getByRole('link',{name:'编辑',exact:true}).count(),0);
    await page.locator('.detail-moderation').getByRole('button',{name:'举报',exact:true}).click();
    await page.getByRole('button',{name:'其他',exact:true}).click();
    await page.getByRole('button',{name:'提交举报',exact:true}).click();await page.getByRole('dialog').waitFor({state:'hidden'});
  });
  await check('Narrow viewport and image viewer do not overflow',async()=>{
    await page.setViewportSize({width:390,height:844});await page.goto(origin+'/posts/'+post);await page.locator('.comments-heading').waitFor();
    assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
    await page.locator('.post-image-grid:not(.compact) button').last().scrollIntoViewIfNeeded();await page.waitForTimeout(300);await page.locator('.detail-scroll-content').evaluate(el=>el.scrollTo(0,0));
    await page.screenshot({path:path.join(out.pathname.replace(/^\/(\w:)/,'$1'),'mobile.png'),fullPage:true});
    await page.locator('.post-image-grid:not(.compact) button').first().click();await page.locator('.el-image-viewer__wrapper').waitFor();
    assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);await page.keyboard.press('Escape');
    assert.deepEqual(errors,[]);
  });
  await check('Delete root hides descendants, counts correctly, and refuses subsequent writes',async()=>{
    const before=ok(await api('GET','/content/posts/'+post)).commentCount;
    const roots=ok(await api('GET','/content/posts/'+post+'/comments?page=2&size=10')).records;
    const root=roots.find(c=>c.id===rootComment.id);
    assert.equal(ok(await api('DELETE','/content/comments/'+root.id)),before-1-root.replyCount);
    assert.equal((await api('GET','/content/comments/'+root.id+'/replies')).code,'CONTENT_COMMENT_DELETED');
    assert.equal((await api('PUT','/content/comments/'+reply.id+'/like')).code,'CONTENT_COMMENT_DELETED');
    await redis.del('meng:content:limit:report-interval:'+users[0]);
    assert.equal((await api('POST','/content/reports',{targetType:'COMMENT',targetId:reply.id,reason:'OTHER'})).code,'CONTENT_COMMENT_DELETED');
    ok(await api('DELETE','/content/posts/'+post,null,1),403);
    ok(await api('DELETE','/content/posts/'+post));ok(await api('GET','/content/posts/'+post),404);
    const [[tag]]=await contentDb.execute('SELECT use_count FROM tag WHERE name_key=?',['detail'+nonce]);assert.equal(tag.use_count,0);
  });
  await check('Comment events award experience after commit',async()=>{
    for(let i=0;i<50;i++){const [[row]]=await growthDb.query("SELECT COUNT(*) total FROM exp_record WHERE user_id IN (?) AND source='COMMENT'",[users]);if(row.total>=5)break;await wait(100);}
    const [[row]]=await growthDb.query("SELECT COUNT(*) total FROM exp_record WHERE user_id IN (?) AND source='COMMENT'",[users]);assert.ok(row.total>=5);
  });
  console.log(JSON.stringify({passed:true,checks:checks.length}));
} catch (failure) {
  const page=browser?.contexts()[0]?.pages().at(-1);
  if(page) { await fs.mkdir(new URL('../test-output/detail/',import.meta.url),{recursive:true}); await page.screenshot({path:new URL('../test-output/detail/failure.png',import.meta.url).pathname.replace(/^\/(\w:)/,'$1'),fullPage:true}); console.error('UI failure at '+page.url()); console.error((await page.locator('body').innerText()).slice(-1200)); }
  throw failure;
} finally {
  await browser?.close();
  if (contentDb && users.length) {
    const [commentFiles] = await contentDb.query("SELECT file_name,thumb_name FROM comment_image WHERE uploader_id IN (?)",[users]);
    const [commentIds] = await contentDb.query("SELECT id FROM comment WHERE author_id IN (?)",[users]);
    if(commentIds.length)await contentDb.query("DELETE FROM comment_like WHERE comment_id IN (?)",[commentIds.map(c=>c.id)]);
    await contentDb.query("DELETE FROM report WHERE reporter_id IN (?)",[users]);
    await contentDb.query("DELETE FROM comment_image WHERE uploader_id IN (?)",[users]);
    await contentDb.query("DELETE FROM comment WHERE author_id IN (?)",[users]);
    for(const image of commentFiles)for(const file of [image.file_name,image.thumb_name])await fs.unlink(path.join(process.env.MENG_CONTENT_IMAGE_ROOT,file)).catch(()=>{});
    const [images] = await contentDb.query(
      "SELECT file_name,thumb_name FROM post_image WHERE uploader_id IN (?)",
      [users],
    );
    const [posts] = await contentDb.query(
      "SELECT id FROM post WHERE author_id IN (?)",
      [users],
    );
    const ids = posts.map((p) => p.id);
    if (ids.length) {
      await contentDb.query("DELETE FROM post_tag WHERE post_id IN (?)", [ids]);
      await contentDb.query("DELETE FROM post_like WHERE post_id IN (?)", [
        ids,
      ]);
      await contentDb.query("DELETE FROM post_favorite WHERE post_id IN (?)", [
        ids,
      ]);
    }
    await contentDb.query("DELETE FROM post_image WHERE uploader_id IN (?)", [
      users,
    ]);
    await contentDb.query("DELETE FROM post WHERE author_id IN (?)", [users]);
    await cleanupPostReadModels(redis, ids);
    await contentDb.query("DELETE FROM post_draft WHERE author_id IN (?)", [
      users,
    ]);
    await contentDb.query(
      "DELETE FROM post_draft_owner WHERE author_id IN (?)",
      [users],
    );
    await contentDb.execute("DELETE FROM tag WHERE name_key=?",['detail'+nonce]);
    for (const image of images)
      for (const file of [image.file_name, image.thumb_name])
        await fs
          .unlink(path.join(process.env.MENG_CONTENT_IMAGE_ROOT, file))
          .catch(() => {});
  }
  if (contentDb && users.length) await contentDb.query("DELETE FROM author_daily_stat WHERE author_id IN (?)", [users]);
  await contentDb?.end();
  if (growthDb && users.length) {
    for (const table of ["exp_record", "check_in", "growth_account"])
      await growthDb.query("DELETE FROM " + table + " WHERE user_id IN (?)", [
        users,
      ]);
  }
  await growthDb?.end();
  if (redis.isOpen) {
    for (const cookie of cookies) {
      const claims = JSON.parse(
        Buffer.from(cookie.split("=")[1].split(".")[1], "base64url"),
      );
      await redis.del("meng:session:" + claims.sessionId);
    }
    for (const user of users) {
      await redis.del("meng:user-sessions:" + user);
      for await (const batch of redis.scanIterator({
        MATCH: "meng:content:*",
        COUNT: 100,
      }))
        for (const key of batch)
          if (key.endsWith(":" + user)) await redis.del(key);
    }
    for (const key of keys) await redis.del(key);
    await redis.quit();
  }
  if (userDb && users.length)
    await userDb.query("DELETE FROM user WHERE id IN (?)", [users]);
  await userDb?.end();
}
