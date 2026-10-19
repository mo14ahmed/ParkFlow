package com.parkflow.ParkFlow;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
@SpringBootApplication
@EnableScheduling
public class ParkFlowApplication {
 public static void main(String[] args) { SpringApplication.run(ParkFlowApplication.class, args); }
}
