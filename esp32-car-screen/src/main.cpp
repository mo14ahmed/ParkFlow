// ParkFlow car screen: log in as a customer, search a destination, see the 5 closest parking buildings.
// Board: Elecrow CrowPanel 5.0" ESP32-S3 (800x480 RGB LCD + GT911 touch). Internet: phone hotspot.
// Arduino core 3 + LVGL 9 + Espressif's RGB LCD driver. LVGL runs in loop() (core 1); API calls run in a
// background task on core 0, so Wi-Fi and HTTPS never block the screen.
#include <Arduino.h>
#include <math.h>
#include <stdio.h>

#include <atomic>
#include <string>

#include "display.h"
#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "lvgl.h"
#include "net.h"
#if __has_include("secrets.h")
#include "secrets.h"
#else
#error "Copy src/secrets.example.h to src/secrets.h and fill in your hotspot name and password."
#endif
#include "theme.h"

static const char *TAG = "app";

// ---------- Background API jobs ----------
enum class Job { None, Login, Me, Search };
static TaskHandle_t netTaskHandle = nullptr;
static std::atomic<Job> jobRunning{Job::None};
static std::atomic<bool> jobDone{false};
static std::string jobArg1, jobArg2;  // set before starting a job, read by the network task
static ApiResult jobResult;
static UserInfo jobUser;
static SearchData result;  // last search result (written by the network task during a search)

static void netTask(void *) {
  for (;;) {
    ulTaskNotifyTake(pdTRUE, portMAX_DELAY);
    switch (jobRunning.load()) {
      case Job::Login: jobResult = net::login(jobArg1, jobArg2, jobUser); break;
      case Job::Me: jobResult = net::me(jobUser); break;
      case Job::Search: jobResult = net::search(jobArg1, result); break;
      default: break;
    }
    jobDone = true;
  }
}

// ---------- UI state ----------
enum class Screen { Connecting, Login, Search, Results, Detail };
static Screen screen = Screen::Connecting;
static std::string firstName;
static int selected = 0;
static bool shownOnline = false;  // what the top bar shows (see pollTimer)
static lv_obj_t *wifiDot = nullptr, *wifiLabel = nullptr;
static lv_obj_t *msgLabel = nullptr;
static lv_obj_t *taUser = nullptr, *taPass = nullptr, *taQuery = nullptr, *keyboard = nullptr;
static lv_obj_t *busyLayer = nullptr;
static std::string lastUser;   // kept so the username stays filled in after an error
static std::string lastQuery;  // kept so "New search" shows the last destination

// Layout (800 x 480). Everything has a fixed place, so nothing moves when text or status changes.
constexpr int BAR_H = 55;
constexpr int PAD = 16;
constexpr int KEYBOARD_H = 282;
constexpr int FIELD_Y = 84;  // text fields and their buttons on the Log in and Search screens
constexpr int FIELD_H = 56;
constexpr int ROW_Y = 134, ROW_H = 62, ROW_GAP = 6;  // result rows

// ---------- Text helpers ----------
// The built-in fonts are ASCII only, so accented letters are shown plain (e.g. "Montreal").
static std::string screenText(const std::string &utf8) {
  static const char *latin = "AAAAAAACEEEEIIIIDNOOOOOxOUUUUYPsaaaaaaaceeeeiiiidnooooo/ouuuuypy";
  std::string out;
  const unsigned char *p = (const unsigned char *)utf8.c_str();
  while (*p) {
    uint32_t cp;
    int n;
    if (p[0] < 0x80) { cp = p[0]; n = 1; }
    else if ((p[0] & 0xE0) == 0xC0 && p[1]) { cp = ((p[0] & 0x1F) << 6) | (p[1] & 0x3F); n = 2; }
    else if ((p[0] & 0xF0) == 0xE0 && p[1] && p[2]) { cp = ((p[0] & 0x0F) << 12) | ((p[1] & 0x3F) << 6) | (p[2] & 0x3F); n = 3; }
    else if ((p[0] & 0xF8) == 0xF0 && p[1] && p[2] && p[3]) { cp = 0xFFFD; n = 4; }
    else { cp = '?'; n = 1; }
    p += n;
    if (cp >= 32 && cp < 127) out += (char)cp;
    else if (cp >= 0xC0 && cp <= 0xFF) out += latin[cp - 0xC0];
    else if (cp == 0x2018 || cp == 0x2019) out += '\'';
    else if (cp == 0x201C || cp == 0x201D) out += '"';
    else if (cp == 0x2013 || cp == 0x2014) out += '-';
    else if (cp >= 0xA0) out += '?';
  }
  return out;
}

static std::string firstPart(const std::string &placeName) {
  size_t comma = placeName.find(',');
  return comma == std::string::npos ? placeName : placeName.substr(0, comma);
}

