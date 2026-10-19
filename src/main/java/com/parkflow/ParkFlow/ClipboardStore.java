package com.parkflow.ParkFlow;

import java.time.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class ClipboardStore {
 public static final long MAX_FILE = 10L * 1024 * 1024, MAX_TOTAL = 100L * 1024 * 1024;
 public static final int MAX_TEXT = 100000, MAX_ITEMS = 200;
 public record Item(String id, String type, String text, String filename, long size, Instant createdAt, Instant expiresAt) {}
 public record History(String date, String timezone, Instant expiresAt, long usedBytes, long maxBytes, int maxItems, List<Item> items) {}
 private record Stored(Item item, byte[] data) {}
 private final ZoneId zone;
 private final Clock clock;
 private LocalDate day;
 private long used;
 private final LinkedHashMap<String,Stored> entries = new LinkedHashMap<>();
 @org.springframework.beans.factory.annotation.Autowired
 public ClipboardStore(@Value("${parkflow.clipboard.timezone}") String timezone) { this(ZoneId.of(timezone), Clock.systemUTC()); }
 ClipboardStore(ZoneId zone, Clock clock) { this.zone=zone; this.clock=clock; this.day=LocalDate.now(clock.withZone(zone)); }
 private Instant expires() { return day.plusDays(1).atStartOfDay(zone).toInstant(); }
 // Called before every operation too: expired items cannot be read during scheduler delay.
 @Scheduled(fixedDelay=1000)
 public synchronized void expire() {
  LocalDate today=LocalDate.now(clock.withZone(zone));
  if (!today.equals(day)) { entries.clear(); used=0; day=today; }
 }
 public synchronized History history() {
  expire(); List<Item> items=new ArrayList<>(); entries.values().forEach(s -> items.add(s.item()));
  Collections.reverse(items);
  return new History(day.toString(),zone.getId(),expires(),used,MAX_TOTAL,MAX_ITEMS,items);
 }
 private void capacity(long size) {
  if (entries.size() >= MAX_ITEMS || used+size > MAX_TOTAL)
   throw new ResponseStatusException(HttpStatus.INSUFFICIENT_STORAGE,"Clipboard full. Delete some items or wait for the daily reset.");
 }
 public synchronized Item text(String text) {
  expire();
  if(text==null || text.isBlank() || text.length()>MAX_TEXT)
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Text must contain 1 to 100,000 characters.");
  long size=text.getBytes(StandardCharsets.UTF_8).length; capacity(size);
  return put("text",text,null,size,null);
 }
 public synchronized Item file(MultipartFile file) throws IOException {
  expire();
  if(file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose a non-empty file.");
  if(file.getSize()>MAX_FILE) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"Files must be 10 MB or smaller.");
  capacity(file.getSize());
  String name=Objects.toString(file.getOriginalFilename(),"file").replace('\\','/');
  name=name.substring(name.lastIndexOf('/')+1).replaceAll("[\\p{Cntrl}]", "_");
  if(name.isBlank()) name="file";
  if(name.length()>180) name=name.substring(name.length()-180);
  byte[] bytes=file.getBytes();
  expire(); capacity(bytes.length);
  return put("file",null,name,bytes.length,bytes);
 }
 private Item put(String type,String text,String filename,long size,byte[] data) {
  Item item=new Item(UUID.randomUUID().toString(),type,text,filename,size,clock.instant(),expires());
  entries.put(item.id(),new Stored(item,data)); used+=size; return item;
 }
 public synchronized Item item(String id) { expire(); return find(id).item(); }
 public synchronized byte[] download(String id) {
  expire(); Stored s=find(id);
  if(s.data()==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"This item is text, not a file.");
  return s.data();
 }
 private Stored find(String id) {
  Stored s=entries.get(id);
  if(s==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Item not found or expired.");
  return s;
 }
 public synchronized void delete(String id) { expire(); used-=find(id).item().size(); entries.remove(id); }
}
