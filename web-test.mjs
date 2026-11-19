import assert from 'node:assert/strict';
import {Game} from './web/engine.mjs';
const g=new Game(()=>.5);let records=0,deaths=0;
g.onScore=()=>records++;g.onDeath=()=>deaths++;
g.start();g.step(1/120);assert.ok(g.y<270);assert.ok(g.velocity>-350);
g.pipes[0].x=40;g.step(0);g.step(0);assert.equal(g.score,1);assert.equal(records,1);
g.y=560;g.step(.01);assert.equal(g.state,'over');assert.equal(deaths,1);
const offsets=[...g.offsets];g.step(.1);assert.deepEqual(g.offsets,offsets);
g.start();assert.equal(g.score,0);assert.equal(g.best,1);
g.pipes[0].x=115;g.y=20;g.step(0);assert.equal(g.state,'over');
g.start();g.y=-1;g.step(0);assert.equal(g.state,'over');
let previous=0;for(const level of ['easy','normal','hard']){g.level=level;g.start();const speed=g.speed,gravity=g.gravity;assert.ok(speed>previous);previous=speed;g.time=90;assert.equal(g.speed,speed*1.4);assert.equal(g.gravity,gravity*1.25);g.time=180;assert.equal(g.speed,speed*1.4);}
g.start();g.step(.06);assert.equal(g.frame,1);g.step(.06);assert.equal(g.frame,2);
g.reset();g.offsets=[1439.99,1439.99,1439.99];g.step(.1);assert.ok(g.offsets.every(n=>n>=0&&n<10));
g.state='exit';const clock=g.clock;g.step(1);assert.equal(g.clock,clock);
console.log('PASS: browser engine jumping, scoring once, collisions, restart, best, difficulty caps, wing frames, parallax wrap, exit');
