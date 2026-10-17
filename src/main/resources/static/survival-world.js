import * as THREE from './vendor/three.module.min.js';

// Original, articulated scene assets for the selected forest-survival art direction.
// No network models or shared disposable materials are used by this scene.
const clamp = THREE.MathUtils.clamp;
export const blend = (start, end, value) => { const t = clamp((value - start) / (end - start), 0, 1); return t * t * (3 - 2 * t); };
const cloth = (color, extra = {}) => new THREE.MeshStandardMaterial({ color, roughness: .91, metalness: 0, ...extra });
function mesh(parent, geometry, material, position = [0, 0, 0]) {
  const object = new THREE.Mesh(geometry, material);
  object.position.set(...position); object.castShadow = true; object.receiveShadow = true; parent.add(object); return object;
}
const box = (parent, size, material, at) => mesh(parent, new THREE.BoxGeometry(...size), material, at);
const sphere = (parent, radius, material, at, detail = 16) => mesh(parent, new THREE.SphereGeometry(radius, detail, 12), material, at);
const capsule = (parent, radius, length, material, at) => mesh(parent, new THREE.CapsuleGeometry(radius, Math.max(.01, length - 2 * radius), 5, 12), material, at);
const REGIONS = { A: [0, 0], B: [-5.8, -4.4], C: [6.2, -4.5], D: [6, 4.2], E: [-5.8, 4.1] };
const PALETTE = [0x697d65, 0x9b805e, 0x687b80, 0x867c65, 0x5b7166, 0x858c73, 0x817968, 0x6c767b];

function seeded(seed = 2781) { return () => { seed = seed * 16807 % 2147483647; return seed / 2147483647; }; }
function terrainTexture(random) {
  const canvas = document.createElement('canvas'); canvas.width = canvas.height = 1024;
  const ctx = canvas.getContext('2d'); ctx.fillStyle = '#64745c'; ctx.fillRect(0, 0, 1024, 1024);
  for (let i = 0; i < 8500; i++) {
    ctx.fillStyle = i % 3 ? 'rgba(30,43,30,.035)' : 'rgba(212,203,174,.045)';
    ctx.beginPath(); ctx.ellipse(random() * 1024, random() * 1024, .7 + random() * 4, .5 + random() * 2, random() * 6, 0, Math.PI * 2); ctx.fill();
  }
  const point = (x, z) => [(x / 25 + .5) * 1024, (z / 25 + .5) * 1024];
  Object.values(REGIONS).slice(1).forEach(([x, z]) => {
    ctx.beginPath(); ctx.moveTo(...point(-.3, .3)); ctx.quadraticCurveTo(...point(x * .5, z * .25), ...point(x, z));
    ctx.lineCap = 'round'; ctx.lineWidth = 31; ctx.strokeStyle = '#99977b'; ctx.stroke();
    ctx.lineWidth = 19; ctx.strokeStyle = '#b1a58a'; ctx.stroke();
  });
  const texture = new THREE.CanvasTexture(canvas); texture.colorSpace = THREE.SRGBColorSpace;
  texture.wrapS = texture.wrapT = THREE.RepeatWrapping; texture.anisotropy = 4; return texture;
}

function pine(parent, x, z, scale, random, foliage) {
  const tree = new THREE.Group(); tree.position.set(x, 0, z); parent.add(tree);
  mesh(tree, new THREE.CylinderGeometry(.075 * scale, .16 * scale, 2.3 * scale, 9), cloth(0x655b45), [0, 1.1 * scale, 0]);
  const leaves = new THREE.InstancedMesh(new THREE.IcosahedronGeometry(1, 1), cloth(0x536b4f), 49);
  const dummy = new THREE.Object3D(); let index = 0;
  for (let level = 0; level < 7; level++) for (let branch = 0; branch < 7; branch++) {
    const angle = branch * Math.PI * 2 / 7 + level * .81;
    const radius = (1.04 - level * .126) * scale * (.85 + random() * .3);
    dummy.position.set(Math.cos(angle) * radius * .65, (1.2 + level * .34 + random() * .23) * scale, Math.sin(angle) * radius * .65);
    dummy.rotation.set(random() * .18, angle, -.1 + random() * .15);
    dummy.scale.set(radius * .71, (.13 + random() * .11) * scale, radius * .36); dummy.updateMatrix();
    leaves.setMatrixAt(index, dummy.matrix);
    leaves.setColorAt(index++, new THREE.Color().setHSL(.27 + random() * .045, .16 + random() * .11, .29 + random() * .12));
  }
  leaves.castShadow = leaves.receiveShadow = true; tree.add(leaves); foliage.push({ tree, phase: random() * 8 });
}

