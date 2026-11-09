// ParkFlow colors for the car screen (dark theme), from docs/design-system.md section 8.
#pragma once
#include "lvgl.h"

namespace pf {
inline lv_color_t asphalt() { return lv_color_hex(0x2E3338); }      // background
inline lv_color_t asphalt2() { return lv_color_hex(0x3A4046); }     // cards, top bar, fields
inline lv_color_t keyFace() { return lv_color_hex(0x4A5158); }      // keyboard keys
inline lv_color_t border() { return lv_color_hex(0x5A6168); }       // borders, special keys
inline lv_color_t onDark() { return lv_color_hex(0xF4F4F2); }       // main text
inline lv_color_t onDarkMuted() { return lv_color_hex(0xB9BEC3); }  // secondary text
inline lv_color_t lineYellow() { return lv_color_hex(0xF2C230); }   // focus, highlights
inline lv_color_t signBlue() { return lv_color_hex(0x1E4F9C); }     // primary buttons, P sign
inline lv_color_t signBlueDown() { return lv_color_hex(0x173E7A); } // primary pressed
inline lv_color_t freeGreen() { return lv_color_hex(0x2E9E5B); }    // closest result, online
inline lv_color_t taken() { return lv_color_hex(0xC8423B); }        // errors
inline lv_color_t held() { return lv_color_hex(0xE08A1E); }         // warnings
inline lv_color_t offline() { return lv_color_hex(0x8A9096); }      // no Wi-Fi
}  // namespace pf
