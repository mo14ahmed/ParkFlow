// The provider's 3D floor editor: walls, columns, parking spaces and cameras on one floor.
// Saves the whole floor with PUT /api/provider/buildings/{id}/floor.
import { createFloorScene, COLORS, cameraColor, hex } from "./floor-scene.js";

const PF = window.PF;
const $ = (id) => document.getElementById(id);

const HINTS = {
  select: "Click something to select it, then drag it or change its numbers. Drag empty floor to move the view.",
  wall: "Click where the wall starts, then where it ends. Keep clicking to draw connected walls. Esc stops.",
  column: "Click the floor to place a column. Change its size after placing it.",
  space: "Click the floor to place a parking space. The size and name come from \"Floor and new spaces\".",
  camera: "Click on or right next to a wall or column to mount a camera on that side. It faces away from it; change Direction after.",
  assign: "Pick a camera, then click parking spaces to give them to it. Click again to take a space away.",
};

let state = null;          // { name, width, length, walls, columns, cameras, spaces }
let buildingId = null;
let buildingName = "";
let tool = "select";
let selected = null;       // { kind, uid }
let activeCamera = null;   // uid of the camera used by the Assign tool
let dirty = false;
let nextUid = 1;
let wallStart = null;      // first click of the wall tool
let drag = null;           // { kind, uid, start: {x,z}, orig: {...} }
let lastKey = null;        // { cameraUid, key } shown once after creating a key
let livePoll = null;

const statusEl = $("status");
const fs = createFloorScene($("canvas"));

// ---------- helpers ----------

const round = (n, d = 2) => Math.round(n * 10 ** d) / 10 ** d;
const snapOn = () => $("snap").checked;
const snap = (v) => (snapOn() ? Math.round(v * 2) / 2 : round(v, 2));
const uid = () => "u" + nextUid++;
const list = (kind) => state[kind === "space" ? "spaces" : kind === "camera" ? "cameras" : kind === "wall" ? "walls" : "columns"];
const find = (kind, u) => list(kind).find((x) => x.uid === u);
const camByRef = (ref) => state.cameras.find((c) => c.ref === ref);
const camIndex = (c) => state.cameras.indexOf(c);

function markDirty() {
  dirty = true;
  $("dirty").textContent = "Unsaved changes";
}

function fromServer(f) {
  nextUid = 1;
  return {
    name: f.name, width: f.width, length: f.length,
    walls: f.walls.map((w) => ({ ...w, uid: uid() })),
    columns: f.columns.map((c) => ({ ...c, uid: uid() })),
    cameras: f.cameras.map((c) => ({ ...c, uid: uid() })),
    spaces: f.spaces.map((s) => ({ ...s, uid: uid() })),
  };
}

function toServer() {
  return {
    name: state.name, width: state.width, length: state.length,
    walls: state.walls.map((w) => ({ x1: w.x1, z1: w.z1, x2: w.x2, z2: w.z2, thickness: w.thickness })),
    columns: state.columns.map((c) => ({ x: c.x, z: c.z, width: c.width, depth: c.depth })),
    cameras: state.cameras.map((c) => ({ id: c.id || null, ref: c.ref, name: c.name, mount: c.mount, x: c.x, z: c.z, direction: c.direction })),
    spaces: state.spaces.map((s) => ({ id: s.id || null, label: s.label, x: s.x, z: s.z, width: s.width, length: s.length,
      rotation: s.rotation, cameraRef: s.cameraRef || null })),
  };
}

function nextLabel() {
  const prefix = $("defPrefix").value.trim() || "A";
  const used = new Set(state.spaces.map((s) => s.label.toLowerCase()));
  for (let n = 1; n < 10000; n++) {
    const label = prefix + String(n).padStart(2, "0");
    if (!used.has(label.toLowerCase())) return label;
  }
  return prefix + Date.now();
}

function nextCameraName() {
  const used = new Set(state.cameras.map((c) => c.name.toLowerCase()));
  for (let n = 1; ; n++) if (!used.has(("Camera " + n).toLowerCase())) return "Camera " + n;
}

// ---------- drawing ----------