function birch(parent, x, z, scale, random, foliage) {
  const tree = new THREE.Group(); tree.position.set(x, 0, z); parent.add(tree);
  mesh(tree, new THREE.CylinderGeometry(.055 * scale, .1 * scale, 2.4 * scale, 9), cloth(0xb5b7a3), [0, 1.2 * scale, 0]);
  for (let i = 0; i < 8; i++) {
    const angle = i * 2.4, y = (1.7 + (i % 3) * .31) * scale;
    const crown = mesh(tree, new THREE.IcosahedronGeometry(.5 * scale, 2), cloth([0x8a986c, 0x73895d, 0x92a174][i % 3]), [Math.cos(angle) * .38 * scale, y, Math.sin(angle) * .38 * scale]);
    crown.scale.set(.9, .65 + random() * .3, 1);
    box(tree, [.12 * scale, .025, .011], cloth(0x626957), [0, (.3 + i * .23) * scale, .08 * scale]);
  }
  foliage.push({ tree, phase: random() * 8 });
}

function tent(parent, x, z, rotation = 0) {
  const group = new THREE.Group(); group.position.set(x, .035, z); group.rotation.y = rotation; parent.add(group);
  const positions = [-.9,0,-.7,0,1.1,-.7,0,1.1,.7,-.9,0,-.7,0,1.1,.7,-.9,0,.7,.9,0,-.7,0,1.1,.7,0,1.1,-.7,.9,0,-.7,.9,0,.7,0,1.1,.7];
  const geometry = new THREE.BufferGeometry(); geometry.setAttribute('position', new THREE.Float32BufferAttribute(positions, 3)); geometry.computeVertexNormals();
  mesh(group, geometry, cloth(0x8d886e, { side: THREE.DoubleSide }), [0, 0, 0]);
  box(group, [1.6, .055, 1.35], cloth(0x4d604d), [0, .04, 0]);
  const opening = mesh(group, new THREE.ConeGeometry(.42, .8, 3), cloth(0x303d32), [0, .3, .712]); opening.rotation.z = Math.PI; opening.scale.z = .01;
  for (const side of [-1, 1]) {
    const rope = new THREE.BufferGeometry().setFromPoints([new THREE.Vector3(0, 1.1, side * .7), new THREE.Vector3(.1, .02, side * 1.35)]);
    group.add(new THREE.Line(rope, new THREE.LineBasicMaterial({ color: 0xc1b99b })));
  }
}

function crate(parent, x, z, scale = 1) {
  const group = new THREE.Group(); group.position.set(x, .3 * scale, z); group.scale.setScalar(scale); group.rotation.y = .12; parent.add(group);
  const wood = cloth(0x8f7c5e); box(group, [.85, .6, .6], wood, [0, 0, 0]);
  for (const y of [-.2, 0, .2]) box(group, [.86, .012, .012], cloth(0x645d48), [0, y, .305]);
  for (const x of [-.3, .3]) box(group, [.055, .63, .63], cloth(0x595a47), [x, 0, 0]);
}

function limb(parent, x, y, length, width, upperMaterial, lowerMaterial, handMaterial, leg = false) {
  const joint = new THREE.Group(); joint.position.set(x, y, 0); parent.add(joint);
  capsule(joint, width, length, upperMaterial, [0, -length / 2, 0]);
  const bend = new THREE.Group(); bend.position.y = -length; joint.add(bend);
  capsule(bend, width * .86, length * .95, lowerMaterial, [0, -length * .475, 0]);
  const end = capsule(bend, width * .85, width * 2.3, handMaterial, [0, -length * .98, leg ? .055 : .012]);
  if (leg) { end.rotation.x = Math.PI / 2; end.scale.set(1.2, 1.5, .75); }
  return { joint, bend, end };
}

