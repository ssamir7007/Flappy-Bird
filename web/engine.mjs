export const LEVELS = {easy:{speed:150,gravity:900},normal:{speed:175,gravity:1050},hard:{speed:205,gravity:1200}};
export class Game {
  constructor(random=Math.random){this.random=random;this.level='normal';this.best=0;this.offsets=[0,0,0];this.ground=0;this.clock=0;this.onScore=()=>{};this.onDeath=()=>{};this.reset();}
  reset(){this.state='menu';this.y=270;this.velocity=0;this.score=0;this.time=0;this.spawn=0;this.pipes=[];this.wing=0;this.tilt=0;this.pulse=0;this.flash=0;this.scorePulse=0;}
  get progress(){return Math.min(this.time/90,1);}
  get speed(){return LEVELS[this.level].speed*(1+.4*this.progress);}
  get gravity(){return LEVELS[this.level].gravity*(1+.25*this.progress);}
  get frame(){return [0,1,2,1][Math.floor(this.wing)];}
  addPipe(){this.pipes.push({x:480,gap:90+Math.floor(this.random()*231),scored:false});}
  start(){this.reset();this.state='playing';this.addPipe();this.flap();}
  flap(){if(this.state!=='playing')return;this.velocity=-350;this.wing=0;this.pulse=.16;}
  die(){if(this.state!=='playing')return;this.state='over';this.flash=.2;this.onDeath();}
  step(dt){
    if(this.state==='exit')return;
    this.clock+=dt;
    if(this.state==='playing'){
      this.time+=dt;this.velocity+=this.gravity*dt;this.y+=this.velocity*dt;this.spawn+=dt;
      if(this.spawn>=1.55){this.spawn-=1.55;this.addPipe();}
      for(const p of this.pipes)p.x-=this.speed*dt;
      if(this.y<=0||this.y+28>=580){this.y=Math.max(0,Math.min(this.y,552));this.die();}
      else for(const p of this.pipes){if(this.hit(p.x,0,70,p.gap)||this.hit(p.x,p.gap+170,70,580-p.gap-170)){this.die();break;}}
      if(this.state==='playing')for(const p of this.pipes){if(!p.scored&&p.x+70<115){p.scored=true;this.score++;this.best=Math.max(this.best,this.score);this.scorePulse=.25;this.onScore();}}
      this.pipes=this.pipes.filter(p=>p.x+70>=0);
    }
    if(this.state!=='over'){
      const rates=[.08,.20,.45];this.offsets=this.offsets.map((v,i)=>(v+this.speed*dt*rates[i])%1440);this.ground=(this.ground+this.speed*dt)%32;
      const duration=this.state==='playing'?(this.velocity<0||this.pulse>0?.055:.14):.22;this.wing=(this.wing+dt/duration)%4;
    }
    const target=this.state==='playing'?Math.max(-.4,Math.min(1.2,this.velocity/550)):0;
    this.tilt+=(target-this.tilt)*(1-Math.exp(-12*dt));this.pulse=Math.max(0,this.pulse-dt);this.flash=Math.max(0,this.flash-dt);this.scorePulse=Math.max(0,this.scorePulse-dt);
  }
  hit(x,y,w,h){const cx=129,cy=this.y+14;const dx=cx-Math.max(x,Math.min(cx,x+w)),dy=cy-Math.max(y,Math.min(cy,y+h));return dx*dx+dy*dy<14*14;}
}
