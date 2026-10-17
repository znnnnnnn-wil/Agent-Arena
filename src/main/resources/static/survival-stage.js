import * as THREE from './vendor/three.module.min.js';
import { makeForest, poseForest, blend } from './survival-world.js';

export class SurvivalStage {
  constructor(host, payload) {
    this.host = host; this.payload = payload; this.mode = 'battle'; this.disposed = false;
    this.reducedMotion = matchMedia('(prefers-reduced-motion: reduce)').matches;
    this.lowPower = (navigator.hardwareConcurrency || 8) <= 4 || matchMedia('(max-width:720px)').matches;
    this.labels = []; this.frame = null; this.drag = null; this.manualCamera = null;
    this.presentation = { chapter: 0, localTime: 0, playing: false }; this.sampledAt = performance.now();
    this.scene = new THREE.Scene(); this.scene.background = new THREE.Color(0x929c90);
    this.scene.fog = new THREE.FogExp2(0x929c90, .033);
    this.camera = new THREE.PerspectiveCamera(42, 1, .1, 140);
    try { this.renderer = new THREE.WebGLRenderer({ antialias: true, alpha: false, powerPreference: 'high-performance' }); }
    catch { host.classList.add('arena3d-unavailable'); host.innerHTML = '<p>当前设备无法启用 WebGL，已保留战术地图和决策解说。</p>'; return; }
    this.renderer.setPixelRatio(Math.min(devicePixelRatio || 1, this.lowPower ? 1 : 1.5));
    this.renderer.outputColorSpace = THREE.SRGBColorSpace; this.renderer.toneMapping = THREE.ACESFilmicToneMapping;
    this.renderer.toneMappingExposure = 1.06; this.renderer.shadowMap.enabled = true;
    this.renderer.shadowMap.type = THREE.PCFSoftShadowMap;
    this.renderer.domElement.setAttribute('aria-hidden', 'true');
    host.classList.add('is-ready', 'survival-forest'); host.replaceChildren(this.renderer.domElement);
    this.scene.add(new THREE.HemisphereLight(0xe4e8da, 0x65705d, 1.95));
    const sun = new THREE.DirectionalLight(0xffdfb8, 2.8); sun.position.set(-5, 10, 5); sun.castShadow = true;
    sun.shadow.mapSize.set(this.lowPower ? 1024 : 2048, this.lowPower ? 1024 : 2048);
    Object.assign(sun.shadow.camera, { left: -14, right: 14, top: 14, bottom: -14, near: .5, far: 38 });
    sun.shadow.bias = -.0004; sun.shadow.normalBias = .025; sun.shadow.radius = 3; this.scene.add(sun);
    const fill = new THREE.DirectionalLight(0xb7c8b8, .65); fill.position.set(5, 4, -4); this.scene.add(fill);
    this.world = makeForest(this.scene, payload.players || []);
    this.labelLayer = document.createElement('div'); this.labelLayer.className = 'arena3d-label-layer'; host.append(this.labelLayer);
    this.controls = document.createElement('div'); this.controls.className = 'arena3d-controls';
    this.controls.innerHTML = '<span>拖动旋转 · 滚轮缩放</span><button type="button" aria-label="重置 3D 镜头">重置镜头</button>'; host.append(this.controls);
    this.world.actors.forEach(actor => this.createLabel(actor));
    host.dataset.arena3dAssets = String(this.world.actors.length); host.dataset.arena3dAssetMode = 'procedural-rigged';
    host.dataset.arena3dMapStyle = 'forest-survival'; host.dataset.arena3dMotion = 'articulated'; host.dataset.arena3dScenery = 'ready';
    this.bindControls(); this.resizeObserver = new ResizeObserver(() => this.resize()); this.resizeObserver.observe(host);
    this.update(payload); this.resize();
  }

  createLabel(actor) {
    const button = document.createElement('button'); button.type = 'button'; button.className = 'arena3d-label';
    button.dataset.arena3dPlayer = String(actor.player.id);
    const number = document.createElement('i'); number.textContent = String(actor.player.seatNo);
    const text = document.createElement('span'), name = document.createElement('b'), detail = document.createElement('small');
    name.textContent = actor.player.displayName; text.append(name, detail); button.append(number, text);
    button.addEventListener('click', () => this.host.dispatchEvent(new CustomEvent('arena3d:select-player', {
      bubbles: true, detail: { playerId: actor.player.id, seatNo: actor.player.seatNo }
    })));
    this.labelLayer.append(button); this.labels.push({ actor, button, detail, name });
  }