export function createSurvivor(parent, player) {
  const root = new THREE.Group(); parent.add(root);
  const hipHeight = .94, hips = new THREE.Group(); hips.position.y = hipHeight; root.add(hips);
  const torso = new THREE.Group(); hips.add(torso);
  const color = PALETTE[Math.max(0, Number(player.seatNo || 1) - 1) % PALETTE.length];
  const jacket = cloth(color), trousers = cloth(0x495342), boots = cloth(0x3e453b), skin = cloth(0xd5b69a, { roughness: .8 });
  const chest = capsule(torso, .207, .58, jacket, [0, .3, 0]); chest.scale.set(1.2, 1, .75);
  box(hips, [.38, .14, .25], trousers, [0, 0, 0]);
  const head = new THREE.Group(); head.position.y = .85; torso.add(head);
  const skull = sphere(head, .175, skin, [0, 0, 0], 22); skull.scale.set(.87, 1.13, .85);
  const hair = sphere(head, .178, cloth(0x514b3c), [0, .07, -.03]); hair.scale.set(.88, .58, .95);
  const capMaterial = cloth(Number(player.seatNo) % 2 ? 0x6f7e62 : 0x9a8c71);
  mesh(head, new THREE.SphereGeometry(.185, 20, 10, 0, Math.PI * 2, 0, Math.PI / 2), capMaterial, [0, .03, 0]);
  box(head, [.29, .028, .14], capMaterial, [0, .052, .15]);
  for (const sign of [-1, 1]) {
    sphere(head, .011, cloth(0x343d35), [sign * .059, .007, .143], 8);
    sphere(head, .032, skin, [sign * .152, -.005, 0], 10);
  }
  const nose = sphere(head, .025, skin, [0, -.022, .162], 10); nose.scale.y = .75;
  const arms = [-1, 1].map(sign => limb(torso, sign * .255, .53, .3, .071, jacket, jacket, skin));
  const legs = [-1, 1].map(sign => limb(hips, sign * .12, 0, .43, .084, trousers, trousers, boots, true));
  arms.forEach((arm, index) => {
    capsule(arm.joint, .075, .13, jacket, [0, -.04, 0]);
    box(torso, [.043, .42, .025], cloth(0x46513f), [(index ? 1 : -1) * .15, .32, .16]);
  });
  box(torso, [.34, .41, .17], cloth(0x657357), [0, .32, -.21]);
  box(torso, [.22, .14, .06], cloth(0x56654b), [0, .22, -.31]);
  for (const x of [-.11, .11]) box(torso, [.11, .12, .025], cloth(color).clone(), [x, .23, .162]);
  box(torso, [.075, .11, .018], cloth(0xd6c9a7), [-.1, .43, .167]);
  const equipment = new THREE.Group(); torso.add(equipment);
  if (player.armed || player.weaponName) {
    box(equipment, [.08, .1, .48], cloth(0x344239, { metalness: .15 }), [.29, .27, .17]);
    box(equipment, [.04, .05, .25], cloth(0x28372e), [.29, .28, .48]);
  }
  return { root, hips, torso, head, arms, legs, hipHeight, player };
}

export function resetPose(actor) {
  actor.hips.position.y = actor.hipHeight; actor.torso.rotation.set(0, 0, 0); actor.head.rotation.set(0, 0, 0);
  actor.root.rotation.z = 0;
  actor.arms.forEach((arm, i) => { arm.joint.rotation.set(0, 0, (i ? 1 : -1) * .075); arm.bend.rotation.set(-.12, 0, 0); });
  actor.legs.forEach(leg => { leg.joint.rotation.set(0, 0, 0); leg.bend.rotation.set(.025, 0, 0); });
}

