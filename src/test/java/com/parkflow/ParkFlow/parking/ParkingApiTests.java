package com.parkflow.ParkFlow.parking;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import com.parkflow.ParkFlow.search.Geocoder;
import com.parkflow.ParkFlow.search.Place;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** End-to-end checks of Phases 1 to 3 through the HTTP API, against an in-memory database. */
@SpringBootTest
@AutoConfigureMockMvc
class ParkingApiTests {
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired MockMvc mvc;
    @MockitoBean Geocoder geocoder;

    // ---------- helpers ----------

    private static String unique(String prefix) { return prefix + SEQ.incrementAndGet(); }

    private ResultActions signup(String username, String role, String company) throws Exception {
        String body = """
            {"username":"%s","password":"secret-pass-1","firstName":"Test","lastName":"User",
             "phone":"+1 416-555-0100","role":"%s"%s}
            """.formatted(username, role, company == null ? "" : ",\"companyName\":\"" + company + "\"");
        return mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String token(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.token");
    }

    private String customer() throws Exception { return token(signup(unique("cust"), "CUSTOMER", null)); }

    private String login(String username, String password) throws Exception {
        return token(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)))
                .andExpect(status().isOk()));
    }

    private String admin() throws Exception { return login("admin", "admin-password-123"); }

    private static String bearer(String token) { return "Bearer " + token; }

    private long id(String token) throws Exception {
        Number n = JsonPath.read(mvc.perform(get("/api/me").header("Authorization", bearer(token)))
                .andReturn().getResponse().getContentAsString(), "$.id");
        return n.longValue();
    }

    private ResultActions addBuilding(String token, String name, double lat, double lng) throws Exception {
        return mvc.perform(post("/api/provider/buildings").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"%s\",\"latitude\":%s,\"longitude\":%s}".formatted(name, lat, lng)));
    }

    /** Signs up a provider and has the admin approve them. Returns the provider's token. */
    private String approvedProvider(String company) throws Exception {
        String t = token(signup(unique("prov"), "PROVIDER", company));
        mvc.perform(post("/api/admin/providers/" + id(t) + "/approve").header("Authorization", bearer(admin())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
        return t;
    }

    // ---------- Phase 1: accounts ----------

    @Test
    void customerSignsUpAsActiveAndCanReadProfile() throws Exception {
        String name = unique("Alice");
        String t = token(signup(name, "CUSTOMER", null).andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.user.status").value("ACTIVE"))
                .andExpect(jsonPath("$.user.username").value(name.toLowerCase()))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist()));
        mvc.perform(get("/api/me").header("Authorization", bearer(t)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.firstName").value("Test"));
    }

    @Test
    void providerStartsPendingAndNeedsCompanyName() throws Exception {
        signup(unique("prov"), "PROVIDER", null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Enter your company name."));
        signup(unique("prov"), "PROVIDER", "Lot Co").andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.status").value("PENDING"))
                .andExpect(jsonPath("$.user.companyName").value("Lot Co"));
    }

    @Test
    void nobodyCanSignUpAsAdmin() throws Exception {
        signup(unique("evil"), "ADMIN", null).andExpect(status().isBadRequest());
    }

    @Test
    void usernamesAreUniqueIgnoringCase() throws Exception {
        String name = unique("Bob");
        signup(name, "CUSTOMER", null).andExpect(status().isCreated());
        signup(name.toUpperCase(), "CUSTOMER", null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("That username is taken. Try another one."));
    }

    @Test
    void signupValidatesFields() throws Exception {
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"x\",\"password\":\"short\",\"firstName\":\"\",\"lastName\":\"L\",\"phone\":\"abc\",\"role\":\"CUSTOMER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.username").exists())
                .andExpect(jsonPath("$.fields.password").exists())
                .andExpect(jsonPath("$.fields.phone").exists());
    }

    @Test
    void loginWorksWithAnyCaseAndRejectsWrongPassword() throws Exception {
        String name = unique("carol");
        signup(name, "CUSTOMER", null);
        login(name.toUpperCase(), "secret-pass-1");
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"wrong-password\"}".formatted(name)))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("Wrong username or password."));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"nobody-here\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void apiNeedsAValidToken() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").exists());
        mvc.perform(get("/api/me").header("Authorization", "Bearer not-a-token")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/anything-else").header("Authorization", bearer(customer()))).andExpect(status().isForbidden());
    }

    @Test
    void profileAndPasswordCanBeChanged() throws Exception {
        String name = unique("dave");
        String t = token(signup(name, "CUSTOMER", null));
        mvc.perform(put("/api/me").header("Authorization", bearer(t)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Dave\",\"lastName\":\"Smith\",\"phone\":\"6475550199\",\"workLocation\":\"Union Station\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.workLocation").value("Union Station"));
        mvc.perform(put("/api/me/password").header("Authorization", bearer(t)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"wrong-one\",\"newPassword\":\"new-secret-22\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/me/password").header("Authorization", bearer(t)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"secret-pass-1\",\"newPassword\":\"new-secret-22\"}"))
                .andExpect(status().isNoContent());
        login(name, "new-secret-22");
    }

    @Test
    void onlyAdminsReviewProviders() throws Exception {
        String provider = token(signup(unique("prov"), "PROVIDER", "Review Co"));
        long pid = id(provider);
        mvc.perform(get("/api/admin/providers").header("Authorization", bearer(customer()))).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/providers/" + pid + "/approve").header("Authorization", bearer(provider)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/providers").param("status", "PENDING").header("Authorization", bearer(admin())))
                .andExpect(status().isOk()).andExpect(jsonPath("$[*].provider.id", hasItem((int) pid)));
        mvc.perform(post("/api/admin/providers/" + pid + "/reject").header("Authorization", bearer(admin())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
        mvc.perform(post("/api/admin/providers/" + id(customer()) + "/approve").header("Authorization", bearer(admin())))
                .andExpect(status().isNotFound());
    }

    // ---------- Phase 2: provider buildings ----------

    @Test
    void pendingProviderCanAddBuildingsButCustomersDoNotSeeThemUntilApproved() throws Exception {
        String provider = token(signup(unique("prov"), "PROVIDER", "Hidden Co"));
        addBuilding(provider, "Hidden Garage", 10.0, 10.0).andExpect(status().isCreated())
                .andExpect(jsonPath("$.visible").value(false));

        String cust = customer();
        mvc.perform(get("/api/buildings/nearby").param("lat", "10.0").param("lng", "10.0").header("Authorization", bearer(cust)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[*].name", not(hasItem("Hidden Garage"))));

        mvc.perform(post("/api/admin/providers/" + id(provider) + "/approve").header("Authorization", bearer(admin())));
        mvc.perform(get("/api/provider/buildings").header("Authorization", bearer(provider)))
                .andExpect(jsonPath("$[0].visible").value(true));
        mvc.perform(get("/api/buildings/nearby").param("lat", "10.0").param("lng", "10.0").header("Authorization", bearer(cust)))
                .andExpect(jsonPath("$[0].name").value("Hidden Garage"))
                .andExpect(jsonPath("$[0].company").value("Hidden Co"));
    }

    @Test
    void providersOnlyTouchTheirOwnBuildings() throws Exception {
        String a = approvedProvider("A Co");
        String b = approvedProvider("B Co");
        Number bid = JsonPath.read(addBuilding(a, "A Garage", 20.0, 20.0).andReturn().getResponse().getContentAsString(), "$.id");
        String body = "{\"name\":\"Taken over\",\"latitude\":20,\"longitude\":20}";
        mvc.perform(put("/api/provider/buildings/" + bid).header("Authorization", bearer(b))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/provider/buildings/" + bid).header("Authorization", bearer(b))).andExpect(status().isNotFound());
        mvc.perform(put("/api/provider/buildings/" + bid).header("Authorization", bearer(a))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"A Garage North\",\"latitude\":20.5,\"longitude\":20.25}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("A Garage North"))
                .andExpect(jsonPath("$.latitude").value(20.5));
        mvc.perform(delete("/api/provider/buildings/" + bid).header("Authorization", bearer(a))).andExpect(status().isNoContent());
        mvc.perform(get("/api/provider/buildings").header("Authorization", bearer(a))).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void buildingNeedsNameAndValidPin() throws Exception {
        String p = approvedProvider("Pin Co");
        addBuilding(p, "", 1, 1).andExpect(status().isBadRequest());
        addBuilding(p, "Bad lat", 91, 1).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Latitude must be between -90 and 90."));
        addBuilding(p, "Bad lng", 1, -181).andExpect(status().isBadRequest());
    }

    @Test
    void customersCannotManageBuildingsAndProvidersCannotSearch() throws Exception {
        addBuilding(customer(), "Nope", 1, 1).andExpect(status().isForbidden());
        mvc.perform(get("/api/buildings/nearby").param("lat", "1").param("lng", "1")
                .header("Authorization", bearer(approvedProvider("No Search Co")))).andExpect(status().isForbidden());
    }

    // ---------- Phase 3: customer search ----------

    @Test
    void nearbyReturnsTheFiveClosestInOrder() throws Exception {
        String p = approvedProvider("Far South Parking");
        // A remote spot so buildings from other tests are thousands of km away.
        double lat = -45.0, lng = 170.0;
        for (int i = 6; i >= 1; i--) addBuilding(p, "Garage " + i, lat + i * 0.01, lng).andExpect(status().isCreated());

        mvc.perform(get("/api/buildings/nearby").param("lat", "" + lat).param("lng", "" + lng).header("Authorization", bearer(customer())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[0].name").value("Garage 1"))
                .andExpect(jsonPath("$[4].name").value("Garage 5"))
                .andExpect(jsonPath("$[0].distanceKm").value(closeTo(1.11, 0.02)));
    }

    @Test
    void searchFindsThePlaceThenTheClosestBuildings() throws Exception {
        String p = approvedProvider("Downtown Parking");
        addBuilding(p, "Front St Garage", 43.6440, -79.3860).andExpect(status().isCreated());
        when(geocoder.search(anyString(), anyInt()))
                .thenReturn(List.of(new Place("CN Tower, Toronto", 43.6426, -79.3871)));

        String cust = customer();
        mvc.perform(get("/api/search").param("q", "CN Tower").header("Authorization", bearer(cust)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.place.name").value("CN Tower, Toronto"))
                .andExpect(jsonPath("$.buildings[0].name").value("Front St Garage"))
                .andExpect(jsonPath("$.buildings[0].distanceKm").value(lessThan(1.0)));
        mvc.perform(get("/api/places").param("q", "CN Tower").header("Authorization", bearer(cust)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].latitude").value(43.6426));
    }

    @Test
    void providersCanLookUpAddressesButNotSearchParking() throws Exception {
        when(geocoder.search(anyString(), anyInt())).thenReturn(List.of(new Place("Union Station", 43.6453, -79.3806)));
        String p = token(signup(unique("prov"), "PROVIDER", "Lookup Co"));
        mvc.perform(get("/api/places").param("q", "Union Station").header("Authorization", bearer(p)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("Union Station"));
        mvc.perform(get("/api/search").param("q", "Union Station").header("Authorization", bearer(p)))
                .andExpect(status().isForbidden());
    }

    @Test
    void searchExplainsEmptyOrTooShortQueries() throws Exception {
        when(geocoder.search(anyString(), anyInt())).thenReturn(List.of());
        String cust = customer();
        mvc.perform(get("/api/search").param("q", "zzqqxx").header("Authorization", bearer(cust)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").exists());
        mvc.perform(get("/api/search").param("q", "a").header("Authorization", bearer(cust)))
                .andExpect(status().isBadRequest());
    }
}