static std::string formatKm(double km) {
  char buf[24];
  if (km < 1.0) snprintf(buf, sizeof(buf), "%d m", (int)lround(km * 1000.0));
  else snprintf(buf, sizeof(buf), "%.1f km", km);
  return buf;
}

// ---------- Widget helpers ----------
// A plain box with no padding, border or scrolling, so children sit exactly where they are placed.
static lv_obj_t *box(lv_obj_t *parent, int x, int y, int w, int h, lv_color_t bg, int radius) {
  lv_obj_t *o = lv_obj_create(parent);
  lv_obj_set_pos(o, x, y);
  lv_obj_set_size(o, w, h);
  lv_obj_set_style_bg_color(o, bg, 0);
  lv_obj_set_style_bg_opa(o, LV_OPA_COVER, 0);
  lv_obj_set_style_radius(o, radius, 0);
  lv_obj_set_style_border_width(o, 0, 0);
  lv_obj_set_style_pad_all(o, 0, 0);
  lv_obj_set_style_shadow_width(o, 0, 0);
  lv_obj_remove_flag(o, LV_OBJ_FLAG_SCROLLABLE);
  lv_obj_remove_flag(o, LV_OBJ_FLAG_CLICKABLE);
  return o;
}

static lv_obj_t *label(lv_obj_t *parent, const std::string &text, const lv_font_t *font, lv_color_t color,
                       int x, int y, int w = LV_SIZE_CONTENT) {
  lv_obj_t *l = lv_label_create(parent);
  lv_label_set_text(l, text.c_str());
  lv_obj_set_style_text_font(l, font, 0);
  lv_obj_set_style_text_color(l, color, 0);
  lv_obj_set_pos(l, x, y);
  if (w != LV_SIZE_CONTENT) {
    lv_obj_set_width(l, w);
    lv_label_set_long_mode(l, LV_LABEL_LONG_DOT);
  }
  return l;
}

static lv_obj_t *button(lv_obj_t *parent, const char *text, bool primary, int x, int y, int w, int h,
                        lv_event_cb_t cb, void *user = nullptr) {
  lv_obj_t *b = lv_button_create(parent);
  lv_obj_set_pos(b, x, y);
  lv_obj_set_size(b, w, h);
  lv_obj_set_style_radius(b, 4, 0);
  lv_obj_set_style_shadow_width(b, 0, 0);
  lv_obj_set_style_pad_all(b, 0, 0);
  lv_obj_set_style_bg_color(b, primary ? pf::signBlue() : pf::asphalt2(), 0);
  lv_obj_set_style_bg_color(b, primary ? pf::signBlueDown() : pf::border(), LV_STATE_PRESSED);
  lv_obj_set_style_border_width(b, primary ? 0 : 1, 0);
  lv_obj_set_style_border_color(b, pf::border(), 0);
  lv_obj_set_style_opa(b, LV_OPA_40, LV_STATE_DISABLED);
  lv_obj_remove_flag(b, LV_OBJ_FLAG_CLICK_FOCUSABLE);  // tapping a button never takes focus from a field
  lv_obj_t *l = lv_label_create(b);
  lv_label_set_text(l, text);
  lv_obj_set_style_text_font(l, &lv_font_montserrat_20, 0);
  lv_obj_set_style_text_color(l, lv_color_white(), 0);
  lv_obj_center(l);
  lv_obj_add_event_cb(b, cb, LV_EVENT_CLICKED, user);
  return b;
}

// Messages under the fields. Errors and warnings also get an icon, so color is never the only sign.
enum class Msg { Info, Warn, Error };

static void setMessage(const std::string &text, Msg kind) {
  if (!msgLabel) return;
  std::string shown = text;
  if (kind != Msg::Info && !text.empty()) shown = std::string(LV_SYMBOL_WARNING "  ") + text;
  lv_label_set_text(msgLabel, shown.c_str());
  lv_color_t c = kind == Msg::Error ? pf::taken() : kind == Msg::Warn ? pf::held() : pf::onDarkMuted();
  lv_obj_set_style_text_color(msgLabel, c, 0);
}

static void updateWifi() {
  if (!wifiDot) return;
  lv_obj_set_style_bg_color(wifiDot, shownOnline ? pf::freeGreen() : pf::offline(), 0);
  lv_label_set_text(wifiLabel, shownOnline ? "Online" : "No Wi-Fi");
}

static void doLogout(lv_event_t *);

