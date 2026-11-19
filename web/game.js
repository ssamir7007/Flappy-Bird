import {Game,LEVELS} from './engine.mjs';
const $=id=>document.getElementById(id), canvas=$('game'), ctx=canvas.getContext('2d');
const game=new Game(), images={}, sounds={};let loaded=false,muted=true,storedBest=0,last=0,accumulator=0;
const key='flappy-bird-best-v1';
try{const raw=localStorage.getItem(key);const n=Number(raw);if(Number.isSafeInteger(n)&&n>=0)game.best=storedBest=n;}catch{$('notice').textContent='Best score is available for this session only.';}
for(const name of ['jump','score','collision']){sounds[name]=new Audio(`assets/${name}.wav`);sounds[name].preload='auto';sounds[name].volume=.45;}
function sound(name){if(muted)return;const a=sounds[name];a.currentTime=0;a.play().catch(()=>{});}
function setSound(){muted=!muted;$('sound').textContent=muted?'Sound off':'Sound on';$('sound').setAttribute('aria-label',muted?'Unmute sound':'Mute sound');$('sound').setAttribute('aria-pressed',String(muted));if(muted)Object.values(sounds).forEach(a=>a.pause());}
function panel(id){$('overlay').hidden=!id;for(const n of ['menu','help','over','goodbye'])$(n).hidden=n!==id;}
function menu(){game.reset();panel('menu');$('play').focus();}
function start(){if(!loaded)return;game.start();accumulator=0;panel(null);canvas.focus();sound('jump');}
function flap(){if(game.state==='playing'){game.flap();sound('jump');}}
function select(level){if(game.state!=='menu'||!LEVELS[level])return;game.level=level;document.querySelectorAll('[data-level]').forEach(b=>b.setAttribute('aria-pressed',String(b.dataset.level===level)));}
game.onScore=()=>{sound('score');if(game.best>storedBest){try{localStorage.setItem(key,String(game.best));storedBest=game.best;}catch{$('notice').textContent='Could not save your best score in this browser.';}}};
game.onDeath=()=>{sound('collision');$('final-score').textContent=game.score;$('final-best').textContent=game.best;panel('over');$('retry').focus();};
$('play').onclick=start;$('retry').onclick=start;$('home').onclick=menu;$('back').onclick=menu;$('return').onclick=menu;$('menu-button').onclick=menu;$('sound').onclick=setSound;
$('instructions').onclick=()=>{game.state='help';panel('help');$('back').focus();};
$('exit').onclick=()=>{game.state='exit';Object.values(sounds).forEach(a=>a.pause());panel('goodbye');$('return').focus();};
document.querySelectorAll('[data-level]').forEach(b=>b.onclick=()=>select(b.dataset.level));
canvas.addEventListener('pointerdown',e=>{e.preventDefault();flap();});
document.addEventListener('keydown',e=>{
  if(e.repeat)return;
  if(e.code==='Escape'){e.preventDefault();menu();}
  else if(e.code==='KeyM')setSound();
  else if(e.code==='KeyR'&&['playing','over'].includes(game.state))start();
  else if(['Digit1','Digit2','Digit3'].includes(e.code))select(['easy','normal','hard'][Number(e.code.slice(-1))-1]);
  else if(['Space','ArrowUp'].includes(e.code)&&game.state==='playing'){e.preventDefault();flap();}
});
document.addEventListener('visibilitychange',()=>{last=0;accumulator=0;});
function pipe(p,top){ctx.save();ctx.translate(p.x,top?p.gap:p.gap+170);if(top)ctx.scale(1,-1);const h=top?p.gap:580-p.gap-170,im=images.pipe,cut=Math.floor(im.height/9);ctx.drawImage(im,0,0,im.width,cut,0,0,70,28);ctx.drawImage(im,0,cut,im.width,im.height-cut,0,28,70,h-28);ctx.restore();}
function draw(){
  const sky=ctx.createLinearGradient(0,0,0,580);sky.addColorStop(0,'#1ea5f5');sky.addColorStop(1,'#9bebf0');ctx.fillStyle=sky;ctx.fillRect(0,0,480,640);
  if(loaded){for(const [layer,name] of ['clouds','mountains','hills'].entries()){const y=[80,280,390][layer],h=[300,300,190][layer];for(let i=0;i*720-game.offsets[layer]<480;i++){const x=i*720-game.offsets[layer];if(x+720<=0)continue;ctx.save();ctx.translate(x,y);if(i%2){ctx.translate(720,0);ctx.scale(-1,1);}ctx.drawImage(images[name],0,0,720,h);ctx.restore();}}
    for(const p of game.pipes){pipe(p,true);pipe(p,false);}
    ctx.save();ctx.translate(129,game.y+14+(game.state==='menu'?Math.sin(game.clock*4)*6:0));ctx.rotate(game.tilt);const s=Math.sin(game.pulse/.16*Math.PI)*.1;ctx.scale(1+s,1-s);ctx.drawImage(images[`bird-${game.frame}`],-19,-19,38,38);ctx.restore();}
  ctx.fillStyle='#69b44b';ctx.fillRect(0,580,480,12);ctx.fillStyle='#e1ce91';ctx.fillRect(0,592,480,48);ctx.fillStyle='#beaa6e';for(let x=-32;x<512;x+=32)ctx.fillRect(x-game.ground,603,18,4);
  if(game.state==='playing'||game.state==='over'){ctx.textAlign='center';ctx.font=`900 ${42+game.scorePulse*32}px system-ui`;ctx.lineWidth=5;ctx.strokeStyle='#ffffffbb';ctx.strokeText(game.score,240,65);ctx.fillStyle='#19414b';ctx.fillText(game.score,240,65);}
  if(game.flash>0){ctx.fillStyle=`rgba(255,255,255,${game.flash/.2*.5})`;ctx.fillRect(0,0,480,640);}
  $('best').textContent=`BEST ${game.best}`;$('mode').textContent=game.level.toUpperCase();$('pace').textContent=`SPEED +${Math.floor(40*game.progress)}% · GRAVITY +${Math.floor(25*game.progress)}%`;
}
function loop(now){if(!document.hidden){if(last)accumulator+=Math.min((now-last)/1000,.1);while(accumulator>=1/120){game.step(1/120);accumulator-=1/120;}draw();}last=now;requestAnimationFrame(loop);}
Promise.all(['clouds','mountains','hills','pipe','bird-0','bird-1','bird-2'].map(name=>new Promise((resolve,reject)=>{const im=new Image();im.onload=()=>{images[name]=im;resolve();};im.onerror=()=>reject(new Error(name));im.src=`assets/${name}.png`;}))).then(()=>{loaded=true;$('play').disabled=false;$('play').textContent='Play';}).catch(()=>{$('play').textContent='Could not load artwork';$('notice').textContent='Please reload the page to try again.';});
requestAnimationFrame(loop);