function look() {
  const live = $("live").checked;
  const focus = tool === "assign" ? activeCamera : selected && selected.kind === "camera" ? selected.uid : null;
  const focusCam = focus ? find("camera", focus) : null;
  return {
    space(s) {
      const cam = s.cameraRef ? camByRef(s.cameraRef) : null;
      const isSel = selected && selected.kind === "space" && selected.uid === s.uid;
      if (live) {
        const fill = s.status === "FREE" ? COLORS.free : s.status === "OCCUPIED" ? COLORS.taken : COLORS.unknown;
        return { fill, edge: COLORS.line, text: "#ffffff", opacity: 1, outline: isSel };
      }
      if (focusCam) {
        const mine = cam === focusCam;
        return { fill: mine ? cameraColor(camIndex(cam)) : COLORS.space, edge: COLORS.line, text: "#ffffff",
          opacity: mine ? 1 : 0.45, outline: isSel };
      }
      return { fill: cam ? cameraColor(camIndex(cam)) : COLORS.space, edge: COLORS.line, text: "#ffffff", opacity: 1, outline: isSel };
    },
    camera(c, i) {
      return { color: cameraColor(i), selected: (selected && selected.kind === "camera" && selected.uid === c.uid) || activeCamera === c.uid };
    },
  };
}

function redraw() {
  fs.render(state, look());
  renderPanels();
}

// ---------- side panels ----------

function setTool(t) {
  tool = t;
  wallStart = null;
  fs.clearPreview();
  document.querySelectorAll("[data-tool]").forEach((b) => b.setAttribute("aria-pressed", b.dataset.tool === t ? "true" : "false"));
  if (t === "assign") {
    if (selected && selected.kind === "camera") activeCamera = selected.uid;
    if (!activeCamera && state.cameras.length) activeCamera = state.cameras[0].uid;
  }
  $("hint").textContent = HINTS[t] + (t === "assign" && !state.cameras.length ? " Add a camera first." : "");
  fs.controls.mouseButtons.LEFT = t === "select" ? fs.THREE.MOUSE.PAN : null;
  redraw();
}

function select(kind, u) {
  selected = kind ? { kind, uid: u } : null;
  if (kind === "camera") activeCamera = u;
  redraw();
}

function numberField(label, key, item, opts = {}) {
  const v = item[key];
  return '<label' + (opts.wide ? ' class="wide"' : "") + ">" + label +
    ' <input data-key="' + key + '" type="' + (opts.text ? "text" : "number") + '"' +
    (opts.step ? ' step="' + opts.step + '"' : ' step="0.1"') + (opts.min != null ? ' min="' + opts.min + '"' : "") +
    (opts.max ? ' maxlength="' + opts.max + '"' : "") + (opts.readonly ? " readonly" : "") +
    ' value="' + PF.esc(opts.text ? v : round(v, 2)) + '"></label>';
}

