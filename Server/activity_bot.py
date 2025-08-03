import requests
import random
import time
import sys

# Konfiguracja
# Ustaw BASE_URL bez końcowego slasha
BASE_URL = "http://192.168.1.30:5000"
REGISTER_ENDPOINT = "/api/register"
LOGIN_ENDPOINT = "/api/login"
UPDATE_ENDPOINT = "/api/update-activity"

NICK = "Anna"
EMAIL = "anna@gmail.com"
PASSWORD = "Anna123"
ITERATIONS = 10      # Ile zmian aktywności
SLEEP_SECONDS = 20   # Czas pomiędzy wysłaniem aktywności (w sekundach)

# Dane do pętli aktywności
TRACKS = [
    ("Shape of You", "Ed Sheeran", "÷"),
    ("Blinding Lights", "The Weeknd", "After Hours"),
    ("Levitating", "Dua Lipa", "Future Nostalgia"),
]

headers = {"Content-Type": "application/json"}

def full_url(endpoint: str) -> str:
    """Łączy BASE_URL z endpointem"""
    return f"{BASE_URL}{endpoint}"


def register():
    """Rejestruje użytkownika lub ignoruje jeśli już istnieje"""
    url = full_url(REGISTER_ENDPOINT)
    print(f"POST {url}")
    payload = {
        "nick": NICK,
        "email": EMAIL,
        "password": PASSWORD,
        "terms_accepted": True
    }
    resp = requests.post(url, json=payload, headers=headers)
    if resp.status_code == 201:
        print("Rejestracja udana!")
        return True
    elif resp.status_code == 409:
        print("Użytkownik już istnieje, przechodzę do logowania.")
        return True
    else:
        print(f"Błąd rejestracji: {resp.status_code} {resp.text}")
        return False


def login():
    """Loguje się i zwraca token JWT"""
    url = full_url(LOGIN_ENDPOINT)
    print(f"POST {url}")
    payload = {"email": EMAIL, "password": PASSWORD}
    resp = requests.post(url, json=payload, headers=headers)
    if resp.status_code == 200:
        data = resp.json()
        token = data.get("token")
        print("Logowanie udane, token otrzymany.")
        return token
    else:
        print(f"Błąd logowania: {resp.status_code} {resp.text}")
        return None


def update_activity(token: str):
    """Wysyła losową aktywność użytkownika ITERATIONS razy"""
    url = full_url(UPDATE_ENDPOINT)
    headers_auth = {**headers, "Authorization": f"Bearer {token}"}
    for i in range(1, ITERATIONS + 1):
        # Losowanie koordynatów w okolicach Warszawy
        lat = 52.2 + random.random() * 0.1
        lon = 21.0 + random.random() * 0.1
        track, artist, album = random.choice(TRACKS)
        payload = {
            "latitude": lat,
            "longitude": lon,
            "track_name": track,
            "artist_name": artist,
            "album_name": album
        }
        print(f"POST {url} | payload={payload}")
        resp = requests.post(url, json=payload, headers=headers_auth)
        if resp.status_code == 200:
            print(f"[{i}] Aktywność wysłana: {track} - {artist} (lat={lat:.4f}, lon={lon:.4f})")
        else:
            print(f"[{i}] Błąd wysyłania: {resp.status_code} {resp.text}")
        time.sleep(SLEEP_SECONDS)


if __name__ == "__main__":
    # Krok 1: Rejestracja
    if not register():
        sys.exit(1)
    # Krok 2: Logowanie
    token = login()
    if not token:
        sys.exit(1)
    # Krok 3: Wysyłanie aktywności
    update_activity(token)
    print("Gotowe!")
