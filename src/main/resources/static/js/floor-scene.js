// Draws a ParkFlow floor (walls, columns, parking spaces, cameras) with three.js.
// Shared by the provider's floor editor and the live floor map.
// Floor coordinates are metres from the floor's corner: x along the width, z along the length.
// Angles are degrees seen from above: 0 points towards +x, 90 towards +z.
import * as THREE from "three";
import { OrbitControls } from "../vendor/three/OrbitControls.js";

export const WALL_HEIGHT = 3.0;
export const CAMERA_HEIGHT = 2.6;
/** The camera cone is only a guide for the provider; the real view depends on the lens. */
export const CONE_ANGLE = 100;
export const CONE_RANGE = 14;

export const COLORS = {
  floor: 0x9a9fa3, grid: 0x7c8186, wall: 0xd9dbd6, column: 0xc9ccc6,
  space: 0x5d646b, line: 0xf4f4f2, selected: 0xf2c230,
  free: 0x2e9e5b, taken: 0xc8423b, unknown: 0x8a9096,
};
/** One colour per camera, in order. */
export const CAMERA_PALETTE = [0x1e4f9c, 0xe08a1e, 0x8e44ad, 0x16a3a3, 0xd6336c, 0x6b8e23, 0x795548, 0x3949ab];

export function cameraColor(index) { return CAMERA_PALETTE[index % CAMERA_PALETTE.length]; }
export function hex(c) { return "#" + c.toString(16).padStart(6, "0"); }
const rad = (deg) => (deg * Math.PI) / 180;

/** Label texture drawn on top of a parking space. */
function labelTexture(text, color) {
  const c = document.createElement("canvas");
  c.width = 256; c.height = 128;
  const g = c.getContext("2d");
  g.fillStyle = color || "#ffffff";
  g.font = "700 72px 'Barlow Condensed', 'Arial Narrow', sans-serif";
  g.textAlign = "center"; g.textBaseline = "middle";
  g.fillText(text, 128, 66, 240);
  const t = new THREE.CanvasTexture(c);
  t.anisotropy = 4;
  return t;
}

