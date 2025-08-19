from flask import Flask, request, jsonify, send_from_directory
from flask_sqlalchemy import SQLAlchemy
from werkzeug.security import generate_password_hash, check_password_hash
import jwt
import datetime
from functools import wraps
import re
import math
import os
from werkzeug.utils import secure_filename
from PIL import Image

# --- KONFIG ---
app = Flask(__name__)
app.config['SECRET_KEY'] = 'your-secret-key-change-this'
app.config['SQLALCHEMY_DATABASE_URI'] = 'sqlite:///hearnear.db'
app.config['SQLALCHEMY_TRACK_MODIFICATIONS'] = False

# Upload / avatar settings
app.config['UPLOAD_FOLDER'] = os.path.join(app.root_path, 'static', 'avatars')
os.makedirs(app.config['UPLOAD_FOLDER'], exist_ok=True)
app.config['MAX_CONTENT_LENGTH'] = 3 * 1024 * 1024  # 3 MB max upload
ALLOWED_EXTENSIONS = {'png', 'jpg', 'jpeg', 'webp'}
MAX_AVATAR_SIZE = (512, 512)  # max thumbnail size

db = SQLAlchemy(app)

# --- MODELE ---
class User(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    nick = db.Column(db.String(50), unique=True, nullable=False)
    email = db.Column(db.String(100), unique=True, nullable=False)
    password_hash = db.Column(db.String(128), nullable=False)
    created_at = db.Column(db.DateTime, default=datetime.datetime.utcnow)
    # opcjonalny profil instagramowy (przechowujemy nazwę użytkownika, mała litera)
    instagram_username = db.Column(db.String(30), unique=True, nullable=True)
    # opcjonalne avatar filename
    avatar_filename = db.Column(db.String(200), unique=False, nullable=True)


class UserActivity(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    user_id = db.Column(db.Integer, db.ForeignKey('user.id'), nullable=False)
    latitude = db.Column(db.Float, nullable=False)
    longitude = db.Column(db.Float, nullable=False)
    track_name = db.Column(db.String(200), nullable=False)
    artist_name = db.Column(db.String(200), nullable=False)
    album_name = db.Column(db.String(200), nullable=True)
    last_updated = db.Column(db.DateTime, default=datetime.datetime.utcnow)

    user = db.relationship('User', backref=db.backref('activities', lazy=True))


# --- WALIDATORY ---
def validate_email(email):
    pattern = r'^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$'
    return re.match(pattern, email) is not None


def validate_instagram_username(username: str) -> bool:
    """
    Instagram username rules (approximation):
    - 1..30 characters
    - letters, numbers, ., _ allowed
    - not start or end with dot
    - no double dots
    """
    if not username:
        return False
    username = username.strip()
    pattern = r'^(?!.*\.\.)(?!\.)[A-Za-z0-9._]{1,30}(?<!\.)$'
    return re.match(pattern, username) is not None


def calculate_distance(lat1, lon1, lat2, lon2):
    R = 6371
    lat1_rad = math.radians(lat1)
    lon1_rad = math.radians(lon1)
    lat2_rad = math.radians(lat2)
    lon2_rad = math.radians(lon2)
    dlat = lat2_rad - lat1_rad
    dlon = lon2_rad - lon1_rad
    a = math.sin(dlat / 2) ** 2 + math.cos(lat1_rad) * math.cos(lat2_rad) * math.sin(dlon / 2) ** 2
    c = 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))
    return R * c


# --- AUTH ---
def token_required(f):
    @wraps(f)
    def decorated(*args, **kwargs):
        token = request.headers.get('Authorization')
        if not token:
            return jsonify({'error': 'Token is missing'}), 401

        try:
            if token.startswith('Bearer '):
                token = token[7:]
            data = jwt.decode(token, app.config['SECRET_KEY'], algorithms=['HS256'])
            current_user = User.query.get(data['user_id'])
            if not current_user:
                raise Exception("User not found")
        except Exception as e:
            return jsonify({'error': 'Token is invalid', 'details': str(e)}), 401

        return f(current_user, *args, **kwargs)

    return decorated