// New screen with the ParkFlow top bar. The old screen is deleted when this one loads.
static lv_obj_t *newScreen(bool loggedIn, const std::string &title) {
  msgLabel = taUser = taPass = taQuery = keyboard = nullptr;
  lv_obj_t *scr = lv_obj_create(nullptr);
  lv_obj_set_style_bg_color(scr, pf::asphalt(), 0);
  lv_obj_set_style_pad_all(scr, 0, 0);
  lv_obj_remove_flag(scr, LV_OBJ_FLAG_SCROLLABLE);

  // Top bar with the yellow painted line under it.
  lv_obj_t *bar = box(scr, 0, 0, SCREEN_W, BAR_H - 3, pf::asphalt2(), 0);
  box(scr, 0, BAR_H - 3, SCREEN_W, 3, pf::lineYellow(), 0);

  lv_obj_t *sign = box(bar, 12, 8, 36, 36, pf::signBlue(), 4);
  lv_obj_t *p = lv_label_create(sign);
  lv_label_set_text(p, "P");
  lv_obj_set_style_text_font(p, &lv_font_montserrat_28, 0);
  lv_obj_set_style_text_color(p, lv_color_white(), 0);
  lv_obj_center(p);
  label(bar, "ParkFlow", &lv_font_montserrat_20, pf::onDark(), 58, 14);

  if (!title.empty()) {
    lv_obj_t *t = label(bar, title, &lv_font_montserrat_20, pf::onDarkMuted(), 0, 0, 300);
    lv_obj_set_style_text_align(t, LV_TEXT_ALIGN_CENTER, 0);
    lv_obj_align(t, LV_ALIGN_CENTER, -40, 0);
  }

  // Wi-Fi pill: fixed size and place, only its color and word change.
  lv_obj_t *pill = box(bar, loggedIn ? 516 : 652, 9, 136, 34, pf::asphalt(), LV_RADIUS_CIRCLE);
  lv_obj_set_style_border_width(pill, 1, 0);
  lv_obj_set_style_border_color(pill, pf::border(), 0);
  wifiDot = box(pill, 14, 10, 12, 12, pf::offline(), LV_RADIUS_CIRCLE);
  wifiLabel = label(pill, "", &lv_font_montserrat_16, pf::onDark(), 34, 7);
  updateWifi();

  if (loggedIn) button(bar, "Log out", false, 664, 6, 124, 40, doLogout);
  return scr;
}

static void showScreen(lv_obj_t *scr, Screen s) {
  screen = s;
  lv_screen_load_anim(scr, LV_SCR_LOAD_ANIM_NONE, 0, 0, true);
}

static void showBusy(const char *text) {
  busyLayer = lv_obj_create(lv_layer_top());
  lv_obj_set_size(busyLayer, SCREEN_W, SCREEN_H);
  lv_obj_set_style_bg_color(busyLayer, lv_color_black(), 0);
  lv_obj_set_style_bg_opa(busyLayer, LV_OPA_50, 0);
  lv_obj_set_style_border_width(busyLayer, 0, 0);
  lv_obj_set_style_radius(busyLayer, 0, 0);
  lv_obj_remove_flag(busyLayer, LV_OBJ_FLAG_SCROLLABLE);
  lv_obj_add_flag(busyLayer, LV_OBJ_FLAG_CLICKABLE);  // blocks taps while waiting

  lv_obj_t *card = box(busyLayer, 0, 0, 420, 112, pf::asphalt2(), 8);
  lv_obj_center(card);
  lv_obj_set_style_border_width(card, 3, 0);
  lv_obj_set_style_border_color(card, pf::lineYellow(), 0);
  lv_obj_t *sp = lv_spinner_create(card);
  lv_obj_set_size(sp, 56, 56);
  lv_obj_align(sp, LV_ALIGN_LEFT_MID, 24, 0);
  lv_obj_set_style_arc_color(sp, pf::lineYellow(), LV_PART_INDICATOR);
  lv_obj_set_style_arc_color(sp, pf::border(), LV_PART_MAIN);
  lv_obj_t *l = label(card, text, &lv_font_montserrat_28, pf::onDark(), 0, 0);
  lv_obj_align(l, LV_ALIGN_LEFT_MID, 104, 0);
}

static void hideBusy() {
  if (busyLayer) lv_obj_delete(busyLayer);
  busyLayer = nullptr;
}

static void startJob(Job j, const char *busyText) {
  if (jobRunning.load() != Job::None) return;
  showBusy(busyText);
  jobDone = false;
  jobRunning = j;
  xTaskNotifyGive(netTaskHandle);
}

// ---------- Keyboard ----------
// Same keys as LVGL's keyboard, but without the "hide keyboard" key (the keyboard always stays) and with
// '@' added. 4 rows of about 64 px tall keys.
#define KB_CTRL(x) ((lv_buttonmatrix_ctrl_t)(x))
#define KB_SPECIAL(w) KB_CTRL(LV_KEYBOARD_CTRL_BUTTON_FLAGS | (w))
#define KB_DARK(w) KB_CTRL(LV_BUTTONMATRIX_CTRL_CHECKED | (w))
static constexpr lv_buttonmatrix_ctrl_t K1 = KB_CTRL(1), K3 = KB_CTRL(3), K4 = KB_CTRL(4), K6 = KB_CTRL(6);  // key widths

