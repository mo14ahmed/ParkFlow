package com.parkflow.ParkFlow;
import java.time.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
class ClipboardStoreTests {
 static class MutableClock extends Clock {
  Instant now=Instant.parse("2026-10-19T03:59:59Z");
  public ZoneId getZone(){return ZoneOffset.UTC;}
  public Clock withZone(ZoneId z){return new Clock(){public ZoneId getZone(){return z;}public Clock withZone(ZoneId next){return MutableClock.this.withZone(next);}public Instant instant(){return now;}};}
  public Instant instant(){return now;}
 }
 @Test void midnightExpiresTextAndFilesEvenBeforeScheduledCleanup() throws Exception {
  var clock=new MutableClock();var store=new ClipboardStore(ZoneId.of("America/Toronto"),clock);
  var text=store.text("hello");var file=store.file(new MockMultipartFile("file","x.txt","text/plain","abc".getBytes()));
  assertEquals(Instant.parse("2026-10-19T04:00:00Z"),text.expiresAt());
  clock.now=clock.now.plusSeconds(1);
  assertThrows(ResponseStatusException.class,()->store.download(file.id()));
  assertThrows(ResponseStatusException.class,()->store.item(text.id()));
  assertTrue(store.history().items().isEmpty());assertEquals(0,store.history().usedBytes());
  assertEquals("2026-10-19",store.history().date());
 }
 @Test void downloadsAreExactAndDeleteReleasesCapacity() throws Exception {
  var store=new ClipboardStore("America/Toronto");byte[] data={0,1,2,-1};
  var file=store.file(new MockMultipartFile("file","C:\\folder\\test.txt","text/plain",data));
  assertEquals("test.txt",file.filename());assertArrayEquals(data,store.download(file.id()));
  store.delete(file.id());assertEquals(0,store.history().usedBytes());assertThrows(ResponseStatusException.class,()->store.download(file.id()));
 }
 @Test void limitsRejectRatherThanSilentlyDroppingHistory() throws Exception {
  var store=new ClipboardStore("America/Toronto");
  assertThrows(ResponseStatusException.class,()->store.text(" "));
  assertThrows(ResponseStatusException.class,()->store.text("a".repeat(100001)));
  assertThrows(ResponseStatusException.class,()->store.file(new MockMultipartFile("file",new byte[0])));
  assertThrows(ResponseStatusException.class,()->store.file(new MockMultipartFile("file",new byte[10*1024*1024+1])));
  for(int i=0;i<200;i++)store.text("a");
  assertThrows(ResponseStatusException.class,()->store.text("b"));assertEquals(200,store.history().items().size());
 }
 @Test void totalByteLimitIsEnforcedAndCanRecover() throws Exception {
  var store=new ClipboardStore("America/Toronto");String id=null;
  for(int i=0;i<10;i++)id=store.file(new MockMultipartFile("file","x","application/octet-stream",new byte[10*1024*1024])).id();
  assertThrows(ResponseStatusException.class,()->store.text("x"));store.delete(id);assertNotNull(store.text("ok"));
 }
 @Test void simultaneousWritersRespectItemLimit() throws Exception {
  var store=new ClipboardStore("America/Toronto");var pool=Executors.newFixedThreadPool(8);
  try{var tasks=new java.util.ArrayList<Callable<Void>>();for(int i=0;i<240;i++)tasks.add(()->{try{store.text("x");}catch(ResponseStatusException expected){}return null;});
   for(var future:pool.invokeAll(tasks))future.get();assertEquals(200,store.history().items().size());assertEquals(200,store.history().usedBytes());
  }finally{pool.shutdownNow();}
 }
 @Test void daylightSavingResetUsesCalendarMidnight() {
  var clock=new MutableClock();clock.now=Instant.parse("2026-11-01T04:00:00Z");var store=new ClipboardStore(ZoneId.of("America/Toronto"),clock);
  assertEquals(Instant.parse("2026-11-02T05:00:00Z"),store.text("fall-back day").expiresAt());
 }
}
