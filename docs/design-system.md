# ParkFlow design system

The look for every ParkFlow screen: the website, the iOS app and the ESP32 in-car screen.
It follows the style already used on the capstone home page: asphalt, painted line yellow,
parking-sign blue, and the Barlow typefaces.

The web version of these values lives in `src/main/resources/static/css/app.css`.
Change a value there and here together.

## 1. Idea in one line

A parking garage you can read at a glance: dark asphalt for structure, yellow lines for
direction and focus, sign blue for actions, green and red only for free and taken.

## 2. Colors

### Brand and surfaces

| Token | Hex | Use |
|---|---|---|
| `--asphalt` | `#2E3338` | Top bar, hero areas, the car screen background |
| `--asphalt-2` | `#3A4046` | Raised surfaces on dark backgrounds (cards on the car screen) |
| `--concrete` | `#EDEEEA` | Page background on the website |
| `--paper` | `#FAFAF8` | Cards, panels, inputs |
| `--white` | `#FFFFFF` | Text on dark, inputs when focused |
| `--rule` | `#D6D8D3` | Borders and dividers |

### Text

| Token | Hex | Use |
|---|---|---|
| `--ink` | `#22262A` | Main text on light backgrounds |
| `--muted` | `#6A7077` | Secondary text, hints, captions |
| `--on-dark` | `#F4F4F2` | Main text on asphalt |
| `--on-dark-muted` | `#B9BEC3` | Secondary text on asphalt |

### Accents

| Token | Hex | Use |
|---|---|---|
| `--line-yellow` | `#F2C230` | Focus rings, the active nav item, the top bar's bottom line, highlights. Text on it is always `--asphalt`. |
| `--line-yellow-hover` | `#FFD34A` | Hover for yellow buttons |
| `--sign-blue` | `#1E4F9C` | Primary buttons, links, the selected destination pin |
| `--sign-blue-hover` | `#173E7A` | Hover for blue buttons |

### Status (meaning only, never decoration)

| Token | Hex | Meaning | Text-safe version |
|---|---|---|---|
| `--free` | `#2E9E5B` | Free space, approved, success | `--free-text` `#1F7A44` |
| `--taken` | `#C8423B` | Taken space, rejected, error | `--taken` (already readable) |
| `--held` | `#E08A1E` | Held for a driver, pending approval, warning | `--held-text` `#9A5A0C` |
| `--offline` | `#8A9096` | Sensor offline, disabled | `--muted` |

Rules:
- Never use color alone. Pair it with a word or an icon ("Pending", "Free 3").
- Small text in a status color uses the text-safe version, so it stays readable on light backgrounds.
- Status badges: tinted background (the color at 12% opacity) plus the text-safe color for the words.

## 3. Typography

| Role | Font | Weight | Size (web) |
|---|---|---|---|
| Display: page titles, big numbers | Barlow Condensed | 700 | `clamp(2.2rem, 6vw, 3.4rem)`, line height 1.05 |
| Heading 2 | Barlow Condensed | 700 | 2rem (1.7rem on phones) |
| Heading 3 | Barlow | 600 | 1.15rem |
| Body | Barlow | 400 | 17px (16px on phones), line height 1.55 |
| Label, button | Barlow | 600 | 0.95rem |
| Caption, hint | Barlow | 400 | 0.875rem, `--muted` |
| Code, IDs | `ui-monospace, Consolas, monospace` | 400 | 0.9rem |

Loading on the web:

```html
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="https://fonts.googleapis.com/css2?family=Barlow:wght@400;500;600&family=Barlow+Condensed:wght@600;700&display=swap" rel="stylesheet">
```

Fallbacks: body `system-ui, -apple-system, "Segoe UI", Roboto, sans-serif`;
display `"Arial Narrow", system-ui, sans-serif`.

Other platforms:
- **iOS:** bundle Barlow and Barlow Condensed (both are free under the SIL Open Font License), or use SF Pro and SF Pro Condensed if bundling is a problem. Support Dynamic Type.
- **ESP32 LCD:** convert Barlow to bitmap fonts for the UI library (for LVGL use the online font converter). Suggested sizes: 20 px body, 28 px headings, 48 px for big numbers. Include only the characters you need to save flash.

## 4. Spacing, shape and layout

- Spacing scale (multiples of 4 px): `4, 8, 12, 16, 24, 32, 48, 64`.
- Page content width: max 1040 px, side padding 20 px (16 px on phones).
- Corner radius: 4 px for buttons, inputs and badges; 8 px for cards and the map; 999 px for pills.
- Borders: 1 px `--rule`. Cards may use a 4 px colored left or top edge to show their kind (the pattern on the home page).
- Shadows: avoid. Use borders and background contrast instead.
- Breakpoints: 520 px (phone), 720 px (small tablet), 860 px (tablet).

## 5. Components

**Top bar.** `--asphalt` background, 3 px `--line-yellow` bottom border. Left: the blue "P" sign plus "ParkFlow". Right: nav links in `#D5D9DD`; the current page uses a yellow pill with asphalt text. When signed in, show the user's first name and a "Log out" link.