# --- POMOCNICZE FUNKCJE DO AVATARÓW ---
def allowed_file(filename):
    return '.' in filename and filename.rsplit('.', 1)[1].lower() in ALLOWED_EXTENSIONS


def process_and_save_avatar(file_storage, user_id):
    """
    Sprawdza rozszerzenie, waliduje obrazek i zapisuje jako webp w upload folder.
    Zwraca nazwę zapisanego pliku.
    """
    filename = secure_filename(file_storage.filename)
    if not allowed_file(filename):
        raise ValueError('Bad file extension')

    # Sprawdzenie obrazu (verify), a następnie ponowne otwarcie aby przetworzyć
    try:
        file_storage.stream.seek(0)
        img = Image.open(file_storage.stream)
        img.verify()
    except Exception:
        raise ValueError('Uploaded file is not a valid image')

    # reset stream i ponowne otwarcie (verify() może przesunąć wskaźnik)
    file_storage.stream.seek(0)
    img = Image.open(file_storage.stream).convert('RGB')

    # unikalna nazwa
    base_name = f"user_{user_id}_{int(datetime.datetime.utcnow().timestamp())}"
    out_name = f"{base_name}.webp"
    out_path = os.path.join(app.config['UPLOAD_FOLDER'], out_name)

    # resize i zapis
    img.thumbnail(MAX_AVATAR_SIZE)
    img.save(out_path, format='WEBP', quality=85)

    return out_name


# --- ENDPOINTY AUTH / REJESTRACJA ---
@app.route('/api/register', methods=['POST'])
def register():
    try:
        data = request.get_json()

        if not data or not all(k in data for k in ('nick', 'email', 'password', 'terms_accepted')):
            return jsonify({'error': 'Missing required fields'}), 400

        nick = data['nick'].strip()
        email = data['email'].strip().lower()
        password = data['password']
        terms_accepted = data['terms_accepted']

        instagram_username = data.get('instagram_username', None)
        if instagram_username:
            instagram_username = instagram_username.strip().lower()

        if not terms_accepted:
            return jsonify({'error': 'Terms must be accepted'}), 400

        if len(nick) < 3 or len(nick) > 50:
            return jsonify({'error': 'Nick must be between 3 and 50 characters'}), 400

        if len(password) < 6:
            return jsonify({'error': 'Password must be at least 6 characters'}), 400

        if not validate_email(email):
            return jsonify({'error': 'Invalid email format'}), 400

        if User.query.filter_by(nick=nick).first():
            return jsonify({'error': 'Nick already exists'}), 409

        if User.query.filter_by(email=email).first():
            return jsonify({'error': 'Email already exists'}), 409

        if instagram_username:
            if not validate_instagram_username(instagram_username):
                return jsonify({'error': 'Invalid Instagram username format'}), 400
            existing_insta = User.query.filter_by(instagram_username=instagram_username).first()
            if existing_insta:
                return jsonify({'error': 'Instagram username already linked to another account'}), 409

        password_hash = generate_password_hash(password)
        new_user = User(
            nick=nick,
            email=email,
            password_hash=password_hash,
            instagram_username=instagram_username
        )

        db.session.add(new_user)
        db.session.commit()

        token = jwt.encode({
            'user_id': new_user.id,
            'exp': datetime.datetime.utcnow() + datetime.timedelta(days=30)
        }, app.config['SECRET_KEY'], algorithm='HS256')

        user_info = {
            'id': new_user.id,
            'nick': new_user.nick,
            'email': new_user.email,
            'instagram_username': new_user.instagram_username,
            'instagram_url': f'https://instagram.com/{new_user.instagram_username}' if new_user.instagram_username else None,
            'avatar_url': f'/avatars/{new_user.avatar_filename}' if new_user.avatar_filename else None
        }

        return jsonify({
            'message': 'User registered successfully',
            'token': token,
            'user': user_info
        }), 201

    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/api/login', methods=['POST'])
