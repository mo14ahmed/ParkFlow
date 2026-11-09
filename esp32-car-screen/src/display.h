// Screen + touch + LVGL for the CrowPanel 5.0" (800x480 RGB LCD, GT911 touch).
#pragma once
#include "lvgl.h"

constexpr int SCREEN_W = 800;
constexpr int SCREEN_H = 480;

// Starts the LCD (Espressif RGB driver with a bounce buffer), the touch chip and LVGL.
// Call from the UI task; all LVGL calls must stay in that task.
lv_display_t *displayBegin();
