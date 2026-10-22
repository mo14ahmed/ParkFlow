package com.parkflow.ParkFlow.building;

import com.parkflow.ParkFlow.building.BuildingDtos.*;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/** Providers manage their own parking buildings. Pending providers can too; customers only see them after approval. */
@RestController
@RequestMapping("/api/provider/buildings")
public class ProviderBuildingController {
    private final BuildingService service;

    public ProviderBuildingController(BuildingService service) { this.service = service; }

    private static Long me(Jwt jwt) { return Long.valueOf(jwt.getSubject()); }

    @GetMapping
    public List<BuildingView> list(@AuthenticationPrincipal Jwt jwt) { return service.mine(me(jwt)); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BuildingView create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody BuildingRequest body) {
        return service.create(me(jwt), body);
    }

    @PutMapping("/{id}")
    public BuildingView update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @Valid @RequestBody BuildingRequest body) {
        return service.update(me(jwt), id, body);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) { service.delete(me(jwt), id); }
}
