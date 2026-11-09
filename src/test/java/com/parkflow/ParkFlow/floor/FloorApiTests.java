package com.parkflow.ParkFlow.floor;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import com.parkflow.ParkFlow.search.Geocoder;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Floor editor, camera keys and occupancy reports, through the HTTP API against an in-memory database. */
@SpringBootTest
@AutoConfigureMockMvc
class FloorApiTests {
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired MockMvc mvc;
    @MockitoBean Geocoder geocoder;

    // ---------- helpers ----------

    private String token(String username, String role, String company) throws Exception {
        String body = """
            {"username":"%s","password":"secret-pass-1","firstName":"Test","lastName":"User",
             "phone":"+1 416-555-0100","role":"%s"%s}
            """.formatted(username, role, company == null ? "" : ",\"companyName\":\"" + company + "\"");
        return JsonPath.read(mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.token");
    }

    private String provider() throws Exception { return token("fprov" + SEQ.incrementAndGet(), "PROVIDER", "Floor Co"); }
    private String customer() throws Exception { return token("fcust" + SEQ.incrementAndGet(), "CUSTOMER", null); }
    private static String bearer(String t) { return "Bearer " + t; }

    private long building(String provider) throws Exception {
        Number id = JsonPath.read(mvc.perform(post("/api/provider/buildings").header("Authorization", bearer(provider))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Garage\",\"latitude\":43.6,\"longitude\":-79.3}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }

    private ResultActions save(String provider, long buildingId, String json) throws Exception {
        return mvc.perform(put("/api/provider/buildings/" + buildingId + "/floor").header("Authorization", bearer(provider))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    /** One wall, one column, two cameras (one new on the wall, one on the column) and three spaces. */
    private static final String LAYOUT = """
        {"name":"Ground floor","width":30,"length":20,
         "walls":[{"x1":0,"z1":0,"x2":30,"z2":0,"thickness":0.3}],
         "columns":[{"x":10,"z":10,"width":0.6,"depth":0.6}],
         "cameras":[{"ref":"new1","name":"Cam North","mount":"WALL","x":5,"z":0.2,"direction":90},
                    {"ref":"new2","name":"Cam Pillar","mount":"COLUMN","x":10,"z":10.4,"direction":450}],
         "spaces":[{"label":"A01","x":3,"z":4,"width":2.6,"length":5.5,"rotation":0,"cameraRef":"new1"},
                   {"label":"A02","x":6,"z":4,"width":2.6,"length":5.5,"rotation":0,"cameraRef":"new1"},
                   {"label":"B01","x":12,"z":14,"width":2.6,"length":5.5,"rotation":-90,"cameraRef":"new2"}]}
        """;

    private String body(ResultActions r) throws Exception { return r.andReturn().getResponse().getContentAsString(); }

    private String newKey(String provider, long cameraId) throws Exception {
        return JsonPath.read(body(mvc.perform(post("/api/provider/cameras/" + cameraId + "/key").header("Authorization", bearer(provider)))
                .andExpect(status().isOk())), "$.key");
    }

    private ResultActions report(String key, String json) throws Exception {
        return mvc.perform(post("/api/devices/camera/occupancy").header("X-Camera-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    // ---------- tests ----------

    @Test
    void newBuildingStartsWithAnUnsavedDefaultFloor() throws Exception {
        String p = provider();
        long b = building(p);
        mvc.perform(get("/api/provider/buildings/" + b + "/floor").header("Authorization", bearer(p)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(false))
                .andExpect(jsonPath("$.walls", hasSize(4)))
                .andExpect(jsonPath("$.spaces", hasSize(0)));
    }

    @Test
    void providerSavesAndReloadsTheFloor() throws Exception {
        String p = provider();
        long b = building(p);
        String saved = body(save(p, b, LAYOUT).andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(true))
                .andExpect(jsonPath("$.walls", hasSize(1)))
                .andExpect(jsonPath("$.columns", hasSize(1)))
                .andExpect(jsonPath("$.cameras", hasSize(2)))
                .andExpect(jsonPath("$.cameras[1].direction").value(90.0))
                .andExpect(jsonPath("$.cameras[0].hasKey").value(false))
                .andExpect(jsonPath("$.spaces[*].label", contains("A01", "A02", "B01")))
                .andExpect(jsonPath("$.spaces[2].rotation").value(270.0))
                .andExpect(jsonPath("$.spaces[0].status").value("UNKNOWN"))
                .andExpect(jsonPath("$.counts.total").value(3))
                .andExpect(jsonPath("$.counts.withoutCamera").value(0)));
        Number cam1 = JsonPath.read(saved, "$.cameras[0].id");
        String ref1 = JsonPath.read(saved, "$.cameras[0].ref");
        Number a01 = JsonPath.read(saved, "$.spaces[0].id");
        org.junit.jupiter.api.Assertions.assertEquals("c" + cam1, ref1);
        org.junit.jupiter.api.Assertions.assertEquals(ref1, JsonPath.read(saved, "$.spaces[0].cameraRef"));

        // Second save: keep camera 1 and A01 (renamed, now without a camera), drop the rest.
        String next = """
            {"name":"Level 1","width":30,"length":20,"walls":[],"columns":[],
             "cameras":[{"id":%d,"ref":"%s","name":"Cam North","mount":"WALL","x":5,"z":0.2,"direction":90}],
             "spaces":[{"id":%d,"label":"A01-X","x":3,"z":4,"width":3,"length":6,"rotation":0}]}
            """.formatted(cam1.longValue(), ref1, a01.longValue());
        save(p, b, next).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Level 1"))
                .andExpect(jsonPath("$.cameras", hasSize(1)))
                .andExpect(jsonPath("$.cameras[0].id").value(cam1.intValue()))
                .andExpect(jsonPath("$.spaces", hasSize(1)))
                .andExpect(jsonPath("$.spaces[0].id").value(a01.intValue()))
                .andExpect(jsonPath("$.spaces[0].label").value("A01-X"))
                .andExpect(jsonPath("$.spaces[0].cameraId").doesNotExist())
                .andExpect(jsonPath("$.counts.withoutCamera").value(1));
    }

    @Test
    void layoutRulesAreChecked() throws Exception {
        String p = provider();
        long b = building(p);
        save(p, b, LAYOUT.replace("\"label\":\"A02\"", "\"label\":\"a01\""))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error", containsString("named")));
        save(p, b, LAYOUT.replace("\"cameraRef\":\"new2\"", "\"cameraRef\":\"ghost\""))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error", containsString("camera that is not on this floor")));
        save(p, b, LAYOUT.replace("\"x\":12,\"z\":14", "\"x\":120,\"z\":14"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error", containsString("outside the floor")));
        save(p, b, LAYOUT.replace("\"mount\":\"WALL\"", "\"mount\":\"CEILING\""))
                .andExpect(status().isBadRequest());
        save(p, b, LAYOUT.replace("\"width\":2.6,\"length\":5.5,\"rotation\":0,\"cameraRef\":\"new1\"}", "\"width\":0.2,\"length\":5.5,\"rotation\":0,\"cameraRef\":\"new1\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields").exists());
        save(p, b, LAYOUT.replace("\"cameras\":[{\"ref\"", "\"cameras\":[{\"id\":999999,\"ref\""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void providersOnlyEditTheirOwnFloors() throws Exception {
        String owner = provider();
        long b = building(owner);
        String saved = body(save(owner, b, LAYOUT).andExpect(status().isOk()));
        Number cam = JsonPath.read(saved, "$.cameras[0].id");

        String other = provider();
        mvc.perform(get("/api/provider/buildings/" + b + "/floor").header("Authorization", bearer(other))).andExpect(status().isNotFound());
        save(other, b, LAYOUT).andExpect(status().isNotFound());
        mvc.perform(post("/api/provider/cameras/" + cam + "/key").header("Authorization", bearer(other))).andExpect(status().isNotFound());
        mvc.perform(get("/api/provider/buildings/" + b + "/floor").header("Authorization", bearer(customer()))).andExpect(status().isForbidden());
    }

    @Test
    void cameraReportsOnlyItsOwnSpacesAndOnlyChangesAreLogged() throws Exception {
        String p = provider();
        long b = building(p);
        String saved = body(save(p, b, LAYOUT));
        Number cam1 = JsonPath.read(saved, "$.cameras[0].id");
        Number a01 = JsonPath.read(saved, "$.spaces[0].id");
        String key = newKey(p, cam1.longValue());

        mvc.perform(get("/api/devices/camera")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/devices/camera").header("X-Camera-Key", key + "x")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/devices/camera").header("X-Camera-Key", "pfc_abc_def")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/devices/camera").header("Authorization", bearer(p))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/devices/camera").header("X-Camera-Key", key)).andExpect(status().isOk())
                .andExpect(jsonPath("$.cameraName").value("Cam North"))
                .andExpect(jsonPath("$.buildingId").value((int) b))
                .andExpect(jsonPath("$.spaces[*].label", contains("A01", "A02")));

        // B01 belongs to the other camera: the whole report is refused.
        report(key, "{\"spaces\":[{\"label\":\"A01\",\"occupied\":true},{\"label\":\"B01\",\"occupied\":true}]}")
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error", containsString("B01")));

        report(key, "{\"spaces\":[{\"spaceId\":%d,\"occupied\":true},{\"label\":\"a02\",\"occupied\":false}]}".formatted(a01.longValue()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.changed").value(2)).andExpect(jsonPath("$.unchanged").value(0));
        report(key, "{\"spaces\":[{\"label\":\"A01\",\"occupied\":true}]}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.changed").value(0)).andExpect(jsonPath("$.unchanged").value(1));
        report(key, "{\"spaces\":[{\"label\":\"A01\"}]}").andExpect(status().isBadRequest());
        mvc.perform(post("/api/devices/camera/heartbeat").header("X-Camera-Key", key)).andExpect(status().isNoContent());

        mvc.perform(get("/api/provider/buildings/" + b + "/floor").header("Authorization", bearer(p)))
                .andExpect(jsonPath("$.spaces[0].status").value("OCCUPIED"))
                .andExpect(jsonPath("$.spaces[1].status").value("FREE"))
                .andExpect(jsonPath("$.spaces[2].status").value("UNKNOWN"))
                .andExpect(jsonPath("$.cameras[0].hasKey").value(true))
                .andExpect(jsonPath("$.cameras[0].lastSeenAt").exists())
                .andExpect(jsonPath("$.counts.occupied").value(1))
                .andExpect(jsonPath("$.counts.free").value(1));

        // A new key replaces the old one.
        String key2 = newKey(p, cam1.longValue());
        mvc.perform(get("/api/devices/camera").header("X-Camera-Key", key)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/devices/camera").header("X-Camera-Key", key2)).andExpect(status().isOk());
    }

    @Test
    void liveFloorIsHiddenUntilTheProviderIsApproved() throws Exception {
        String p = provider();
        long b = building(p);
        String cust = customer();
        mvc.perform(get("/api/buildings/" + b + "/floor").header("Authorization", bearer(p))).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("This building has no floor map yet."));
        String saved = body(save(p, b, LAYOUT));
        mvc.perform(get("/api/buildings/" + b + "/floor").header("Authorization", bearer(p))).andExpect(status().isOk());
        mvc.perform(get("/api/buildings/" + b + "/floor").header("Authorization", bearer(cust))).andExpect(status().isNotFound());
        mvc.perform(get("/api/buildings/" + b + "/floor")).andExpect(status().isUnauthorized());

        String admin = JsonPath.read(body(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"admin-password-123\"}"))), "$.token");
        Number pid = JsonPath.read(body(mvc.perform(get("/api/me").header("Authorization", bearer(p)))), "$.id");
        mvc.perform(post("/api/admin/providers/" + pid + "/approve").header("Authorization", bearer(admin))).andExpect(status().isOk());

        mvc.perform(get("/api/buildings/" + b + "/floor").header("Authorization", bearer(cust))).andExpect(status().isOk())
                .andExpect(jsonPath("$.spaces", hasSize(3)))
                .andExpect(jsonPath("$.cameras[0].hasKey").doesNotExist());
        // Another provider can look too ("everyone" for now).
        mvc.perform(get("/api/buildings/" + b + "/floor").header("Authorization", bearer(provider()))).andExpect(status().isOk());
    }
}
