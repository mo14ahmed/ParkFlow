// Minimal GT911 touch driver (I2C on SDA 19 / SCL 20) using ESP-IDF's I2C master driver.
// Uses the corrected byte order from our guide: X = data[0..1], Y = data[2..3].
#pragma once
#include <stdint.h>

namespace gt911 {
bool begin();                 // finds the chip at 0x5D or 0x14
bool poll(int &x, int &y);    // true while a finger is down; x/y = last position
}
