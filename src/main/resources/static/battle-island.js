import * as THREE from './vendor/three.module.min.js';
import { GLTFLoader } from './vendor/addons/loaders/GLTFLoader.js';

const loader = new GLTFLoader();
const models = new Map();
const clearings = [[0,0],[-6.2,-4.3],[6.2,-4.3],[6.2,4.3],[-6.2,4.3]];
const smooth = value => { const t = THREE.MathUtils.clamp(value, 0, 1); return t * t * (3 - 2 * t); };
function elevation(x, z) {
  const hills = [[-4.8,-7,1.65,2.6],[8.8,0.2,1.25,2],[-9,2,0.9,2]];
  let height = hills.reduce((sum,[hx,hz,h,r]) => sum + h * Math.exp(-((x-hx)**2+(z-hz)**2)/(r*r)), 0);
  clearings.forEach(([cx,cz]) => { height *= smooth((Math.hypot(x-cx,z-cz)-1.8)/1.5); });
  return 0.355 + height;
}

function prop(group, name, x, z, height, rotation = 0, y = null) {
  group.userData.pending ||= [];
  if (!models.has(name)) models.set(name, loader.loadAsync(`./assets/3d/kenney/environment/${name}.glb`).catch(error => { models.delete(name); throw error; }));
  const promise = models.get(name).then(gltf => {
    if (group.userData.active === false) return;
    const model = gltf.scene.clone(true);
    const box = new THREE.Box3().setFromObject(model), size = box.getSize(new THREE.Vector3());
    const scale = Math.min(height / Math.max(size.y, 0.01), (name === 'rock-flat-grass' ? 2.4 : 3) / Math.max(size.x, size.z, 0.01));
    model.scale.setScalar(scale);
    model.position.set(x, (y ?? elevation(x + group.position.x, z + group.position.z)) - box.min.y * scale, z);
    model.rotation.y = rotation;
    model.traverse(item => {
      if (!item.isMesh) return;
      item.castShadow = true; item.receiveShadow = true; item.userData.externalAsset = true;
      item.material = item.material.clone(); item.material.roughness = 0.88;
    });
    group.add(model);
  });
  group.userData.pending.push(promise);
}

function terrain() {
  const vertices = [], uvs = [], indices = [];
  const sectors = 96, rings = 26;
  for (let r = 0; r <= rings; r++) {
    for (let s = 0; s <= sectors; s++) {
      const a = s * Math.PI * 2 / sectors, edge = 1 + Math.sin(a*3+0.7)*0.065 + Math.cos(a*5)*0.035;
      const x = Math.cos(a)*11.6*edge*r/rings, z = -Math.sin(a)*8.6*edge*r/rings;
      vertices.push(x,elevation(x,z),z); uvs.push(x,-z);
      if (r < rings && s < sectors) { const i = r*(sectors+1)+s; indices.push(i,i+sectors+1,i+1,i+1,i+sectors+1,i+sectors+2); }
    }
  }
  const geometry = new THREE.BufferGeometry();
  geometry.setAttribute('position',new THREE.Float32BufferAttribute(vertices,3));
  geometry.setAttribute('uv',new THREE.Float32BufferAttribute(uvs,2));
  geometry.setIndex(indices); geometry.computeVertexNormals();
  return geometry;
}

function regionSign(group, region) {
  const names = {A:'中央村落',B:'北部林地',C:'河畔仓库',D:'南部废墟',E:'野外营地'};
  const canvas = document.createElement('canvas'); canvas.width=384;canvas.height=80;
  const ctx=canvas.getContext('2d');
  ctx.fillStyle='rgba(12,29,32,.88)';ctx.beginPath();ctx.roundRect(0,0,384,80,12);ctx.fill();
  ctx.fillStyle='#b8c9a4';ctx.font='600 32px sans-serif';ctx.textAlign='center';ctx.fillText(`${region} / ${names[region]}`,192,52);
  const texture=new THREE.CanvasTexture(canvas);texture.colorSpace=THREE.SRGBColorSpace;
  const sign=new THREE.Sprite(new THREE.SpriteMaterial({map:texture,depthTest:false,transparent:true,opacity:0.85}));
  sign.position.set(0,0.65,2.3);sign.scale.set(2.8,0.58,1);group.add(sign);
  (group.userData.textures ||= []).push(texture);
}

