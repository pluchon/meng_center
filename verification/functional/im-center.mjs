import assert from 'node:assert/strict';
import crypto from 'node:crypto';
import fs from 'node:fs/promises';
import path from 'node:path';
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { performance } from 'node:perf_hooks';
import mysql from 'mysql2/promise';
import { createClient } from 'redis';
import amqp from 'amqplib';
import { chromium } from 'playwright';
import { cleanupPostReadModels } from './read-model-cleanup.mjs';

const root=fileURLToPath(new URL('../../',import.meta.url));
const output=path.join(root,'test-output/im-center');
const base='http://127.0.0.1:8080', origin='http://127.0.0.1:5173';
const production=process.env.MENG_IM_PRODUCTION==='1';
const frontOrigin=production?'http://127.0.0.1:5189':origin;
const nonce=crypto.randomBytes(5).toString('hex'), users=[], cookies=[], tickets=[], sockets=[], posts=[], images=[];
const redis=createClient({url:`redis://127.0.0.1:${process.env.MENG_REDIS_PORT}`,password:process.env.MENG_REDIS_PASSWORD});
let userDb,imDb,contentDb,growthDb,rabbit,channel,browser,preview,priorLoginCount;
const db=domain=>mysql.createConnection({host:'127.0.0.1',port:Number(process.env.MENG_MYSQL_PORT),user:`meng_${domain}_app`,password:process.env[`MENG_MYSQL_${domain.toUpperCase()}_PASSWORD`],database:`meng_${domain}`});
const wait=ms=>new Promise(resolve=>setTimeout(resolve,ms));
async function eventually(test,label,attempts=100) { for(let i=0;i<attempts;i++) { const value=await test(); if(value) return value; await wait(100); } throw Error('Timed out: '+label); }
async function api(url,account=0,method='GET',body) {
 const response=await fetch(base+url,{method,headers:{Origin:origin,...(cookies[account]?{Cookie:cookies[account]}:{}),...(body&&!(body instanceof FormData)?{'Content-Type':'application/json'}:{})},...(body?{body:body instanceof FormData?body:JSON.stringify(body)}:{})});
 const text=await response.text();
 return {status:response.status,...(text?JSON.parse(text):{})};
}
function ok(response,status=200) { assert.equal(response.status,status,JSON.stringify(response)); return response.data; }
async function check(name,fn) { await fn(); console.log('PASS '+name); }
const message=(peer,text='hello',client=crypto.randomUUID(),imageIds=[])=>({peerId:peer,clientMsgId:client,content:text,imageIds});
async function socket(context) {
 const page=await context.newPage();
 await page.goto(origin);
 const frames=[];
 const ws=await page.evaluateHandle(()=>new WebSocket(location.origin.replace('http','ws')+'/api/im/ws'));
 await page.evaluate(ws=>{window.__imFrames=[]; ws.addEventListener('message',event=>window.__imFrames.push(JSON.parse(event.data)));},ws);
 await eventually(()=>page.evaluate(ws=>ws.readyState===1,ws),'WebSocket open');
 const value={page,ws,frames:()=>page.evaluate(()=>window.__imFrames)};
 sockets.push(value);
 return value;
}
async function event(type,body,id=crypto.randomUUID()) {
 const envelope={eventId:id,eventType:type,occurredAt:new Date().toISOString(),traceId:id,body};
 channel.publish(`meng.${type.startsWith('growth.')?'growth':type.startsWith('user.')?'user':'content'}.topic`,type,Buffer.from(JSON.stringify(envelope)),{contentType:'application/json',headers:{__TypeId__:'com.pluchon.meng.common.core.event.EventEnvelope'},persistent:true});
 return id;
}
try {
 await fs.mkdir(output,{recursive:true});
 await redis.connect();
 userDb=await db('user'); imDb=await db('im'); contentDb=await db('content'); growthDb=await db('growth');
 const [[admin]]=await userDb.execute("SELECT password_hash FROM user WHERE username=?",[process.env.MENG_ADMIN_USERNAME]);
 assert(admin);
 // 测试来源的登录失败计数保存后恢复，不影响正常账号
 priorLoginCount=await redis.get('meng:rate:login:ip:127.0.0.1');
 for(let i=0;i<4;i++) {
   const username=`verify_im_${nonce}_${i}`;
   const [insert]=await userDb.execute("INSERT INTO user(username,nickname,password_hash,role,status,level) VALUES(?,?,?,'USER','NORMAL',1)",[username,['云朵收藏家','雾岛栞','小鹿汽水','未审核用户'][i],admin.password_hash]);
   users.push(insert.insertId);
   const ticket=crypto.randomUUID(); tickets.push(ticket);
   await redis.set('meng:captcha-ticket:'+ticket,crypto.createHmac('sha256',Buffer.from(process.env.MENG_PII_HASH_KEY,'base64')).update('127.0.0.1').digest('hex'),{EX:120});
   const response=await fetch(base+'/user/sessions',{method:'POST',headers:{Origin:origin,'Content-Type':'application/json'},body:JSON.stringify({username,password:process.env.MENG_ADMIN_PASSWORD,ticket})});
   assert.equal(response.status,200,await response.text());
   cookies.push(response.headers.get('set-cookie').split(';')[0]);
 }
 await userDb.execute("UPDATE user SET status='PENDING' WHERE id=?",[users[3]]);
 rabbit=await amqp.connect({hostname:'127.0.0.1',port:Number(process.env.MENG_RABBITMQ_PORT),username:process.env.MENG_RABBITMQ_USERNAME,password:process.env.MENG_RABBITMQ_PASSWORD,vhost:'meng'});
 channel=await rabbit.createChannel();
 browser=await chromium.launch({channel:'msedge',headless:true});
 const contexts=await Promise.all(cookies.slice(0,3).map(async cookie=>{
   const context=await browser.newContext({viewport:{width:1560,height:1200}});
   const value=cookie.slice(cookie.indexOf('=')+1);
   await context.addCookies([{name:'meng_token',value,url:origin,httpOnly:true},{name:'meng_token',value,url:base,httpOnly:true}]);
   return context;
 }));
 const sa=await socket(contexts[0]),sb=await socket(contexts[1]),sa2=await socket(contexts[0]);
 await check('Authentication, peer eligibility and empty conversations',async()=>{
   ok(await api('/im/unread',-1),401);
   const empty=ok(await api('/im/conversations/peer/'+users[1])); assert.equal(empty.id==null,true);
   assert.equal(ok(await api('/im/conversations')).total,0);
   ok(await api('/im/messages',0,'POST',message(users[0])),409);
   ok(await api('/im/messages',0,'POST',message(users[3])),404);
   ok(await api('/im/messages',0,'POST',message(users[1],'text',crypto.randomUUID(),[1])),400);
   const anonymous=await browser.newContext(), page=await anonymous.newPage(); await page.goto(origin);
   const code=await page.evaluate(()=>new Promise(resolve=>{const ws=new WebSocket(location.origin.replace('http','ws')+'/api/im/ws');ws.onopen=()=>{resolve(101);ws.close()};ws.onclose=event=>resolve(event.code)})); assert.notEqual(code,101); await anonymous.close();
 });
 let conversation,first;
 await check('Real-time delivery, other sender tabs and stranger limit',async()=>{
   first=ok(await api('/im/messages',0,'POST',message(users[1],'看到你发的水彩云朵了 [表情:萌萌]'))); conversation=first.conversationId;
   await eventually(async()=>(await sb.frames()).some(frame=>frame.type==='dm.message'&&frame.data.id===first.id),'recipient live message');
   await eventually(async()=>(await sa2.frames()).some(frame=>frame.type==='dm.message'&&frame.data.id===first.id),'sender second tab message');
   ok(await api('/im/messages',0,'POST',message(users[1],'第二条')));
   ok(await api('/im/messages',0,'POST',message(users[1],'第三条')));
   const denied=await api('/im/messages',0,'POST',message(users[1],'第四条')); ok(denied,403); assert.equal(denied.code,'IM_WAIT_REPLY');
   assert.equal(ok(await api('/im/unread',1)).chat,3);
   ok(await api('/im/messages',1,'POST',message(users[0],'谢谢！色卡可以借你参考')));
   ok(await api('/im/messages',0,'POST',message(users[1],'可以呀，整理好就分享')));
   ok(await api(`/im/conversations/${conversation}/messages`,2),404);
   ok(await api(`/im/conversations/${conversation}/read`,2,'PUT',{messageId:first.id}),404);
 });
 await check('Idempotent concurrent retry and read receipts',async()=>{
   await redis.del('meng:im:rate:msg:'+users[0]);
   const body=message(users[1],'同一个客户端消息 ID');
   const results=await Promise.all(Array.from({length:8},()=>api('/im/messages',0,'POST',body)));
   assert.equal(new Set(results.map(row=>ok(row).id)).size,1);
   const read=results[0].data.id;
   ok(await api(`/im/conversations/${conversation}/read`,1,'PUT',{messageId:read}));
   assert.equal(ok(await api('/im/unread',1)).chat,0);
   await eventually(async()=>(await sa.frames()).some(frame=>frame.type==='dm.read'&&frame.data.messageId===read),'read receipt');
   ok(await api(`/im/conversations/${conversation}/read`,1,'PUT',{messageId:first.id}));
   assert.equal(ok(await api('/im/conversations/peer/'+users[0],1)).lastReadMessageId,read);
 });
 await check('Blocking and delete visibility boundaries',async()=>{
   ok(await api('/im/blocks',1,'POST',{userId:users[0]}));
   const response=await api('/im/messages',0,'POST',message(users[1])); ok(response,403); assert.equal(response.code,'IM_BLOCKED');
   ok(await api('/im/blocks/'+users[0],1,'DELETE'));
   ok(await api(`/im/conversations/${conversation}`,1,'DELETE'));
   assert.equal(ok(await api('/im/conversations',1)).total,0);
   assert.equal(ok(await api(`/im/conversations/${conversation}/messages`,1)).records.length,0);
   const later=ok(await api('/im/messages',0,'POST',message(users[1],'删除之后的新消息')));
   assert.deepEqual(ok(await api(`/im/conversations/${conversation}/messages`,1)).records.map(row=>row.id),[later.id]);
 });
 await check('Private image permissions, album and body boundaries',async()=>{
   const bytes=await fs.readFile(path.join(root,'vue_front/front_user/src/assets/user_avatar.png'));
   for(let i=0;i<6;i++) { const body=new FormData(); body.append('file',new Blob([bytes],{type:'image/png'}),'avatar.png'); const image=ok(await api('/im/images',0,'POST',body)); images.push(image.id); }
   const imageIds=images.slice(0,5);
   const body=message(users[1],null,crypto.randomUUID(),imageIds);
   const result=ok(await api('/im/messages',0,'POST',body)); assert.equal(result.images.length,5);
   const foreign=await fetch(base+'/im/images/'+imageIds[0],{headers:{Cookie:cookies[2]}}); assert.equal(foreign.status,404);
   const permitted=await fetch(base+'/im/images/'+imageIds[0]+'?thumb=true',{headers:{Cookie:cookies[1]}}); assert.equal(permitted.status,200); assert.match(permitted.headers.get('cache-control'),/no-store/);
   ok(await api('/im/messages',1,'POST',message(users[0],null,crypto.randomUUID(),[images[5]])),404);
   ok(await api('/im/messages',0,'POST',message(users[1],null,crypto.randomUUID(),imageIds)),404);
   ok(await api('/im/messages',0,'POST',message(users[1],null,crypto.randomUUID(),Array.from({length:11},(_,i)=>i+1))),400);
   ok(await api('/im/messages',0,'POST',message(users[1],'[表情:萌萌]'.repeat(11))),400);
 });
 await check('Presence subscription, multiple tabs and hidden mode',async()=>{
   await sa.page.evaluate(([ws,id])=>ws.send(JSON.stringify({type:'presence.watch',data:{userId:id}})),[sa.ws,users[1]]);
   await eventually(async()=>(await sa.frames()).some(frame=>frame.type==='presence'&&frame.data.online),'initial presence');
   ok(await api('/im/settings',1,'PUT',{hidden:true}));
   await eventually(async()=>(await sa.frames()).some(frame=>frame.type==='presence'&&!frame.data.online),'hidden presence');
   ok(await api('/im/settings',1,'PUT',{hidden:false}));
 });
 let postId,commentId,focusedReply;
 await check('Actual content events, reply snapshots, likes and favorites',async()=>{
   const post=ok(await api('/content/posts',0,'POST',{title:'水彩云朵的十二种画法 '+nonce,content:'一起分享水彩练习',category:'CREATION',tags:[],imageIds:[],coverImageId:null,draftId:null})); postId=post; posts.push(postId);
   const comment=ok(await api(`/content/posts/${postId}/comments`,1,'POST',{content:'配色太治愈了，求一份色卡 [表情:萌萌]',imageIds:[],replyToCommentId:null})); commentId=comment.id;
   await eventually(async()=>ok(await api('/im/notifications?channel=COMMENT&source=POST')).records.some(item=>item.notice.commentId===commentId),'comment notification');
   ok(await api(`/content/posts/${postId}/comments`,0,'POST',{content:'借你吉言，下次一起画',imageIds:[],replyToCommentId:commentId}));
   await eventually(async()=>ok(await api('/im/notifications?channel=COMMENT&source=POST',1)).records.some(item=>item.notice.type==='COMMENT_REPLY'&&item.notice.originalExcerpt.includes('配色')),'reply snapshot');
   for(const account of [1,2]) ok(await api(`/content/posts/${postId}/like`,account,'PUT'));
   ok(await api(`/content/posts/${postId}/favorite`,1,'PUT'));
   ok(await api(`/content/comments/${commentId}/like`,0,'PUT'));
   ok(await api(`/content/posts/${postId}/like`,1,'DELETE')); ok(await api(`/content/posts/${postId}/like`,1,'PUT'));
   await eventually(async()=>ok(await api('/im/notifications?channel=LIKE&source=POST')).total===2,'like and favorite groups');
   const likes=ok(await api('/im/notifications?channel=LIKE&source=POST')).records;
   const group=likes.find(item=>item.notice.type==='POST_LIKED'); assert.equal(group.notice.actorCount,2); assert.equal(group.actors.length,2);
   const [[count]]=await imDb.execute('SELECT COUNT(*) count FROM notification WHERE receiver_id=? AND type=? AND actor_id=?',[users[0],'POST_LIKED',users[1]]); assert.equal(count.count,1);
   const before=ok(await api('/im/unread')).like;
   ok(await api(`/content/posts/${postId}/like`,0,'PUT')); await wait(300); assert.equal(ok(await api('/im/unread')).like,before);
 });
 await check('System notifications, event dedupe and group reads',async()=>{
   const id=await event('growth.level-changed',{userId:users[0],level:3}); await event('growth.level-changed',{userId:users[0],level:3},id);
   await event('user.approved',{userId:users[0]});
   await eventually(async()=>ok(await api('/im/notifications?channel=SYSTEM')).total===2,'system events');
   const groups=ok(await api('/im/notification-groups?channel=SYSTEM')); assert.deepEqual(groups.map(row=>row.key),['GROWTH','REPORT','APPROVAL']);
   ok(await api('/im/notifications/read',0,'PUT',{channel:'LIKE',source:'POST'})); assert.equal(ok(await api('/im/unread')).like,0);
   ok(await api('/im/notifications?channel=SYSTEM&category=BAD'),400);
   ok(await api('/im/notifications/read',0,'PUT',{channel:'SYSTEM'}),400);
   await eventually(async()=>(await sa.frames()).some(frame=>frame.type==='notice.new'),'live notification');
 });
 await check('Message and daily image rate limits and finite MQ dead lettering',async()=>{
   await redis.set('meng:im:rate:msg:'+users[0],'20',{EX:60}); ok(await api('/im/messages',0,'POST',message(users[1])),429); await redis.del('meng:im:rate:msg:'+users[0]);
   const today=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Taipei',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date());
   const key='meng:im:rate:image:'+users[0]+':'+today; await redis.set(key,'100',{EX:60}); const form=new FormData();form.append('file',new Blob([await fs.readFile(path.join(root,'vue_front/front_user/src/assets/user_avatar.png'))],{type:'image/png'}),'image.png');ok(await api('/im/images',0,'POST',form),429);await redis.del(key);
   const before=(await channel.checkQueue('meng.im.dead')).messageCount, id='verify-im-dead-'+nonce;
   channel.publish('meng.content.topic','post.liked',Buffer.from(JSON.stringify({eventId:id,eventType:'wrong',occurredAt:new Date().toISOString(),traceId:id,body:{eventType:'post.liked',postId,postTitle:'fixture',actorId:users[1],authorId:users[0]}})),{contentType:'application/json',persistent:true});
   await eventually(async()=>(await channel.checkQueue('meng.im.dead')).messageCount>before,'finite retries to dead queue');
   const held=[];let own;
   for(let i=0;i<before+5;i++) {const item=await channel.get('meng.im.dead');if(!item)break;if(JSON.parse(item.content.toString()).eventId===id){own=item;channel.ack(item);break;}held.push(item);}
   for(const item of held)channel.nack(item,false,true);assert(own);
 });
 await check('Notification pagination and locating comments beyond the first page',async()=>{
   for(let i=0;i<11;i++) {
     const [row]=await contentDb.execute('INSERT INTO comment(post_id,author_id,root_id,content,image_count,like_count,reply_count) VALUES(?,?,0,?,0,0,0)',[postId,users[1],'水彩练习与配色分享 '+(i+1)]);
     await event('comment.created',{commentId:row.insertId,postId,postTitle:'水彩云朵的十二种画法',authorId:users[1],targetUserId:users[0],parentCommentId:null,excerpt:'水彩练习与配色分享 '+(i+1)});
     const [reply]=await contentDb.execute('INSERT INTO comment(post_id,author_id,root_id,reply_to_comment_id,reply_to_user_id,content,image_count,like_count,reply_count) VALUES(?,?,?,?,?,?,0,0,0)',[postId,users[0],commentId,commentId,users[1],'跨页回复 '+(i+1)]); focusedReply=reply.insertId;
   }
   await contentDb.execute('UPDATE post SET comment_count=comment_count+22 WHERE id=?',[postId]);
   await contentDb.execute('UPDATE comment SET reply_count=reply_count+11 WHERE id=?',[commentId]);
   await eventually(async()=>ok(await api('/im/notifications?channel=COMMENT&source=POST')).total===12,'12 comment notifications');
   const second=ok(await api('/im/notifications?channel=COMMENT&source=POST&page=2')); assert.equal(second.records.length,2);
   const location=ok(await api('/content/comments/'+focusedReply+'/location')); assert.equal(location.page,2); assert.equal(location.replyPage,2);
   const page=await contexts[1].newPage(); await page.goto(origin+'/posts/'+postId+'#comment-'+focusedReply); await page.locator('#comment-'+focusedReply).waitFor(); assert.equal(await page.locator('#comment-'+focusedReply).isVisible(),true); await page.close();
 });
 if(production) {
   const front=path.join(root,'vue_front/front_user');
   // 隔离预览通过自己的代理沿用开发 Origin，不改业务允许来源。
   const vite=fileURLToPath(new URL('../../vue_front/front_user/node_modules/vite/dist/node/index.js',import.meta.url));
   const previewCode=`import { preview } from ${JSON.stringify(new URL('file:///'+vite.replaceAll('\\','/')).href)};
     await preview({configFile:${JSON.stringify(path.join(front,'vite.config.js'))},preview:{host:'127.0.0.1',port:5189,strictPort:true,proxy:{'/api':{target:${JSON.stringify(base)},ws:true,changeOrigin:false,rewrite:path=>path.replace(/^\\/api/,''),configure:proxy=>{proxy.on('proxyReq',request=>request.setHeader('origin',${JSON.stringify(origin)}));proxy.on('proxyReqWs',request=>request.setHeader('origin',${JSON.stringify(origin)}));}}}}});`;
   preview=spawn(process.execPath,['--input-type=module','-e',previewCode],{cwd:front,windowsHide:true,env:{...process.env,VITE_GATEWAY_TARGET:base},stdio:'ignore'});
   await eventually(()=>fetch(frontOrigin,{signal:AbortSignal.timeout(500)}).catch(()=>null),'production preview');
 }
 await check('Four-page browser flow, live composer, album and navigation badge',async()=>{
   const page=await contexts[0].newPage(), errors=[];
   page.on('pageerror',error=>errors.push(error.message)); page.on('console',message=>{if(message.type()==='error'||message.type()==='warning') errors.push(message.text());});
   await page.goto(frontOrigin+'/messages?with='+users[1]);
   await page.locator('.message-chat').waitFor(); await page.locator('.messages-view[data-connection="connected"]').waitFor();
   assert.match(await page.title(),/消息中心/); assert.equal(await page.locator('.vite-error-overlay').count(),0);
   await page.getByRole('textbox',{name:'说点什么吧'}).fill('浏览器发送 [表情:水滴]'); await page.getByRole('button',{name:'发送',exact:true}).click();
   await eventually(async()=> (await api(`/im/conversations/${conversation}/messages`,1)).data.records.some(item=>item.content==='浏览器发送 [表情:水滴]'),'browser HTTP send');
   let failedClient, retryClient, failOnce=true;
   await page.route('**/im/messages',async route=>{ const body=route.request().postDataJSON(); if(failOnce) { failOnce=false; failedClient=body.clientMsgId; await route.fulfill({status:503,contentType:'application/json',body:JSON.stringify({code:'COMMON_INTERNAL_ERROR',message:'服务暂时不可用'})}); } else {retryClient=body.clientMsgId;await route.continue();} });
   await page.getByRole('textbox',{name:'说点什么吧'}).fill('失败重试沿用客户端 ID'); await page.getByRole('button',{name:'发送',exact:true}).click();
   await page.getByRole('button',{name:'发送失败，点击重发',exact:true}).click(); await eventually(async()=>retryClient===failedClient&&ok(await api(`/im/conversations/${conversation}/messages`,1)).records.some(item=>item.content==='失败重试沿用客户端 ID'),'stable retry ID'); await page.unroute('**/im/messages');
   await page.getByRole('button',{name:'展开图集'}).click(); await page.getByRole('button',{name:'收起图集'}).waitFor(); assert(await page.locator('.message-images.expanded img').count()>=5); await page.getByRole('button',{name:'收起图集'}).click();
   await page.screenshot({path:path.join(output,'chat-desktop.png')});
   const height=await page.locator('.messages-card').evaluate(node=>node.getBoundingClientRect().height);
   for(const tab of ['评论','喜欢','系统']) { await page.getByRole('tab',{name:new RegExp('^'+tab)}).click(); await page.locator('.notice-card').first().waitFor(); assert.equal(await page.locator('.messages-card').evaluate(node=>node.getBoundingClientRect().height),height); await page.locator('.notices-footer nav').waitFor(); await page.waitForTimeout(300); await page.screenshot({path:path.join(output,tab==='评论'?'comment-desktop.png':tab==='喜欢'?'like-desktop.png':'system-desktop.png')}); }
   await page.getByRole('tab',{name:/^评论/}).click(); await page.getByRole('button',{name:'第 2 页',exact:true}).click(); await eventually(()=>page.locator('.notice-card').count().then(count=>count===2),'notification second page'); await page.getByRole('button',{name:'第 1 页',exact:true}).click(); await page.getByRole('tab',{name:/^系统/}).click();
   await page.getByRole('button',{name:/举报通知/}).click(); await page.getByText('暂时没有通知',{exact:true}).waitFor();
   await page.getByRole('button',{name:/审核通知/}).click(); await page.getByText('注册申请通过啦',{exact:true}).first().waitFor();
   await page.getByRole('button',{name:'在线状态',exact:true}).click(); await page.getByRole('button',{name:'隐身',exact:true}).click(); await eventually(async()=>ok(await api('/im/settings')).hidden,'hidden setting');
   await page.getByRole('button',{name:'在线状态',exact:true}).click(); await page.getByRole('button',{name:'自动在线',exact:true}).click();
   await page.getByRole('link',{name:'回到首页'}).click(); await page.locator('.nav-messages').waitFor(); assert.equal(await page.locator('.nav-tools .nav-messages').count(),1);
   await page.goto(frontOrigin+'/posts/'+postId); await page.locator('.user-contact-avatar').first().click();
   await page.goto(frontOrigin+'/messages?tab=comment'); await page.getByRole('tab',{name:/^私信/}).click();
   try { await page.locator('.conversation-select').first().click(); } catch(failure) { await page.screenshot({path:path.join(output,'browser-failure.png'),fullPage:true}); await fs.writeFile(path.join(output,'browser-failure.txt'),JSON.stringify({text:await page.locator('body').innerText(),errors},null,2)); throw failure; } await page.locator('.message-chat').waitFor();
   await page.setViewportSize({width:390,height:844}); await eventually(()=>page.locator('.chat-history').evaluate(node=>node.scrollHeight-node.scrollTop-node.clientHeight<4),'mobile scroll at latest message'); assert(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth)); await page.screenshot({path:path.join(output,'chat-mobile.png'),fullPage:true});
   await page.emulateMedia({reducedMotion:'reduce'}); assert.equal(await page.locator('.messages-card').evaluate(node=>getComputedStyle(node).animationName),'none');
   assert.deepEqual(errors.filter(error=>!error.includes('503')),[]);
   await page.close();
 });
 await check('Unread recovery after network reconnection and request latency',async()=>{
   const page=await contexts[1].newPage(); await page.goto(origin+'/messages?with='+users[0]); await page.locator('.message-chat').waitFor();
   await contexts[1].setOffline(true); await wait(200); const sent=ok(await api('/im/messages',0,'POST',message(users[1],'断线期间的新消息')));
   await contexts[1].setOffline(false); await page.locator('.messages-view[data-connection="connected"]').waitFor(); await page.getByText('断线期间的新消息',{exact:true}).waitFor();
   await page.close();
   const timings=await Promise.all(Array.from({length:24},async()=>{const start=performance.now();ok(await api('/im/notifications?channel=LIKE&source=POST'));return performance.now()-start})); timings.sort((a,b)=>a-b);
   console.log(`IM read latency p50=${Math.round(timings[12])}ms p95=${Math.round(timings[22])}ms`);
   assert(sent.id);
   await sa.page.evaluate(([ws,id])=>ws.send(JSON.stringify({type:'presence.watch',data:{userId:id}})),[sa.ws,users[1]]);
   await eventually(async()=>(await sa.frames()).filter(frame=>frame.type==='presence').at(-1)?.data.online,'online before closing tabs');
   await contexts[1].close(); await wait(2000); assert.equal((await sa.frames()).filter(frame=>frame.type==='presence').at(-1).data.online,true);
   await eventually(async()=>(await sa.frames()).filter(frame=>frame.type==='presence').at(-1)?.data.online===false,'offline after grace period',200);
 });
} finally {
 if(browser) await browser.close();
 if(preview&&!preview.killed) preview.kill();
 if(channel) await channel.close(); if(rabbit) await rabbit.close();
 if(imDb&&users.length) {
   const marks=users.map(()=>'?').join(',');
   const [files]=await imDb.execute(`SELECT file_name,thumb_name FROM message_image WHERE uploader_id IN (${marks})`,users);
   for(const row of files) for(const name of [row.file_name,row.thumb_name]) await fs.rm(path.join(process.env.MENG_IM_IMAGE_ROOT,name),{force:true});
   await imDb.execute(`DELETE FROM message_image WHERE uploader_id IN (${marks})`,users);
   await imDb.execute(`DELETE FROM private_message WHERE sender_id IN (${marks})`,users);
   await imDb.execute(`DELETE FROM conversation_member WHERE user_id IN (${marks})`,users);
   await imDb.execute(`DELETE FROM conversation WHERE user_low_id IN (${marks}) OR user_high_id IN (${marks})`,[...users,...users]);
   for(const table of ['notification','user_block','im_user_setting']) await imDb.execute(`DELETE FROM ${table} WHERE ${table==='notification'?'receiver_id':'user_id'} IN (${marks})`,users);
   if(contentDb) {
     const [fixturePosts]=await contentDb.execute(`SELECT id FROM post WHERE author_id IN (${marks})`,users);
     for(const post of fixturePosts.map(row=>row.id)) {
       const [comments]=await contentDb.execute('SELECT id FROM comment WHERE post_id=?',[post]);
       for(const comment of comments) await contentDb.execute('DELETE FROM comment_like WHERE comment_id=?',[comment.id]);
       for(const table of ['comment','post_like','post_favorite','post_tag']) await contentDb.execute(`DELETE FROM ${table} WHERE post_id=?`,[post]);
       await contentDb.execute('DELETE FROM post WHERE id=?',[post]);
     }
     await cleanupPostReadModels(redis,fixturePosts.map(row=>row.id));
     await contentDb.execute(`DELETE FROM author_daily_stat WHERE author_id IN (${marks})`,users);
   }
   if(growthDb) for(const table of ['exp_record','check_in','growth_account']) await growthDb.execute(`DELETE FROM ${table} WHERE user_id IN (${marks})`,users);
   if(userDb) { await userDb.execute(`DELETE FROM user WHERE id IN (${marks})`,users); }
 }
 if(redis.isOpen) {
   for(const cookie of cookies) { const claims=JSON.parse(Buffer.from(cookie.split('=')[1].split('.')[1],'base64url')); await redis.del('meng:session:'+claims.sessionId); }
   for(const user of users) for(const prefix of ['meng:im:rate:msg:','meng:im:rate:image:','meng:content:limit:','meng:user-sessions:']) { for await(const keys of redis.scanIterator({MATCH:prefix+'*',COUNT:200})) { const own=keys.filter(key=>new RegExp(':'+user+'(:|$)').test(key)); if(own.length) await redis.del(own); } }
   for(const ticket of tickets) await redis.del('meng:captcha-ticket:'+ticket);
   if(priorLoginCount!==undefined) { if(priorLoginCount===null) await redis.del('meng:rate:login:ip:127.0.0.1'); else await redis.set('meng:rate:login:ip:127.0.0.1',priorLoginCount,{EX:60}); }
   await redis.quit();
 }
 for(const connection of [userDb,imDb,contentDb,growthDb]) if(connection) await connection.end();
}