def login():
    try:
        data = request.get_json()

        if not data or not all(k in data for k in ('email', 'password')):
            return jsonify({'error': 'Email and password required'}), 400

        email = data['email'].strip().lower()
        password = data['password']

        user = User.query.filter_by(email=email).first()

        if not user or not check_password_hash(user.password_hash, password):
            return jsonify({'error': 'Invalid credentials'}), 401

        token = jwt.encode({
            'user_id': user.id,
            'exp': datetime.datetime.utcnow() + datetime.timedelta(days=30)
        }, app.config['SECRET_KEY'], algorithm='HS256')

        user_info = {
            'id': user.id,
            'nick': user.nick,
            'email': user.email,
            'instagram_username': user.instagram_username,
            'instagram_url': f'https://instagram.com/{user.instagram_username}' if user.instagram_username else None,
            'avatar_url': f'/avatars/{user.avatar_filename}' if user.avatar_filename else None
        }

        return jsonify({
            'message': 'Login successful',
            'token': token,
            'user': user_info
        }), 200

    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/api/verify-token', methods=['POST'])
@token_required
def verify_token(current_user):
    return jsonify({
        'valid': True,
        'user': {
            'id': current_user.id,
            'nick': current_user.nick,
            'email': current_user.email,
            'instagram_username': current_user.instagram_username,
            'instagram_url': f'https://instagram.com/{current_user.instagram_username}' if current_user.instagram_username else None,
            'avatar_url': f'/avatars/{current_user.avatar_filename}' if current_user.avatar_filename else None
        }
    }), 200


@app.route('/api/logout', methods=['POST'])
@token_required
def logout(current_user):
    return jsonify({'message': 'Logout successful'}), 200


# --- ENDPOINTY DO INSTAGRAMA (dodaj / aktualizuj / usuń) ---
@app.route('/api/instagram', methods=['POST'])
@token_required
def add_or_update_instagram(current_user):
    """
    Body: { "instagram_username": "nazwa" }
    Jeśli pusty string lub null -> usuwa pole (ustawia na None)
    """
    try:
        data = request.get_json()
        if data is None or 'instagram_username' not in data:
            return jsonify({'error': 'instagram_username is required (or null to remove)'}), 400

        insta = data.get('instagram_username')
        if insta is None or (isinstance(insta, str) and insta.strip() == ''):
            # Usuń
            current_user.instagram_username = None
            db.session.commit()
            return jsonify({'message': 'Instagram profile removed'}), 200

        insta = insta.strip().lower()
        if not validate_instagram_username(insta):
            return jsonify({'error': 'Invalid Instagram username format'}), 400

        # Sprawdź czy ktoś inny już nie używa tej nazwy
        other = User.query.filter(User.instagram_username == insta, User.id != current_user.id).first()
        if other:
            return jsonify({'error': 'This Instagram username is already linked to another account'}), 409

        current_user.instagram_username = insta
        db.session.commit()

        return jsonify({
            'message': 'Instagram username saved',
            'instagram_username': current_user.instagram_username,
            'instagram_url': f'https://instagram.com/{current_user.instagram_username}'
        }), 200

    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/api/instagram', methods=['DELETE'])
@token_required
def delete_instagram(current_user):
    try:
        if not current_user.instagram_username:
            return jsonify({'message': 'No instagram username to delete'}), 404
        current_user.instagram_username = None
        db.session.commit()
        return jsonify({'message': 'Instagram username removed'}), 200
    except Exception as e:
        return jsonify({'error': str(e)}), 500