const surface = (color, roughness = 0.95) => new THREE.MeshStandardMaterial({ color, roughness, metalness: 0 });
function object(group, geometry, color, x, y, z, rotation = 0) {
  const item = new THREE.Mesh(geometry, surface(color));
  item.position.set(x, y, z);
  item.rotation.y = rotation;
  item.castShadow = true;
  item.receiveShadow = true;
  group.add(item);
  return item;
}

function outline(scale = 1) {
  const points = [];
  for (let i = 0; i < 28; i++) {
    const a = i * Math.PI * 2 / 28;
    const edge = 1 + Math.sin(a * 3 + 0.7) * 0.065 + Math.cos(a * 5) * 0.035;
    points.push(new THREE.Vector2(Math.cos(a) * 11.6 * edge * scale, Math.sin(a) * 8.6 * edge * scale));
  }
  const curve = new THREE.CatmullRomCurve3(points.map(point => new THREE.Vector3(point.x, point.y, 0)), true);
  const shape = new THREE.Shape(curve.getPoints(112).map(point => new THREE.Vector2(point.x, point.y)));
  shape.closePath();
  return shape;
}

function groundMap() {
  const canvas = document.createElement('canvas');
  canvas.width = canvas.height = 1024;
  const ctx = canvas.getContext('2d');
  ctx.fillStyle = '#608451';
  ctx.fillRect(0, 0, 1024, 1024);
  let seed = 7381;
  const random = () => ((seed = (seed * 16807) % 2147483647) / 2147483647);
  for (let i = 0; i < 1600; i++) {
    ctx.fillStyle = i % 2 ? 'rgba(182,195,129,.09)' : 'rgba(38,85,61,.08)';
    ctx.beginPath(); ctx.ellipse(random() * 1024, random() * 1024, 10 + random() * 65, 5 + random() * 40, random() * 6, 0, Math.PI * 2); ctx.fill();
  }
  const point = (x, z) => [(x / 26 + 0.5) * 1024, (-z / 26 + 0.5) * 1024];
  const road = (x, z) => {
    const start = point(0, 0), end = point(x, z);
    ctx.beginPath(); ctx.moveTo(...start); ctx.quadraticCurveTo(...point(x * 0.55, z * 0.28), ...end);
    ctx.lineCap = 'round'; ctx.lineWidth = 38; ctx.strokeStyle = '#98a17b'; ctx.stroke();
    ctx.lineWidth = 23; ctx.strokeStyle = '#c3b58d'; ctx.stroke();
    ctx.lineWidth = 2; ctx.strokeStyle = 'rgba(243,231,193,.45)'; ctx.stroke();
  };
  [[-6.2, -4.3], [6.2, -4.3], [6.2, 4.3], [-6.2, 4.3]].forEach(([x,z]) => road(x,z));
  ctx.fillStyle = '#c5bd9b'; ctx.beginPath(); ctx.ellipse(...point(0, 0), 75, 62, 0, 0, Math.PI * 2); ctx.fill();
  const texture = new THREE.CanvasTexture(canvas);
  texture.colorSpace = THREE.SRGBColorSpace;
  texture.wrapS = texture.wrapT = THREE.ClampToEdgeWrapping;
  texture.repeat.set(1 / 26, 1 / 26);
  texture.offset.set(0.5, 0.5);
  return texture;
}

function tree(group, x, z, size = 1, pine = false) {
  object(group, new THREE.CylinderGeometry(0.09, 0.14, 0.9, 6), 0x665143, x, 0.78, z);
  if (pine) {
    object(group, new THREE.ConeGeometry(0.72 * size, 1.65 * size, 7), 0x356957, x, 1.65 * size, z);
    object(group, new THREE.ConeGeometry(0.55 * size, 1.3 * size, 7), 0x528268, x, 2.3 * size, z);
  } else {
    object(group, new THREE.IcosahedronGeometry(0.86 * size, 0), 0x4f805e, x, 1.7 * size, z);
    object(group, new THREE.IcosahedronGeometry(0.64 * size, 0), 0x759856, x + 0.43 * size, 2 * size, z + 0.14);
  }
}