function renderProps() {
  const box = $("props"), body = $("propsBody");
  const item = selected && find(selected.kind, selected.uid);
  if (!item) { box.hidden = true; return; }
  box.hidden = false;
  let html = "";
  if (selected.kind === "space") {
    $("propsTitle").textContent = "Parking space " + item.label;
    const options = ['<option value="">No camera</option>'].concat(state.cameras.map((c) =>
      '<option value="' + PF.esc(c.ref) + '"' + (c.ref === item.cameraRef ? " selected" : "") + ">" + PF.esc(c.name) + "</option>"));
    html = '<div class="fe-grid">' + numberField("Name", "label", item, { text: true, max: 20, wide: true }) +
      numberField("Width (m)", "width", item, { min: 1 }) + numberField("Length (m)", "length", item, { min: 1 }) +
      numberField("Turn (°)", "rotation", item, { step: 15 }) + '<label>Camera <select data-key="cameraRef">' + options.join("") + "</select></label>" +
      numberField("x (m)", "x", item) + numberField("z (m)", "z", item) + "</div>" +
      (item.id ? '<p class="small muted">State: ' + PF.esc(item.status || "UNKNOWN") + "</p>" : "");
  } else if (selected.kind === "camera") {
    const count = state.spaces.filter((s) => s.cameraRef === item.ref).length;
    $("propsTitle").textContent = item.name;
    html = '<div class="fe-grid">' + numberField("Name", "name", item, { text: true, max: 50, wide: true }) +
      numberField("Direction (°)", "direction", item, { step: 5 }) +
      '<label>Mounted on <input readonly value="' + (item.mount === "WALL" ? "Wall" : "Column") + '"></label>' +
      numberField("x (m)", "x", item) + numberField("z (m)", "z", item) + "</div>" +
      '<p class="small"><span class="swatch" style="background:' + hex(cameraColor(camIndex(item))) + '"></span>' +
      count + " parking space" + (count === 1 ? "" : "s") + " assigned.</p>" +
      '<div class="row"><button type="button" class="btn btn-secondary btn-small" id="assignHere">Assign spaces</button></div>' +
      '<h3>Device key</h3>';
    if (!item.id) html += '<p class="small muted">Save the floor first, then create a key for the camera\'s detector program.</p>';
    else {
      html += '<p class="small muted">' + (item.hasKey ? "This camera has a key. A new key replaces it." : "No key yet.") +
        (item.lastSeenAt ? " Last report: " + new Date(item.lastSeenAt).toLocaleString() + "." : "") + "</p>";
      if (lastKey && lastKey.cameraUid === item.uid)
        html += '<div class="fe-key" id="keyText">' + PF.esc(lastKey.key) + '</div><p class="small">Copy it now. It will not be shown again.</p>' +
          '<button type="button" class="btn btn-secondary btn-small" id="copyKey">Copy key</button> ';
      html += '<button type="button" class="btn btn-secondary btn-small" id="makeKey">' + (item.hasKey ? "Replace key" : "Create key") + "</button>";
    }
  } else if (selected.kind === "column") {
    $("propsTitle").textContent = "Column";
    html = '<div class="fe-grid">' + numberField("Width (m)", "width", item, { min: 0.1 }) + numberField("Depth (m)", "depth", item, { min: 0.1 }) +
      numberField("x (m)", "x", item) + numberField("z (m)", "z", item) + "</div>";
  } else if (selected.kind === "wall") {
    $("propsTitle").textContent = "Wall";
    html = '<div class="fe-grid">' + numberField("Start x", "x1", item) + numberField("Start z", "z1", item) +
      numberField("End x", "x2", item) + numberField("End z", "z2", item) + numberField("Thickness (m)", "thickness", item, { min: 0.05 }) + "</div>";
  }
  html += '<div class="row" style="margin-top:0.5rem"><button type="button" class="btn btn-danger btn-small" id="deleteSel">Delete</button></div>';
  body.innerHTML = html;

  body.querySelectorAll("[data-key]").forEach((input) => input.addEventListener("change", () => {
    const key = input.dataset.key;
    if (input.tagName === "SELECT") item[key] = input.value || null;
    else if (input.type === "text") {
      const v = input.value.trim();
      if (!v) { input.value = item[key]; return; }
      if (key === "label" && state.spaces.some((s) => s !== item && s.label.toLowerCase() === v.toLowerCase())) {
        PF.say(statusEl, "Another parking space is already named " + v + ".", "error");
        input.value = item[key]; return;
      }
      item[key] = v;
    } else {
      const n = Number(input.value);
      if (!Number.isFinite(n) || (input.min && n < Number(input.min))) { input.value = item[key]; return; }
      item[key] = n;
    }
    markDirty(); redraw();
  }));
  const del = $("deleteSel"); if (del) del.addEventListener("click", deleteSelected);
  const ah = $("assignHere"); if (ah) ah.addEventListener("click", () => { activeCamera = item.uid; setTool("assign"); });
  const mk = $("makeKey"); if (mk) mk.addEventListener("click", () => makeKey(item));
  const cp = $("copyKey"); if (cp) cp.addEventListener("click", async () => {
    try { await navigator.clipboard.writeText(lastKey.key); PF.say(statusEl, "Key copied.", "ok"); }
    catch (e) { PF.say(statusEl, "Select the key and copy it by hand.", "warn"); }
  });
}

