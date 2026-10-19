package com.parkflow.ParkFlow;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
@RestController
public class TestApiController {
 @GetMapping("/api/test")
 public Map<String,Object> getParkingStatus() {
  return Map.of("status","ok","facility","ParkFlow Demo Garage","totalSpaces",100,"availableSpaces",42);
 }
}
