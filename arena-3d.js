import * as THREE from './vendor/three.module.min.js';
import { GLTFLoader } from './vendor/addons/loaders/GLTFLoader.js';
import { clone as cloneSkeleton } from './vendor/addons/utils/SkeletonUtils.js';
import { buildBattleIsland, buildBattleSettlement } from './battle-island.js';
import { SurvivalStage } from './survival-stage.js';
import './battle-world.js';

const ACTIVE_STAGES = new WeakMap();
const GLTF_LOADER = new GLTFLoader();
const GLTF_CACHE = new Map();
const TEXTURE_CACHE = new Map();
let HUMANOID_CLIPS_PROMISE;
const ASSETS = {
  humanMale: './assets/3d/quaternius/universal-base/Superhero_Male_FullBody.gltf',
  humanFemale: './assets/3d/quaternius/universal-base/Superhero_Female_FullBody.gltf',
  humanoidClips: './assets/3d/quaternius/animations/humanoid-clips.json',
  eyeDrone: './assets/3d/quaternius/eye-drone/Enemy_EyeDrone.gltf'
};
ASSETS.survivor = './assets/3d/kenney/survivors/survivor.glb';
ASSETS.survivorSkins = ['survivorMaleB.png', 'survivorFemaleA.png'];

function loadSurvivorSkin(seat) {
  const url = `./assets/3d/kenney/survivors/${ASSETS.survivorSkins[Math.abs(Number(seat || 1) - 1) % 2]}`;
  if (!TEXTURE_CACHE.has(url)) TEXTURE_CACHE.set(url, new THREE.TextureLoader().loadAsync(url).then(texture => {
    texture.colorSpace = THREE.SRGBColorSpace;
    texture.flipY = true;
    return texture;
  }).catch(error => { TEXTURE_CACHE.delete(url); throw error; }));
  return TEXTURE_CACHE.get(url);
}
const HUMAN_ACTION_CLIPS = {
  IDLE: 'Idle_FoldArms_Loop',
  MOVE: 'Walk_Carry_Loop',
  SEARCH: 'Farm_Harvest',
  CAPTURE: 'Sword_Block',
  REST: 'Consume',
  TRADE: 'Idle_Rail_Call',
  ATTACK: 'Melee_Hook',
  RANGED_ATTACK: 'OverhandThrow',
  HIDE: 'Idle_Rail_Call',
  HEAL: 'Consume',
  GUARD: 'Sword_Block',
  SKIP: 'Yes',
  TALK: 'Idle_Rail_Call',
  VOTE: 'Yes',
  HIT: 'Hit_Knockback'
};
HUMAN_ACTION_CLIPS.REVIVE = 'Consume';
HUMAN_ACTION_CLIPS.EXECUTE = 'Melee_Hook';
HUMAN_ACTION_CLIPS.CONTRIBUTE = 'Farm_Harvest';
HUMAN_ACTION_CLIPS.RUMOR = 'Idle_Rail_Call';
const DRONE_ACTION_CLIPS = {
  IDLE: 'Idle',
  SEARCH: 'Look',
  TALK: 'Look',
  ATTACK: 'Attack',
  RANGED_ATTACK: 'Attack',
  GUARD: 'Charging',
  CAPTURE: 'Charging',
  HIT: 'Hit',
  MOVE: 'BackFlip',
  VOTE: 'Look'
};
const ACTION_LABELS = {
  MOVE: '转移区域',
  SEARCH: '搜索物资',
  CAPTURE: '占领空投',
  REST: '原地休整',
  TRADE: '交换资源',
  ATTACK: '发动攻击',
  HIDE: '隐蔽潜伏',
  HEAL: '使用医疗包',
  GUARD: '加固防具',
  SKIP: '观察局势',
  TALK: '公开发言',
  VOTE: '提交投票'
};
Object.assign(ACTION_LABELS, {
  REVIVE: '救援倒地玩家', EXECUTE: '处决倒地玩家', CONTRIBUTE: '建设协作信标',
  RUMOR: '传播未经证实的传闻', INFO_TRADE: '交换私密情报'
});
const COLORS = {
  ink: 0x071017,
  panel: 0x101c25,
  cyan: 0x52f4ff,
  lime: 0xc8ff3d,
  amber: 0xffc857,
  red: 0xff5268,
  violet: 0xa884ff,
  green: 0x54e29b,
  muted: 0x536270
};

const playerIdEquals = (left, right) => String(left ?? '') === String(right ?? '');
const alive = (player) => player?.status === 'ALIVE';
const downed = (player) => player?.status === 'DOWNED';
const eliminated = (player) => player?.status !== 'ALIVE' && player?.status !== 'DOWNED';
const latest = (events, predicate) => [...(events || [])].reverse().find(predicate);
const colorForSeat = (seat) => [0x57e3ff, 0xa98aff, 0xffc15a, 0x7be07b, 0xff6f91, 0x68a5ff, 0xf28cff, 0xff8a5b][Math.max(0, Number(seat || 1) - 1) % 8];

function loadGltf(url) {
  if (!GLTF_CACHE.has(url)) {
    GLTF_CACHE.set(url, GLTF_LOADER.loadAsync(url).catch((error) => {
      GLTF_CACHE.delete(url);
      throw error;
    }));
  }
  return GLTF_CACHE.get(url);
}

function loadHumanoidClips() {
  if (!HUMANOID_CLIPS_PROMISE) {
    HUMANOID_CLIPS_PROMISE = fetch(ASSETS.humanoidClips)
      .then((response) => {
        if (!response.ok) throw new Error(`Animation asset returned ${response.status}`);
        return response.json();
      })
      .then((payload) => (payload.clips || []).map((clip) => THREE.AnimationClip.parse(clip)))
      .catch((error) => {
        HUMANOID_CLIPS_PROMISE = undefined;
        throw error;
      });
  }
  return HUMANOID_CLIPS_PROMISE;
}

function material(color, options = {}) {
  return new THREE.MeshStandardMaterial({
    color,
    roughness: options.roughness ?? 0.48,
    metalness: options.metalness ?? 0.34,
    transparent: options.opacity != null && options.opacity < 1,
    opacity: options.opacity ?? 1,
    emissive: options.emissive ?? 0x000000,
    emissiveIntensity: options.emissiveIntensity ?? 0
  });
}

function mesh(geometry, meshMaterial, position, rotation) {
  const value = new THREE.Mesh(geometry, meshMaterial);
  if (position) value.position.set(...position);
  if (rotation) value.rotation.set(...rotation);
  value.castShadow = true;
  value.receiveShadow = true;
  return value;
}

function ring(radius, color, y = 0.04, opacity = 0.72) {
  const value = mesh(
    new THREE.TorusGeometry(radius, 0.035, 8, 96),
    new THREE.MeshBasicMaterial({ color, transparent: true, opacity }),
    [0, y, 0],
    [Math.PI / 2, 0, 0]
  );
  value.castShadow = false;
  return value;
}

function lineBetween(start, end, color, opacity = 0.8) {
  const geometry = new THREE.BufferGeometry().setFromPoints([start, end]);
  const value = new THREE.Line(geometry, new THREE.LineBasicMaterial({ color, transparent: true, opacity }));
  value.userData.flow = { start, end, speed: 0.18 + Math.random() * 0.12 };
  return value;
}

function addParticles(scene, count, color, area = 18, height = 12) {
  const positions = new Float32Array(count * 3);
  for (let index = 0; index < count; index += 1) {
    positions[index * 3] = (Math.random() - 0.5) * area;
    positions[index * 3 + 1] = Math.random() * height;
    positions[index * 3 + 2] = (Math.random() - 0.5) * area;
  }
  const geometry = new THREE.BufferGeometry();
  geometry.setAttribute('position', new THREE.BufferAttribute(positions, 3));
  const points = new THREE.Points(geometry, new THREE.PointsMaterial({
    color, size: 0.035, transparent: true, opacity: 0.46, depthWrite: false
  }));
  points.userData.particles = true;
  scene.add(points);
}

function easeInOut(value) {
  const clamped = THREE.MathUtils.clamp(value, 0, 1);
  return clamped < 0.5 ? 2 * clamped * clamped : 1 - Math.pow(-2 * clamped + 2, 2) / 2;
}

function eventSequence(event) {
  return Number(event?.sequenceNo || 0);
}

class ArenaStage {
  constructor(host, mode, payload) {
    this.host = host;
    this.mode = mode;
    this.payload = payload;
    this.labels = [];
    this.animated = [];
    this.mixers = [];
    this.agents = new Map();
    this.positionTweens = [];
    this.transients = [];
    this.timers = new Set();
    this.disposables = [];
    this.loadedAssetCount = 0;
    this.disposed = false;
    this.reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    this.lowPower = (navigator.hardwareConcurrency || 8) <= 4
      || (navigator.deviceMemory || 8) <= 4
      || window.matchMedia('(max-width: 720px)').matches;
    // Low-power runners keep the procedural agents responsive while capable
    // devices upgrade them asynchronously to the external GLTF assets.
    this.externalAssetsEnabled = this.mode === 'battle' || !this.lowPower;
    this.animateContinuously = !this.reducedMotion && !this.lowPower;
    this.clock = new THREE.Clock();
    this.pointer = new THREE.Vector2();
    this.lastLabelUpdate = -Infinity;
    this.lastMomentSequence = 0;
    this.pendingMoment = null;
    this.cameraFocus = null;
    this.frameCostTotal = 0;
    this.frameCostSamples = 0;
    this.cameraState = { yaw: 0, pitch: mode === 'battle' ? 0.78 : 0.58, distance: mode === 'battle' ? 28 : mode === 'werewolf' ? 16.5 : 17 };
    this.cameraDefault = { ...this.cameraState };
    this.dragState = null;
    this.init();
  }

