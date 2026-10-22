// Shared helpers for the ParkFlow website: login session, API calls, top bar, map pins.
// The same API is used by the iOS app and the ESP32 car screen.
(function () {
  "use strict";
  const TOKEN_KEY = "pf_token";
  const USER_KEY = "pf_user";
  const HOME_BY_ROLE = { CUSTOMER: "search.html", PROVIDER: "provider.html", ADMIN: "admin.html" };

  function store() {
    try { return window.localStorage; } catch (e) { return null; }
  }

  const PF = {
    token() { const s = store(); return s ? s.getItem(TOKEN_KEY) : null; },
    user() {
      const s = store();
      try { return s ? JSON.parse(s.getItem(USER_KEY) || "null") : null; } catch (e) { return null; }
    },
    saveSession(auth) {
      const s = store(); if (!s) return;
      s.setItem(TOKEN_KEY, auth.token);
      s.setItem(USER_KEY, JSON.stringify(auth.user));
    },
    saveUser(user) { const s = store(); if (s) s.setItem(USER_KEY, JSON.stringify(user)); },
    logout() {
      const s = store();
      if (s) { s.removeItem(TOKEN_KEY); s.removeItem(USER_KEY); }
      window.location.href = "login.html";
    },
    homeFor(user) { return (user && HOME_BY_ROLE[user.role]) || "index.html"; },

    /** Calls the API. Throws an Error whose message is safe to show, with .status and .fields. */
    async api(method, path, body) {
      const headers = { "Accept": "application/json" };
      const token = PF.token();
      if (token) headers["Authorization"] = "Bearer " + token;
      if (body !== undefined) headers["Content-Type"] = "application/json";
      let res;
      try {
        res = await fetch(path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
      } catch (e) {
        throw Object.assign(new Error("Can't reach the server. Check your connection and try again."), { status: 0 });
      }
      if (res.status === 204) return null;
      let data = null;
      try { data = await res.json(); } catch (e) { /* not JSON */ }
      if (!res.ok) {
        if (res.status === 401 && token && !path.startsWith("/api/auth/")) {
          // Token expired or no longer valid: start over at the login page.
          const s = store(); if (s) { s.removeItem(TOKEN_KEY); s.removeItem(USER_KEY); }
          window.location.href = "login.html?expired=1&next=" + encodeURIComponent(location.pathname.replace(/^\//, ""));
        }
        const message = (data && data.error) || ("Something went wrong (error " + res.status + ").");
        throw Object.assign(new Error(message), { status: res.status, fields: (data && data.fields) || {} });
      }
      return data;
    },

    /** Use at the top of a page that needs login. Redirects if not logged in or the role does not fit. */
    async requireLogin(roles) {
      if (!PF.token()) {
        window.location.href = "login.html?next=" + encodeURIComponent(location.pathname.replace(/^\//, ""));
        return new Promise(() => {});
      }
      const user = await PF.api("GET", "/api/me");
      PF.saveUser(user);
      if (roles && roles.indexOf(user.role) < 0) {
        window.location.href = PF.homeFor(user);
        return new Promise(() => {});
      }
      return user;
    },

    /** Draws the top bar into <header id="topbar">. */
    renderNav(current) {
      const el = document.getElementById("topbar");
      if (!el) return;
      const user = PF.token() ? PF.user() : null;
      let links;
      if (!user) {
        links = [["index.html", "Home"], ["mcr1.html", "MCR I"], ["login.html", "Log in"], ["signup.html", "Sign up"]];
      } else if (user.role === "PROVIDER") {
        links = [["provider.html", "My parking"], ["account.html", "Account"]];
      } else if (user.role === "ADMIN") {
        links = [["admin.html", "Approvals"], ["search.html", "Find parking"], ["account.html", "Account"]];
      } else {
        links = [["search.html", "Find parking"], ["account.html", "Account"]];
      }
      el.className = "topbar";
      el.innerHTML =
        '<div class="topbar-inner">' +
        '<a class="brand" href="' + (user ? PF.homeFor(user) : "index.html") + '"><span class="sign" aria-hidden="true">P</span><span class="label">ParkFlow</span></a>' +
        '<nav aria-label="Main"><ul class="navlinks">' +
        links.map(([href, text]) => '<li><a href="' + href + '"' + (href === current ? ' aria-current="page"' : "") + ">" + text + "</a></li>").join("") +
        (user ? '<li class="who">' + PF.esc(user.firstName) + '</li><li><button type="button" id="logout">Log out</button></li>' : "") +
        "</ul></nav></div>";
      const out = document.getElementById("logout");
      if (out) out.addEventListener("click", PF.logout);
    },

    esc(s) {
      return String(s == null ? "" : s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
    },

    /** Shows a message in an element with class "msg". kind: ok | error | warn | info. */
    say(el, text, kind) {
      if (!el) return;
      el.className = "msg" + (kind && kind !== "info" ? " " + kind : "");
      el.textContent = text || "";
      el.setAttribute("role", kind === "error" ? "alert" : "status");
    },

    /** Marks invalid fields from an API error (error.fields) next to inputs with matching name. */
    showFieldErrors(form, err) {
      form.querySelectorAll(".field-error").forEach((n) => n.remove());
      form.querySelectorAll("[aria-invalid]").forEach((n) => n.removeAttribute("aria-invalid"));
      const fields = (err && err.fields) || {};
      Object.keys(fields).forEach((name) => {
        const input = form.querySelector('[name="' + name + '"]');
        if (!input) return;
        input.setAttribute("aria-invalid", "true");
        const note = document.createElement("span");
        note.className = "field-error";
        note.textContent = fields[name];
        (input.closest(".field") || input.parentNode).appendChild(note);
      });
    },

    /** Message for the top of a form: the error itself, or a count when several fields need fixing. */
    errorText(err) {
      const n = Object.keys((err && err.fields) || {}).length;
      return n > 1 ? "Please fix the " + n + " highlighted fields." : err.message;
    },

    formData(form) {
      const out = {};
      new FormData(form).forEach((v, k) => { out[k] = typeof v === "string" ? v.trim() : v; });
      return out;
    },

    statusText(status) {
      return { ACTIVE: "Approved", PENDING: "Waiting for approval", REJECTED: "Not approved" }[status] || status;
    },

    // ---------- Map (Leaflet + OpenStreetMap) ----------
    DEFAULT_CENTER: [43.6532, -79.3832], // Toronto
    makeMap(elementId, center, zoom) {
      const map = L.map(elementId).setView(center || PF.DEFAULT_CENTER, zoom || 12);
      L.tileLayer("https://tile.openstreetmap.org/{z}/{x}/{y}.png", {
        maxZoom: 19,
        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
      }).addTo(map);
      return map;
    },
    parkingIcon(hidden) {
      return L.divIcon({ className: "", html: '<div class="pin pin-p' + (hidden ? " hidden-pin" : "") + '">P</div>', iconSize: [30, 30], iconAnchor: [15, 15], popupAnchor: [0, -16] });
    },
    destinationIcon() {
      return L.divIcon({ className: "", html: '<div class="pin pin-dest"><span>&#9733;</span></div>', iconSize: [30, 30], iconAnchor: [15, 30], popupAnchor: [0, -30] });
    },
    km(d) { return d < 1 ? Math.round(d * 1000) + " m" : d.toFixed(1) + " km"; }
  };

  window.PF = PF;
})();