static const char *const kbLower[] = {
    "1#", "q", "w", "e", "r", "t", "y", "u", "i", "o", "p", LV_SYMBOL_BACKSPACE, "\n",
    "ABC", "a", "s", "d", "f", "g", "h", "j", "k", "l", LV_SYMBOL_NEW_LINE, "\n",
    "_", "-", "z", "x", "c", "v", "b", "n", "m", ".", "@", "\n",
    LV_SYMBOL_LEFT, " ", LV_SYMBOL_RIGHT, ""};
static const char *const kbUpper[] = {
    "1#", "Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P", LV_SYMBOL_BACKSPACE, "\n",
    "abc", "A", "S", "D", "F", "G", "H", "J", "K", "L", LV_SYMBOL_NEW_LINE, "\n",
    "_", "-", "Z", "X", "C", "V", "B", "N", "M", ".", "@", "\n",
    LV_SYMBOL_LEFT, " ", LV_SYMBOL_RIGHT, ""};
static const lv_buttonmatrix_ctrl_t kbLetterCtrl[] = {
    KB_SPECIAL(5), K4, K4, K4, K4, K4, K4, K4, K4, K4, K4, KB_DARK(7),
    KB_SPECIAL(6), K3, K3, K3, K3, K3, K3, K3, K3, K3, KB_DARK(7),
    K1, K1, K1, K1, K1, K1, K1, K1, K1, K1, K1,
    KB_DARK(2), K6, KB_DARK(2)};
static const char *const kbSymbols[] = {
    "1", "2", "3", "4", "5", "6", "7", "8", "9", "0", LV_SYMBOL_BACKSPACE, "\n",
    "abc", "+", "&", "/", "*", "=", "%", "!", "?", "#", LV_SYMBOL_NEW_LINE, "\n",
    "\\", "$", "(", ")", "[", "]", ";", ":", "'", "\"", ",", "\n",
    LV_SYMBOL_LEFT, " ", LV_SYMBOL_RIGHT, ""};
static const lv_buttonmatrix_ctrl_t kbSymbolCtrl[] = {
    K1, K1, K1, K1, K1, K1, K1, K1, K1, K1, KB_DARK(2),
    KB_SPECIAL(2), K1, K1, K1, K1, K1, K1, K1, K1, K1, KB_DARK(2),
    K1, K1, K1, K1, K1, K1, K1, K1, K1, K1, K1,
    KB_DARK(2), K6, KB_DARK(2)};

static lv_obj_t *makeKeyboard(lv_obj_t *scr) {
  lv_obj_t *kb = lv_keyboard_create(scr);
  lv_keyboard_set_map(kb, LV_KEYBOARD_MODE_TEXT_LOWER, kbLower, kbLetterCtrl);
  lv_keyboard_set_map(kb, LV_KEYBOARD_MODE_TEXT_UPPER, kbUpper, kbLetterCtrl);
  lv_keyboard_set_map(kb, LV_KEYBOARD_MODE_SPECIAL, kbSymbols, kbSymbolCtrl);
  lv_keyboard_set_mode(kb, LV_KEYBOARD_MODE_TEXT_LOWER);
  lv_obj_set_size(kb, SCREEN_W, KEYBOARD_H);
  lv_obj_align(kb, LV_ALIGN_BOTTOM_MID, 0, 0);
  lv_obj_set_style_bg_color(kb, pf::asphalt(), 0);
  lv_obj_set_style_border_side(kb, LV_BORDER_SIDE_TOP, 0);
  lv_obj_set_style_border_width(kb, 1, 0);
  lv_obj_set_style_border_color(kb, pf::border(), 0);
  lv_obj_set_style_pad_all(kb, 6, 0);
  lv_obj_set_style_pad_gap(kb, 6, 0);
  lv_obj_set_style_radius(kb, 0, 0);
  lv_obj_set_style_text_font(kb, &lv_font_montserrat_28, LV_PART_ITEMS);
  lv_obj_set_style_text_color(kb, pf::onDark(), LV_PART_ITEMS);
  lv_obj_set_style_bg_color(kb, pf::keyFace(), LV_PART_ITEMS);
  lv_obj_set_style_radius(kb, 4, LV_PART_ITEMS);
  lv_obj_set_style_shadow_width(kb, 0, LV_PART_ITEMS);
  lv_obj_set_style_border_width(kb, 0, LV_PART_ITEMS);
  lv_obj_set_style_bg_color(kb, pf::border(), LV_PART_ITEMS | LV_STATE_CHECKED);
  lv_obj_set_style_bg_color(kb, pf::lineYellow(), LV_PART_ITEMS | LV_STATE_PRESSED);
  lv_obj_set_style_text_color(kb, pf::asphalt(), LV_PART_ITEMS | LV_STATE_PRESSED);
  return kb;
}

static void onTextFocused(lv_event_t *e) {
  lv_obj_t *ta = (lv_obj_t *)lv_event_get_target(e);
  for (lv_obj_t *other : {taUser, taPass}) {  // only one field looks focused
    if (other && other != ta) lv_obj_remove_state(other, LV_STATE_FOCUSED);
  }
  if (keyboard) lv_keyboard_set_textarea(keyboard, ta);
}