export function crouch(actor, amount, time = 0, reach = false) {
  actor.hips.position.y -= amount * .32; actor.torso.rotation.x = amount * .36;
  actor.head.rotation.x = -amount * .13;
  actor.legs.forEach(leg => { leg.joint.rotation.x = -amount * .76; leg.bend.rotation.x = amount * 1.42; });
  actor.arms.forEach((arm, i) => {
    arm.joint.rotation.x = -amount * (reach ? .95 : .35); arm.bend.rotation.x = -amount * .5 - .15;
    if (reach) arm.bend.rotation.x += Math.sin(time * 2.4 + i) * .035 * amount;
  });
}

export function makeForest(scene, players) {
  const root = new THREE.Group(); scene.add(root); const foliage = [], random = seeded();
  const texture = terrainTexture(random);
  const ground = mesh(root, new THREE.PlaneGeometry(70, 70, 1, 1), cloth(0xffffff, { map: texture }), [0, -.015, 0]); ground.rotation.x = -Math.PI / 2;
  texture.repeat.set(70 / 25, 70 / 25); texture.offset.set(-.9, -.9);
  // No foreground trees in the camera's principal action corridor.
  for (let i = 0; i < 34; i++) {
    const x = (i % 17 - 8) * 1.5, z = -6 - Math.floor(i / 17) * 3 - random() * 2;
    (i % 5 ? pine : birch)(root, x, z, 1.05 + random() * .4, random, foliage);
  }
  [[-9,-2],[-9,1],[9,-1],[10,3],[9,5]].forEach(([x,z],i) => (i % 2 ? pine : birch)(root, x, z, 1.2, random, foliage));
  tent(root, 3.2, -2.3, .15); tent(root, -6, 4.2, -.2);
  crate(root, 2.8, .25, .85); crate(root, 3.6, -.1, .65);
  crate(root, 6.1, -4.5); crate(root, 6.9, -4.1, .75);
  const rockGeometry = new THREE.DodecahedronGeometry(1, 1), rockMaterial = cloth(0x8a8c78);
  const rocks = new THREE.InstancedMesh(rockGeometry, rockMaterial, 44), dummy = new THREE.Object3D();
  for (let i = 0; i < 44; i++) {
    const angle = i * 2.4, radius = 4 + random() * 7;
    dummy.position.set(Math.sin(angle) * radius, .07, Math.cos(angle) * radius);
    dummy.scale.set(.2 + random() * .5, .12 + random() * .2, .2 + random() * .4); dummy.rotation.y = angle; dummy.updateMatrix(); rocks.setMatrixAt(i, dummy.matrix);
  }
  rocks.castShadow = rocks.receiveShadow = true; root.add(rocks);
  const blades = new THREE.InstancedMesh(new THREE.ConeGeometry(.035, .3, 3), cloth(0x899473), 400);
  for (let i = 0; i < 400; i++) {
    const angle = i * 2.4, r = 3.4 + random() * 10;
    dummy.position.set(Math.sin(angle) * r, .13, Math.cos(angle) * r); dummy.scale.setScalar(.6 + random() * .8);
    dummy.rotation.set(0, angle, (random() - .5) * .35); dummy.updateMatrix(); blades.setMatrixAt(i, dummy.matrix);
  }
  blades.castShadow = false; root.add(blades);
  const actors = players.map(player => {
    const actor = createSurvivor(root, player); const [x, z] = REGIONS[player.region] || REGIONS.A;
    actor.root.position.set(x, 0, z); actor.root.rotation.y = -.4;
    return actor;
  });
  const kit = new THREE.Group(); root.add(kit);
  box(kit, [.28, .15, .2], cloth(0xdad1b8), [0, 0, 0]);
  const cross = cloth(0x709379); box(kit, [.12, .014, .03], cross, [0, .081, 0]); box(kit, [.03, .014, .12], cross, [0, .081, 0]); kit.visible = false;
  const ring = mesh(root, new THREE.RingGeometry(.68, .71, 64), new THREE.MeshBasicMaterial({ color: 0xa6bea1, transparent: true, opacity: 0, depthWrite: false, side: THREE.DoubleSide }), [1.15, .018, 0]); ring.rotation.x = -Math.PI / 2;
  return { root, actors, foliage, kit, ring };
}

