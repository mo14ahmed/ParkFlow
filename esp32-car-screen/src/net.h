// Wi-Fi (phone hotspot), saved login (NVS) and calls to the ParkFlow API.
// The car screen never talks to the database. Run the API calls from a background task.
#pragma once
#include <string>

struct ApiResult {
  int status = 0;     // HTTP status, or <= 0 when the server could not be reached
  std::string error;  // message safe to show on screen (empty on success)
  bool ok() const { return status >= 200 && status < 300; }
};

struct UserInfo {
  std::string username, firstName, role;  // role: CUSTOMER, PROVIDER or ADMIN
};

struct Building {
  long id = 0;
  std::string name, company;
  double latitude = 0, longitude = 0, distanceKm = 0;
};

constexpr int MAX_RESULTS = 5;

struct SearchData {
  std::string placeName;  // e.g. "CN Tower, 290, Bremner Boulevard, ..."
  double latitude = 0, longitude = 0;
  Building buildings[MAX_RESULTS];
  int count = 0;
};

namespace net {
void begin();               // NVS, saved login, Wi-Fi start
bool wifiConnected();
bool hasToken();
std::string savedFirstName();
void logout();
ApiResult login(const std::string &username, const std::string &password, UserInfo &user);
ApiResult me(UserInfo &user);
ApiResult search(const std::string &query, SearchData &out);
}  // namespace net