// The field keeps its focus look while the keyboard is used (it is only removed when another field is
// chosen), so the yellow border never flickers.
static void keepFocus(lv_event_t *e) {
  lv_obj_t *ta = (lv_obj_t *)lv_event_get_target(e);
  if (keyboard && lv_keyboard_get_textarea(keyboard) == ta) lv_obj_add_state(ta, LV_STATE_FOCUSED);
}

static lv_obj_t *textField(lv_obj_t *parent, int x, int y, int w, const char *placeholder, uint32_t maxLen) {
  lv_obj_t *ta = lv_textarea_create(parent);
  lv_textarea_set_one_line(ta, true);
  lv_textarea_set_max_length(ta, maxLen);
  lv_textarea_set_placeholder_text(ta, placeholder);
  lv_obj_set_pos(ta, x, y);
  lv_obj_set_size(ta, w, FIELD_H);
  lv_obj_set_style_pad_left(ta, 14, 0);
  lv_obj_set_style_pad_right(ta, 14, 0);
  lv_obj_set_style_pad_top(ta, 14, 0);
  lv_obj_set_style_text_font(ta, &lv_font_montserrat_20, 0);
  lv_obj_set_style_bg_color(ta, pf::asphalt2(), 0);
  lv_obj_set_style_text_color(ta, pf::onDark(), 0);
  lv_obj_set_style_text_color(ta, pf::offline(), LV_PART_TEXTAREA_PLACEHOLDER);
  // Same border width focused or not (only the color changes), so the text never shifts.
  lv_obj_set_style_border_width(ta, 3, 0);
  lv_obj_set_style_border_color(ta, pf::border(), 0);
  lv_obj_set_style_border_color(ta, pf::lineYellow(), LV_STATE_FOCUSED);
  lv_obj_set_style_outline_width(ta, 0, LV_STATE_FOCUSED);
  lv_obj_set_style_radius(ta, 4, 0);
  lv_obj_set_style_border_color(ta, pf::lineYellow(), LV_PART_CURSOR | LV_STATE_FOCUSED);
  lv_obj_add_event_cb(ta, onTextFocused, LV_EVENT_FOCUSED, nullptr);
  lv_obj_add_event_cb(ta, keepFocus, LV_EVENT_DEFOCUSED, nullptr);
  return ta;
}

static void focusField(lv_obj_t *ta) {
  if (taUser && taUser != ta) lv_obj_remove_state(taUser, LV_STATE_FOCUSED);
  if (taPass && taPass != ta) lv_obj_remove_state(taPass, LV_STATE_FOCUSED);
  if (keyboard) lv_keyboard_set_textarea(keyboard, ta);
  lv_obj_add_state(ta, LV_STATE_FOCUSED);
}

// ---------- Screens ----------
static void showLogin(const std::string &msg = "", Msg kind = Msg::Info);
static void showSearch();
static void showResults();
static void showDetail();

// Log in: two fields side by side above a keyboard that is always there.
static void doLogin(lv_event_t *) {
  std::string u = lv_textarea_get_text(taUser), pw = lv_textarea_get_text(taPass);
  while (!u.empty() && u.back() == ' ') u.pop_back();
  if (u.empty() || pw.empty()) {
    setMessage("Enter your username and password.", Msg::Error);
    focusField(u.empty() ? taUser : taPass);
    return;
  }
  lastUser = u;
  jobArg1 = u;
  jobArg2 = pw;
  startJob(Job::Login, "Logging in...");
}

static void onUserReady(lv_event_t *) { focusField(taPass); }

static void onShowPassword(lv_event_t *e) {
  bool hidden = lv_textarea_get_password_mode(taPass);
  lv_textarea_set_password_mode(taPass, !hidden);
  lv_obj_t *b = (lv_obj_t *)lv_event_get_target(e);
  lv_label_set_text(lv_obj_get_child(b, 0), hidden ? LV_SYMBOL_EYE_CLOSE " Hide" : LV_SYMBOL_EYE_OPEN " Show");
  focusField(taPass);
}

static void onFieldTapped(lv_event_t *e) { focusField((lv_obj_t *)lv_event_get_target(e)); }