# --- ENDPOINTY DO AVATARÓW (upload / delete / serve) ---
@app.route('/api/avatar', methods=['POST'])
@token_required
def upload_avatar(current_user):
    """
    Upload/Update avatar.
    Body: multipart/form-data z polem 'avatar'
    """
    try:
        if 'avatar' not in request.files:
            return jsonify({'error': 'No file part'}), 400
        file = request.files['avatar']
        if file.filename == '':
            return jsonify({'error': 'No selected file'}), 400

        new_filename = process_and_save_avatar(file, current_user.id)

        # usuń stare avatar jeśli istnieje
        if current_user.avatar_filename:
            try:
                old_path = os.path.join(app.config['UPLOAD_FOLDER'], current_user.avatar_filename)
                if os.path.exists(old_path):
                    os.remove(old_path)
            except Exception:
                pass

        current_user.avatar_filename = new_filename
        db.session.commit()

        avatar_url = f'/avatars/{new_filename}'
        return jsonify({'message': 'Avatar uploaded', 'avatar_url': avatar_url}), 200

    except ValueError as ve:
        return jsonify({'error': str(ve)}), 400
    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/api/avatar', methods=['DELETE'])
@token_required
def delete_avatar(current_user):
    try:
        if not current_user.avatar_filename:
            return jsonify({'message': 'No avatar to delete'}), 404
        path = os.path.join(app.config['UPLOAD_FOLDER'], current_user.avatar_filename)
        if os.path.exists(path):
            os.remove(path)
        current_user.avatar_filename = None
        db.session.commit()
        return jsonify({'message': 'Avatar removed'}), 200
    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/avatars/<filename>', methods=['GET'])
def serve_avatar(filename):
    # publiczny endpoint do serwowania avatarów
    return send_from_directory(app.config['UPLOAD_FOLDER'], filename)


# --- AKTUALIZACJA AKTYWNOŚCI ---
@app.route('/api/update-activity', methods=['POST'])
@token_required
def update_activity(current_user):
    try:
        data = request.get_json()
        required_fields = ['latitude', 'longitude', 'track_name', 'artist_name']
        if not data or not all(k in data for k in required_fields):
            return jsonify({'error': 'Missing required fields: latitude, longitude, track_name, artist_name'}), 400

        latitude = float(data['latitude'])
        longitude = float(data['longitude'])
        track_name = data['track_name'].strip()
        artist_name = data['artist_name'].strip()
        album_name = data.get('album_name', '').strip() if data.get('album_name') else None

        if not (-90 <= latitude <= 90):
            return jsonify({'error': 'Invalid latitude'}), 400
        if not (-180 <= longitude <= 180):
            return jsonify({'error': 'Invalid longitude'}), 400

        if len(track_name) < 1 or len(track_name) > 200:
            return jsonify({'error': 'Track name must be between 1 and 200 characters'}), 400
        if len(artist_name) < 1 or len(artist_name) > 200:
            return jsonify({'error': 'Artist name must be between 1 and 200 characters'}), 400

        existing_activity = UserActivity.query.filter_by(user_id=current_user.id).first()

        if existing_activity:
            existing_activity.latitude = latitude
            existing_activity.longitude = longitude
            existing_activity.track_name = track_name
            existing_activity.artist_name = artist_name
            existing_activity.album_name = album_name
            existing_activity.last_updated = datetime.datetime.utcnow()
        else:
            new_activity = UserActivity(
                user_id=current_user.id,
                latitude=latitude,
                longitude=longitude,
                track_name=track_name,
                artist_name=artist_name,
                album_name=album_name
            )
            db.session.add(new_activity)

        db.session.commit()

        return jsonify({
            'message': 'Activity updated successfully',
            'activity': {
                'latitude': latitude,
                'longitude': longitude,
                'track_name': track_name,
                'artist_name': artist_name,
                'album_name': album_name,
                'last_updated': datetime.datetime.utcnow().isoformat()
            }
        }), 200

    except ValueError:
        return jsonify({'error': 'Invalid coordinate format'}), 400
    except Exception as e:
        return jsonify({'error': str(e)}), 500


