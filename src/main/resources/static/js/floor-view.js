// Live floor map: shows each parking space as free, taken or unknown. Read only, refreshed every 5 seconds.
import { createFloorScene, COLORS } from "./floor-scene.js";

const PF = window.PF;
const $ = (id) => document.getElementById(id);
const statusEl = $("status");
const fs = createFloorScene($("canvas"));
let state = null;
let signature = "";

const look = {
  space(s) {
    const fill = s.status === "FREE" ? COLORS.free : s.status === "OCCUPIED" ? COLORS.taken : COLORS.unknown;
    return { fill, edge: COLORS.line, text: "#ffffff", opacity: 1 };
  },
  camera() { return { color: 0x3a4046, selected: false }; },
};

function show(f) {
  const first = !state;
  state = { ...f, walls: f.walls.map((w, i) => ({ ...w, uid: "w" + i })), columns: f.columns.map((c, i) => ({ ...c, uid: "c" + i })),
    cameras: f.cameras.map((c) => ({ ...c, uid: "k" + c.id })), spaces: f.spaces.map((s) => ({ ...s, uid: "s" + s.id })) };
  // Only redraw when something changed.
  const sig = f.updatedAt + "|" + f.spaces.map((s) => s.id + s.status).join(",");
  if (sig !== signature) { fs.render(state, look); signature = sig; }
  if (first) fs.viewTop();
  $("nFree").textContent = f.counts.free;
  $("nTaken").textContent = f.counts.occupied;
  $("nUnknown").textContent = f.counts.unknown;
  $("updated").textContent = f.counts.total + " parking spaces. Checked " + new Date().toLocaleTimeString() + ".";
}

async function refresh(id) {
  try { show(await PF.api("GET", "/api/buildings/" + id + "/floor")); PF.say(statusEl, ""); }
  catch (err) { PF.say(statusEl, err.message, "error"); }
}

const tip = $("tip");
fs.renderer.domElement.addEventListener("pointermove", (e) => {
  const hit = state && fs.pick(e, ["space"]);
  const s = hit && state.spaces.find((x) => x.uid === hit.uid);
  if (!s) { tip.hidden = true; return; }
  const r = fs.renderer.domElement.getBoundingClientRect();
  tip.hidden = false;
  tip.style.left = e.clientX - r.left + "px";
  tip.style.top = e.clientY - r.top + "px";
  tip.textContent = s.label + ": " + (s.status === "FREE" ? "free" : s.status === "OCCUPIED" ? "taken" : "unknown");
});
fs.renderer.domElement.addEventListener("contextmenu", (e) => e.preventDefault());
$("viewTop").addEventListener("click", () => fs.viewTop());
$("view3d").addEventListener("click", () => fs.view3d());

(async function start() {
  PF.renderNav("");
  let user;
  try { user = await PF.requireLogin(); } catch (err) { PF.say(statusEl, err.message, "error"); return; }
  PF.renderNav("");
  $("back").href = PF.homeFor(user);
  const id = Number(new URLSearchParams(location.search).get("building"));
  if (!id) { PF.say(statusEl, "No building chosen.", "error"); return; }
  try {
    const f = await PF.api("GET", "/api/buildings/" + id + "/floor");
    $("title").textContent = f.buildingName;
    $("floorName").textContent = f.name;
    document.title = f.buildingName + " live | ParkFlow";
    show(f);
    setInterval(() => refresh(id), 5000);
  } catch (err) { PF.say(statusEl, err.message, "error"); }
})();