static void showLogin(const std::string &msg, Msg kind) {
  lv_obj_t *scr = newScreen(false, "Log in");
  label(scr, "Username", &lv_font_montserrat_16, pf::onDarkMuted(), PAD, 62);
  label(scr, "Password", &lv_font_montserrat_16, pf::onDarkMuted(), 308, 62);
  taUser = textField(scr, PAD, FIELD_Y, 280, "username", 30);
  taPass = textField(scr, 308, FIELD_Y, 220, "password", 100);
  lv_textarea_set_password_mode(taPass, true);
  button(scr, LV_SYMBOL_EYE_OPEN " Show", false, 536, FIELD_Y, 100, FIELD_H, onShowPassword);
  button(scr, "Log in", true, 648, FIELD_Y, 136, FIELD_H, doLogin);
  msgLabel = label(scr, "", &lv_font_montserrat_20, pf::onDarkMuted(), PAD, 148, SCREEN_W - 2 * PAD);
  if (msg.empty()) setMessage("For customer accounts. You stay logged in on this screen.", Msg::Info);
  else setMessage(msg, kind);
  lv_obj_add_event_cb(taUser, onUserReady, LV_EVENT_READY, nullptr);
  lv_obj_add_event_cb(taPass, doLogin, LV_EVENT_READY, nullptr);
  lv_obj_add_event_cb(taUser, onFieldTapped, LV_EVENT_CLICKED, nullptr);
  lv_obj_add_event_cb(taPass, onFieldTapped, LV_EVENT_CLICKED, nullptr);
  keyboard = makeKeyboard(scr);
  lv_textarea_set_text(taUser, lastUser.c_str());
  showScreen(scr, Screen::Login);
  focusField(lastUser.empty() ? taUser : taPass);
}

// Search: one wide field above the keyboard.
static void doSearch(lv_event_t *) {
  std::string q = lv_textarea_get_text(taQuery);
  while (!q.empty() && q.back() == ' ') q.pop_back();
  if (q.size() < 2) {
    setMessage("Type at least 2 letters of a place or address.", Msg::Error);
    return;
  }
  jobArg1 = q;
  startJob(Job::Search, "Searching...");
}

static void showSearch() {
  std::string title = firstName.empty() ? "Search" : "Hi " + screenText(firstName);
  lv_obj_t *scr = newScreen(true, title);
  label(scr, "Where are you going?", &lv_font_montserrat_16, pf::onDarkMuted(), PAD, 62);
  taQuery = textField(scr, PAD, FIELD_Y, 616, "Place or address", 100);
  lv_textarea_set_text(taQuery, lastQuery.c_str());
  button(scr, "Search", true, 648, FIELD_Y, 136, FIELD_H, doSearch);
  msgLabel = label(scr, "", &lv_font_montserrat_20, pf::onDarkMuted(), PAD, 148, SCREEN_W - 2 * PAD);
  setMessage("We show the 5 parking buildings closest to it. Example: CN Tower", Msg::Info);
  lv_obj_add_event_cb(taQuery, doSearch, LV_EVENT_READY, nullptr);
  keyboard = makeKeyboard(scr);
  showScreen(scr, Screen::Search);
  focusField(taQuery);
}

// Results: the 5 closest buildings, closest first.
static void onRow(lv_event_t *e) {
  selected = (int)(intptr_t)lv_event_get_user_data(e);
  showDetail();
}

static void onNewSearch(lv_event_t *) { showSearch(); }

static void showResults() {
  lv_obj_t *scr = newScreen(true, "Parking nearby");
  button(scr, LV_SYMBOL_LEFT "  New search", false, PAD, 66, 190, FIELD_H, onNewSearch);
  label(scr, "Closest parking to", &lv_font_montserrat_16, pf::onDarkMuted(), 222, 64);
  label(scr, screenText(firstPart(result.placeName)), &lv_font_montserrat_28, pf::onDark(), 222, 86, SCREEN_W - 222 - PAD);

  if (result.count == 0) {
    lv_obj_t *card = box(scr, PAD, ROW_Y, SCREEN_W - 2 * PAD, 150, pf::asphalt2(), 8);
    lv_obj_t *l1 = label(card, "No parking buildings near this place yet.", &lv_font_montserrat_20, pf::onDark(), 0, 0);
    lv_obj_align(l1, LV_ALIGN_CENTER, 0, -18);
    lv_obj_t *l2 = label(card, "Try a different destination.", &lv_font_montserrat_20, pf::onDarkMuted(), 0, 0);
    lv_obj_align(l2, LV_ALIGN_CENTER, 0, 18);
  }
  for (int i = 0; i < result.count; i++) {
    const Building &b = result.buildings[i];
    bool closest = i == 0;
    lv_obj_t *row = lv_button_create(scr);
    lv_obj_set_pos(row, PAD, ROW_Y + i * (ROW_H + ROW_GAP));
    lv_obj_set_size(row, SCREEN_W - 2 * PAD, ROW_H);
    lv_obj_set_style_radius(row, 8, 0);
    lv_obj_set_style_shadow_width(row, 0, 0);
    lv_obj_set_style_pad_all(row, 0, 0);
    lv_obj_set_style_bg_color(row, pf::asphalt2(), 0);
    lv_obj_set_style_bg_color(row, pf::border(), LV_STATE_PRESSED);
    // Every row has the same 2 px border (green only on the closest), so all rows line up exactly.
    lv_obj_set_style_border_width(row, 2, 0);
    lv_obj_set_style_border_color(row, closest ? pf::freeGreen() : pf::asphalt2(), 0);
    if (!closest) lv_obj_set_style_border_color(row, pf::border(), LV_STATE_PRESSED);
    lv_obj_remove_flag(row, LV_OBJ_FLAG_CLICK_FOCUSABLE);
    lv_obj_add_event_cb(row, onRow, LV_EVENT_CLICKED, (void *)(intptr_t)i);

    lv_obj_t *rank = box(row, 12, (ROW_H - 4 - 36) / 2, 36, 36, closest ? pf::freeGreen() : pf::asphalt(), LV_RADIUS_CIRCLE);
    lv_obj_t *n = lv_label_create(rank);
    lv_label_set_text_fmt(n, "%d", i + 1);
    lv_obj_set_style_text_font(n, &lv_font_montserrat_20, 0);
    lv_obj_set_style_text_color(n, lv_color_white(), 0);
    lv_obj_center(n);

    label(row, screenText(b.name), &lv_font_montserrat_20, pf::onDark(), 62, 5, 500);
    std::string sub = b.company.empty() ? "Parking building" : screenText(b.company);
    if (closest) sub = "Closest  -  " + sub;
    label(row, sub, &lv_font_montserrat_16, closest ? pf::freeGreen() : pf::onDarkMuted(), 62, 33, 500);

    lv_obj_t *dist = label(row, formatKm(b.distanceKm), &lv_font_montserrat_28, pf::onDark(), 0, 0);
    lv_obj_align(dist, LV_ALIGN_RIGHT_MID, -44, 0);
    lv_obj_t *arrow = label(row, LV_SYMBOL_RIGHT, &lv_font_montserrat_20, pf::onDarkMuted(), 0, 0);
    lv_obj_align(arrow, LV_ALIGN_RIGHT_MID, -14, 0);
  }
  showScreen(scr, Screen::Results);
}