  update(payload) {
    if (this.disposed || !this.renderer) return;
    this.payload = payload;
    const players = new Map((payload.players || []).map(player => [String(player.id), player]));
    this.world.actors.forEach(actor => { actor.player = players.get(String(actor.player.id)) || actor.player; });
    const presentation = payload.presentation || {};
    this.setPresentation({ chapter: presentation.chapter || 0, localTime: presentation.localTime || 0, playing: presentation.playing === true });
  }

  setPresentation(presentation) {
    if (this.disposed || !this.renderer) return;
    const next = { ...this.presentation, ...presentation };
    const beat = next.localTime < 6 ? 0 : next.localTime < 13 ? 1 : 2;
    const unchangedStaticPose = this.reducedMotion && next.chapter === this.presentation.chapter && beat === this.lastBeat;
    this.presentation = next; this.lastBeat = beat; this.sampledAt = performance.now();
    this.host.dataset.survivalChapter = String(next.chapter);
    this.host.dataset.survivalAction = beat ? ['REVIVE', 'GUARD', 'FULFILL_PROMISE'][next.chapter] : 'APPROACH';
    this.host.dataset.survivalTargetState = next.chapter === 0 && beat === 2 || next.chapter === 2 ? 'ALIVE' : 'DOWNED';
    if (!next.playing || this.reducedMotion) {
      cancelAnimationFrame(this.frame); this.frame = null;
      if (!unchangedStaticPose) this.draw(next.localTime);
    } else { this.draw(next.localTime); this.schedule(); }
  }

  schedule() {
    if (this.frame !== null || this.disposed) return;
    this.frame = requestAnimationFrame(now => {
      this.frame = null;
      if (!this.disposed && this.presentation.playing && !this.reducedMotion) {
        if (!document.hidden && this.host.getClientRects().length) this.draw(Math.min(20, this.presentation.localTime + (now - this.sampledAt) / 1000));
        this.schedule();
      }
    });
  }

  draw(seconds = this.presentation.localTime) {
    if (!this.renderer || this.disposed) return;
    poseForest(this.world, this.presentation.chapter, seconds, this.reducedMotion);
    const focus = blend(2, 7, seconds) * (1 - blend(17, 20, seconds));
    const mobile = this.host.clientWidth <= 540;
    const distance = this.manualCamera?.distance || THREE.MathUtils.lerp(mobile ? 20 : 18.5, mobile ? 13.2 : 11.4, focus);
    const yaw = this.manualCamera?.yaw ?? -.57, pitch = this.manualCamera?.pitch ?? .44;
    const target = new THREE.Vector3(.6, .75, 0);
    this.camera.position.set(target.x + Math.sin(yaw) * Math.cos(pitch) * distance,
      target.y + Math.sin(pitch) * distance, target.z + Math.cos(yaw) * Math.cos(pitch) * distance);
    this.camera.lookAt(target); this.lastCamera = { yaw, pitch, distance };
    this.renderer.render(this.scene, this.camera); this.updateLabels(seconds);
  }

