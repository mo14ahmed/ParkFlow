#include "net.h"

#include <Arduino.h>
#include <ArduinoJson.h>
#include <HTTPClient.h>
#include <Preferences.h>
#include <WiFi.h>
#include <WiFiClient.h>
#include <WiFiClientSecure.h>

#include "secrets.h"

static std::string token, firstName;
static Preferences prefs;

// ---------- Saved login ----------
static void saveLogin(const std::string &t, const std::string &name) {
  token = t;
  firstName = name;
  prefs.putString("token", token.c_str());
  prefs.putString("first", firstName.c_str());
}

// ---------- HTTP ----------
static std::string urlEncode(const std::string &s) {
  static const char *hex = "0123456789ABCDEF";
  std::string out;
  for (unsigned char c : s) {
    if (isalnum(c) || c == '-' || c == '_' || c == '.' || c == '~') {
      out += (char)c;
    } else {
      out += '%';
      out += hex[c >> 4];
      out += hex[c & 0x0F];
    }
  }
  return out;
}

static std::string jsonStr(JsonVariantConst v) {
  const char *s = v.as<const char *>();
  return s ? std::string(s) : std::string();
}

// Sends one request. On success `out` holds the parsed JSON reply.
static ApiResult request(bool post, const std::string &path, const std::string &body, JsonDocument &out) {
  ApiResult r;
  if (WiFi.status() != WL_CONNECTED) {
    r.status = -1;
    r.error = "No Wi-Fi. Turn on your phone hotspot.";
    return r;
  }
  std::string url = std::string(PF_API_BASE) + path;
  bool https = url.rfind("https://", 0) == 0;

  WiFiClientSecure secureClient;
  WiFiClient plainClient;
  // Demo: the connection is encrypted, but the server's certificate is not checked.
  if (https) secureClient.setInsecure();

  HTTPClient http;
  http.setConnectTimeout(10000);
  http.setTimeout(15000);
  bool started = https ? http.begin(secureClient, url.c_str()) : http.begin(plainClient, url.c_str());
  if (!started) {
    r.status = -1;
    r.error = "Bad server address. Check PF_API_BASE in secrets.h.";
    return r;
  }
  http.addHeader("Accept", "application/json");
  if (!token.empty()) http.addHeader("Authorization", ("Bearer " + token).c_str());

  int code;
  if (post) {
    http.addHeader("Content-Type", "application/json");
    code = http.POST((uint8_t *)body.data(), body.size());
  } else {
    code = http.GET();
  }

  if (code <= 0) {
    log_w("%s %s failed: %s", post ? "POST" : "GET", path.c_str(), HTTPClient::errorToString(code).c_str());
    http.end();
    r.status = -1;
    r.error = "Can't reach ParkFlow. Check that the hotspot has internet.";
    return r;
  }
  String resp = http.getString();
  http.end();
  log_i("%s %s -> %d", post ? "POST" : "GET", path.c_str(), code);

  r.status = code;
  JsonDocument json;
  bool parsed = !deserializeJson(json, resp);
  if (r.ok()) {
    if (!parsed) {
      r.status = -2;
      r.error = "The server sent an unexpected reply.";
      return r;
    }
    out = std::move(json);
    return r;
  }
  std::string msg = parsed ? jsonStr(json["error"]) : "";
  r.error = !msg.empty() ? msg : "Something went wrong (error " + std::to_string(code) + ").";
  return r;
}

static void readUser(JsonVariantConst u, UserInfo &user) {
  user.username = jsonStr(u["username"]);
  user.firstName = jsonStr(u["firstName"]);
  user.role = jsonStr(u["role"]);
}

// ---------- Public ----------
namespace net {

void begin() {
  prefs.begin("parkflow", false);
  // isKey() first, so a fresh board doesn't log "key not found" errors.
  if (prefs.isKey("token")) token = prefs.getString("token").c_str();
  if (prefs.isKey("first")) firstName = prefs.getString("first").c_str();

  WiFi.mode(WIFI_STA);
  WiFi.setSleep(false);  // faster replies
  WiFi.setAutoReconnect(true);  // keep trying the hotspot
  WiFi.begin(PF_WIFI_SSID, PF_WIFI_PASSWORD);
}

bool wifiConnected() { return WiFi.status() == WL_CONNECTED; }
bool hasToken() { return !token.empty(); }
std::string savedFirstName() { return firstName; }

void logout() {
  token.clear();
  firstName.clear();
  if (prefs.isKey("token")) prefs.remove("token");
  if (prefs.isKey("first")) prefs.remove("first");
}

ApiResult login(const std::string &username, const std::string &password, UserInfo &user) {
  JsonDocument req;
  req["username"] = username;
  req["password"] = password;
  std::string body;
  serializeJson(req, body);

  token.clear();  // never send an old token with a login
  JsonDocument res;
  ApiResult r = request(true, "/api/auth/login", body, res);
  if (!r.ok()) return r;
  readUser(res["user"], user);
  std::string t = jsonStr(res["token"]);
  if (user.role != "CUSTOMER") {
    r.status = 403;
    r.error = "This screen is for customer accounts. Log in with a customer account.";
    return r;
  }
  saveLogin(t, user.firstName);
  return r;
}

ApiResult me(UserInfo &user) {
  JsonDocument res;
  ApiResult r = request(false, "/api/me", "", res);
  if (r.status == 401) {
    logout();
    r.error = "Your login expired. Please log in again.";
    return r;
  }
  if (!r.ok()) return r;
  readUser(res.as<JsonVariantConst>(), user);
  if (user.role != "CUSTOMER") {
    logout();
    r.status = 403;
    r.error = "This screen is for customer accounts. Log in with a customer account.";
    return r;
  }
  if (user.firstName != firstName) saveLogin(token, user.firstName);
  return r;
}

ApiResult search(const std::string &query, SearchData &out) {
  JsonDocument res;
  ApiResult r = request(false, "/api/search?q=" + urlEncode(query), "", res);
  if (r.status == 401) {
    logout();
    r.error = "Your login expired. Please log in again.";
    return r;
  }
  if (!r.ok()) return r;
  JsonVariantConst place = res["place"];
  out.placeName = jsonStr(place["name"]);
  out.latitude = place["latitude"] | 0.0;
  out.longitude = place["longitude"] | 0.0;
  out.count = 0;
  for (JsonVariantConst b : res["buildings"].as<JsonArrayConst>()) {
    if (out.count >= MAX_RESULTS) break;
    Building &x = out.buildings[out.count++];
    x.id = b["id"] | 0L;
    x.name = jsonStr(b["name"]);
    x.company = jsonStr(b["company"]);
    x.latitude = b["latitude"] | 0.0;
    x.longitude = b["longitude"] | 0.0;
    x.distanceKm = b["distanceKm"] | 0.0;
  }
  return r;
}

}  // namespace net