  init() {
    this.scene = new THREE.Scene();
    this.scene.fog = new THREE.FogExp2(COLORS.ink, this.mode === 'battle' ? 0.032 : 0.046);
    this.camera = new THREE.PerspectiveCamera(38, 1, 0.1, 120);
    this.cameraTarget = new THREE.Vector3(0, this.mode === 'battle' ? 1 : 1.4, 0);

    try {
      this.renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true, powerPreference: 'high-performance' });
    } catch (error) {
      this.host.classList.add('arena3d-unavailable');
      this.host.innerHTML = '<p>当前设备无法启用 WebGL，已保留 2D 观战视图。</p>';
      return;
    }

    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, this.lowPower ? 1 : 1.25));
    this.renderer.setSize(this.host.clientWidth || 900, this.host.clientHeight || 620, false);
    this.renderer.setClearColor(0x000000, 0);
    this.renderer.outputColorSpace = THREE.SRGBColorSpace;
    this.renderer.toneMapping = THREE.ACESFilmicToneMapping;
    this.renderer.toneMappingExposure = 1.08;
    this.shadowsEnabled = !this.lowPower;
    this.renderer.shadowMap.enabled = this.shadowsEnabled;
    this.renderer.shadowMap.type = THREE.PCFShadowMap;
    this.renderer.domElement.setAttribute('aria-hidden', 'true');
    this.host.replaceChildren(this.renderer.domElement);
    this.host.classList.add('is-ready');
    this.host.closest('.battle-map, .werewolf-arena, .turing-stage-main')?.classList.add('arena3d-active');

    this.labelLayer = document.createElement('div');
    this.labelLayer.className = 'arena3d-label-layer';
    this.host.append(this.labelLayer);
    this.controls = document.createElement('div');
    this.controls.className = 'arena3d-controls';
    this.controls.innerHTML = '<span>拖动旋转 · 滚轮缩放</span><button type="button" aria-label="重置 3D 镜头">重置镜头</button>';
    this.host.append(this.controls);
    this.momentCard = document.createElement('div');
    this.momentCard.className = 'arena3d-moment-card';
    this.momentCard.dataset.arena3dMoment = '';
    this.host.append(this.momentCard);

    const hemisphere = new THREE.HemisphereLight(0x96cfff, 0x081017, this.mode === 'battle' ? 1.35 : 0.9);
    this.scene.add(hemisphere);
    if (this.mode === 'battle') this.scene.add(new THREE.AmbientLight(0xe8f1ff, 0.7));
    const key = new THREE.DirectionalLight(this.mode === 'battle' ? 0xffedcf : this.mode === 'werewolf' ? 0xbca4ff : 0xd9f6ff, this.mode === 'battle' ? 3.8 : 3.2);
    key.position.set(-7, 12, 8);
    key.castShadow = this.shadowsEnabled;
    key.shadow.mapSize.set(512, 512);
    if (this.mode === 'battle') {
      key.shadow.mapSize.set(1024, 1024);
      key.shadow.bias = -0.0005;
      key.shadow.normalBias = 0.04;
    }
    key.shadow.camera.left = -14;
    key.shadow.camera.right = 14;
    key.shadow.camera.top = 14;
    key.shadow.camera.bottom = -14;
    this.scene.add(key);
    const rimLight = new THREE.PointLight(this.mode === 'turing' ? COLORS.lime : COLORS.cyan, 14, 30, 2);
    rimLight.position.set(7, 7, -5);
    this.scene.add(rimLight);

    const floor = mesh(
      new THREE.CircleGeometry(this.mode === 'battle' ? 21 : 15, 96),
      material(0x071016, { roughness: 0.82, metalness: 0.12 }),
      [0, -0.12, 0],
      [-Math.PI / 2, 0, 0]
    );
    if (this.mode !== 'battle') {
      this.scene.add(floor);
      this.scene.add(ring(11.5, this.mode === 'turing' ? COLORS.lime : COLORS.cyan, -0.07, 0.18));
    } else { floor.geometry.dispose(); floor.material.dispose(); }

    if (this.mode === 'battle') this.buildBattle();
    if (this.mode === 'werewolf') this.buildWerewolf();
    if (this.mode === 'turing') this.buildTuring();
    addParticles(
      this.scene,
      this.lowPower ? 72 : this.mode === 'battle' ? 180 : 120,
      this.mode === 'turing' ? COLORS.lime : 0x75c8dc
    );

    this.resizeObserver = new ResizeObserver(() => this.resize());
    this.resizeObserver.observe(this.host);
    this.bindControls();
    this.updateCamera();
    this.animate();
    if (!this.reducedMotion) this.queueLatestMoment(true);
  }

  bindControls() {
    this.onPointerDown = (event) => {
      if (event.target.closest('.arena3d-controls, .arena3d-label')) return;
      this.cancelCameraFocus();
      this.dragState = { x: event.clientX, y: event.clientY, yaw: this.cameraState.yaw, pitch: this.cameraState.pitch };
      this.host.setPointerCapture?.(event.pointerId);
      this.host.classList.add('is-dragging');
    };
    this.onPointerMove = (event) => {
      if (!this.dragState) return;
      this.cameraState.yaw = this.dragState.yaw - (event.clientX - this.dragState.x) * 0.006;
      this.cameraState.pitch = THREE.MathUtils.clamp(this.dragState.pitch + (event.clientY - this.dragState.y) * 0.004, 0.28, 1.08);
      this.updateCamera();
    };
    this.onPointerUp = () => {
      this.dragState = null;
      this.host.classList.remove('is-dragging');
    };
    this.onWheel = (event) => {
      event.preventDefault();
      this.cameraState.distance = THREE.MathUtils.clamp(this.cameraState.distance + event.deltaY * 0.012, 10, 27);
      this.updateCamera();
    };
    this.host.addEventListener('pointerdown', this.onPointerDown);
    this.host.addEventListener('pointermove', this.onPointerMove);
    this.host.addEventListener('pointerup', this.onPointerUp);
    this.host.addEventListener('pointercancel', this.onPointerUp);
    this.host.addEventListener('wheel', this.onWheel, { passive: false });
    this.controls.querySelector('button')?.addEventListener('click', () => {
      this.cancelCameraFocus();
      Object.assign(this.cameraState, this.cameraDefault);
      this.updateCamera();
    });
  }

  updateCamera() {
    const { yaw, pitch, distance } = this.cameraState;
    this.camera.position.set(
      Math.sin(yaw) * Math.cos(pitch) * distance,
      Math.sin(pitch) * distance,
      Math.cos(yaw) * Math.cos(pitch) * distance
    );
    this.camera.lookAt(this.cameraTarget);
  }

  createLabel(object, player, detail, state = '') {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = `arena3d-label ${state}`;
    button.dataset.arena3dPlayer = String(player.id);
    button.innerHTML = `<i>${Number(player.seatNo || 0)}</i><span><b>${this.escape(player.displayName || `${player.seatNo}号`)}</b><small>${this.escape(detail || '')}</small></span>`;
    button.addEventListener('click', () => {
      this.host.dispatchEvent(new CustomEvent('arena3d:select-player', {
        bubbles: true,
        detail: { playerId: Number(player.id), seatNo: Number(player.seatNo) }
      }));
    });
    this.labelLayer.append(button);
    this.labels.push({
      element: button,
      object,
      playerId: String(player.id),
      offset: this.mode === 'battle' ? 2.65 : 1.65
    });
  }

  createAgent(player, scale = 1, assetKind = 'human') {
    const group = new THREE.Group();
    const active = playerIdEquals(player.id, this.payload.game?.currentActorPlayerId);
    const bodyColor = alive(player) ? colorForSeat(player.seatNo) : 0x313942;
    const body = mesh(new THREE.CylinderGeometry(0.25 * scale, 0.34 * scale, 0.82 * scale, 12), material(bodyColor, {
      emissive: active ? bodyColor : 0x000000,
      emissiveIntensity: active ? 1.5 : 0.08,
      metalness: 0.62
    }), [0, 0.52 * scale, 0]);
    const head = mesh(new THREE.SphereGeometry(0.25 * scale, 18, 14), material(0xe9f3f4, {
      emissive: bodyColor, emissiveIntensity: active ? 0.65 : 0.16
    }), [0, 1.08 * scale, 0]);
    body.userData.agentPlaceholder = true;
    head.userData.agentPlaceholder = true;
    const base = mesh(new THREE.CylinderGeometry(0.5 * scale, 0.56 * scale, 0.1, 24), material(0x172733, {
      emissive: bodyColor, emissiveIntensity: active ? 0.85 : 0.18
    }), [0, 0.04, 0]);
    group.add(base, body, head);
    if (downed(player)) { group.rotation.z = 0.32; group.position.y -= 0.22; }
    else if (eliminated(player)) group.rotation.z = Math.PI / 2;
    const pulse = ring(0.62 * scale, COLORS.lime, 0.09, 0.95);
    pulse.userData.pulse = true;
    pulse.visible = active;
    group.add(pulse);
    this.animated.push(pulse);
    group.userData.player = player;
    group.userData.base = base;
    group.userData.pulse = pulse;
    group.userData.scale = scale;
    group.userData.desiredAssetKind = assetKind;
    this.agents.set(String(player.id), group);
    if (this.externalAssetsEnabled) this.attachAgentModel(group, player, scale, assetKind);
    else this.markAssetReady(group, player, 'procedural-fallback');
    return group;
  }

  markAssetReady(group, player, assetKind = group.userData.desiredAssetKind) {
    if (group.userData.assetReady) return;
    group.userData.assetReady = true;
    group.userData.assetKind = assetKind;
    this.loadedAssetCount += 1;
    this.host.dataset.arena3dAssets = String(this.loadedAssetCount);
    this.host.dataset.arena3dAssetMode = this.externalAssetsEnabled ? 'external' : 'procedural-fallback';
    if (this.pendingMoment && playerIdEquals(this.pendingMoment.actorPlayerId, player.id)) {
      const pending = this.pendingMoment;
      this.pendingMoment = null;
      this.performMoment(pending, true);
    }
  }

  async attachAgentModel(group, player, scale, assetKind) {
    try {
      if (this.mode === 'battle') {
        const [gltf, skin] = await Promise.all([loadGltf(ASSETS.survivor), loadSurvivorSkin(player.seatNo)]);
        if (this.disposed || !group.parent) return;
        const model = cloneSkeleton(gltf.scene);
        this.prepareModel(model, player, scale, 'survivor');
        model.traverse(object => {
          if (object.isMesh) {
            object.material.map = skin;
            object.material.roughness = 0.86;
            object.material.metalness = 0;
            object.material.needsUpdate = true;
          }
        });
        group.add(model);
        this.configureAgentAnimations(group, model, gltf.animations, 'survivor');
        this.addSurvivorEquipment(group, player, scale);
      } else if (assetKind === 'drone') {
        const gltf = await loadGltf(ASSETS.eyeDrone);
        if (this.disposed || !group.parent || group.userData.desiredAssetKind !== assetKind) return;
        const model = cloneSkeleton(gltf.scene);
        this.prepareModel(model, player, scale, 'drone');
        group.add(model);
        this.configureAgentAnimations(group, model, gltf.animations, 'drone');
      } else {
        const url = Number(player.seatNo) % 2 ? ASSETS.humanFemale : ASSETS.humanMale;
        const [gltf, clips] = await Promise.all([loadGltf(url), loadHumanoidClips()]);
        if (this.disposed || !group.parent || group.userData.desiredAssetKind !== assetKind) return;
        const model = cloneSkeleton(gltf.scene);
        this.prepareModel(model, player, scale, 'human');
        group.add(model);
        this.configureAgentAnimations(group, model, clips, 'human');
      }
      group.children.forEach((child) => {
        if (child.userData.agentPlaceholder) child.visible = false;
      });
      this.markAssetReady(group, player, assetKind);
      if (!this.animateContinuously) {
        this.updateLabels();
        this.renderer.render(this.scene, this.camera);
      }
    } catch (error) {
      console.warn(`[Agent Arena 3D] Failed to load ${assetKind} model; using procedural fallback.`, error);
      this.markAssetReady(group, player, 'procedural-fallback');
    }
  }

  prepareModel(model, player, scale, assetKind) {
    const initialBox = new THREE.Box3().setFromObject(model);
    const size = initialBox.getSize(new THREE.Vector3());
    const center = initialBox.getCenter(new THREE.Vector3());
    const targetHeight = (assetKind === 'survivor' ? 2.45 : assetKind === 'drone' ? 1.48 : 1.58) * scale;
    const normalizedScale = targetHeight / Math.max(size.y, 0.001);
    model.scale.setScalar(normalizedScale);
    model.position.set(
      -center.x * normalizedScale,
      -initialBox.min.y * normalizedScale + (assetKind === 'drone' ? 0.28 * scale : 0.09),
      -center.z * normalizedScale
    );
    const accent = new THREE.Color(colorForSeat(player.seatNo));
    model.traverse((object) => {
      if (!object.isMesh && !object.isSkinnedMesh) return;
      object.castShadow = this.shadowsEnabled;
      object.receiveShadow = true;
      object.userData.externalAsset = true;
      const values = Array.isArray(object.material) ? object.material : [object.material];
      const cloned = values.map((source) => {
        const value = source.clone();
        value.userData.externalAsset = true;
        if (assetKind === 'drone') {
          value.emissive?.copy(accent);
          value.emissiveIntensity = 0.28;
          value.metalness = Math.max(0.62, value.metalness || 0);
          value.roughness = Math.min(0.42, value.roughness ?? 0.42);
        } else if (/superhero/i.test(value.name || '')) {
          value.color?.lerp(accent, 0.32);
          value.emissive?.copy(accent);
          value.emissiveIntensity = playerIdEquals(player.id, this.payload.game?.currentActorPlayerId) ? 0.24 : 0.06;
        }
        if (eliminated(player)) {
          value.color?.multiplyScalar(0.28);
          value.transparent = true;
          value.opacity = 0.48;
        }
        return value;
      });
      object.material = Array.isArray(object.material) ? cloned : cloned[0];
    });
  }

  configureAgentAnimations(group, model, clips, kind) {
    group.userData.model = model;
    if (this.reducedMotion || !clips?.length) return;
    const mixer = new THREE.AnimationMixer(model);
    const clipMap = new Map(clips.map((clip) => [clip.name, clip]));
    group.userData.animation = { mixer, actions: new Map(), clips: clipMap, kind, current: null, currentName: '', resumeAt: 0 };
    this.mixers.push(mixer);
    this.playAgentAction(group, 'IDLE');
  }

  playAgentAction(group, actionType, options = {}) {
    const state = group?.userData.animation;
    if (!state && this.mode === 'battle' && group?.userData.model && !this.reducedMotion) {
      group.userData.model.userData.reaction = { action: actionType, startedAt: this.clock.elapsedTime };
      return;
    }
    if (!state || this.reducedMotion) return;
    const map = state.kind === 'drone' ? DRONE_ACTION_CLIPS : HUMAN_ACTION_CLIPS;
    const resolvedType = actionType === 'ATTACK' && group.userData.player?.armed ? 'RANGED_ATTACK' : actionType;
    const clipName = map[resolvedType] || map.IDLE;
    const resolvedClip = state.clips.get(clipName) || state.clips.get(map.IDLE) || state.clips.values().next().value;
    if (!resolvedClip) return;
    let next = state.actions.get(resolvedClip.name);
    if (!next) {
      next = state.mixer.clipAction(resolvedClip);
      state.actions.set(resolvedClip.name, next);
    }
    if (!next || (state.current === next && resolvedType === 'IDLE')) return;
    state.current?.fadeOut(0.16);
    next.reset();
    next.enabled = true;
    next.setEffectiveTimeScale(options.timeScale || (resolvedType === 'IDLE' ? 0.92 : 1.18));
    next.setEffectiveWeight(1);
    const looping = resolvedType === 'IDLE' || resolvedType === 'MOVE';
    next.setLoop(looping ? THREE.LoopRepeat : THREE.LoopOnce, looping ? Infinity : 1);
    next.clampWhenFinished = !looping;
    next.fadeIn(0.16).play();
    state.current = next;
    state.currentName = resolvedType;
    state.resumeAt = looping && resolvedType === 'IDLE'
      ? 0
      : this.clock.elapsedTime + (options.duration || Math.min(2.2, Math.max(0.8, next.getClip().duration / (options.timeScale || 1.18))));
  }

  buildBattle() {
    this.scene.background = new THREE.Color(0x214651);
    this.scene.fog.color.set(0x214651);
    this.scene.fog.density = 0.009;
    const island = buildBattleIsland(this.scene);
    const environmentGroups = [island];
    this.disposables.push(() => {
      island.userData.groundTexture.dispose();
      environmentGroups.forEach(group => { group.userData.active = false; (group.userData.textures || []).forEach(texture => texture.dispose()); });
    });
    this.host.dataset.arena3dMapStyle = 'survival-island';
    const players = this.payload.players || [];
    const game = this.payload.game || {};
    const events = this.payload.events || [];
    const world = window.BattleWorld.resolve(game, events);
    const safe = new Set(world.safeRegions);
    this.battleDangerMarkers = new Map();
    const positions = {
      A: new THREE.Vector3(0, 0.05, 0),
      B: new THREE.Vector3(-6.2, 0, -4.3),
      C: new THREE.Vector3(6.2, 0, -4.3),
      D: new THREE.Vector3(6.2, 0, 4.3),
      E: new THREE.Vector3(-6.2, 0, 4.3)
    };
    this.battleRegionPositions = positions;
    const supplyRegion = world.supplyDropRegion;
    this.host.dataset.arena3dStage3 = 'social-simulation';

    Object.entries(positions).forEach(([region, position]) => {
      environmentGroups.push(buildBattleSettlement(this.scene, region, position));
      {
        const warning = mesh(new THREE.PlaneGeometry(5.8, 4.8), new THREE.MeshBasicMaterial({ color: COLORS.red, transparent: true, opacity: 0.13, depthWrite: false }), [position.x, 0.39, position.z], [-Math.PI / 2, 0, 0]);
        this.scene.add(warning);
        const storm = new THREE.PointLight(COLORS.red, 2, 6, 2);
        storm.position.copy(position).add(new THREE.Vector3(0, 2, 0));
        this.scene.add(storm);
        warning.visible = storm.visible = !safe.has(region);
        this.battleDangerMarkers.set(region, { warning, storm });
      }

      const occupants = players.filter((player) => player.region === region);
      occupants.forEach((player) => {
        const agent = this.createAgent(player, 1);
        agent.position.copy(this.battlePositionForPlayer(player, players));
        this.scene.add(agent);
        const weapon = player.weaponName ? `${player.weaponName} Lv.${Number(player.weaponLevel || 1)}` : '徒手';
        this.createLabel(agent, player, `${weapon} · HP ${player.health ?? '-'}${downed(player) ? ' · 倒地待援' : ''}`, eliminated(player) ? 'eliminated' : '');
      });
    });

    this.updateSupplyBeacon(supplyRegion);
    this.host.dataset.arena3dSafeRegions = [...safe].sort().join(',');
    this.addCooperativeBeacon(positions.C, events);
    this.addSocialLinks(players, events);
    Promise.all(environmentGroups.map(group => group.userData.ready)).then(() => {
      if (this.disposed) return;
      this.host.dataset.arena3dScenery = 'ready';
      if (!this.animateContinuously) { this.renderer.render(this.scene, this.camera); this.updateLabels(); }
    }).catch(error => {
      if (!this.disposed) this.host.dataset.arena3dScenery = 'degraded';
      console.warn('[Agent Arena 3D] Scenery asset load failed.', error);
    });

  }

  addSurvivorEquipment(group, player, scale) {
    const previous = group.userData.equipment;
    if (previous) {
      previous.removeFromParent();
      previous.traverse(object => { object.geometry?.dispose(); object.material?.dispose(); });
    }
    const equipment = new THREE.Group();
    const accent = colorForSeat(player.seatNo);
    equipment.add(mesh(new THREE.BoxGeometry(0.36, 0.5, 0.2), material(0x283d49, { roughness: 0.9 }), [0, 1.02, -0.23]));
    equipment.add(mesh(new THREE.BoxGeometry(0.3, 0.12, 0.025), material(accent, { emissive: accent, emissiveIntensity: 0.2 }), [0, 1.16, -0.34]));
    if (player.armed || player.weaponName) {
      const gun = new THREE.Group();
      gun.add(mesh(new THREE.BoxGeometry(0.11, 0.14, 0.55), material(0x26343d), [0, 0, 0]));
      gun.add(mesh(new THREE.CylinderGeometry(0.025, 0.025, 0.3, 8), material(0x111d26), [0, 0.025, 0.4], [Math.PI / 2, 0, 0]));
      gun.add(mesh(new THREE.BoxGeometry(0.07, 0.2, 0.1), material(accent), [0, -0.12, -0.04]));
      gun.position.set(0.4, 0.88, 0.16);
      equipment.add(gun);
    }
    equipment.scale.setScalar(scale * 1.2);
    group.add(equipment);
    group.userData.equipment = equipment;
    group.userData.equipmentSignature = `${Boolean(player.armed)}:${player.weaponName || ''}`;
    this.host.dataset.arena3dBattleStyle = 'survivors';
  }

  addCooperativeBeacon(position, events) {
    const complete = Boolean(latest(events, (event) => event.eventType === 'BATTLE_OBJECTIVE_COMPLETED'));
    const contribution = latest(events, (event) => event.eventType === 'BATTLE_OBJECTIVE_CONTRIBUTED');
    const group = new THREE.Group();
    group.position.copy(position).add(new THREE.Vector3(0, 0.48, 0));
    const color = complete ? COLORS.lime : COLORS.cyan;
    const core = mesh(new THREE.OctahedronGeometry(0.42, 0), material(color, { emissive: color, emissiveIntensity: complete ? 2.1 : 1.1 }), [0, 1.1, 0]);
    const beam = mesh(new THREE.CylinderGeometry(0.04, 0.25, complete ? 7 : 3.6, 14, 1, true), new THREE.MeshBasicMaterial({ color, transparent: true, opacity: complete ? 0.3 : 0.12, side: THREE.DoubleSide }), [0, complete ? 4.1 : 2.4, 0]);
    beam.userData.pulse = true; group.add(core, beam); this.scene.add(group); this.animated.push(core, beam);
    group.userData.objective = contribution?.payload?.objectiveId || 'public-beacon';
  }

  addSocialLinks(players, events) {
    const active = new Map();
    (events || []).forEach((event) => {
      const a = event.payload?.memberAPlayerId ?? event.payload?.proposerPlayerId;
      const b = event.payload?.memberBPlayerId ?? event.payload?.targetPlayerId;
      if (a == null || b == null) return;
      const key = [String(a), String(b)].sort().join(':');
      if (event.eventType === 'BATTLE_ALLIANCE_FORMED') active.set(key, [a, b]);
      if (['BATTLE_ALLIANCE_LEFT', 'BATTLE_ALLIANCE_BETRAYED'].includes(event.eventType)) active.delete(key);
    });
    active.forEach(([a, b]) => {
      const pa = players.find((p) => playerIdEquals(p.id, a)); const pb = players.find((p) => playerIdEquals(p.id, b));
      if (!pa || !pb) return;
      const start = this.battlePositionForPlayer(pa, players).add(new THREE.Vector3(0, 1, 0));
      const end = this.battlePositionForPlayer(pb, players).add(new THREE.Vector3(0, 1, 0));
      const link = lineBetween(start, end, COLORS.lime, 0.34); this.scene.add(link);
    });
  }

  battlePositionForPlayer(player, players = this.payload.players || []) {
    const regionPosition = this.battleRegionPositions?.[player?.region];
    if (!regionPosition) return new THREE.Vector3(0, 0.36, 0);
    const occupants = players.filter((candidate) => candidate.region === player.region);
    const index = Math.max(0, occupants.findIndex((candidate) => playerIdEquals(candidate.id, player.id)));
    const angle = (Math.PI * 2 * index) / Math.max(occupants.length, 1);
    return regionPosition.clone().add(new THREE.Vector3(Math.cos(angle) * 1.15, 0.36, Math.sin(angle) * 1.15));
  }

  regionGlyph(region, position, radius) {
    const group = new THREE.Group();
    group.position.copy(position);
    group.position.y = 0.37;
    const halo = ring(radius * 0.87, region === 'A' ? COLORS.lime : COLORS.cyan, 0.02, 0.22);
    group.add(halo);
    const marker = mesh(new THREE.CylinderGeometry(0.28, 0.35, 0.08, 12), material(region === 'A' ? COLORS.lime : COLORS.cyan, {
      emissive: region === 'A' ? COLORS.lime : COLORS.cyan, emissiveIntensity: 1.1
    }), [0, 0.08, 0]);
    group.add(marker);
    return group;
  }

  addRegionScenery(region, origin) {
    const group = new THREE.Group();
    group.position.copy(origin);
    if (region === 'B') {
      for (let index = 0; index < 8; index += 1) {
        const angle = index * 2.39;
        const tree = new THREE.Group();
        tree.add(mesh(new THREE.CylinderGeometry(0.06, 0.09, 0.65, 7), material(0x513b2c), [0, 0.34, 0]));
        tree.add(mesh(new THREE.ConeGeometry(0.32, 0.9, 8), material(0x2c7a58), [0, 0.95, 0]));
        tree.position.set(Math.cos(angle) * 1.85, 0.25, Math.sin(angle) * 1.85);
        group.add(tree);
      }
    } else if (region === 'C') {
      for (let index = -2; index <= 2; index += 1) {
        group.add(mesh(new THREE.BoxGeometry(0.18, 0.16, 1.4), material(0x506a73), [index * 0.42, 0.37, -1.55]));
      }
    } else if (region === 'D') {
      group.add(mesh(new THREE.BoxGeometry(3.1, 0.55, 0.34), material(0x756653), [0, 0.55, -1.65]));
      group.add(mesh(new THREE.BoxGeometry(0.4, 1.1, 2.7), material(0x63594e), [-1.55, 0.72, 0]));
    } else if (region === 'E') {
      const tent = mesh(new THREE.ConeGeometry(0.9, 1.25, 4), material(0x6c8050), [0, 0.95, -1.25], [0, Math.PI / 4, 0]);
      group.add(tent);
    } else {
      for (let index = 0; index < 6; index += 1) {
        group.add(mesh(new THREE.BoxGeometry(0.45, 0.35 + (index % 3) * 0.28, 0.45), material(0x5c6870), [
          Math.cos(index) * 2.1, 0.55, Math.sin(index) * 2.1
        ], [0, index * 0.47, 0]));
      }
    }
    this.scene.add(group);
  }

  addSupplyBeacon(position) {
    const group = new THREE.Group();
    group.position.copy(position).add(new THREE.Vector3(0, 0.45, 0));
    const crate = mesh(new THREE.BoxGeometry(0.75, 0.55, 0.75), material(COLORS.amber, {
      emissive: COLORS.amber, emissiveIntensity: 0.5
    }), [0, 0.35, 0]);
    const beam = mesh(new THREE.CylinderGeometry(0.06, 0.42, 8, 16, 1, true), new THREE.MeshBasicMaterial({
      color: COLORS.amber, transparent: true, opacity: 0.15, side: THREE.DoubleSide, depthWrite: false
    }), [0, 4.3, 0]);
    beam.userData.pulse = true;
    group.add(crate, beam);
    this.scene.add(group);
    this.animated.push(beam);
    return group;
  }

  updateSupplyBeacon(region) {
    const position = this.battleRegionPositions?.[region];
    if (position && !this.battleSupplyBeacon) this.battleSupplyBeacon = this.addSupplyBeacon(position);
    if (this.battleSupplyBeacon) {
      this.battleSupplyBeacon.visible = Boolean(position);
      if (position) this.battleSupplyBeacon.position.copy(position).add(new THREE.Vector3(0, 0.45, 0));
    }
    this.host.dataset.arena3dSupplyRegion = position ? region : '';
  }

  buildWerewolf() {
    const game = this.payload.game || {};
    const players = this.payload.players || [];
    const projection = this.payload.projection?.werewolfSituation || {};
    const isNight = String(game.currentPhase || '').startsWith('NIGHT_');
    this.scene.background = new THREE.Color(isNight ? 0x070914 : 0x101512);
    this.scene.fog.color.set(isNight ? 0x090b17 : 0x101712);
    const table = mesh(new THREE.CylinderGeometry(5.15, 5.55, 0.62, 64), material(isNight ? 0x171b2a : 0x202a25, {
      metalness: 0.58, roughness: 0.3, emissive: isNight ? 0x1c1840 : 0x182b20, emissiveIntensity: 0.35
    }), [0, 0.32, 0]);
    const accentRings = [ring(5.05, isNight ? COLORS.violet : COLORS.lime, 0.66, 0.8)];
    for (let index = 0; index < 4; index += 1) {
      accentRings.push(ring(1.55 + index * 0.83, isNight ? COLORS.violet : COLORS.green, 0.67, 0.12));
    }
    this.scene.add(table, ...accentRings);

    const seatPosition = new Map();
    players.forEach((player, index) => {
      const angle = -Math.PI / 2 + (Math.PI * 2 * index) / Math.max(players.length, 1);
      const position = new THREE.Vector3(Math.cos(angle) * 7, 0.05, Math.sin(angle) * 5.4);
      seatPosition.set(Number(player.seatNo), position);
      const chair = mesh(new THREE.BoxGeometry(1.25, 0.18, 1), material(0x202832), [position.x, 0.18, position.z], [0, -angle - Math.PI / 2, 0]);
      this.scene.add(chair);
      const agent = this.createAgent(player, 1.04);
      agent.position.copy(position).add(new THREE.Vector3(0, 0.28, 0));
      agent.rotation.y = -angle - Math.PI / 2;
      this.scene.add(agent);
      const stance = (projection.players || []).find((item) => Number(item.seatNo) === Number(player.seatNo));
      const suspicion = stance?.suspects?.[0]?.seatNo ? `怀疑 ${Number(stance.suspects[0].seatNo)}号` : (alive(player) ? '立场观察中' : '已出局');
      this.createLabel(agent, player, suspicion, `${alive(player) ? '' : 'eliminated'} ${playerIdEquals(player.id, game.currentActorPlayerId) ? 'active' : ''}`);
    });

    this.werewolfSeatPositions = seatPosition;
    this.werewolfRelationGroup = new THREE.Group();
    this.scene.add(this.werewolfRelationGroup);

    const crown = new THREE.Group();
    crown.add(mesh(new THREE.OctahedronGeometry(0.78, 1), material(isNight ? COLORS.violet : COLORS.lime, {
      emissive: isNight ? COLORS.violet : COLORS.lime, emissiveIntensity: 1.5, opacity: 0.42, metalness: 0.8
    }), [0, 2.2, 0]));
    crown.add(ring(1.35, isNight ? COLORS.violet : COLORS.lime, 1.52, 0.85));
    crown.children.forEach((item) => { item.userData.rotate = 0.25; });
    this.scene.add(crown);
    this.animated.push(...crown.children);
    const spotlight = new THREE.SpotLight(isNight ? COLORS.violet : COLORS.lime, 42, 22, 0.32, 0.65, 1.4);
    spotlight.position.set(0, 11, 2);
    spotlight.target.position.set(0, 0, 0);
    this.scene.add(spotlight, spotlight.target);
    this.werewolfTheme = { table, accentRings, crown, spotlight };
    this.updateWerewolfTheme();
    this.updateWerewolfRelations();
  }

  werewolfRelations() {
    const selected = this.payload.selectedRelation || 'SUSPECT';
    const projection = this.payload.projection?.werewolfSituation || {};
    if (selected === 'SKILL') {
      const game = this.payload.game || {};
      const players = this.payload.players || [];
      const playerById = new Map(players.map((player) => [String(player.id), player]));
      return (this.payload.events || [])
        .filter((event) => (
          ['WOLF_TARGET_SELECTED', 'GUARD_PROTECTED', 'SEER_CHECKED', 'WITCH_ACTION_COMPLETED'].includes(event.eventType)
          && Number(event.roundNo || 0) === Number(game.currentRound || 0)
          && event.payload?.targetPlayerId != null
        ))
        .slice(-4)
        .map((event) => ({
          type: 'SKILL',
          fromSeatNo: playerById.get(String(event.actorPlayerId))?.seatNo,
          toSeatNo: playerById.get(String(event.payload.targetPlayerId))?.seatNo
        }));
    }
    let relations = (projection.relationships || []).filter((relation) => relation.type === selected);
    if (selected === 'VOTE' && !relations.length) {
      relations = (projection.relationships || []).filter((relation) => relation.type === 'VOTE_INTENT');
      if (!relations.length) {
        relations = (projection.players || [])
          .filter((stance) => stance.voteIntent?.seatNo)
          .map((stance) => ({
            type: 'VOTE_INTENT',
            from: { seatNo: stance.seatNo },
            to: { seatNo: stance.voteIntent.seatNo }
          }));
      }
    }
    return relations.map((relation) => ({
      type: relation.type,
      fromSeatNo: relation.from?.seatNo,
      toSeatNo: relation.to?.seatNo
    }));
  }

  updateWerewolfRelations() {
    if (!this.werewolfRelationGroup || !this.werewolfSeatPositions) return;
    const oldLines = [...this.werewolfRelationGroup.children];
    this.animated = this.animated.filter((item) => !oldLines.includes(item));
    oldLines.forEach((line) => {
      line.removeFromParent();
      line.geometry?.dispose?.();
      line.material?.dispose?.();
    });
    const relationColors = { SUSPECT: COLORS.red, SUPPORT: COLORS.green, VOTE: COLORS.amber, VOTE_INTENT: COLORS.amber, SKILL: COLORS.violet };
    const relations = this.werewolfRelations();
    relations.forEach((relation) => {
      const from = this.werewolfSeatPositions.get(Number(relation.fromSeatNo));
      const to = this.werewolfSeatPositions.get(Number(relation.toSeatNo));
      if (!from || !to || Number(relation.fromSeatNo) === Number(relation.toSeatNo)) return;
      const value = lineBetween(
        from.clone().add(new THREE.Vector3(0, 1.05, 0)),
        to.clone().add(new THREE.Vector3(0, 1.05, 0)),
        relationColors[relation.type] || COLORS.cyan,
        0.84
      );
      this.werewolfRelationGroup.add(value);
      this.animated.push(value);
    });
    this.host.dataset.arena3dRelation = this.payload.selectedRelation || 'SUSPECT';
    this.host.dataset.arena3dRelationCount = String(this.werewolfRelationGroup.children.length);
  }

  updateWerewolfTheme() {
    if (!this.werewolfTheme) return;
    const isNight = String(this.payload.game?.currentPhase || '').startsWith('NIGHT_');
    const accent = isNight ? COLORS.violet : COLORS.lime;
    this.scene.background.setHex(isNight ? 0x070914 : 0x101512);
    this.scene.fog.color.setHex(isNight ? 0x090b17 : 0x101712);
    this.werewolfTheme.table.material.color.setHex(isNight ? 0x171b2a : 0x202a25);
    this.werewolfTheme.table.material.emissive.setHex(isNight ? 0x1c1840 : 0x182b20);
    this.werewolfTheme.accentRings.forEach((value, index) => value.material.color.setHex(
      isNight ? COLORS.violet : index === 0 ? COLORS.lime : COLORS.green
    ));
    this.werewolfTheme.crown.children.forEach((value) => {
      value.material?.color?.setHex(accent);
      value.material?.emissive?.setHex(accent);
    });
    this.werewolfTheme.spotlight.color.setHex(accent);
    this.host.dataset.arena3dPhaseTheme = isNight ? 'night' : 'day';
  }

  buildTuring() {
    const game = this.payload.game || {};
    const players = this.payload.players || [];
    const counts = game.publicSuspicionCounts || {};
    const revealed = game.identitiesRevealed || game.status === 'FINISHED';
    this.scene.background = new THREE.Color(0x07100e);
    this.scene.fog.color.set(0x07100e);
    const stage = mesh(new THREE.CylinderGeometry(7.6, 8.2, 0.52, 6), material(0x14201d, {
      metalness: 0.7, roughness: 0.23, emissive: 0x15281f, emissiveIntensity: 0.35
    }), [0, 0.2, 0]);
    this.scene.add(stage, ring(7.25, COLORS.lime, 0.48, 0.68));

    players.forEach((player, index) => {
      const angle = -Math.PI / 2 + (Math.PI * 2 * index) / Math.max(players.length, 1);
      const position = new THREE.Vector3(Math.cos(angle) * 5.75, 0.45, Math.sin(angle) * 4.55);
      const human = revealed && player.identity === 'HUMAN';
      const panelColor = revealed ? (human ? COLORS.cyan : COLORS.violet) : COLORS.lime;
      const booth = new THREE.Group();
      booth.position.copy(position);
      booth.rotation.y = -angle - Math.PI / 2;
      booth.add(mesh(new THREE.BoxGeometry(1.65, 0.16, 1.05), material(0x26352f), [0, 0.08, 0]));
      booth.add(mesh(new THREE.BoxGeometry(1.72, 2.65, 0.1), material(panelColor, {
        opacity: alive(player) ? 0.11 : 0.035, emissive: panelColor, emissiveIntensity: alive(player) ? 1.1 : 0.1
      }), [0, 1.48, 0.48]));
      booth.add(mesh(new THREE.BoxGeometry(1.82, 0.05, 0.12), material(panelColor, {
        emissive: panelColor, emissiveIntensity: 2
      }), [0, 2.78, 0.48]));
      const suspicion = Math.max(0, Number(counts[player.seatNo] || 0));
      if (suspicion) {
        const meter = mesh(new THREE.BoxGeometry(0.13, 0.35 + suspicion * 0.28, 0.13), material(COLORS.red, {
          emissive: COLORS.red, emissiveIntensity: 1.3
        }), [0.68, 0.2 + suspicion * 0.14, 0.56]);
        booth.add(meter);
      }
      const agentKind = revealed && human ? 'human' : 'drone';
      const agent = this.createAgent(player, 0.92, agentKind);
      agent.position.set(0, 0.16, -0.1);
      booth.add(agent);
      this.scene.add(booth);
      const detail = revealed ? (human ? '真人身份揭晓' : 'AI 身份揭晓') : `${suspicion} 票怀疑`;
      this.createLabel(booth, player, detail, `${alive(player) ? '' : 'eliminated'} ${human ? 'human' : ''}`);
    });

    const core = new THREE.Group();
    const phaseIndex = ['ROUND_PROMPT', 'ANSWER_COLLECTION', 'ANSWER_REVEAL', 'SUSPICION_COLLECTION', 'CONTROVERSY_REVEAL', 'QUESTION', 'RESPONSE', 'DEFENSE', 'VOTE_COLLECTION', 'VOTE_REVEAL', 'ROUND_RESULT', 'FINISHED'].indexOf(game.currentPhase);
    const geometry = game.status === 'FINISHED' ? new THREE.IcosahedronGeometry(1.05, 1) : new THREE.OctahedronGeometry(0.95, 1);
    const crystal = mesh(geometry, material(game.status === 'FINISHED' ? COLORS.cyan : COLORS.lime, {
      opacity: 0.46,
      emissive: game.status === 'FINISHED' ? COLORS.cyan : COLORS.lime,
      emissiveIntensity: 1.7,
      metalness: 0.9
    }), [0, 2.35, 0]);
    crystal.userData.rotate = 0.36;
    core.add(crystal);
    for (let index = 0; index < 3; index += 1) {
      const orbit = ring(1.35 + index * 0.38, index <= phaseIndex % 3 ? COLORS.lime : 0x3d544b, 1.4 + index * 0.28, 0.62);
      orbit.rotation.x = Math.PI / 2 + index * 0.55;
      orbit.rotation.z = index * 0.8;
      orbit.userData.rotate = index % 2 ? -0.17 : 0.2;
      core.add(orbit);
      this.animated.push(orbit);
    }
    this.scene.add(core);
    this.animated.push(crystal);
    const spotlight = new THREE.SpotLight(COLORS.lime, 55, 24, 0.26, 0.58, 1.3);
    spotlight.position.set(0, 12, 0);
    spotlight.target.position.set(0, 0, 0);
    this.scene.add(spotlight, spotlight.target);
  }

  update(payload = {}) {
    if (this.disposed) return;
    this.payload = payload;
    this.host.closest('.battle-map, .werewolf-arena, .turing-stage-main')?.classList.add('arena3d-active');
    const players = payload.players || [];
    players.forEach((player) => {
      const group = this.agents.get(String(player.id));
      if (!group) return;
      const previous = group.userData.player || {};
      group.userData.player = player;
      group.userData.pulse.visible = alive(player) && playerIdEquals(player.id, payload.game?.currentActorPlayerId);
      if (group.userData.base?.material) {
        const accent = colorForSeat(player.seatNo);
        group.userData.base.material.emissive.setHex(accent);
        group.userData.base.material.emissiveIntensity = group.userData.pulse.visible ? 0.85 : 0.18;
      }
      if (alive(previous) && downed(player)) {
        this.playAgentAction(group, 'HIT', { duration: 1 });
        this.addTimer(() => { if (!this.disposed) { group.rotation.z = 0.32; group.position.y -= 0.22; } }, 520);
      } else if (!eliminated(previous) && eliminated(player)) {
        this.playAgentAction(group, 'HIT', { duration: 1 });
        this.addTimer(() => { if (!this.disposed) group.rotation.z = Math.PI / 2; }, 520);
      } else if (alive(player)) {
        group.rotation.z = 0;
      }
      if (this.mode === 'battle') {
        if (group.userData.model && group.userData.equipmentSignature !== `${Boolean(player.armed)}:${player.weaponName || ''}`) {
          this.addSurvivorEquipment(group, player, group.userData.scale || 1);
        }
        const target = this.battlePositionForPlayer(player, players);
        if (group.position.distanceTo(target) > 0.08) this.tweenAgent(group, target, 1.35);
      }
      if (this.mode === 'turing') {
        const revealed = payload.game?.identitiesRevealed || payload.game?.status === 'FINISHED';
        const desired = revealed && player.identity === 'HUMAN' ? 'human' : 'drone';
        if (group.userData.desiredAssetKind !== desired) this.swapAgentAsset(group, player, desired);
      }
      this.updatePlayerLabel(player);
    });
    if (this.mode === 'werewolf') {
      this.updateWerewolfTheme();
      this.updateWerewolfRelations();
    }
    if (this.mode === 'battle') {
      const world = window.BattleWorld.resolve(payload.game, payload.events);
      const safe = new Set(world.safeRegions);
      this.updateSupplyBeacon(world.supplyDropRegion);
      this.battleDangerMarkers?.forEach(({ warning, storm }, region) => { warning.visible = storm.visible = !safe.has(region); });
      this.host.dataset.arena3dSafeRegions = [...safe].sort().join(',');
    }
    this.queueLatestMoment(false);
    this.resize();
    if (!this.animateContinuously) {
      this.updateLabels();
      this.renderer.render(this.scene, this.camera);
    }
  }

  updatePlayerLabel(player) {
    const label = this.labels.find((item) => item.playerId === String(player.id));
    if (!label) return;
    const small = label.element.querySelector('small');
    if (this.mode === 'battle' && small) {
      const weapon = player.weaponName ? `${player.weaponName} Lv.${Number(player.weaponLevel || 1)}` : '徒手';
      small.textContent = `${weapon} · HP ${player.health ?? '-'}`;
    } else if (this.mode === 'werewolf' && small) {
      const situation = this.payload.projection?.werewolfSituation || {};
      const stance = (situation.players || []).find((item) => Number(item.seatNo) === Number(player.seatNo));
      small.textContent = stance?.suspects?.[0]?.seatNo
        ? `怀疑 ${Number(stance.suspects[0].seatNo)}号`
        : alive(player) ? '立场观察中' : '已出局';
    } else if (this.mode === 'turing' && small) {
      const revealed = this.payload.game?.identitiesRevealed || this.payload.game?.status === 'FINISHED';
      small.textContent = revealed ? (player.identity === 'HUMAN' ? '真人身份揭晓' : 'AI 身份揭晓') : `${Number(this.payload.game?.publicSuspicionCounts?.[player.seatNo] || 0)} 票怀疑`;
    }
    label.element.classList.toggle('active', playerIdEquals(player.id, this.payload.game?.currentActorPlayerId));
    label.element.classList.toggle('eliminated', this.mode === 'battle' ? eliminated(player) : !alive(player));
  }

  swapAgentAsset(group, player, assetKind) {
    const animation = group.userData.animation;
    if (animation) {
      animation.mixer.stopAllAction();
      this.mixers = this.mixers.filter((mixer) => mixer !== animation.mixer);
    }
    const model = group.userData.model;
    if (model) {
      model.traverse((object) => {
        const values = Array.isArray(object.material) ? object.material : [object.material];
        values.filter(Boolean).forEach((value) => value.dispose?.());
      });
      group.remove(model);
    }
    delete group.userData.animation;
    delete group.userData.model;
    group.userData.desiredAssetKind = assetKind;
    group.children.forEach((child) => {
      if (child.userData.agentPlaceholder) child.visible = true;
    });
    if (this.externalAssetsEnabled) this.attachAgentModel(group, player, group.userData.scale || 1, assetKind);
    else this.markAssetReady(group, player, 'procedural-fallback');
  }

  latestMomentEvent() {
    const events = this.payload.events || [];
    if (this.mode === 'battle') {
      return latest(events, (event) => ['BATTLE_ACTION_EXECUTED', 'BATTLE_PLAYER_REVIVED', 'BATTLE_PLAYER_EXECUTED',
        'BATTLE_OBJECTIVE_CONTRIBUTED', 'BATTLE_OBJECTIVE_COMPLETED', 'BATTLE_RUMOR_SHARED',
        'BATTLE_INFORMATION_TRADE_COMPLETED'].includes(event.eventType));
    }
    return latest(events, (event) => (
      event?.actorPlayerId != null
      && !['AI_DECISION', 'GAME_CREATED', 'GAME_STARTED', 'PHASE_CHANGED'].includes(event.eventType)
    ));
  }

  queueLatestMoment(initial = false) {
    const event = this.latestMomentEvent();
    const sequence = eventSequence(event);
    if (!event || sequence <= this.lastMomentSequence) return;
    this.lastMomentSequence = sequence;
    const actor = this.agents.get(String(event.actorPlayerId));
    if (!actor?.userData.assetReady) {
      this.pendingMoment = event;
      return;
    }
    this.performMoment(event, initial);
  }

  actionForEvent(event) {
    if (this.mode === 'battle') {
      if (event.eventType === 'BATTLE_PLAYER_REVIVED') return 'REVIVE';
      if (event.eventType === 'BATTLE_PLAYER_EXECUTED') return 'EXECUTE';
      if (event.eventType?.startsWith('BATTLE_OBJECTIVE_')) return 'CONTRIBUTE';
      if (event.eventType === 'BATTLE_RUMOR_SHARED') return 'RUMOR';
      if (event.eventType?.startsWith('BATTLE_INFORMATION_TRADE_')) return 'INFO_TRADE';
      return String(event.payload?.action || 'SKIP').toUpperCase();
    }
    const type = String(event.eventType || '').toUpperCase();
    if (/ATTACK|KILL|ELIMINAT/.test(type)) return 'ATTACK';
    if (/VOTE|BALLOT/.test(type)) return 'VOTE';
    if (/GUARD|PROTECT|CHECK|INSPECT|SKILL/.test(type)) return 'GUARD';
    return 'TALK';
  }

  performMoment(event, initial = false) {
    if (this.disposed || this.reducedMotion) return;
    const actor = this.agents.get(String(event.actorPlayerId));
    if (!actor?.userData.assetReady) {
      this.pendingMoment = event;
      return;
    }
    const action = this.actionForEvent(event);
    this.playAgentAction(actor, action);
    const target = this.agents.get(String(event.payload?.targetPlayerId));
    if (target && action === 'ATTACK') {
      const targetPosition = new THREE.Vector3();
      target.getWorldPosition(targetPosition);
      actor.lookAt(targetPosition.x, actor.position.y, targetPosition.z);
      this.addTimer(() => {
        if (!this.disposed) this.playAgentAction(target, 'HIT', { duration: 0.9 });
      }, 360);
    }
    this.showMomentCard(event, action);
    this.focusOn(actor, target);
    this.addActionEffect(actor, target, action);
    if (!initial) this.host.dataset.arena3dLastEvent = String(eventSequence(event));
  }

  showMomentCard(event, action) {
    if (!this.momentCard) return;
    const player = (this.payload.players || []).find((candidate) => playerIdEquals(candidate.id, event.actorPlayerId));
    const payload = event.payload || {};
    let detail = '';
    if (action === 'SEARCH') detail = payload.lootName ? `获得 ${payload.lootName}` : `${payload.region || player?.region || '?'} 区扫描完成`;
    else if (action === 'ATTACK') detail = `${payload.damage || 0} 点伤害${payload.critical ? ' · 暴击' : ''}`;
    else if (action === 'MOVE') detail = `前往 ${payload.region || player?.region || '?'} 区`;
    else if (action === 'HEAL') detail = `生命恢复至 ${payload.health ?? player?.health ?? '-'}`;
    else if (action === 'CAPTURE') detail = '正在控制空投信标';
    else if (action === 'GUARD') detail = `护甲 ${payload.armor ?? player?.armor ?? '-'}`;
    else if (action === 'TALK') detail = '观点已进入公共讨论';
    else if (action === 'VOTE') detail = '投票意向已更新';
    else detail = `${payload.region || player?.region || ''}`;
    this.momentCard.dataset.arena3dMoment = String(eventSequence(event));
    this.momentCard.dataset.action = action.toLowerCase();
    this.momentCard.innerHTML = `<i>${Number(player?.seatNo || 0)}</i><span><small>LIVE ACTION</small><b>${this.escape(player?.displayName || '系统')} · ${this.escape(ACTION_LABELS[action] || action)}</b><em>${this.escape(detail)}</em></span>`;
    this.momentCard.classList.remove('show');
    void this.momentCard.offsetWidth;
    this.momentCard.classList.add('show');
    if (this.momentCardTimer) {
      clearTimeout(this.momentCardTimer);
      this.timers.delete(this.momentCardTimer);
    }
    this.momentCardTimer = this.addTimer(() => this.momentCard?.classList.remove('show'), 2600);
  }

  addActionEffect(actor, target, action) {
    const actorPosition = new THREE.Vector3();
    actor.getWorldPosition(actorPosition);
    actorPosition.y += 0.18;
    if (action === 'ATTACK' && target) {
      const targetPosition = new THREE.Vector3();
      target.getWorldPosition(targetPosition);
      targetPosition.y += 0.9;
      const start = actorPosition.clone().add(new THREE.Vector3(0, 0.8, 0));
      const beam = lineBetween(start, targetPosition, COLORS.red, 1);
      this.scene.add(beam);
      this.addTransient(beam, 0.85, (progress) => {
        beam.material.opacity = 1 - progress;
        beam.scale.setScalar(1 + Math.sin(progress * Math.PI) * 0.025);
      });
      this.pulseAt(targetPosition, COLORS.red, 1.1);
      return;
    }
    const colors = {
      SEARCH: COLORS.cyan,
      HEAL: COLORS.green,
      REST: COLORS.green,
      CAPTURE: COLORS.amber,
      GUARD: COLORS.cyan,
      HIDE: COLORS.violet,
      TALK: COLORS.lime,
      VOTE: COLORS.amber,
      MOVE: COLORS.cyan
      ,REVIVE: COLORS.green, EXECUTE: COLORS.red, CONTRIBUTE: COLORS.cyan,
      RUMOR: COLORS.violet, INFO_TRADE: COLORS.amber
    };
    this.pulseAt(actorPosition, colors[action] || COLORS.lime, action === 'SEARCH' ? 1.55 : 1.15);
  }

  pulseAt(position, color, radius = 1.2) {
    const group = new THREE.Group();
    group.position.copy(position);
    for (let index = 0; index < 3; index += 1) {
      const wave = ring(radius * (0.45 + index * 0.18), color, index * 0.05, 0.9 - index * 0.18);
      wave.userData.baseOpacity = wave.material.opacity;
      group.add(wave);
    }
    this.scene.add(group);
    this.addTransient(group, 1.25, (progress) => {
      const scale = 0.55 + easeInOut(progress) * 1.7;
      group.scale.setScalar(scale);
      group.children.forEach((child) => { child.material.opacity = child.userData.baseOpacity * (1 - progress); });
    });
  }

  addTransient(object, duration, update) {
    this.transients.push({ object, duration, update, startedAt: this.clock.elapsedTime });
  }

  tweenAgent(group, target, duration) {
    this.positionTweens = this.positionTweens.filter((tween) => tween.group !== group);
    if (!this.animateContinuously) {
      group.position.copy(target);
      return;
    }
    const from = group.position.clone();
    const direction = target.clone().sub(from);
    if (direction.lengthSq() > 0.01) group.rotation.y = Math.atan2(direction.x, direction.z);
    this.positionTweens.push({ group, from, target: target.clone(), duration, startedAt: this.clock.elapsedTime });
    this.playAgentAction(group, 'MOVE', { duration });
  }

  focusOn(actor, target) {
    const focus = new THREE.Vector3();
    actor.getWorldPosition(focus);
    if (target) {
      const targetPosition = new THREE.Vector3();
      target.getWorldPosition(targetPosition);
      focus.lerp(targetPosition, 0.5);
    }
    focus.y += 0.85;
    this.cameraFocus = {
      baseTarget: this.cameraTarget.clone(),
      target: focus,
      baseDistance: this.cameraState.distance,
      startedAt: this.clock.elapsedTime,
      duration: 2.6
    };
  }

  cancelCameraFocus() {
    if (!this.cameraFocus) return;
    this.cameraTarget.copy(this.cameraFocus.baseTarget);
    this.cameraState.distance = this.cameraFocus.baseDistance;
    this.cameraFocus = null;
    this.updateCamera();
  }

  addTimer(callback, delay) {
    const timer = window.setTimeout(() => {
      this.timers.delete(timer);
      callback();
    }, delay);
    this.timers.add(timer);
    return timer;
  }

  resize() {
    if (!this.renderer || !this.host.isConnected) return;
    const width = Math.max(1, this.host.clientWidth);
    const height = Math.max(1, this.host.clientHeight);
    if (width <= 720 && !this.mobileCameraApplied) {
      const multiplier = this.mode === 'werewolf' ? 1.48 : this.mode === 'turing' ? 1.16 : 1.18;
      this.cameraState.distance *= multiplier;
      this.cameraDefault.distance *= multiplier;
      this.mobileCameraApplied = true;
      this.updateCamera();
    }
    this.camera.fov = this.mode === 'battle' ? (width <= 720 ? 58 : 42) : width <= 720 && this.mode === 'werewolf' ? 50 : 38;
    this.camera.aspect = width / height;
    this.camera.updateProjectionMatrix();
    this.renderer.setSize(width, height, false);
    // Resizing clears the drawing buffer, including stages without an animation loop.
    if (!this.animateContinuously) {
      this.renderer.render(this.scene, this.camera);
      this.updateLabels();
    }
  }

  updateLabels() {
    const width = this.host.clientWidth;
    const height = this.host.clientHeight;
    this.labels.forEach(({ element, object, offset }) => {
      const position = new THREE.Vector3();
      object.getWorldPosition(position);
      position.y += offset;
      position.project(this.camera);
      const visible = position.z > -1 && position.z < 1;
      const labelMargin = element.classList.contains('active') && width <= 720 ? 54 : 18;
      const x = THREE.MathUtils.clamp((position.x * 0.5 + 0.5) * width, labelMargin, width - labelMargin);
      const y = THREE.MathUtils.clamp((-position.y * 0.5 + 0.5) * height, 30, height - 6);
      element.style.transform = `translate3d(${x}px, ${y}px, 0) translate(-50%, -100%)`;
      element.style.opacity = visible ? '1' : '0';
      element.style.pointerEvents = visible ? 'auto' : 'none';
    });
  }

  animate() {
    if (!this.renderer || !this.host.isConnected) {
      this.dispose();
      return;
    }
    if (!this.reducedMotion && (document.hidden || this.host.getClientRects().length === 0)) {
      this.clock.getDelta();
      this.animationFrame = requestAnimationFrame(() => this.animate());
      return;
    }
    const frameStartedAt = performance.now();
    const delta = Math.min(0.05, this.clock.getDelta());
    const elapsed = this.clock.elapsedTime;
    if (!this.reducedMotion) {
      this.mixers.forEach((mixer) => mixer.update(delta));
      this.agents.forEach((group) => {
        const model = group.userData.model;
        if (this.mode === 'battle' && model?.userData.reaction) {
          const reaction = model.userData.reaction;
          const progress = Math.min(1, (elapsed - reaction.startedAt) / 0.65);
          model.rotation.x = Math.sin(progress * Math.PI) * (reaction.action === 'HIT' ? 0.18 : reaction.action === 'ATTACK' ? -0.16 : 0);
          if (progress === 1) delete model.userData.reaction;
        }
        const state = group.userData.animation;
        if (state?.resumeAt > 0 && elapsed >= state.resumeAt) {
          state.resumeAt = 0;
          this.playAgentAction(group, 'IDLE');
        }
      });
      this.positionTweens = this.positionTweens.filter((tween) => {
        const progress = (elapsed - tween.startedAt) / tween.duration;
        tween.group.position.lerpVectors(tween.from, tween.target, easeInOut(progress));
        if (progress < 1) return true;
        tween.group.position.copy(tween.target);
        tween.group.rotation.y = 0;
        this.playAgentAction(tween.group, 'IDLE');
        return false;
      });
      this.transients = this.transients.filter((transient) => {
        const progress = (elapsed - transient.startedAt) / transient.duration;
        transient.update(THREE.MathUtils.clamp(progress, 0, 1));
        if (progress < 1) return true;
        transient.object.removeFromParent();
        transient.object.traverse((object) => {
          object.geometry?.dispose?.();
          if (Array.isArray(object.material)) object.material.forEach((value) => value.dispose?.());
          else object.material?.dispose?.();
        });
        return false;
      });
      if (this.cameraFocus) {
        const progress = (elapsed - this.cameraFocus.startedAt) / this.cameraFocus.duration;
        let strength;
        if (progress < 0.2) strength = easeInOut(progress / 0.2);
        else if (progress < 0.72) strength = 1;
        else strength = 1 - easeInOut((progress - 0.72) / 0.28);
        this.cameraTarget.lerpVectors(this.cameraFocus.baseTarget, this.cameraFocus.target, strength);
        this.cameraState.distance = THREE.MathUtils.lerp(
          this.cameraFocus.baseDistance,
          Math.max(10.5, this.cameraFocus.baseDistance * 0.84),
          strength
        );
        this.updateCamera();
        if (progress >= 1) this.cancelCameraFocus();
      }
      this.animated.forEach((item, index) => {
        if (item.userData.rotate) item.rotation.z += item.userData.rotate * delta;
        if (item.userData.pulse) {
          const scale = 1 + Math.sin(elapsed * 2.4 + index) * 0.12;
          item.scale.set(scale, scale, scale);
          if (item.material) item.material.opacity = 0.42 + Math.sin(elapsed * 2.4 + index) * 0.18;
        }
        if (item.userData.flow) item.material.opacity = 0.5 + Math.sin(elapsed * 4 + index) * 0.35;
      });
      this.scene.children.filter((item) => item.userData.particles).forEach((points) => {
        points.rotation.y = elapsed * 0.012;
      });
    }
    if (this.cameraFocus || this.positionTweens.length || elapsed - this.lastLabelUpdate >= 0.05) {
      this.updateLabels();
      this.lastLabelUpdate = elapsed;
    }
    this.renderer.render(this.scene, this.camera);
    this.frameCostTotal += performance.now() - frameStartedAt;
    this.frameCostSamples += 1;
    if (this.frameCostSamples >= 45) {
      this.host.dataset.arena3dRenderMs = (this.frameCostTotal / this.frameCostSamples).toFixed(1);
      this.frameCostTotal = 0;
      this.frameCostSamples = 0;
    }
    if (this.animateContinuously) this.animationFrame = requestAnimationFrame(() => this.animate());
  }

  dispose() {
    if (this.disposed) return;
    this.disposed = true;
    cancelAnimationFrame(this.animationFrame);
    this.timers.forEach((timer) => clearTimeout(timer));
    this.timers.clear();
    this.disposables.forEach(dispose => dispose());
    this.disposables.length = 0;
    this.resizeObserver?.disconnect();
    this.host?.removeEventListener('pointerdown', this.onPointerDown);
    this.host?.removeEventListener('pointermove', this.onPointerMove);
    this.host?.removeEventListener('pointerup', this.onPointerUp);
    this.host?.removeEventListener('pointercancel', this.onPointerUp);
    this.host?.removeEventListener('wheel', this.onWheel);
    this.mixers.forEach((mixer) => mixer.stopAllAction());
    this.mixers.length = 0;
    this.scene?.traverse((object) => {
      if (!object.userData.externalAsset) object.geometry?.dispose?.();
      if (Array.isArray(object.material)) object.material.forEach((value) => value.dispose?.());
      else object.material?.dispose?.();
    });
    this.renderer?.dispose();
    this.renderer?.forceContextLoss?.();
  }

  escape(value) {
    const span = document.createElement('span');
    span.textContent = String(value ?? '');
    return span.innerHTML;
  }
}

function mount(host, mode, payload) {
  if (!host) return null;
  const existing = ACTIVE_STAGES.get(host);
  const sameGame = playerIdEquals(existing?.payload?.game?.id, payload?.game?.id);
  const survivalStyle = mode === 'battle' && payload?.presentation?.style === 'survival';
  if (existing && existing.mode === mode && sameGame && !existing.disposed
      && (existing instanceof SurvivalStage) === survivalStyle) {
    existing.update(payload || {});
    return existing;
  }
  existing?.dispose();
  const stage = survivalStyle ? new SurvivalStage(host, payload || {}) : new ArenaStage(host, mode, payload || {});
  ACTIVE_STAGES.set(host, stage);
  return stage;
}

window.AgentArena3D = { mount };
window.dispatchEvent(new CustomEvent('agent-arena-3d-ready'));
