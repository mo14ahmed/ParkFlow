// Copy this file to src/secrets.h and fill it in.
// secrets.h is in .gitignore so your hotspot password never goes to GitHub.
#pragma once

// Your phone hotspot (2.4 GHz). On iPhone, turn on "Maximize Compatibility".
#define PF_WIFI_SSID     "My iPhone"
#define PF_WIFI_PASSWORD "hotspot-password"

// The ParkFlow server. For testing against your PC on the same Wi-Fi you can use
// e.g. "http://192.168.1.50:8080". HTTPS certificates are checked automatically.
#define PF_API_BASE      "https://www.parkflow.tech"