function renderPanels() {
  renderProps();
  // cameras
  const cl = $("cameraList");
  if (!state.cameras.length) cl.innerHTML = '<li class="empty">No cameras yet. Use the Camera tool on a wall or column.</li>';
  else {
    cl.innerHTML = state.cameras.map((c, i) => {
      const n = state.spaces.filter((s) => s.cameraRef === c.ref).length;
      const on = activeCamera === c.uid || (selected && selected.uid === c.uid);
      return '<li><button type="button" data-cam="' + c.uid + '" aria-pressed="' + on + '"><span><span class="swatch" style="background:' +
        hex(cameraColor(i)) + '"></span>' + PF.esc(c.name) + '</span><span class="small muted">' + n + " space" + (n === 1 ? "" : "s") + "</span></button></li>";
    }).join("");
    cl.querySelectorAll("[data-cam]").forEach((b) => b.addEventListener("click", () => select("camera", b.dataset.cam)));
  }
  // checks
  const none = state.spaces.filter((s) => !s.cameraRef);
  const live = state.spaces.reduce((a, s) => { a[s.status || "UNKNOWN"] = (a[s.status || "UNKNOWN"] || 0) + 1; return a; }, {});
  $("counts").textContent = state.spaces.length + " parking spaces, " + state.cameras.length + " cameras, " +
    state.walls.length + " walls, " + state.columns.length + " columns." +
    ($("live").checked ? " Free " + (live.FREE || 0) + ", taken " + (live.OCCUPIED || 0) + ", unknown " + (live.UNKNOWN || 0) + "." : "");
  const un = $("unassigned");
  if (!state.spaces.length) un.innerHTML = "";
  else if (!none.length) un.innerHTML = '<span class="badge ok">Every space has a camera</span>';
  else {
    un.innerHTML = '<span class="badge warn">' + none.length + " without a camera</span>" +
      '<div class="fe-chips">' + none.slice(0, 60).map((s) => '<button type="button" data-space="' + s.uid + '">' + PF.esc(s.label) + "</button>").join("") +
      (none.length > 60 ? " …" : "") + "</div>";
    un.querySelectorAll("[data-space]").forEach((b) => b.addEventListener("click", () => select("space", b.dataset.space)));
  }
  $("floorName").value = state.name;
  $("floorWidth").value = state.width;
  $("floorLength").value = state.length;
}

// ---------- editing ----------

function deleteSelected() {
  if (!selected) return;
  const arr = list(selected.kind);
  const i = arr.findIndex((x) => x.uid === selected.uid);
  if (i < 0) return;
  const [item] = arr.splice(i, 1);
  if (selected.kind === "camera") {
    state.spaces.forEach((s) => { if (s.cameraRef === item.ref) s.cameraRef = null; });
    if (activeCamera === item.uid) activeCamera = null;
  }
  selected = null;
  markDirty(); redraw();
}

function addOuterWalls() {
  const { width: w, length: l } = state, t = 0.3;
  state.walls.push({ uid: uid(), x1: 0, z1: 0, x2: w, z2: 0, thickness: t }, { uid: uid(), x1: w, z1: 0, x2: w, z2: l, thickness: t },
    { uid: uid(), x1: w, z1: l, x2: 0, z2: l, thickness: t }, { uid: uid(), x1: 0, z1: l, x2: 0, z2: 0, thickness: t });
  markDirty(); redraw();
}

/**
 * Finds the wall or column face closest to a point on the floor (within 1.5 m).
 * Returns { kind, x, z, nx, nz }: a spot just outside that face and the direction pointing away from it.
 */
function nearestMount(p) {
  let best = null;
  const consider = (kind, d, x, z, nx, nz) => { if (d <= 1.5 && (!best || d < best.d)) best = { kind, d, x, z, nx, nz }; };
  state.walls.forEach((w) => {
    const dx = w.x2 - w.x1, dz = w.z2 - w.z1, len2 = dx * dx + dz * dz;
    if (len2 < 1e-6) return;
    const t = Math.max(0, Math.min(1, ((p.x - w.x1) * dx + (p.z - w.z1) * dz) / len2));
    const cx = w.x1 + t * dx, cz = w.z1 + t * dz;
    const len = Math.sqrt(len2);
    let nx = -dz / len, nz = dx / len;                       // perpendicular to the wall
    if ((p.x - cx) * nx + (p.z - cz) * nz < 0) { nx = -nx; nz = -nz; } // towards the side that was clicked
    const d = Math.max(0, Math.hypot(p.x - cx, p.z - cz) - w.thickness / 2);
    consider("wall", d, cx + nx * w.thickness / 2, cz + nz * w.thickness / 2, nx, nz);
  });
  state.columns.forEach((c) => {
    const hx = c.width / 2, hz = c.depth / 2, rx = p.x - c.x, rz = p.z - c.z;
    // pick the face the point is most "in front of"
    const useX = Math.abs(rx) / hx >= Math.abs(rz) / hz;
    const nx = useX ? Math.sign(rx) || 1 : 0, nz = useX ? 0 : Math.sign(rz) || 1;
    const fx = useX ? c.x + nx * hx : Math.max(c.x - hx, Math.min(c.x + hx, p.x));
    const fz = useX ? Math.max(c.z - hz, Math.min(c.z + hz, p.z)) : c.z + nz * hz;
    const d = Math.max(0, Math.abs(rx) - hx, Math.abs(rz) - hz);
    consider("column", d, fx, fz, nx, nz);
  });
  return best;
}