function house(group, x, z, color = 0xd6c9a8, rotation = 0) {
  const home = new THREE.Group(); home.position.set(x, 0.35, z); home.rotation.y = rotation; group.add(home);
  object(home, new THREE.BoxGeometry(1.8, 1.3, 1.55), color, 0, 0.65, 0);
  const roof = object(home, new THREE.CylinderGeometry(0, 1.55, 0.75, 4), 0x865a4b, 0, 1.6, 0, Math.PI / 4);
  roof.scale.z = 0.8;
  object(home, new THREE.BoxGeometry(0.38, 0.7, 0.04), 0x5c645b, 0.4, 0.35, 0.8);
  [-0.55, 0.55].forEach(dx => object(home, new THREE.BoxGeometry(0.35, 0.38, 0.045), 0x496f79, dx, 0.84, 0.8));
  object(home, new THREE.BoxGeometry(0.22, 0.6, 0.25), 0xb29b80, -0.55, 1.8, -0.2);
  object(home,new THREE.BoxGeometry(2,0.13,0.65),0x937653,0,0.08,1.08);
  [-0.85,0.85].forEach(dx => object(home,new THREE.BoxGeometry(0.07,0.85,0.07),0x795f47,dx,0.5,1.3));
  object(home,new THREE.BoxGeometry(2,0.09,0.7),0xb2a27e,0,1.02,1.1);
  for(let i=0;i<8;i++) object(home,new THREE.BoxGeometry(1.9,0.025,0.025),0xad826b,0,1.32+i*0.055,-0.7+i*0.18);
}

export function buildBattleIsland(scene) {
  const island = new THREE.Group();
  island.name = 'battle-survival-island';
  scene.add(island);
  const water = object(island, new THREE.PlaneGeometry(400, 400), 0x245867, 0, -0.75, 0);
  water.rotation.x = -Math.PI / 2;
  water.material.roughness = 0.55;
  const shore = new THREE.Mesh(new THREE.ExtrudeGeometry(outline(1.075), { depth: 0.55, bevelEnabled: true, bevelSize: 0.18, bevelThickness: 0.1, bevelSegments: 1, steps: 1 }), surface(0xc5ad7d));
  shore.rotation.x = -Math.PI / 2; shore.position.y = -0.7; island.add(shore);
  const cliff = new THREE.Mesh(new THREE.ExtrudeGeometry(outline(), { depth: 0.65, bevelEnabled: false, steps: 1 }), surface(0x8a8c70));
  cliff.rotation.x = -Math.PI / 2; cliff.position.y = -0.3; island.add(cliff);
  const texture = groundMap();
  island.userData.groundTexture = texture;
  const top = new THREE.Mesh(terrain(), new THREE.MeshStandardMaterial({ map: texture, roughness: 1 }));
  top.receiveShadow = true; island.add(top);
  // A stream separates the eastern settlement, with bridges carrying the roads across it.
  const stream = new THREE.Shape();
  stream.moveTo(2.6, -7.8); stream.bezierCurveTo(4.2, -4, 1.6, -1, 3.2, 2.5); stream.bezierCurveTo(4, 5, 3.8, 6.5, 4.3, 7.5);
  stream.lineTo(5.1, 7.5); stream.bezierCurveTo(4.6, 5.5, 4.9, 4.5, 4, 2); stream.bezierCurveTo(2.4, -1, 5, -4, 3.4, -7.8); stream.closePath();
  const river = new THREE.Mesh(new THREE.ShapeGeometry(stream, 24), surface(0x4a9ba2));
  river.rotation.x = -Math.PI / 2; river.position.y = 0.375; island.add(river);
  [-1.5, 2.2].forEach(z => {
    object(island, new THREE.BoxGeometry(2.1, 0.16, 0.8), 0x8b765c, 3.2, 0.47, z);
    [-0.38, 0.38].forEach(offset => object(island, new THREE.BoxGeometry(2.1, 0.18, 0.07), 0xbda67a, 3.2, 0.72, z + offset));
  });
  // Deterministic scenery keeps the map stable across refreshes and screenshot checks.
  for (let i = 0; i < 28; i++) {
    const a = i * 2.39996;
    const x = Math.cos(a) * (8.5 + (i % 3) * 0.55), z = Math.sin(a) * (6.6 + (i % 2) * 0.3);
    prop(island,i%3===0?'tree-tall':'tree',x,z,1.9+(i%4)*0.25,a);
    if (i % 3 === 0) prop(island,'rock-flat-grass',x+0.8,z,0.5,a);
    if (i % 2 === 0) prop(island,'grass-large',x-0.5,z+0.6,0.28,a);
  }
  for (let i = 0; i < 18; i++) {
    const a = i * Math.PI * 2 / 18;
    const wave = new THREE.Mesh(new THREE.BoxGeometry(0.7 + (i % 3) * 0.3, 0.012, 0.045), new THREE.MeshBasicMaterial({ color: 0x80b8b5, transparent: true, opacity: 0.35 }));
    wave.position.set(Math.cos(a) * 13.5, -0.72, Math.sin(a) * 10.2); wave.rotation.y = -a; island.add(wave);
  }
  for(let i=0;i<22;i++) {
    const a=i*2.39996, x=Math.cos(a)*11.5,z=Math.sin(a)*8.6;
    prop(island,i%2?'rock-a':'rock-b',x,z,0.5+(i%4)*0.25,a,-0.35);
  }
  prop(island,'rock-flat-grass',-4.5,-7,1.2,0.5);
  prop(island,'rock-b',8.7,-0.4,1.3,0.7);
  island.userData.ready = Promise.all(island.userData.pending);
  return island;
}

