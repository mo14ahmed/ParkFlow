# ParkFlow — daily shared clipboard

This version keeps the existing capstone home and MCR I pages and adds a private,
shared clipboard for your iOS app and Windows browser. Parking hardware remains
planned; `/api/test` still returns the original demo parking numbers.

## Start here

1. Open `API-KEY.txt`: it contains your generated fixed shared key. The same key
   is configured as the server default in `src/main/resources/application.properties`.
2. Build with a **JDK 17 or newer** and access to Maven Central:
   - Windows PowerShell: `.\mvnw.cmd clean package`
   - Linux/macOS: `bash mvnw clean package`
3. Run: `java -jar target/ParkFlow-0.0.1-SNAPSHOT.war`
4. Open `http://localhost:8080/clipboard.html` and enter the key.
5. Deploy the newly built WAR to your VPS, replacing the older deployed WAR, and
   restart the process/service that runs it. The uploaded ZIP's old WAR is NOT
   included because it does not contain the API or these changes.
6. With your existing domain proxy pointing to this app, open
   `https://www.parkflow.tech/clipboard.html` on Windows or iOS.

Java target was lowered from 25 to 17 for compatibility. The Spring Boot version
stays at 4.1.1. PostgreSQL/JPA dependencies were removed because there are no
entities or database features in this project; the app no longer needs a database
just to start. Add those dependencies back when implementing parking persistence.

## Daily behavior and limits

- One shared history for every device holding the key; there are no separate users.
- Text, links, images, and other small files can be uploaded in either direction.
- **10 MiB per file**, **100,000 Java string characters per text**, **200 items**,
  and **100 MiB total stored payload**. Empty uploads are rejected.
- Newest items first; the website refreshes every 10 seconds and when revisited.
- Reset is **calendar midnight in America/Toronto**, not 24 hours after upload.
  A file uploaded at 23:59 expires one minute later. Daylight saving is handled.
- All items are held in RAM and are lost on process restart, redeploy, or shutdown.
  No clipboard database is needed. Expiry is checked on every request; a scheduler
  drops expired entries within about a second even if nobody uses the app.
- Multipart upload buffering can use the servlet container's temporary directory;
  these temporary request files are separate from clipboard retention and are
  normally cleaned by the container at request completion.
- Downloads already saved to a device cannot be removed by the server's reset.
- Run **one application instance**. Multiple replicas have independent RAM histories.
- When full, uploads return 507; delete items or wait for midnight. Nothing is
  silently evicted to make room.
- The Windows page supports Copy text, download, delete, file selection, drag/drop,
  and pasted files/images. It does not automatically monitor your system clipboard.
- The key stays only in the browser tab's JavaScript memory; Lock or reload removes it.

Override settings on the server if desired:

```bash
export PARKFLOW_API_KEY='your-replacement-fixed-secret'
export PARKFLOW_TIMEZONE='America/Toronto'
java -jar target/ParkFlow-0.0.1-SNAPSHOT.war
```

If you change the key, update your iOS app and enter the new key in the website.
The default key also secures `/api/test`; the earlier demo key no longer works.
Keep this project ZIP private because it contains the configured key. Do not put
`API-KEY.txt` or the configured properties in a public repository.

## API contract for iOS

Base URL after deployment: `https://www.parkflow.tech/api/clipboard`
Every request must send `X-API-Key: <key from API-KEY.txt>`.
The server checks it before handling API requests, including upload and download.
No key is accepted through query parameters; no session cookie is required.

| Method | Path | Request | Success |
|---|---|---|---|
| GET | `/api/clipboard` | None | 200: today's history |
| POST | `/api/clipboard/text` | JSON `{"text":"Hello from iPhone"}` | 201: created item |
| POST | `/api/clipboard/files` | multipart/form-data, part named `file` | 201: created item |
| GET | `/api/clipboard/{id}` | None | 200: one item's metadata and text |
| GET | `/api/clipboard/{id}/download` | None | 200: file bytes as an attachment |
| DELETE | `/api/clipboard/{id}` | None | 204: removed from shared history |

Upload one file per API request; the website sends multiple files sequentially.
All API responses are marked `Cache-Control: no-store`. Files are downloaded as
`application/octet-stream` attachments, never rendered as uploaded HTML.

Example history (IDs and dates here are illustrative):

```json
{
  "date": "2026-10-18",
  "timezone": "America/Toronto",
  "expiresAt": "2026-10-19T04:00:00Z",
  "usedBytes": 5,
  "maxBytes": 104857600,
  "maxItems": 200,
  "items": [{
    "id": "8c73c266-9269-4aca-a9c6-f262657d0032",
    "type": "text",
    "text": "Hello",
    "filename": null,
    "size": 5,
    "createdAt": "2026-10-18T20:00:00Z",
    "expiresAt": "2026-10-19T04:00:00Z"
  }]
}
```