/** Puts a camera on a wall or column face, facing away from it. */
function mountCamera(m) {
  const cam = {
    uid: uid(), id: null, ref: "new" + Date.now().toString(36) + nextUid, name: nextCameraName(),
    mount: m.kind === "wall" ? "WALL" : "COLUMN",
    x: round(m.x + m.nx * 0.15), z: round(m.z + m.nz * 0.15),
    direction: round((Math.atan2(m.nz, m.nx) * 180) / Math.PI, 1), hasKey: false,
  };
  if (cam.direction < 0) cam.direction = round(cam.direction + 360, 1);
  state.cameras.push(cam);
  selected = { kind: "camera", uid: cam.uid };
  activeCamera = cam.uid;
  markDirty();
  PF.say(statusEl, cam.name + " added. Now click the parking spaces it can see.", "ok");
  setTool("assign");
}

function toggleAssign(space) {
  const cam = find("camera", activeCamera);
  if (!cam) { PF.say(statusEl, "Pick a camera first: click one on the map or in the Cameras list.", "warn"); return; }
  if (space.cameraRef === cam.ref) { space.cameraRef = null; PF.say(statusEl, space.label + " removed from " + cam.name + ".", "info"); }
  else {
    const before = space.cameraRef ? camByRef(space.cameraRef) : null;
    space.cameraRef = cam.ref;
    PF.say(statusEl, space.label + " now watched by " + cam.name + (before ? " (moved from " + before.name + ")." : "."), "ok");
  }
  markDirty(); redraw();
}

async function makeKey(cam) {
  if (dirty) { PF.say(statusEl, "Save the floor first.", "warn"); return; }
  if (cam.hasKey && !window.confirm("Replace the key for " + cam.name + "? The old key stops working right away.")) return;
  try {
    const r = await PF.api("POST", "/api/provider/cameras/" + cam.id + "/key");
    cam.hasKey = true;
    lastKey = { cameraUid: cam.uid, key: r.key };
    renderProps();
  } catch (err) { PF.say(statusEl, err.message, "error"); }
}

// ---------- mouse ----------

const el = fs.renderer.domElement;

