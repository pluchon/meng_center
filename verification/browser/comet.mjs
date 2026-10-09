import {chromium} from 'playwright'
import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
const out=new URL('../test-output/comet/',import.meta.url)
await fs.mkdir(out,{recursive:true})
const browser=await chromium.launch({channel:'msedge',headless:true})
const page=await browser.newPage({viewport:{width:1366,height:768}})
const report={scope:'Cursor only; no complete validation',errors:[],passed:false}
page.on('pageerror',error=>report.errors.push(error.message))
await page.addInitScript(()=>{const proto=CanvasRenderingContext2D.prototype,clear=proto.clearRect,arc=proto.arc,stroke=proto.stroke;proto.clearRect=function(...args){if(this.canvas.classList.contains('glow-cursor'))window.cometSample={arcs:[],strokes:[]};return clear.apply(this,args)};proto.arc=function(x,y,r,...args){if(this.canvas.classList.contains('glow-cursor'))window.cometSample.arcs.push({x,y,r,alpha:this.globalAlpha});return arc.call(this,x,y,r,...args)};proto.stroke=function(...args){if(this.canvas.classList.contains('glow-cursor'))window.cometSample.strokes.push({width:this.lineWidth,alpha:this.globalAlpha});return stroke.apply(this,args)}})
try{
 await page.goto('http://127.0.0.1:5173/reset-password');await page.locator('h1').waitFor();await page.evaluate(()=>document.fonts.ready)
 await page.mouse.move(160,205)
 for(let i=1;i<=30;i++){await page.mouse.move(160+i*10,205+Math.sin(i*.045)*85);await page.waitForTimeout(12)}
 await page.waitForTimeout(15)
 const sample=await page.evaluate(()=>window.cometSample)
 assert.ok(sample.arcs.length>2&&sample.arcs.length<=24,'bounded stardust and head')
 const widths=sample.strokes.map(item=>item.width)
 assert.ok(Math.max(...widths)/Math.min(...widths)>10,'tail layers taper')
 assert.ok(Math.hypot(sample.arcs.at(-1).x-460,sample.arcs.at(-1).y-(205+Math.sin(30*.045)*85))<30,'bright nucleus follows pointer closely')
 await page.screenshot({path:new URL('comet-desktop.png',out).pathname.replace(/^\//,'')})
 await page.waitForTimeout(900)
 const pixels=await page.locator('.glow-cursor').evaluate(canvas=>canvas.getContext('2d').getImageData(0,0,canvas.width,canvas.height).data.some((value,index)=>index%4===3&&value>0))
 assert.equal(pixels,false,'comet and dust fade when stationary')
 await page.getByRole('button',{name:'切换为手机号',exact:true}).click();assert.equal(await page.locator('.contact-region').count(),1)
 await page.emulateMedia({reducedMotion:'reduce'});assert.equal(await page.locator('.glow-cursor').isVisible(),false)
 assert.deepEqual(report.errors,[])
 report.passed=true;report.checks=['bright head, tapered fading layers, bounded dispersing stardust','fast idle disappearance; controls remain clickable','reduced motion disabled; no runtime errors'];report.observedDust=sample.arcs.length-2
}catch(error){report.failure=error.message;throw error}
finally{await fs.writeFile(new URL('report.json',out),JSON.stringify(report,null,2));await browser.close();console.log(JSON.stringify(report))}