export function buildBattleSettlement(scene, region, origin) {
  const group = new THREE.Group(); group.position.copy(origin); scene.add(group);
  regionSign(group,region);
  if (region === 'A') {
    house(group, -2.4, -1.9); house(group, 2.2, -2, 0xc2c8b4, -0.2);
    object(group, new THREE.CylinderGeometry(0.55, 0.7, 0.32, 12), 0xb6afa0, 0, 0.53, -0.1);
    object(group, new THREE.CylinderGeometry(0.09, 0.13, 0.85, 8), 0x9aabac, 0, 1.1, -0.1);
  } else if (region === 'B') {
    for (let i = 0; i < 8; i++) { const a = i * 2.4; prop(group,'tree-tall',Math.cos(a)*2.4,Math.sin(a)*2.2,2.5+(i%3)*0.3,a); }
    prop(group,'tree-log',0,-1.7,0.35,0.2);
  } else if (region === 'C') {
    object(group, new THREE.BoxGeometry(2.5, 1.25, 1.2), 0x638690, 0, 0.98, -2);
    for (let i = 0; i < 8; i++) object(group, new THREE.BoxGeometry(0.045, 1.22, 1.24), 0x8aafb2, -1.1 + i * 0.31, 0.98, -2);
    object(group, new THREE.BoxGeometry(1.5, 0.8, 0.85), 0xbb8f52, 2, 0.75, -0.4);
    object(group, new THREE.BoxGeometry(2.4, 0.16, 1), 0x95876f, 2.1, 0.4, 1.7);
  } else if (region === 'D') {
    house(group, 0, -2.1, 0xb9ad99, -0.2);
    object(group, new THREE.BoxGeometry(2.3, 0.85, 0.22), 0x8b8c7b, -1.4, 0.78, 0.8, -0.35);
    for (let i = 0; i < 4; i++) object(group, new THREE.DodecahedronGeometry(0.25 + i * 0.035, 0), 0xa4a495, -1.7 + i * 0.55, 0.47, 1.6);
  } else {
    [-1,1].forEach(side => { prop(group,'tent-canvas',side*1.7,-1.7,1.1,side*0.2);prop(group,'bedroll',side*1.7,-0.8,0.16,Math.PI/2); });
    prop(group,'campfire-pit',0,0,0.28);
    prop(group,'workbench',-2.2,1.2,0.75,0.2);
  }
  if (region !== 'B') {
    prop(group,'barrel',-2.1,-0.5,0.65,0.2);
    prop(group,'box-large',2,-1.3,0.5,0.15);
    prop(group,'signpost',0.5,1.8,0.8,0.3);
    for(let i=0;i<3;i++)prop(group,'fence',-2.5+i*0.75,1.8,0.5,0);
  }
  group.userData.ready=Promise.all(group.userData.pending || []);
  return group;
}