File items have `type: "file"`, a filename, and `text: null`.
Timestamps use ISO-8601 UTC; timezone determines the shared calendar day.
Typical failures: 400 invalid/empty input; 401 missing/wrong key;
404 deleted/expired/missing item; 413 oversized file/request; 507 capacity reached.
Application validation errors use `{"error":"message"}`. A reverse proxy may
return its own error body, so also inspect HTTP status in your app.

### Swift integration

Copy `ios/ClipboardAPI.swift` into your iOS project (iOS 15+ async URLSession).
Supply the key from `API-KEY.txt`, preferably after entering it once and storing it
in Keychain. The server validates the key; the app simply sends it.

```swift
let api = ClipboardAPI(
    baseURL: URL(string: "https://www.parkflow.tech/")!,
    apiKey: keyReadFromKeychain
)
let item = try await api.sendText("Hello from iPhone")
let today = try await api.history()
let uploaded = try await api.upload(data: fileData, filename: "notes.txt")
let bytes = try await api.download(id: uploaded.id)
try await api.delete(id: item.id)
```

Use a user-tapped action to read `UIPasteboard.general.string`, call `sendText`,
and show upload results. Use a file importer/document picker for files and a share
sheet or document export for downloaded bytes. This ZIP supplies the API client,
not a complete iOS UI or Share Extension. Swift client has not been compiled here.

### Command-line examples (Linux/macOS)

```bash
export KEY='paste-key-from-API-KEY.txt'
curl -H "X-API-Key: $KEY" https://www.parkflow.tech/api/clipboard
curl -H "X-API-Key: $KEY" -H 'Content-Type: application/json' \
  -d '{"text":"Hello from laptop"}' https://www.parkflow.tech/api/clipboard/text
curl -H "X-API-Key: $KEY" -F 'file=@notes.txt' \
  https://www.parkflow.tech/api/clipboard/files
curl -H "X-API-Key: $KEY" -o received.txt \
  https://www.parkflow.tech/api/clipboard/ITEM_ID/download
```

## VPS proxy

Use HTTPS for the key and file transfers. It also enables browser clipboard copy.
If Nginx is already forwarding this domain to port 8080, add the following inside
that existing `server` block so its default upload limit does not block your files:

```nginx
client_max_body_size 11m;
```

Keep your existing proxy configuration; ensure `/api/` and the new static page are
forwarded to this Spring application. Validate with `sudo nginx -t` before reload.
For existing HTTP requests, configure the proxy to redirect to HTTPS.
Do not expose the source project directory as the website root.
If deployed under a servlet context path, include that prefix in API URLs; the
browser page uses relative URLs and supports that layout.

## Verification and files

- Storage logic compiled against the Spring libraries included in your uploaded
  project; 15 standalone storage checks passed: exact binary transfer, safe
  basename, newest-first order, invalid text, non-file download, midnight reset,
  DST reset, capacity release, file size, and concurrent writers.
- JavaScript syntax check passed.
- JUnit tests included for API authentication, public pages, uploads, invalid text,
  storage size/item limits, concurrency, daily expiration, and DST behavior.
- Full `mvnw test`/WAR build could NOT run here: Maven Central DNS resolution was
  unavailable. No claim is made that the full application integration tests passed.
  Run `mvnw clean package` on your machine before deployment. There is no bundled WAR.
- Browser layout and the Swift client have not been runtime-tested here.

Key backend files: `SecurityConfig`, `ClipboardStore`, `ClipboardController`,
`ApiErrors`. UI files: `clipboard.html`, `clipboard.css`, `clipboard.js`.
Security uses a stateless header-only API and exempts `/api/**` from CSRF because
no cookie or HTTP Basic credential authenticates these operations. Other routes
retain CSRF protection, and unlisted routes are denied. See Spring documentation:
https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html
https://spring.io/guides/gs/uploading-files/

## Authentication build fix

The supplied Windows build log compiled all sources and passed the six storage
tests and application startup test, but exposed three API test failures. The
filter used servletPath, which was empty in MockMvc. It now checks requestURI
minus contextPath, validates the key, and sets a request-scoped authenticated
principal. API authorization also requires CLIPBOARD_API authority, so a missed
filter match cannot grant anonymous access. Added explicit servlet-path and WAR
context-path regression tests. The cache test checks for the no-store directive
rather than requiring one exact header string. The shared key is unchanged.
Rebuild with `mvnw.cmd clean package`; do not skip the tests.
# ParkFlow
