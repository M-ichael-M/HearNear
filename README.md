# 🎵 HearNear

> See what the world around you is listening to — in real time.

HearNear is a social music discovery app for Android. It reads what you're playing on Spotify or YouTube Music, shares your location anonymously, and shows you a live map of nearby listeners — so you can discover new music and connect with people who share your taste.

---

## What It Does

- **Detects your music** — reads playback notifications from Spotify & YouTube Music (no API keys needed)
- **Shows nearby listeners on a map** — see real-time pins of people within your chosen radius
- **Tap any pin** to see what someone's listening to and visit their profile
- **Optional Instagram link** — users can connect their IG so others can reach out
- **Privacy-first** — sharing can be toggled off at any time; exact coordinates are never exposed to other users

---

## Stack

| Layer | Technology |
|---|---|
| Android app | Kotlin, Jetpack Compose |
| Maps | MapLibre (MapTiler tiles) |
| Navigation | Compose Navigation |
| State management | ViewModel + StateFlow |
| Auth | JWT (stored in DataStore) |
| Backend | Python, Flask, SQLAlchemy |
| Database | SQLite (dev) |
| Music detection | Android NotificationListenerService |

---

## Project Structure

```
HearNear/
├── App/          # Android app (Kotlin + Compose)
├── Server/       # Flask REST API + user activity simulator (Python)
└── Materials/    # Design assets, icons
```

---

## Backend API (Flask)

RESTful API with JWT auth. Key endpoints:

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/register` | Register new user |
| POST | `/api/login` | Login, returns JWT |
| POST | `/api/update-activity` | Push current location + track |
| GET | `/api/nearby-listeners` | Get listeners within radius |
| GET | `/api/my-activity` | Your current activity |
| POST | `/api/avatar` | Upload profile avatar |
| POST | `/api/instagram` | Link Instagram username |

The simulator (`Server/simulator.py`) acts as a fake user — registers, logs in, and periodically pushes randomised tracks and Warsaw-area coordinates. Useful for local testing without a second device.

---

## App Features (current state)

- [x] Registration & login
- [x] JWT session persistence
- [x] Live map with listener pins (MapLibre)
- [x] Tap pin → see track info + open user profile
- [x] Music detection via NotificationListener (Spotify, YT Music)
- [x] Toggle music sharing on/off
- [x] Instagram profile linking
- [x] Avatar upload
- [x] Nearby listeners list view
- [ ] Push notifications for new nearby listeners *(planned)*
- [ ] Offline mode / caching *(planned)*
- [ ] Friend system *(planned)*

---

## Running Locally

**Backend:**
```bash
cd Server
pip install -r requirements.txt
python app.py
```
Server starts at `http://0.0.0.0:5000`

**Simulator (optional):**
```bash
python simulator.py
# Edit BASE_URL in the file to point to your machine's IP
```

**Android app:**
Open `App/` in Android Studio, set your server IP in the network config, and run on a physical device (NotificationListener requires a real device or a fully configured emulator).

---

## Why It's Interesting (Technically)

- **NotificationListenerService** — intercepts system notifications to extract track metadata without needing Spotify/YT Music OAuth
- **Haversine distance calculation** on the server side to filter nearby users efficiently
- **MapLibre** chosen over Google Maps for open-source flexibility and no per-request billing
- **JWT stateless auth** with 30-day tokens, verified server-side on every protected route
- Avatar images are re-encoded server-side as WebP thumbnails (max 512×512) to save bandwidth

---

## Status

Active development. Core features work end-to-end; the app is not yet on the Play Store.

---

## Author

Built solo as a personal project. Feel free to reach out with questions or ideas.