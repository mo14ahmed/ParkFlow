// LVGL settings for the ParkFlow car screen. Anything not set here uses LVGL's default.
#ifndef LV_CONF_H
#define LV_CONF_H
#define LV_COLOR_DEPTH 16
// Use the normal C library (big blocks go to PSRAM automatically)
#define LV_USE_STDLIB_MALLOC LV_STDLIB_CLIB
#define LV_USE_STDLIB_STRING LV_STDLIB_CLIB
#define LV_USE_STDLIB_SPRINTF LV_STDLIB_CLIB
// Redraw and read touch every 15 ms (about 60 times a second)
#define LV_DEF_REFR_PERIOD 15
#define LV_USE_OS LV_OS_NONE
#define LV_USE_LOG 0
// Fonts (Montserrat is LVGL's built-in font)
#define LV_FONT_MONTSERRAT_16 1
#define LV_FONT_MONTSERRAT_20 1
#define LV_FONT_MONTSERRAT_28 1
#define LV_FONT_MONTSERRAT_40 1
#define LV_FONT_DEFAULT &lv_font_montserrat_20
#define LV_BUILD_EXAMPLES 0
#define LV_USE_DEMO_WIDGETS 0
#endif