// Details of one building, with Previous / Next to step through the results.
static void onBack(lv_event_t *) { showResults(); }

static void onStep(lv_event_t *e) {
  int next = selected + (int)(intptr_t)lv_event_get_user_data(e);
  if (next < 0 || next >= result.count) return;
  selected = next;
  showDetail();
}

static void showDetail() {
  const Building &b = result.buildings[selected];
  lv_obj_t *scr = newScreen(true, "Parking details");
  button(scr, LV_SYMBOL_LEFT "  All results", false, PAD, 66, 190, FIELD_H, onBack);

  char pos[24];
  snprintf(pos, sizeof(pos), "%d of %d", selected + 1, result.count);
  lv_obj_t *posLabel = label(scr, pos, &lv_font_montserrat_20, pf::onDarkMuted(), 0, 0, 100);
  lv_obj_set_style_text_align(posLabel, LV_TEXT_ALIGN_RIGHT, 0);
  lv_obj_set_pos(posLabel, 548, 82);
  lv_obj_t *prev = button(scr, LV_SYMBOL_LEFT, false, 660, 66, 56, FIELD_H, onStep, (void *)(intptr_t)-1);
  lv_obj_t *next = button(scr, LV_SYMBOL_RIGHT, false, 728, 66, 56, FIELD_H, onStep, (void *)(intptr_t)1);
  if (selected == 0) lv_obj_add_state(prev, LV_STATE_DISABLED);
  if (selected >= result.count - 1) lv_obj_add_state(next, LV_STATE_DISABLED);

  label(scr, screenText(b.name), &lv_font_montserrat_28, pf::onDark(), PAD, 138, SCREEN_W - 2 * PAD);
  label(scr, b.company.empty() ? "Parking building" : screenText(b.company), &lv_font_montserrat_20,
        pf::onDarkMuted(), PAD, 176, SCREEN_W - 2 * PAD);

  lv_obj_t *card = box(scr, PAD, 214, SCREEN_W - 2 * PAD, 140, pf::asphalt2(), 8);
  label(card, "Distance", &lv_font_montserrat_16, pf::onDarkMuted(), 24, 14);
  label(card, formatKm(b.distanceKm), &lv_font_montserrat_40, pf::lineYellow(), 24, 36);
  label(card, "from " + screenText(firstPart(result.placeName)) + " (straight line)", &lv_font_montserrat_20,
        pf::onDarkMuted(), 24, 96, SCREEN_W - 2 * PAD - 48);
  if (selected == 0) {
    lv_obj_t *badge = box(card, SCREEN_W - 2 * PAD - 16 - 110, 16, 110, 32, pf::freeGreen(), LV_RADIUS_CIRCLE);
    lv_obj_t *t = label(badge, "Closest", &lv_font_montserrat_16, lv_color_white(), 0, 0);
    lv_obj_center(t);
  }

  label(scr, "Location", &lv_font_montserrat_16, pf::onDarkMuted(), PAD, 370);
  char coords[48];
  snprintf(coords, sizeof(coords), "%.5f, %.5f", b.latitude, b.longitude);
  label(scr, coords, &lv_font_montserrat_20, pf::onDark(), PAD, 392);
  label(scr, "Open this location in your phone's maps app to drive there.", &lv_font_montserrat_16,
        pf::onDarkMuted(), PAD, 432, SCREEN_W - 2 * PAD);
  showScreen(scr, Screen::Detail);
}

