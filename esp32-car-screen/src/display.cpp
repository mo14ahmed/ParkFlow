#include "display.h"

#include "driver/gpio.h"
#include "esp_heap_caps.h"
#include "esp_lcd_panel_ops.h"
#include "esp_lcd_panel_rgb.h"
#include "esp_log.h"
#include "esp_timer.h"
#include "gt911.h"

static const char *TAG = "display";

// Pins and timings: the ones verified on our board (programming guide, section 5).
static constexpr gpio_num_t PIN_BACKLIGHT = GPIO_NUM_2;
static constexpr int PIN_PCLK = 0, PIN_DE = 40, PIN_VSYNC = 41, PIN_HSYNC = 39;
// D0..D15 = B0..B4, G0..G5, R0..R4 (RGB565)
static constexpr int PIN_DATA[16] = {8, 3, 46, 9, 1, 5, 6, 7, 15, 16, 4, 45, 48, 47, 21, 14};
static constexpr uint32_t PIXEL_CLOCK_HZ = 15 * 1000 * 1000;

// The LCD reads the picture through this small buffer in fast internal RAM (refilled from the
// PSRAM frame buffer by the CPU), so it keeps getting pixels on time even when PSRAM is busy.
static constexpr int BOUNCE_LINES = 10;
// LVGL draws only changed areas, into two internal-RAM buffers of this many lines each.
static constexpr int DRAW_LINES = 40;

static esp_lcd_panel_handle_t panel = nullptr;

static uint32_t tickMs() { return (uint32_t)(esp_timer_get_time() / 1000); }

static void flushCb(lv_display_t *disp, const lv_area_t *area, uint8_t *pixels) {
  // Copies the finished area into the frame buffer.
  esp_lcd_panel_draw_bitmap(panel, area->x1, area->y1, area->x2 + 1, area->y2 + 1, pixels);
  lv_display_flush_ready(disp);
}

static void touchCb(lv_indev_t *, lv_indev_data_t *data) {
  int x, y;
  if (gt911::poll(x, y)) {
    data->point.x = x;
    data->point.y = y;
    data->state = LV_INDEV_STATE_PRESSED;
  } else {
    data->state = LV_INDEV_STATE_RELEASED;
  }
}

static void *allocDrawBuffer(size_t bytes) {
  void *p = heap_caps_malloc(bytes, MALLOC_CAP_INTERNAL | MALLOC_CAP_DMA);
  if (!p) {
    ESP_LOGW(TAG, "Draw buffer in PSRAM (not enough internal RAM)");
    p = heap_caps_malloc(bytes, MALLOC_CAP_SPIRAM);
  }
  return p;
}

lv_display_t *displayBegin() {
  esp_lcd_rgb_panel_config_t cfg = {};
  cfg.clk_src = LCD_CLK_SRC_DEFAULT;
  cfg.timings.pclk_hz = PIXEL_CLOCK_HZ;
  cfg.timings.h_res = SCREEN_W;
  cfg.timings.v_res = SCREEN_H;
  cfg.timings.hsync_pulse_width = 4;
  cfg.timings.hsync_back_porch = 43;
  cfg.timings.hsync_front_porch = 8;
  cfg.timings.vsync_pulse_width = 4;
  cfg.timings.vsync_back_porch = 12;
  cfg.timings.vsync_front_porch = 8;
  // Same signal timing as the LovyanGFX setup that showed a correct picture on this board:
  // sync pulses idle low, and pixels latched on the rising clock edge.
  cfg.timings.flags.hsync_idle_low = 1;
  cfg.timings.flags.vsync_idle_low = 1;
  cfg.timings.flags.pclk_active_neg = 0;
  cfg.data_width = 16;
  cfg.num_fbs = 1;
  cfg.bounce_buffer_size_px = SCREEN_W * BOUNCE_LINES;
  cfg.hsync_gpio_num = PIN_HSYNC;
  cfg.vsync_gpio_num = PIN_VSYNC;
  cfg.de_gpio_num = PIN_DE;
  cfg.pclk_gpio_num = PIN_PCLK;
  cfg.disp_gpio_num = -1;  // no display-enable pin (must not be 0: GPIO 0 is PCLK)
  for (int i = 0; i < 16; i++) cfg.data_gpio_nums[i] = PIN_DATA[i];
  cfg.flags.fb_in_psram = 1;

  ESP_ERROR_CHECK(esp_lcd_new_rgb_panel(&cfg, &panel));
  ESP_ERROR_CHECK(esp_lcd_panel_reset(panel));
  ESP_ERROR_CHECK(esp_lcd_panel_init(panel));

  gpio_config_t bl = {};
  bl.pin_bit_mask = 1ULL << PIN_BACKLIGHT;
  bl.mode = GPIO_MODE_OUTPUT;
  gpio_config(&bl);
  gpio_set_level(PIN_BACKLIGHT, 1);

  gt911::begin();

  lv_init();
  lv_tick_set_cb(tickMs);

  lv_display_t *disp = lv_display_create(SCREEN_W, SCREEN_H);
  lv_display_set_color_format(disp, LV_COLOR_FORMAT_RGB565);
  size_t bytes = SCREEN_W * DRAW_LINES * 2;
  lv_display_set_buffers(disp, allocDrawBuffer(bytes), allocDrawBuffer(bytes), bytes, LV_DISPLAY_RENDER_MODE_PARTIAL);
  lv_display_set_flush_cb(disp, flushCb);

  lv_indev_t *indev = lv_indev_create();
  lv_indev_set_type(indev, LV_INDEV_TYPE_POINTER);
  lv_indev_set_read_cb(indev, touchCb);

  ESP_LOGI(TAG, "Display ready. Internal RAM free: %u, PSRAM free: %u",
           (unsigned)heap_caps_get_free_size(MALLOC_CAP_INTERNAL), (unsigned)heap_caps_get_free_size(MALLOC_CAP_SPIRAM));
  return disp;
}