  updateLabels(seconds) {
    const width = this.host.clientWidth, height = this.host.clientHeight;
    const poseTime = this.reducedMotion ? seconds < 6 ? 0 : seconds < 13 ? 10 : 19 : seconds;
    this.labels.forEach(({ actor, button, detail, name }, index) => {
      const downed = index === 1 && (this.presentation.chapter === 1 || this.presentation.chapter === 0 && poseTime < 16.8);
      const position = new THREE.Vector3(); actor.root.getWorldPosition(position);
      position.y += downed ? .4 : 2; position.project(this.camera);
      const visible = position.z > -1 && position.z < 1 && Math.abs(position.x) < 1.07 && Math.abs(position.y) < 1.05;
      const offsetX = index === 0 ? -32 : index === 1 ? 48 : 0;
      const offsetY = index === 1 ? 52 : 0;
      button.style.transform = `translate3d(${THREE.MathUtils.clamp((position.x * .5 + .5) * width + offsetX, 30, width - 30)}px,${THREE.MathUtils.clamp((-position.y * .5 + .5) * height + offsetY, 45, height - 12)}px,0) translate(-50%,-100%)`;
      button.style.visibility = visible ? 'visible' : 'hidden'; button.classList.toggle('active', index === 0);
      button.classList.toggle('downed', downed); button.classList.toggle('supporting', index > 1);
      name.textContent = actor.player.displayName;
      detail.textContent = index === 0 ? seconds < 6 ? '接近' : ['救援', '防御', '兑现承诺'][this.presentation.chapter] : downed ? '倒地' : `HP ${actor.player.health ?? 80}`;
      button.setAttribute('aria-label', `${actor.player.displayName} · ${downed ? '倒地待援' : `HP ${actor.player.health ?? 80}`} · ${detail.textContent}`);
    });
  }

  resize() {
    if (!this.renderer || this.disposed) return;
    const width = Math.max(1, this.host.clientWidth), height = Math.max(1, this.host.clientHeight);
    this.renderer.setSize(width, height, false); this.camera.aspect = width / height;
    this.camera.fov = width <= 540 ? 51 : 42; this.camera.updateProjectionMatrix(); this.draw();
  }

  bindControls() {
    this.pointerDown = event => {
      if (event.target.closest('button, .arena3d-controls')) return;
      this.manualCamera = { ...this.lastCamera }; this.drag = { x: event.clientX, y: event.clientY, ...this.manualCamera };
      this.host.setPointerCapture?.(event.pointerId); this.host.classList.add('is-dragging');
    };
    this.pointerMove = event => {
      if (!this.drag) return;
      this.manualCamera.yaw = this.drag.yaw - (event.clientX - this.drag.x) * .006;
      this.manualCamera.pitch = THREE.MathUtils.clamp(this.drag.pitch + (event.clientY - this.drag.y) * .004, .23, 1.04); this.draw();
    };
    this.pointerUp = () => { this.drag = null; this.host.classList.remove('is-dragging'); };
    this.wheel = event => {
      event.preventDefault(); this.manualCamera ||= { ...this.lastCamera };
      this.manualCamera.distance = THREE.MathUtils.clamp(this.manualCamera.distance + event.deltaY * .01, 8.5, 28); this.draw();
    };
    this.resetCamera = () => { this.manualCamera = null; this.draw(); };
    this.host.addEventListener('pointerdown', this.pointerDown); this.host.addEventListener('pointermove', this.pointerMove);
    this.host.addEventListener('pointerup', this.pointerUp); this.host.addEventListener('pointercancel', this.pointerUp);
    this.host.addEventListener('wheel', this.wheel, { passive: false }); this.controls.querySelector('button').addEventListener('click', this.resetCamera);
  }

  dispose() {
    if (this.disposed) return; this.disposed = true; cancelAnimationFrame(this.frame); this.resizeObserver?.disconnect();
    this.host.removeEventListener('pointerdown', this.pointerDown); this.host.removeEventListener('pointermove', this.pointerMove);
    this.host.removeEventListener('pointerup', this.pointerUp); this.host.removeEventListener('pointercancel', this.pointerUp);
    this.host.removeEventListener('wheel', this.wheel); this.controls?.querySelector('button')?.removeEventListener('click', this.resetCamera);
    const geometries = new Set(), materials = new Set(), textures = new Set();
    this.scene?.traverse(object => {
      if (object.isInstancedMesh) object.dispose(); if (object.geometry) geometries.add(object.geometry);
      for (const material of [object.material].flat().filter(Boolean)) materials.add(material);
    });
    materials.forEach(material => { Object.values(material).forEach(value => { if (value?.isTexture) textures.add(value); }); material.dispose(); });
    geometries.forEach(geometry => geometry.dispose()); textures.forEach(texture => texture.dispose());
    this.renderer?.dispose(); this.renderer?.forceContextLoss();
    this.host.replaceChildren(); this.host.classList.remove('is-ready', 'survival-forest');
  }
}
