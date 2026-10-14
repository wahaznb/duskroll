D U S K R O L L
═══════════════════════════════════════════════════════


A minimalist Android tool for tracking screen time on shortform video applications.

 ░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░
 │  simple tracking for shortform scrolling habits              │
 └─────────────────────────────────────────────────────────────┘

Purpose

Modern social media platforms are designed to maximize engagement.
Duskroll provides awareness without gamification by tracking time
spent scrolling through shortform content.

Functionality

- Tracks time intervals across multiple sessions
- Provides simple overlays to visualize progress
- Minimal UI to avoid distraction
- No telemetry or cloud services

Technical Details

 +-----------------------------+
 |  rust + kotlin/compose      |
 |  + overlay service          |
 |  + scroll detection         |
 |  + local preferences        |
 +-----------------------------+

Files:

- app/ - Compose UI and Android manifest
- service/ - Linux overlay service
- data/ - Local preference storage

Permissions Required

- Display over other apps - for overlay elements
- Accessibility service - for detecting scroll gestures (local only)

Privacy

 * No network access
 * Local processing only
 * No analytics
 * No telemetry

License

MIT License

Permission is hereby granted to use, copy, modify, merge, distribute,
and sublicense this software freely.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED.


═══════════════════════════════════════════════════════