**Buttons.**
| Kind | Look | Use |
|---|---|---|
| Primary | `--sign-blue` background, white text | The main action on a screen (Log in, Save, Search) |
| Accent | `--line-yellow` background, asphalt text | Calls to action on dark areas |
| Secondary | `--paper` background, 1 px `--rule` border, ink text | Cancel, Back, less important actions |
| Danger | white background, `--taken` border and text | Delete, Reject |

Height 44 px minimum (56 px on the car screen). Padding 12 px x 20 px. Disabled: 50% opacity.

**Inputs.** `--paper` background, 1 px `--rule` border, 4 px radius, 44 px tall, label above in 600 weight. Focus: 3 px `--line-yellow` outline. Error: `--taken` border plus a message under the field.

**Cards and panels.** `--paper` background, 1 px `--rule` border, 8 px radius, 24 px padding.

**Status badge.** Pill, 0.8rem 600 text, tinted background. Examples: "Pending" (held), "Approved" (free), "Rejected" (taken).

**Messages.** A bar at the top of the content: success uses a `--free` left edge, error a `--taken` left edge, info a `--sign-blue` left edge. Always say what happened and what to do next.

**Map.** Leaflet with OpenStreetMap tiles, 8 px radius, 1 px `--rule` border, at least 360 px tall on the web.
- Parking building pin: blue circle with a white "P" (same as the brand sign).
- Searched destination: yellow pin with an asphalt border.
- Provider's pin being placed: blue, draggable.
- Credit line "© OpenStreetMap contributors" must stay visible.

**Result list (closest parking).** One row per building: name (600), provider company (muted), distance on the right in Barlow Condensed ("0.4 km"). The closest result gets a 4 px `--free` left edge.

## 6. Voice

- Short, plain sentences. Say what to do: "Enter your username and password."
- Use the user's words: "parking building", "provider", "customer", "destination".
- Errors explain the fix: "That username is taken. Try another one."

## 7. Accessibility

- Text contrast at least 4.5:1 (all text tokens above pass on their intended backgrounds).
- Every input has a visible label. Every icon-only button has an `aria-label`.
- Visible focus everywhere: 3 px `--line-yellow` outline, 2 px offset.
- Touch targets at least 44 x 44 px (56 x 56 px on the car screen).

## 8. ESP32 car screen (5.5 inch touch LCD)

The car screen is used while driving or parked, so it is simpler than the website.

- Dark theme only: `--asphalt` background, `--asphalt-2` cards, `--on-dark` text. This avoids glare at night.
- One task per screen: Log in, Search destination, Results, Building details.
- Large targets: 56 px tall buttons, at least 12 px apart. The on-screen keyboard keys at least 48 px.
- Text: 20 px minimum, 28 px headings, distances in 48 px Barlow Condensed.
- Show connection state in the corner (phone hotspot connected or not) using `--free` / `--offline` plus a word.
- Exact screen resolution: to be filled in when the LCD details arrive.

## 9. Tokens as CSS

```css
:root {
  --asphalt: #2E3338; --asphalt-2: #3A4046;
  --concrete: #EDEEEA; --paper: #FAFAF8; --white: #FFFFFF; --rule: #D6D8D3;
  --ink: #22262A; --muted: #6A7077; --on-dark: #F4F4F2; --on-dark-muted: #B9BEC3;
  --line-yellow: #F2C230; --line-yellow-hover: #FFD34A;
  --sign-blue: #1E4F9C; --sign-blue-hover: #173E7A;
  --free: #2E9E5B; --free-text: #1F7A44;
  --taken: #C8423B;
  --held: #E08A1E; --held-text: #9A5A0C;
  --offline: #8A9096;
  --body: "Barlow", system-ui, -apple-system, "Segoe UI", Roboto, sans-serif;
  --display: "Barlow Condensed", "Arial Narrow", system-ui, sans-serif;
  --radius-s: 4px; --radius-m: 8px;
}
```

For C on the ESP32 (RGB565 is common on these displays; convert with `lv_color_hex(0x2E3338)` in LVGL):

```c
#define PF_ASPHALT     0x2E3338
#define PF_ASPHALT_2   0x3A4046
#define PF_ON_DARK     0xF4F4F2
#define PF_LINE_YELLOW 0xF2C230
#define PF_SIGN_BLUE   0x1E4F9C
#define PF_FREE        0x2E9E5B
#define PF_TAKEN       0xC8423B
#define PF_HELD        0xE08A1E
#define PF_OFFLINE     0x8A9096
```

For Swift:

```swift
extension Color {
    static let pfAsphalt    = Color(red: 0x2E/255, green: 0x33/255, blue: 0x38/255)
    static let pfLineYellow = Color(red: 0xF2/255, green: 0xC2/255, blue: 0x30/255)
    static let pfSignBlue   = Color(red: 0x1E/255, green: 0x4F/255, blue: 0x9C/255)
    static let pfFree       = Color(red: 0x2E/255, green: 0x9E/255, blue: 0x5B/255)
    static let pfTaken      = Color(red: 0xC8/255, green: 0x42/255, blue: 0x3B/255)
}
```
