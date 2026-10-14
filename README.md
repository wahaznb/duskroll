# doomscroller

A minimalist Android tool for tracking screen time on shortform video applications (TikTok, Instagram Reels, YouTube Shorts).

```
 ░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░
 │  simple tracking for shortform scrolling habits          │
 └───────────────────────────────────────────────────────────┘
```

## Purpose

Modern social media platforms are designed to maximize engagement. doomscroller provides awareness without gamification by tracking time spent scrolling through shortform content.

## Functionality

- Tracks time intervals across multiple sessions
- Provides simple overlays to visualize progress
- Minimal UI to avoid distraction
- No telemetry or cloud services

## Technical Details

```
┌─────────────────────────────────────────────────┐
│  rust ├── kotlin/compose                        │
│  ├── overlay service                           │
│  ├── scroll detection                          │
│  └── local preferences                         │
└─────────────────────────────────────────────────┘
```

Files:

- `app/` - Compose UI and Android manifest
- `service/` - Linux overlay service
- `data/` - Local preference storage

## Permissions Required

- Display over other apps - for overlay elements
- Accessibility service - for detecting scroll gestures (local only)

## Privacy

```
● No network access
● Local processing only
● No analytics
● No telemetry
```

## License

MIT License

### Terms

This software comes without any warranty. Use at your own discretion. The license allows personal and commercial use without restriction provided this notice and the license text remain intact.