el.addEventListener("pointerdown", (e) => {
  if (!state || e.button !== 0) return;
  const p = fs.groundPoint(e);
  if (tool === "select") {
    const hit = fs.pick(e, ["space", "camera", "column", "wall"]);
    if (!hit) { if (selected) select(null); return; }  // empty floor: the view pans
    fs.controls.enabled = false;
    select(hit.kind, hit.uid);
    const item = find(hit.kind, hit.uid);
    if (p && hit.kind !== "camera") drag = { kind: hit.kind, uid: hit.uid, start: p, orig: { ...item } };
    el.setPointerCapture(e.pointerId);
  } else if (tool === "wall") {
    if (!p) return;
    const pt = { x: snap(p.x), z: snap(p.z) };
    if (!wallStart) { wallStart = pt; return; }
    if (Math.hypot(pt.x - wallStart.x, pt.z - wallStart.z) < 0.2) return;
    state.walls.push({ uid: uid(), x1: wallStart.x, z1: wallStart.z, x2: pt.x, z2: pt.z, thickness: 0.3 });
    wallStart = pt;
    markDirty(); redraw();
  } else if (tool === "column") {
    if (!p) return;
    const c = { uid: uid(), x: snap(p.x), z: snap(p.z), width: 0.6, depth: 0.6 };
    state.columns.push(c);
    selected = { kind: "column", uid: c.uid };
    markDirty(); redraw();
  } else if (tool === "space") {
    if (!p) return;
    const s = { uid: uid(), id: null, label: nextLabel(), x: snap(p.x), z: snap(p.z),
      width: Number($("defW").value) || 2.6, length: Number($("defL").value) || 5.5, rotation: Number($("defR").value) || 0,
      cameraRef: null, status: "UNKNOWN" };
    state.spaces.push(s);
    selected = { kind: "space", uid: s.uid };
    markDirty(); redraw();
  } else if (tool === "camera") {
    // A click on the side of a wall or column (3D view) uses that face; otherwise the closest face to the click.
    const hit = fs.pick(e, ["wall", "column"]);
    let m = null;
    if (hit && hit.normal && Math.abs(hit.normal.y) < 0.5) {
      const len = Math.hypot(hit.normal.x, hit.normal.z);
      m = { kind: hit.kind, x: hit.point.x, z: hit.point.z, nx: hit.normal.x / len, nz: hit.normal.z / len };
    } else {
      const at = hit ? { x: hit.point.x, z: hit.point.z } : p;
      m = at && nearestMount(at);
    }
    if (!m) { PF.say(statusEl, "Cameras go on a wall or a column. Click on or right next to one.", "warn"); return; }
    mountCamera(m);
  } else if (tool === "assign") {
    const hit = fs.pick(e, ["camera", "space"]);
    if (!hit) return;
    if (hit.kind === "camera") { activeCamera = hit.uid; select("camera", hit.uid); return; }
    toggleAssign(find("space", hit.uid));
  }
}, true);

el.addEventListener("pointermove", (e) => {
  if (!state) return;
  if (drag) {
    const p = fs.groundPoint(e);
    if (!p) return;
    const item = find(drag.kind, drag.uid);
    const dx = p.x - drag.start.x, dz = p.z - drag.start.z;
    if (drag.kind === "wall") {
      const ox = snap(drag.orig.x1 + dx) - drag.orig.x1, oz = snap(drag.orig.z1 + dz) - drag.orig.z1;
      item.x1 = round(drag.orig.x1 + ox); item.z1 = round(drag.orig.z1 + oz);
      item.x2 = round(drag.orig.x2 + ox); item.z2 = round(drag.orig.z2 + oz);
    } else {
      item.x = snap(drag.orig.x + dx); item.z = snap(drag.orig.z + dz);
    }
    drag.moved = true;
    fs.place(drag.kind, item);
    return;
  }
  const p = fs.groundPoint(e);
  if (!p) { fs.clearPreview(); return; }
  if (tool === "wall" && wallStart) fs.previewWall(wallStart.x, wallStart.z, snap(p.x), snap(p.z), 0.3);
  else if (tool === "space") fs.previewBox(snap(p.x), snap(p.z), Number($("defW").value) || 2.6, Number($("defL").value) || 5.5, Number($("defR").value) || 0);
  else if (tool === "column") fs.previewBox(snap(p.x), snap(p.z), 0.6, 0.6, 0, 3);
  else fs.clearPreview();
});

function endDrag() {
  fs.controls.enabled = true;
  if (drag && drag.moved) { markDirty(); redraw(); }
  drag = null;
}
el.addEventListener("pointerup", endDrag);
el.addEventListener("pointercancel", endDrag);
el.addEventListener("contextmenu", (e) => e.preventDefault());

document.addEventListener("keydown", (e) => {
  if (!state) return;
  const typing = /INPUT|SELECT|TEXTAREA/.test(document.activeElement && document.activeElement.tagName);
  if (e.key === "Escape") { wallStart = null; fs.clearPreview(); if (tool === "select") select(null); return; }
  if (typing) return;
  if ((e.key === "Delete" || e.key === "Backspace") && selected) { e.preventDefault(); deleteSelected(); return; }
  if ((e.key === "r" || e.key === "R") && selected && selected.kind === "space") {
    const s = find("space", selected.uid); s.rotation = (s.rotation + 90) % 360; markDirty(); redraw(); return;
  }
  const keys = { v: "select", w: "wall", c: "column", s: "space", k: "camera", a: "assign" };
  if (keys[e.key.toLowerCase()] && !e.ctrlKey && !e.metaKey && !e.altKey) setTool(keys[e.key.toLowerCase()]);
});