export function poseForest(world, chapter, seconds, reducedMotion = false) {
  const primary = world.actors[0], target = world.actors[1]; if (!primary || !target) return;
  const t = clamp(seconds, 0, 20), actionTime = reducedMotion ? (t < 6 ? 0 : t < 13 ? 10 : 19) : t;
  world.actors.forEach((actor, i) => {
    resetPose(actor); actor.root.position.y = 0;
    if (!reducedMotion) { actor.torso.rotation.z = Math.sin(actionTime * 1.5 + i) * .006; actor.head.rotation.y = Math.sin(actionTime * .6 + i) * .07; }
  });
  const move = blend(.6, 5, actionTime);
  primary.root.position.set(THREE.MathUtils.lerp(-2.6, 1.02, move), 0, -.86);
  primary.root.rotation.y = THREE.MathUtils.lerp(Math.PI / 2, 0, blend(4.1, 5.3, actionTime));
  target.root.position.set(.42, .13, .02); target.root.rotation.set(0, 0, -Math.PI / 2);
  if (actionTime > .6 && actionTime < 5 && !reducedMotion) {
    const stride = Math.sin(actionTime * 8.2) * Math.sin(move * Math.PI) * .52;
    primary.legs.forEach((leg, i) => { const angle = stride * (i ? 1 : -1); leg.joint.rotation.x = angle; leg.bend.rotation.x = Math.max(0, -angle) * .9; });
    primary.arms.forEach((arm, i) => { arm.joint.rotation.x = stride * (i ? -1 : 1) * .65; });
    primary.hips.position.y += Math.abs(Math.sin(actionTime * 8.2)) * .02;
  }
  const work = blend(6, 7.4, actionTime) * (1 - blend(13.4, 15.3, actionTime));
  world.kit.visible = false; world.ring.material.opacity = 0;
  if (chapter === 0) {
    crouch(primary, work, actionTime, true);
    const recover = blend(13.2, 16.8, actionTime), effort = Math.sin(recover * Math.PI);
    target.root.rotation.z = -Math.PI / 2 * (1 - recover); target.root.position.y = .13 * (1 - recover);
    target.root.rotation.y = -.65 * recover; target.hips.position.y -= effort * .32;
    target.torso.rotation.x = effort * .52;
    target.legs.forEach(leg => { leg.joint.rotation.x = -effort * .8; leg.bend.rotation.x = effort * 1.4; });
    target.arms.forEach(arm => { arm.joint.rotation.x = -effort * .7; arm.bend.rotation.x = -effort * .35; });
    world.kit.visible = actionTime > 6.5 && actionTime < 13.6;
    world.ring.material.opacity = work * .2;
  } else if (chapter === 1) {
    // Without a medkit the model guards. The downed target never stands up.
    primary.arms.forEach((arm, i) => { arm.joint.rotation.x = -blend(6, 8, actionTime) * .65; arm.bend.rotation.x = -blend(6, 8, actionTime) * (i ? 1.1 : .9); });
    primary.torso.rotation.y = -.22 * blend(6, 8, actionTime);
  } else {
    target.root.rotation.set(0, -.7, 0); target.root.position.set(.42, 0, .15);
    crouch(primary, work * .85, actionTime, false);
    const handover = blend(6, 8, actionTime) * (1 - blend(14, 16, actionTime));
    primary.arms[1].joint.rotation.x = -handover * 1.1; primary.arms[1].bend.rotation.x = -handover * .4;
    target.arms[0].joint.rotation.x = -handover * .8;
    world.kit.visible = actionTime > 7 && actionTime < 16;
    world.kit.position.set(1.1 - blend(12, 15, actionTime) * .55, .69, -.1);
  }
  if (chapter !== 2) world.kit.position.set(1.05, .24, -.18);
  if (!reducedMotion) world.foliage.forEach(({ tree, phase }) => { tree.rotation.z = Math.sin(actionTime * .65 + phase) * .006; });
}