// Connecting: shown until the phone hotspot is found.
static void showConnecting() {
  lv_obj_t *scr = newScreen(false, "");
  lv_obj_t *sign = box(scr, 0, 0, 84, 84, pf::signBlue(), 8);
  lv_obj_align(sign, LV_ALIGN_CENTER, 0, -96);
  lv_obj_t *p = label(sign, "P", &lv_font_montserrat_40, lv_color_white(), 0, 0);
  lv_obj_center(p);
  lv_obj_t *t = label(scr, "Connecting to your phone hotspot", &lv_font_montserrat_28, pf::onDark(), 0, 0);
  lv_obj_align(t, LV_ALIGN_CENTER, 0, -10);
  lv_obj_t *n = label(scr, std::string("Wi-Fi name: ") + PF_WIFI_SSID, &lv_font_montserrat_20, pf::onDarkMuted(), 0, 0);
  lv_obj_align(n, LV_ALIGN_CENTER, 0, 32);
  lv_obj_t *h = label(scr, "Turn on Personal Hotspot on your phone.", &lv_font_montserrat_20, pf::onDarkMuted(), 0, 0);
  lv_obj_align(h, LV_ALIGN_CENTER, 0, 62);
  lv_obj_t *sp = lv_spinner_create(scr);
  lv_obj_set_size(sp, 48, 48);
  lv_obj_align(sp, LV_ALIGN_CENTER, 0, 130);
  lv_obj_set_style_arc_color(sp, pf::lineYellow(), LV_PART_INDICATOR);
  lv_obj_set_style_arc_color(sp, pf::border(), LV_PART_MAIN);
  showScreen(scr, Screen::Connecting);
}

static void doLogout(lv_event_t *) {
  net::logout();
  firstName.clear();
  showLogin("You are logged out.");
}

// ---------- Results of background jobs ----------
static void onJobFinished(Job j) {
  hideBusy();
  const ApiResult &r = jobResult;
  switch (j) {
    case Job::Login:
      if (r.ok()) {
        firstName = jobUser.firstName;
        jobArg2.clear();
        lastQuery.clear();
        showSearch();
      } else {
        setMessage(r.error, Msg::Error);
      }
      break;
    case Job::Me:
      if (r.ok()) {
        firstName = jobUser.firstName;
        showSearch();
      } else if (r.status == 401 || r.status == 403) {
        showLogin(r.error, Msg::Warn);
      } else {
        // Server not reachable right now: keep the saved login and let the customer try a search.
        firstName = net::savedFirstName();
        showSearch();
        setMessage(r.error, Msg::Warn);
      }
      break;
    case Job::Search:
      if (r.ok()) {
        lastQuery = jobArg1;
        showResults();
      } else if (r.status == 401) {
        showLogin(r.error, Msg::Warn);
      } else {
        setMessage(r.error, Msg::Error);
      }
      break;
    default:
      break;
  }
}

// Runs every 50 ms in the UI loop.
static void pollTimer(lv_timer_t *) {
  // "No Wi-Fi" only shows after the hotspot has been gone for 3 seconds, so a short drop
  // doesn't make the top bar flip between Online and No Wi-Fi.
  static uint32_t lastSeenUp = 0;
  bool up = net::wifiConnected();
  if (up) lastSeenUp = lv_tick_get();
  bool show = up || (shownOnline && lv_tick_elaps(lastSeenUp) < 3000);
  if (show != shownOnline) {
    shownOnline = show;
    updateWifi();
    if (show && screen == Screen::Connecting) {
      if (net::hasToken()) startJob(Job::Me, "Signing you in...");
      else showLogin();
    }
  }
  if (jobDone) {
    Job j = jobRunning;
    jobDone = false;
    jobRunning = Job::None;
    onJobFinished(j);
  }
}

void setup() {
  Serial.begin(115200);
  ESP_LOGI(TAG, "ParkFlow car screen starting");
  net::begin();
  xTaskCreatePinnedToCore(netTask, "net", 12288, nullptr, 4, &netTaskHandle, 0);

  lv_display_t *disp = displayBegin();
  lv_theme_t *theme = lv_theme_default_init(disp, pf::signBlue(), pf::lineYellow(), true, &lv_font_montserrat_20);
  lv_display_set_theme(disp, theme);
  showConnecting();
  lv_timer_create(pollTimer, 50, nullptr);
}

void loop() {
  uint32_t wait = lv_timer_handler();
  if (wait < 2) wait = 2;
  if (wait > 15) wait = 15;
  delay(wait);
}