// ---------- floor settings, save, live ----------

$("floorName").addEventListener("change", (e) => { const v = e.target.value.trim(); if (v) { state.name = v; markDirty(); } });
["floorWidth", "floorLength"].forEach((id) => $(id).addEventListener("change", (e) => {
  const n = Number(e.target.value);
  if (!Number.isFinite(n) || n < 5 || n > 500) { PF.say(statusEl, "Floor size must be 5 to 500 m.", "error"); renderPanels(); return; }
  state[id === "floorWidth" ? "width" : "length"] = n;
  markDirty(); redraw();
}));
$("outerWalls").addEventListener("click", addOuterWalls);
document.querySelectorAll("[data-tool]").forEach((b) => b.addEventListener("click", () => setTool(b.dataset.tool)));
$("viewTop").addEventListener("click", () => fs.viewTop());
$("view3d").addEventListener("click", () => fs.view3d());

$("save").addEventListener("click", async () => {
  const btn = $("save");
  btn.disabled = true;
  try {
    const saved = await PF.api("PUT", "/api/provider/buildings/" + buildingId + "/floor", toServer());
    const selLabel = selected && selected.kind === "space" ? find("space", selected.uid).label : null;
    state = fromServer(saved);
    selected = selLabel ? { kind: "space", uid: (state.spaces.find((s) => s.label === selLabel) || {}).uid } : null;
    activeCamera = null; lastKey = null;
    dirty = false; $("dirty").textContent = "Saved";
    PF.say(statusEl, "Floor saved: " + saved.counts.total + " parking spaces, " + saved.cameras.length + " cameras" +
      (saved.counts.withoutCamera ? ", " + saved.counts.withoutCamera + " spaces without a camera." : "."), "ok");
    redraw();
  } catch (err) { PF.say(statusEl, err.message, "error"); }
  btn.disabled = false;
});

$("live").addEventListener("change", () => {
  clearInterval(livePoll);
  if ($("live").checked) {
    refreshLive();
    livePoll = setInterval(refreshLive, 5000);
  }
  redraw();
});

/** Copies the latest space states from the server onto the drawing, without touching unsaved edits. */
async function refreshLive() {
  try {
    const f = await PF.api("GET", "/api/provider/buildings/" + buildingId + "/floor");
    const byId = new Map(f.spaces.map((s) => [s.id, s]));
    state.spaces.forEach((s) => { const n = s.id && byId.get(s.id); if (n) s.status = n.status; });
    const camById = new Map(f.cameras.map((c) => [c.id, c]));
    state.cameras.forEach((c) => { const n = c.id && camById.get(c.id); if (n) { c.lastSeenAt = n.lastSeenAt; c.hasKey = n.hasKey; } });
    if ($("live").checked) { fs.render(state, look()); renderPanels(); }
  } catch (err) { PF.say(statusEl, err.message, "error"); }
}

window.addEventListener("beforeunload", (e) => { if (dirty) { e.preventDefault(); e.returnValue = ""; } });

// ---------- start ----------

(async function start() {
  PF.renderNav("provider.html");
  try { await PF.requireLogin(["PROVIDER"]); } catch (err) { PF.say(statusEl, err.message, "error"); return; }
  PF.renderNav("provider.html");
  buildingId = Number(new URLSearchParams(location.search).get("building"));
  if (!buildingId) { PF.say(statusEl, "No building chosen. Open the editor from My parking.", "error"); return; }
  try {
    const f = await PF.api("GET", "/api/provider/buildings/" + buildingId + "/floor");
    buildingName = f.buildingName;
    state = fromServer(f);
    $("title").textContent = buildingName;
    document.title = buildingName + " floor | ParkFlow";
    $("viewLink").href = "floor.html?building=" + buildingId;
    if (!f.saved) {
      dirty = true;
      $("dirty").textContent = "Not saved yet";
      PF.say(statusEl, "This is a new floor with four outside walls. Change its size under \"Floor and new spaces\", then add columns, spaces and cameras.", "info");
    } else $("dirty").textContent = "Saved";
    setTool("select");
    fs.viewTop();
  } catch (err) { PF.say(statusEl, err.message, "error"); }
})();
