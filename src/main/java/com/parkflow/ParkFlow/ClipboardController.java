package com.parkflow.ParkFlow;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
@RestController
@RequestMapping("/api/clipboard")
public class ClipboardController {
 private final ClipboardStore store;
 public ClipboardController(ClipboardStore store) { this.store=store; }
 public record TextRequest(@NotBlank @Size(max=100000) String text) {}
 @GetMapping public ClipboardStore.History history() { return store.history(); }
 @PostMapping(value="/text", consumes=MediaType.APPLICATION_JSON_VALUE)
 @ResponseStatus(HttpStatus.CREATED)
 public ClipboardStore.Item text(@Valid @RequestBody TextRequest body) { return store.text(body.text()); }
 @PostMapping(value="/files", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
 @ResponseStatus(HttpStatus.CREATED)
 public ClipboardStore.Item file(@RequestParam("file") MultipartFile file) throws IOException { return store.file(file); }
 @GetMapping("/{id}") public ClipboardStore.Item item(@PathVariable String id) { return store.item(id); }
 @GetMapping("/{id}/download")
 public ResponseEntity<byte[]> download(@PathVariable String id) {
  synchronized(store) {
   ClipboardStore.Item item=store.item(id); byte[] data=store.download(id);
   return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
    .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(item.filename(),StandardCharsets.UTF_8).build().toString())
    .header(HttpHeaders.CACHE_CONTROL,"no-store").body(data);
  }
 }
 @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
 public void delete(@PathVariable String id) { store.delete(id); }
}