# --- LISTA SŁUCHACZY W POBLIŻU (z informacją o IG i avatar jeśli jest) ---
@app.route('/api/nearby-listeners', methods=['GET'])
@token_required
def get_nearby_listeners(current_user):
    try:
        current_activity = UserActivity.query.filter_by(user_id=current_user.id).first()
        if not current_activity:
            return jsonify({'error': 'User location not found. Please update your activity first.'}), 400

        max_distance = request.args.get('max_distance', 50, type=float)
        max_age_minutes = request.args.get('max_age_minutes', 60, type=int)
        cutoff_time = datetime.datetime.utcnow() - datetime.timedelta(minutes=max_age_minutes)

        all_activities = UserActivity.query.join(User).filter(
            UserActivity.user_id != current_user.id,
            UserActivity.last_updated >= cutoff_time
        ).all()

        nearby_listeners = []

        for activity in all_activities:
            distance = calculate_distance(
                current_activity.latitude, current_activity.longitude,
                activity.latitude, activity.longitude
            )

            if distance <= max_distance:
                listener_data = {
                    'email': activity.user.email,
                    'nick': activity.user.nick,
                    'distance_km': round(distance, 2),
                    'latitude': activity.latitude,
                    'longitude': activity.longitude,
                    'track_name': activity.track_name,
                    'artist_name': activity.artist_name,
                    'album_name': activity.album_name,
                    'last_updated': activity.last_updated.isoformat(),
                    'minutes_ago': int((datetime.datetime.utcnow() - activity.last_updated).total_seconds() / 60),
                    'instagram_username': activity.user.instagram_username,
                    'instagram_url': f'https://instagram.com/{activity.user.instagram_username}' if activity.user.instagram_username else None,
                    'avatar_url': f'/avatars/{activity.user.avatar_filename}' if activity.user.avatar_filename else None
                }
                nearby_listeners.append(listener_data)

        nearby_listeners.sort(key=lambda x: x['distance_km'])

        return jsonify({
            'listeners': nearby_listeners,
            'total_count': len(nearby_listeners),
            'search_params': {
                'max_distance_km': max_distance,
                'max_age_minutes': max_age_minutes,
                'your_location': {
                    'latitude': current_activity.latitude,
                    'longitude': current_activity.longitude
                }
            }
        }), 200

    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/api/my-activity', methods=['GET'])
@token_required
def get_my_activity(current_user):
    try:
        activity = UserActivity.query.filter_by(user_id=current_user.id).first()

        if not activity:
            return jsonify({'message': 'No activity found'}), 404

        return jsonify({
            'activity': {
                'latitude': activity.latitude,
                'longitude': activity.longitude,
                'track_name': activity.track_name,
                'artist_name': activity.artist_name,
                'album_name': activity.album_name,
                'last_updated': activity.last_updated.isoformat(),
                'minutes_ago': int((datetime.datetime.utcnow() - activity.last_updated).total_seconds() / 60)
            }
        }), 200

    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/api/delete-activity', methods=['DELETE'])
@token_required
def delete_activity(current_user):
    try:
        activity = UserActivity.query.filter_by(user_id=current_user.id).first()

        if not activity:
            return jsonify({'message': 'No activity to delete'}), 404

        db.session.delete(activity)
        db.session.commit()

        return jsonify({'message': 'Activity deleted successfully'}), 200

    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/api/cleanup-old-activities', methods=['POST'])
def cleanup_old_activities():
    try:
        hours_old = request.json.get('hours_old', 24) if request.json else 24
        cutoff_time = datetime.datetime.utcnow() - datetime.timedelta(hours=hours_old)

        old_activities = UserActivity.query.filter(UserActivity.last_updated < cutoff_time).all()
        count = len(old_activities)

        for activity in old_activities:
            db.session.delete(activity)

        db.session.commit()

        return jsonify({
            'message': f'Cleaned up {count} old activities',
            'deleted_count': count
        }), 200

    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/api/health', methods=['GET'])
def health():
    return jsonify({'status': 'healthy'}), 200


if __name__ == '__main__':
    with app.app_context():
        db.create_all()
    app.run(debug=True, host='0.0.0.0', port=5000)
