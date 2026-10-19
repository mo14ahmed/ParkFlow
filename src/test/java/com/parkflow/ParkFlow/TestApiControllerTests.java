package com.parkflow.ParkFlow;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties="parkflow.api-key=test-private-key")
@AutoConfigureMockMvc
class TestApiControllerTests {
 @Autowired MockMvc mvc;
 @Test void testEndpointUsesSharedKey() throws Exception {
  mvc.perform(get("/api/test").header("X-API-Key","test-private-key")).andExpect(status().isOk()).andExpect(jsonPath("$.availableSpaces").value(42));
  mvc.perform(get("/api/test")).andExpect(status().isUnauthorized());
 }
 @Test void allClipboardOperationsRequireKey() throws Exception {
  mvc.perform(get("/api/clipboard")).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/clipboard/id/download").header("X-API-Key","wrong")).andExpect(status().isUnauthorized());
  mvc.perform(post("/api/clipboard/text").contentType("application/json").content("{\"text\":\"secret\"}")).andExpect(status().isUnauthorized());
  mvc.perform(multipart("/api/clipboard/files").file(new MockMultipartFile("file","a.txt","text/plain","abc".getBytes()))).andExpect(status().isUnauthorized());
  mvc.perform(delete("/api/clipboard/id")).andExpect(status().isUnauthorized());
 }
 @Test void textAndFilesCanBeSharedWithoutCookiesOrCsrf() throws Exception {
  mvc.perform(post("/api/clipboard/text").header("X-API-Key","test-private-key").contentType("application/json").content("{\"text\":\"Hello iOS 👋\"}"))
   .andExpect(status().isCreated()).andExpect(jsonPath("$.text").value("Hello iOS 👋"));
  mvc.perform(multipart("/api/clipboard/files").file(new MockMultipartFile("file","a.txt","text/plain","abc".getBytes())).header("X-API-Key","test-private-key"))
   .andExpect(status().isCreated()).andExpect(jsonPath("$.filename").value("a.txt"));
  mvc.perform(get("/api/clipboard").header("X-API-Key","test-private-key"))
   .andExpect(status().isOk()).andExpect(header().string("Cache-Control",org.hamcrest.Matchers.containsString("no-store"))).andExpect(jsonPath("$.timezone").value("America/Toronto"));
 }
 @Test void rejectsInvalidText() throws Exception {
  mvc.perform(post("/api/clipboard/text").header("X-API-Key","test-private-key").contentType("application/json").content("{\"text\":\" \"}"))
   .andExpect(status().isBadRequest());
 }
 @Test void keyCheckingWorksWithExplicitServletPath() throws Exception {
  mvc.perform(get("/api/clipboard").servletPath("/api/clipboard")).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/clipboard").servletPath("/api/clipboard").header("X-API-Key","wrong")).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/clipboard").servletPath("/api/clipboard").header("X-API-Key","test-private-key")).andExpect(status().isOk());
 }
 @Test void keyCheckingWorksUnderWarContextPath() throws Exception {
  mvc.perform(get("/ParkFlow/api/clipboard").contextPath("/ParkFlow")).andExpect(status().isUnauthorized());
  mvc.perform(get("/ParkFlow/api/clipboard").contextPath("/ParkFlow").header("X-API-Key","wrong")).andExpect(status().isUnauthorized());
  mvc.perform(get("/ParkFlow/api/clipboard").contextPath("/ParkFlow").header("X-API-Key","test-private-key")).andExpect(status().isOk());
 }
 @Test void websiteIsPublic() throws Exception {
  for(String path:new String[]{"/index.html","/mcr1.html","/clipboard.html","/clipboard.js","/clipboard.css"})mvc.perform(get(path)).andExpect(status().isOk());
 }
}