export function createFloorScene(container) {
  const renderer = new THREE.WebGLRenderer({ antialias: true });
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
  renderer.setClearColor(0xdaddd8);
  container.appendChild(renderer.domElement);
  renderer.domElement.tabIndex = 0;

  const scene = new THREE.Scene();
  const camera = new THREE.PerspectiveCamera(45, 1, 0.1, 2000);
  scene.add(new THREE.HemisphereLight(0xffffff, 0x8a8f94, 1.6));
  const sun = new THREE.DirectionalLight(0xffffff, 1.4);
  sun.position.set(30, 60, 20);
  scene.add(sun);

  const controls = new OrbitControls(camera, renderer.domElement);
  controls.enableDamping = true;
  controls.dampingFactor = 0.15;
  controls.maxPolarAngle = Math.PI / 2 - 0.05;
  controls.mouseButtons = { LEFT: THREE.MOUSE.PAN, MIDDLE: THREE.MOUSE.PAN, RIGHT: THREE.MOUSE.ROTATE };
  controls.screenSpacePanning = true;

  const root = new THREE.Group();
  scene.add(root);
  const groups = {};      // "kind:uid" -> THREE.Group
  const pickables = [];   // meshes that can be clicked
  let floorSize = { width: 60, length: 40 };
  let floorMesh = null;
  const preview = new THREE.Group();
  scene.add(preview);

  function disposeTree(obj) {
    obj.traverse((o) => {
      if (o.geometry) o.geometry.dispose();
      if (o.material) {
        const ms = Array.isArray(o.material) ? o.material : [o.material];
        ms.forEach((m) => { if (m.map) m.map.dispose(); m.dispose(); });
      }
    });
  }

  function clear() {
    root.children.slice().forEach((c) => { root.remove(c); disposeTree(c); });
    Object.keys(groups).forEach((k) => delete groups[k]);
    pickables.length = 0;
  }

  function tag(mesh, kind, uid) { mesh.userData = { kind, uid }; pickables.push(mesh); return mesh; }

  // ---------- builders, one per kind; each returns a Group placed by place() ----------

  function buildWall(w) {
    const g = new THREE.Group();
    const len = Math.max(0.05, Math.hypot(w.x2 - w.x1, w.z2 - w.z1));
    const m = new THREE.Mesh(new THREE.BoxGeometry(len, WALL_HEIGHT, w.thickness),
      new THREE.MeshStandardMaterial({ color: COLORS.wall, roughness: 0.9 }));
    m.position.y = WALL_HEIGHT / 2;
    g.add(tag(m, "wall", w.uid));
    return g;
  }

  function buildColumn(c) {
    const g = new THREE.Group();
    const m = new THREE.Mesh(new THREE.BoxGeometry(c.width, WALL_HEIGHT, c.depth),
      new THREE.MeshStandardMaterial({ color: COLORS.column, roughness: 0.85 }));
    m.position.y = WALL_HEIGHT / 2;
    g.add(tag(m, "column", c.uid));
    // yellow safety band, like real garage columns
    const band = new THREE.Mesh(new THREE.BoxGeometry(c.width + 0.02, 0.5, c.depth + 0.02),
      new THREE.MeshStandardMaterial({ color: COLORS.selected, roughness: 0.7 }));
    band.position.y = 0.4;
    g.add(band);
    return g;
  }

  function buildSpace(s, style) {
    const g = new THREE.Group();
    const fill = new THREE.Mesh(new THREE.BoxGeometry(s.width, 0.04, s.length),
      new THREE.MeshStandardMaterial({ color: style.fill, transparent: style.opacity < 1, opacity: style.opacity, roughness: 0.8 }));
    fill.position.y = 0.02;
    g.add(tag(fill, "space", s.uid));
    const edges = new THREE.LineSegments(new THREE.EdgesGeometry(new THREE.BoxGeometry(s.width, 0.04, s.length)),
      new THREE.LineBasicMaterial({ color: style.edge }));
    edges.position.y = 0.02;
    g.add(edges);
    if (style.outline) {
      const o = new THREE.Mesh(new THREE.BoxGeometry(s.width + 0.25, 0.03, s.length + 0.25),
        new THREE.MeshBasicMaterial({ color: COLORS.selected }));
      o.position.y = 0.01;
      g.add(o);
    }
    const size = Math.min(s.width * 0.95, s.length * 0.6);
    const label = new THREE.Mesh(new THREE.PlaneGeometry(size * 2, size),
      new THREE.MeshBasicMaterial({ map: labelTexture(s.label, style.text), transparent: true, depthWrite: false }));
    label.rotation.x = -Math.PI / 2;
    label.rotation.z = s.length >= s.width ? Math.PI / 2 : 0; // read along the long side
    label.position.y = 0.05;
    g.add(label);
    return g;
  }

  function buildCamera(c, style) {
    const g = new THREE.Group();
    const body = new THREE.Mesh(new THREE.BoxGeometry(0.45, 0.3, 0.3),
      new THREE.MeshStandardMaterial({ color: style.color, roughness: 0.5 }));
    body.position.set(0.15, CAMERA_HEIGHT, 0);
    g.add(tag(body, "camera", c.uid));
    const lens = new THREE.Mesh(new THREE.CylinderGeometry(0.1, 0.1, 0.12, 16),
      new THREE.MeshStandardMaterial({ color: 0x111111 }));
    lens.rotation.z = Math.PI / 2;
    lens.position.set(0.42, CAMERA_HEIGHT, 0);
    g.add(lens);
    // A big invisible box so the camera is easy to click from above.
    const hit = new THREE.Mesh(new THREE.BoxGeometry(1.2, CAMERA_HEIGHT + 0.4, 1.2), new THREE.MeshBasicMaterial({ visible: false }));
    hit.position.y = (CAMERA_HEIGHT + 0.4) / 2;
    g.add(tag(hit, "camera", c.uid));
    // Coverage fan on the floor (a guide only).
    const fan = new THREE.Mesh(new THREE.CircleGeometry(CONE_RANGE, 32, -rad(CONE_ANGLE / 2), rad(CONE_ANGLE)),
      new THREE.MeshBasicMaterial({ color: style.color, transparent: true, opacity: style.selected ? 0.22 : 0.1, depthWrite: false, side: THREE.DoubleSide }));
    fan.rotation.x = -Math.PI / 2;
    fan.position.y = 0.06;
    g.add(fan);
    const ray = new THREE.Line(new THREE.BufferGeometry().setFromPoints([
      new THREE.Vector3(0.4, CAMERA_HEIGHT, 0), new THREE.Vector3(CONE_RANGE * 0.5, 0.06, 0)]),
      new THREE.LineBasicMaterial({ color: style.color }));
    g.add(ray);
    if (style.selected) {
      const ring = new THREE.Mesh(new THREE.RingGeometry(0.55, 0.75, 32), new THREE.MeshBasicMaterial({ color: COLORS.selected, side: THREE.DoubleSide }));
      ring.rotation.x = -Math.PI / 2;
      ring.position.y = 0.07;
      g.add(ring);
    }
    return g;
  }

  /** Moves and turns an item's group to match its numbers (used while dragging, without rebuilding). */
  function place(kind, item) {
    const g = groups[kind + ":" + item.uid];
    if (!g) return;
    if (kind === "wall") {
      g.position.set((item.x1 + item.x2) / 2, 0, (item.z1 + item.z2) / 2);
      g.rotation.y = -Math.atan2(item.z2 - item.z1, item.x2 - item.x1);
    } else if (kind === "camera") {
      g.position.set(item.x, 0, item.z);
      g.rotation.y = -rad(item.direction);
    } else if (kind === "space") {
      g.position.set(item.x, 0, item.z);
      g.rotation.y = -rad(item.rotation);
    } else {
      g.position.set(item.x, 0, item.z);
    }
  }

  function add(kind, item, group) {
    groups[kind + ":" + item.uid] = group;
    root.add(group);
    place(kind, item);
  }

  /**
   * Redraws everything. look = { space(s) -> {fill, edge, text, opacity, outline}, camera(c, index) -> {color, selected} }.
   */
  function render(state, look) {
    clear();
    floorSize = { width: state.width, length: state.length };
    floorMesh = new THREE.Mesh(new THREE.PlaneGeometry(state.width, state.length),
      new THREE.MeshStandardMaterial({ color: COLORS.floor, roughness: 1 }));
    floorMesh.rotation.x = -Math.PI / 2;
    floorMesh.position.set(state.width / 2, 0, state.length / 2);
    floorMesh.userData = { kind: "floor" };
    root.add(floorMesh);
    const step = 1;
    const grid = new THREE.GridHelper(Math.max(state.width, state.length), Math.max(state.width, state.length) / step, COLORS.grid, COLORS.grid);
    grid.material.transparent = true; grid.material.opacity = 0.35;
    grid.position.set(Math.max(state.width, state.length) / 2, 0.005, Math.max(state.width, state.length) / 2);
    // clip the square grid to the floor rectangle
    grid.material.clippingPlanes = [
      new THREE.Plane(new THREE.Vector3(-1, 0, 0), state.width), new THREE.Plane(new THREE.Vector3(0, 0, -1), state.length)];
    renderer.localClippingEnabled = true;
    root.add(grid);

    state.walls.forEach((w) => add("wall", w, buildWall(w)));
    state.columns.forEach((c) => add("column", c, buildColumn(c)));
    state.spaces.forEach((s) => add("space", s, buildSpace(s, look.space(s))));
    state.cameras.forEach((c, i) => add("camera", c, buildCamera(c, look.camera(c, i))));
  }

  // ---------- picking ----------

  const raycaster = new THREE.Raycaster();
  const ndc = new THREE.Vector2();
  const groundPlane = new THREE.Plane(new THREE.Vector3(0, 1, 0), 0);

  function aim(event) {
    const r = renderer.domElement.getBoundingClientRect();
    ndc.x = ((event.clientX - r.left) / r.width) * 2 - 1;
    ndc.y = -((event.clientY - r.top) / r.height) * 2 + 1;
    raycaster.setFromCamera(ndc, camera);
  }

  /** The closest clickable thing under the mouse, optionally only of some kinds. */
  function pick(event, kinds) {
    aim(event);
    const hits = raycaster.intersectObjects(pickables, false);
    for (const h of hits) {
      const k = h.object.userData.kind;
      if (kinds && kinds.indexOf(k) < 0) continue;
      let normal = null;
      if (h.face) normal = h.face.normal.clone().transformDirection(h.object.matrixWorld);
      return { kind: k, uid: h.object.userData.uid, point: h.point, normal };
    }
    return null;
  }

  /** Where the mouse points on the floor (y = 0), in floor coordinates. */
  function groundPoint(event) {
    aim(event);
    const p = new THREE.Vector3();
    return raycaster.ray.intersectPlane(groundPlane, p) ? { x: p.x, z: p.z } : null;
  }

  // ---------- preview shapes used by the editor tools ----------

  function clearPreview() { preview.children.slice().forEach((c) => { preview.remove(c); disposeTree(c); }); }
  function previewWall(x1, z1, x2, z2, t) {
    clearPreview();
    const len = Math.hypot(x2 - x1, z2 - z1);
    if (len < 0.05) return;
    const m = new THREE.Mesh(new THREE.BoxGeometry(len, WALL_HEIGHT, t),
      new THREE.MeshBasicMaterial({ color: COLORS.selected, transparent: true, opacity: 0.55 }));
    m.position.set((x1 + x2) / 2, WALL_HEIGHT / 2, (z1 + z2) / 2);
    m.rotation.y = -Math.atan2(z2 - z1, x2 - x1);
    preview.add(m);
  }
  function previewBox(x, z, w, l, rotDeg, h) {
    clearPreview();
    const m = new THREE.Mesh(new THREE.BoxGeometry(w, h || 0.05, l),
      new THREE.MeshBasicMaterial({ color: COLORS.selected, transparent: true, opacity: 0.5 }));
    m.position.set(x, (h || 0.05) / 2, z);
    m.rotation.y = -rad(rotDeg || 0);
    preview.add(m);
  }

  // ---------- views ----------

  function viewTop() {
    const { width, length } = floorSize;
    const d = Math.max(width / camera.aspect, length) / (2 * Math.tan(rad(camera.fov / 2))) * 1.08;
    controls.target.set(width / 2, 0, length / 2);
    camera.position.set(width / 2, d, length / 2 + 0.001);
    camera.up.set(0, 1, 0);
    controls.update();
  }
  function view3d() {
    const { width, length } = floorSize;
    const d = Math.max(width, length) * 0.9;
    controls.target.set(width / 2, 0, length / 2);
    camera.position.set(width / 2 - d * 0.35, d * 0.75, length / 2 + d * 0.9);
    controls.update();
  }

  function resize() {
    const w = container.clientWidth || 1, h = container.clientHeight || 1;
    renderer.setSize(w, h, false);
    camera.aspect = w / h;
    camera.updateProjectionMatrix();
  }
  new ResizeObserver(resize).observe(container);
  resize();

  renderer.setAnimationLoop(() => { controls.update(); renderer.render(scene, camera); });

  return { THREE, scene, camera, renderer, controls, render, place, pick, groundPoint, previewWall, previewBox, clearPreview, viewTop, view3d, resize };
}
