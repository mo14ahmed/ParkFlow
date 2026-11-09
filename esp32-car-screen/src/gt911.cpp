#include "gt911.h"

#include "driver/i2c_master.h"
#include "esp_log.h"
#include "esp_timer.h"

static const char *TAG = "gt911";
static i2c_master_bus_handle_t bus = nullptr;
static i2c_master_dev_handle_t dev = nullptr;
static bool touched = false;
static int lastX = 0, lastY = 0;
static int64_t lastReportUs = 0;

static bool readReg(uint16_t reg, uint8_t *buf, size_t len) {
  uint8_t addr[2] = {(uint8_t)(reg >> 8), (uint8_t)(reg & 0xFF)};
  return i2c_master_transmit_receive(dev, addr, 2, buf, len, 50) == ESP_OK;
}

static void writeReg(uint16_t reg, uint8_t value) {
  uint8_t data[3] = {(uint8_t)(reg >> 8), (uint8_t)(reg & 0xFF), value};
  i2c_master_transmit(dev, data, 3, 50);
}

namespace gt911 {

bool begin() {
  i2c_master_bus_config_t bc = {};
  bc.i2c_port = I2C_NUM_0;
  bc.sda_io_num = GPIO_NUM_19;
  bc.scl_io_num = GPIO_NUM_20;
  bc.clk_source = I2C_CLK_SRC_DEFAULT;
  bc.glitch_ignore_cnt = 7;
  bc.flags.enable_internal_pullup = true;
  if (i2c_new_master_bus(&bc, &bus) != ESP_OK) return false;

  const uint16_t candidates[] = {0x5D, 0x14};
  for (uint16_t a : candidates) {
    if (i2c_master_probe(bus, a, 50) != ESP_OK) continue;
    i2c_device_config_t dc = {};
    dc.dev_addr_length = I2C_ADDR_BIT_LEN_7;
    dc.device_address = a;
    dc.scl_speed_hz = 100000;
    if (i2c_master_bus_add_device(bus, &dc, &dev) != ESP_OK) return false;
    uint8_t id[4] = {0};
    readReg(0x8140, id, 4);
    ESP_LOGI(TAG, "GT911 found at 0x%02X, product id %c%c%c%c", a, id[0], id[1], id[2], id[3]);
    return true;
  }
  ESP_LOGW(TAG, "GT911 not found");
  return false;
}

bool poll(int &x, int &y) {
  if (!dev) return false;
  uint8_t status = 0;
  if (readReg(0x814E, &status, 1)) {
    int64_t now = esp_timer_get_time();
    if (status & 0x80) {  // a new report is ready
      uint8_t count = status & 0x0F;
      if (count > 0 && count <= 5) {
        uint8_t d[8];
        if (readReg(0x8150, d, 8)) {
          int rx = d[0] | (d[1] << 8);
          int ry = d[2] | (d[3] << 8);
          if (rx >= 0 && rx < 800 && ry >= 0 && ry < 480) {  // reject bad data, never clamp
            lastX = rx;
            lastY = ry;
            touched = true;
          }
        }
      } else {
        touched = false;
      }
      writeReg(0x814E, 0);  // tell the chip we read this report
      lastReportUs = now;
    } else if (touched && now - lastReportUs > 120000) {
      touched = false;  // the chip reports continuously while held, so silence means released
    }
  }
  x = lastX;
  y = lastY;
  return touched;
}

}  // namespace gt911
