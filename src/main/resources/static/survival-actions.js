import * as THREE from './vendor/three.module.min.js';
import { resetPose, crouch, blend } from './survival-world.js';

// Real match poses use authoritative player status and the latest resolved event.
// Unlike the timed showcase, no action here changes another player's health/status.
export function poseSurvivor(actor, { action = 'IDLE', time = 0, duration = 2.6, elapsed = 0, reducedMotion = false, recovery = null } = {}) {
  resetPose(actor);
  actor.root.position.y = 0;
  actor.root.rotation.x = 0;
  actor.equipment.visible = Boolean(actor.player.armed || actor.player.weaponName);
  const status = actor.player.status;
  if (status !== 'ALIVE') {
    actor.root.rotation.z = -Math.PI / 2;
    actor.root.position.y = .18;
    actor.legs[0].joint.rotation.x = -.35;
    actor.legs[0].bend.rotation.x = .65;
    actor.arms.forEach(arm => { arm.joint.rotation.x = -.4; arm.bend.rotation.x = -.35; });
    if (status === 'DOWNED') actor.head.rotation.y = reducedMotion ? .12 : Math.sin(elapsed * .8) * .09;
    actor.root.visible = true;
    return;
  }
  actor.root.visible = true;
  if (recovery != null && recovery < 1 && !reducedMotion) {
    const rise = blend(0, 1, recovery), effort = Math.sin(rise * Math.PI);
    actor.root.rotation.z = -Math.PI / 2 * (1 - rise);
    actor.root.position.y = .18 * (1 - rise);
    crouch(actor, effort, elapsed, true);
    return;
  }
  const t = reducedMotion ? .8 : Math.max(0, time);
  const strength = reducedMotion ? 1 : blend(0, .32, t) * (1 - blend(Math.max(.4, duration - .5), duration, t));
  if (!reducedMotion) {
    actor.torso.rotation.z = Math.sin(elapsed * 1.5 + actor.player.seatNo) * .006;
    actor.head.rotation.y = Math.sin(elapsed * .6 + actor.player.seatNo) * .06;
  }
  if (action === 'MOVE') {
    const stride = reducedMotion ? .24 : Math.sin(t * 8.2) * .5 * strength;
    actor.legs.forEach((leg, i) => { const angle = stride * (i ? 1 : -1); leg.joint.rotation.x = angle; leg.bend.rotation.x = Math.max(0, -angle) * .9; });
    actor.arms.forEach((arm, i) => { arm.joint.rotation.x = stride * (i ? -1 : 1) * .65; });
    if (!reducedMotion) actor.hips.position.y += Math.abs(Math.sin(t * 8.2)) * .02;
  } else if (['REVIVE', 'HEAL', 'SEARCH', 'CONTRIBUTE', 'CAPTURE'].includes(action)) {
    crouch(actor, strength * (action === 'HEAL' ? .65 : 1), t, true);
    actor.head.rotation.x += strength * .14;
    actor.arms[1].joint.rotation.x -= Math.sin(t * 3) * .08 * strength;
  } else if (['ATTACK', 'EXECUTE'].includes(action)) {
    const armed = actor.equipment.visible;
    actor.arms.forEach((arm, i) => {
      arm.joint.rotation.x = -strength * (armed ? .95 : i ? 1.2 : .4);
      arm.bend.rotation.x = -strength * (i ? .8 : 1.1);
    });
    actor.torso.rotation.y = -strength * .15;
    actor.torso.rotation.x = strength * (armed ? -.035 * Math.sin(t * 22) : .13 * Math.sin(t * 9));
  } else if (action === 'HIDE' || actor.player.hidden) {
    crouch(actor, 1, t);
    actor.head.rotation.y = -.2;
  } else if (['REST', 'GUARD'].includes(action)) {
    crouch(actor, strength * (action === 'REST' ? .9 : .35), t);
    actor.arms.forEach(arm => { arm.joint.rotation.x = -strength * .5; arm.bend.rotation.x = -strength * 1.1; });
  } else if (action === 'HIT') {
    actor.torso.rotation.x = -.24 * strength;
    actor.head.rotation.x = .15 * strength;
  } else if (['TRADE', 'INFO_TRADE', 'RUMOR', 'TALK'].includes(action)) {
    actor.arms[1].joint.rotation.x = -.9 * strength;
    actor.arms[1].bend.rotation.x = -.55 * strength;
    actor.head.rotation.y = -.12 * strength;
  }
}
