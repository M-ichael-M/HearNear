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
MAX_AVATAR_SIZE = (512, 512)

db = SQLAlchemy(app)

# --- MODELE ---
class User(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    nick = db.Column(db.String(50), unique=True, nullable=False)
    email = db.Column(db.String(100), unique=True, nullable=False)
    password_hash = db.Column(db.String(128), nullable=False)
    created_at = db.Column(db.DateTime, default=datetime.datetime.utcnow)
    instagram_username = db.Column(db.String(30), unique=True, nullable=True)
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


class Friendship(db.Model):
    """
    Reprezentuje relację znajomości / zaproszenia między dwoma użytkownikami.
    requester_id  – kto wysłał zaproszenie
    addressee_id  – kto je otrzymał
    status        – 'pending' | 'accepted' | 'declined'
    """
    id = db.Column(db.Integer, primary_key=True)
    requester_id = db.Column(db.Integer, db.ForeignKey('user.id'), nullable=False)
    addressee_id = db.Column(db.Integer, db.ForeignKey('user.id'), nullable=False)
    status = db.Column(db.String(20), nullable=False, default='pending')
    created_at = db.Column(db.DateTime, default=datetime.datetime.utcnow)
    updated_at = db.Column(db.DateTime, default=datetime.datetime.utcnow, onupdate=datetime.datetime.utcnow)

    requester = db.relationship('User', foreign_keys=[requester_id], backref=db.backref('sent_requests', lazy=True))
    addressee = db.relationship('User', foreign_keys=[addressee_id], backref=db.backref('received_requests', lazy=True))

    __table_args__ = (
        db.UniqueConstraint('requester_id', 'addressee_id', name='unique_friendship'),
    )


# --- WALIDATORY ---
def validate_email(email):
    pattern = r'^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$'
    return re.match(pattern, email) is not None


def validate_instagram_username(username: str) -> bool:
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
    filename = secure_filename(file_storage.filename)
    if not allowed_file(filename):
        raise ValueError('Bad file extension')
    try:
        file_storage.stream.seek(0)
        img = Image.open(file_storage.stream)
        img.verify()
    except Exception:
        raise ValueError('Uploaded file is not a valid image')
    file_storage.stream.seek(0)
    img = Image.open(file_storage.stream).convert('RGB')
    base_name = f"user_{user_id}_{int(datetime.datetime.utcnow().timestamp())}"
    out_name = f"{base_name}.webp"
    out_path = os.path.join(app.config['UPLOAD_FOLDER'], out_name)
    img.thumbnail(MAX_AVATAR_SIZE)
    img.save(out_path, format='WEBP', quality=85)
    return out_name


def _user_info(user):
    return {
        'id': user.id,
        'nick': user.nick,
        'email': user.email,
        'instagram_username': user.instagram_username,
        'instagram_url': f'https://instagram.com/{user.instagram_username}' if user.instagram_username else None,
        'avatar_url': f'/avatars/{user.avatar_filename}' if user.avatar_filename else None
    }


# --- ENDPOINTY AUTH ---
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
            if User.query.filter_by(instagram_username=instagram_username).first():
                return jsonify({'error': 'Instagram username already linked to another account'}), 409
        password_hash = generate_password_hash(password)
        new_user = User(nick=nick, email=email, password_hash=password_hash, instagram_username=instagram_username)
        db.session.add(new_user)
        db.session.commit()
        token = jwt.encode({
            'user_id': new_user.id,
            'exp': datetime.datetime.utcnow() + datetime.timedelta(days=30)
        }, app.config['SECRET_KEY'], algorithm='HS256')
        return jsonify({'message': 'User registered successfully', 'token': token, 'user': _user_info(new_user)}), 201
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
        return jsonify({'message': 'Login successful', 'token': token, 'user': _user_info(user)}), 200
    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/api/verify-token', methods=['POST'])
@token_required
def verify_token(current_user):
    return jsonify({'valid': True, 'user': _user_info(current_user)}), 200


@app.route('/api/logout', methods=['POST'])
@token_required
def logout(current_user):
    return jsonify({'message': 'Logout successful'}), 200


# --- INSTAGRAM ---
@app.route('/api/instagram', methods=['POST'])
@token_required
def add_or_update_instagram(current_user):
    try:
        data = request.get_json()
        if data is None or 'instagram_username' not in data:
            return jsonify({'error': 'instagram_username is required (or null to remove)'}), 400
        insta = data.get('instagram_username')
        if insta is None or (isinstance(insta, str) and insta.strip() == ''):
            current_user.instagram_username = None
            db.session.commit()
            return jsonify({'message': 'Instagram profile removed'}), 200
        insta = insta.strip().lower()
        if not validate_instagram_username(insta):
            return jsonify({'error': 'Invalid Instagram username format'}), 400
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


# --- AVATARY ---
@app.route('/api/avatar', methods=['POST'])
@token_required
def upload_avatar(current_user):
    try:
        if 'avatar' not in request.files:
            return jsonify({'error': 'No file part'}), 400
        file = request.files['avatar']
        if file.filename == '':
            return jsonify({'error': 'No selected file'}), 400
        new_filename = process_and_save_avatar(file, current_user.id)
        if current_user.avatar_filename:
            try:
                old_path = os.path.join(app.config['UPLOAD_FOLDER'], current_user.avatar_filename)
                if os.path.exists(old_path):
                    os.remove(old_path)
            except Exception:
                pass
        current_user.avatar_filename = new_filename
        db.session.commit()
        return jsonify({'message': 'Avatar uploaded', 'avatar_url': f'/avatars/{new_filename}'}), 200
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
    return send_from_directory(app.config['UPLOAD_FOLDER'], filename)


# --- AKTYWNOŚĆ ---
@app.route('/api/update-activity', methods=['POST'])
@token_required
def update_activity(current_user):
    try:
        data = request.get_json()
        required_fields = ['latitude', 'longitude', 'track_name', 'artist_name']
        if not data or not all(k in data for k in required_fields):
            return jsonify({'error': 'Missing required fields'}), 400
        latitude = float(data['latitude'])
        longitude = float(data['longitude'])
        track_name = data['track_name'].strip()
        artist_name = data['artist_name'].strip()
        album_name = data.get('album_name', '').strip() if data.get('album_name') else None
        if not (-90 <= latitude <= 90):
            return jsonify({'error': 'Invalid latitude'}), 400
        if not (-180 <= longitude <= 180):
            return jsonify({'error': 'Invalid longitude'}), 400
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
                user_id=current_user.id, latitude=latitude, longitude=longitude,
                track_name=track_name, artist_name=artist_name, album_name=album_name
            )
            db.session.add(new_activity)
        db.session.commit()
        return jsonify({
            'message': 'Activity updated successfully',
            'activity': {
                'latitude': latitude, 'longitude': longitude,
                'track_name': track_name, 'artist_name': artist_name,
                'album_name': album_name, 'last_updated': datetime.datetime.utcnow().isoformat()
            }
        }), 200
    except ValueError:
        return jsonify({'error': 'Invalid coordinate format'}), 400
    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/api/friends/activity', methods=['GET'])
@token_required
def get_friends_activity(current_user):
    """
    Zwraca aktywność wszystkich zaakceptowanych znajomych,
    którzy aktualnie udostępniają muzykę (bez limitu odległości).
    """
    try:
        max_age_minutes = request.args.get('max_age_minutes', 60, type=int)
        cutoff_time = datetime.datetime.utcnow() - datetime.timedelta(minutes=max_age_minutes)

        # Pobierz wszystkich zaakceptowanych znajomych
        friendships = Friendship.query.filter(
            db.or_(
                Friendship.requester_id == current_user.id,
                Friendship.addressee_id == current_user.id
            ),
            Friendship.status == 'accepted'
        ).all()

        friend_ids = []
        for f in friendships:
            fid = f.addressee_id if f.requester_id == current_user.id else f.requester_id
            friend_ids.append(fid)

        if not friend_ids:
            return jsonify({
                'listeners': [],
                'total_count': 0
            }), 200

        # Pobierz aktywności znajomych (nie starsze niż max_age_minutes)
        activities = UserActivity.query.join(User).filter(
            UserActivity.user_id.in_(friend_ids),
            UserActivity.last_updated >= cutoff_time
        ).all()

        # Jeśli aktualny użytkownik ma lokalizację, policz odległość; wpp distance = None
        current_activity = UserActivity.query.filter_by(user_id=current_user.id).first()

        result = []
        for activity in activities:
            distance = None
            if current_activity:
                distance = round(calculate_distance(
                    current_activity.latitude, current_activity.longitude,
                    activity.latitude, activity.longitude
                ), 2)

            result.append({
                'user_id': activity.user_id,
                'email': activity.user.email,
                'nick': activity.user.nick,
                'distance_km': distance if distance is not None else -1,
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
            })

        result.sort(key=lambda x: x['distance_km'] if x['distance_km'] >= 0 else float('inf'))

        return jsonify({
            'listeners': result,
            'total_count': len(result)
        }), 200

    except Exception as e:
        return jsonify({'error': str(e)}), 500

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
                    'user_id': activity.user_id,
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
                'latitude': activity.latitude, 'longitude': activity.longitude,
                'track_name': activity.track_name, 'artist_name': activity.artist_name,
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
        return jsonify({'message': f'Cleaned up {count} old activities', 'deleted_count': count}), 200
    except Exception as e:
        return jsonify({'error': str(e)}), 500


# ============================================================
# --- SYSTEM ZNAJOMYCH ---
# ============================================================

def _get_friendship(user_a_id: int, user_b_id: int):
    """Zwraca rekord Friendship niezależnie od kolejności requester/addressee, lub None."""
    return Friendship.query.filter(
        db.or_(
            db.and_(Friendship.requester_id == user_a_id, Friendship.addressee_id == user_b_id),
            db.and_(Friendship.requester_id == user_b_id, Friendship.addressee_id == user_a_id)
        )
    ).first()


def _friendship_status_for(current_user_id: int, other_user_id: int):
    """
    Zwraca słownik opisujący stan relacji z perspektywy current_user:
    {
      'status': 'none' | 'pending_sent' | 'pending_received' | 'accepted',
      'friendship_id': int | None
    }
    """
    f = _get_friendship(current_user_id, other_user_id)
    if f is None:
        return {'status': 'none', 'friendship_id': None}
    if f.status == 'accepted':
        return {'status': 'accepted', 'friendship_id': f.id}
    if f.status == 'pending':
        if f.requester_id == current_user_id:
            return {'status': 'pending_sent', 'friendship_id': f.id}
        else:
            return {'status': 'pending_received', 'friendship_id': f.id}
    # declined – traktujemy jak brak relacji (można wysłać ponownie)
    return {'status': 'none', 'friendship_id': None}


@app.route('/api/friends/request/<int:target_user_id>', methods=['POST'])
@token_required
def send_friend_request(current_user, target_user_id):
    """Wysyła zaproszenie do znajomych."""
    try:
        if current_user.id == target_user_id:
            return jsonify({'error': 'Cannot send friend request to yourself'}), 400

        target = User.query.get(target_user_id)
        if not target:
            return jsonify({'error': 'User not found'}), 404

        existing = _get_friendship(current_user.id, target_user_id)
        if existing:
            if existing.status == 'accepted':
                return jsonify({'error': 'Already friends'}), 409
            if existing.status == 'pending':
                return jsonify({'error': 'Friend request already pending'}), 409
            if existing.status == 'declined':
                # Pozwól wysłać ponownie – usuń stary rekord
                db.session.delete(existing)
                db.session.commit()

        friendship = Friendship(
            requester_id=current_user.id,
            addressee_id=target_user_id,
            status='pending'
        )
        db.session.add(friendship)
        db.session.commit()

        return jsonify({
            'message': 'Friend request sent',
            'friendship_id': friendship.id,
            'status': 'pending_sent'
        }), 201

    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/api/friends/accept/<int:friendship_id>', methods=['POST'])
@token_required
def accept_friend_request(current_user, friendship_id):
    """Akceptuje zaproszenie (tylko adresat może akceptować)."""
    try:
        friendship = Friendship.query.get(friendship_id)
        if not friendship:
            return jsonify({'error': 'Friend request not found'}), 404
        if friendship.addressee_id != current_user.id:
            return jsonify({'error': 'Not authorized to accept this request'}), 403
        if friendship.status != 'pending':
            return jsonify({'error': f'Request is already {friendship.status}'}), 409

        friendship.status = 'accepted'
        friendship.updated_at = datetime.datetime.utcnow()
        db.session.commit()

        return jsonify({'message': 'Friend request accepted', 'friendship_id': friendship.id}), 200

    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/api/friends/decline/<int:friendship_id>', methods=['POST'])
@token_required
def decline_friend_request(current_user, friendship_id):
    """Odrzuca zaproszenie (tylko adresat) lub usuwa ze znajomych (obie strony)."""
    try:
        friendship = Friendship.query.get(friendship_id)
        if not friendship:
            return jsonify({'error': 'Friend request not found'}), 404

        is_addressee = friendship.addressee_id == current_user.id
        is_requester = friendship.requester_id == current_user.id

        if not is_addressee and not is_requester:
            return jsonify({'error': 'Not authorized'}), 403

        db.session.delete(friendship)
        db.session.commit()

        return jsonify({'message': 'Friend request declined / friendship removed'}), 200

    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/api/friends/remove/<int:target_user_id>', methods=['DELETE'])
@token_required
def remove_friend(current_user, target_user_id):
    """Usuwa znajomego (działa dla obu stron)."""
    try:
        friendship = _get_friendship(current_user.id, target_user_id)
        if not friendship:
            return jsonify({'error': 'No friendship found'}), 404

        db.session.delete(friendship)
        db.session.commit()

        return jsonify({'message': 'Friend removed'}), 200

    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/api/friends', methods=['GET'])
@token_required
def get_friends(current_user):
    """Zwraca listę zaakceptowanych znajomych."""
    try:
        friendships = Friendship.query.filter(
            db.or_(
                Friendship.requester_id == current_user.id,
                Friendship.addressee_id == current_user.id
            ),
            Friendship.status == 'accepted'
        ).all()

        friends = []
        for f in friendships:
            friend_user = f.addressee if f.requester_id == current_user.id else f.requester
            friends.append({
                'friendship_id': f.id,
                'user': _user_info(friend_user)
            })

        return jsonify({'friends': friends, 'total_count': len(friends)}), 200

    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/api/friends/pending', methods=['GET'])
@token_required
def get_pending_requests(current_user):
    """
    Zwraca oczekujące zaproszenia:
    - received: zaproszenia przysłane DO mnie (do akceptacji)
    - sent: zaproszenia wysłane PRZEZE mnie
    """
    try:
        received = Friendship.query.filter_by(
            addressee_id=current_user.id,
            status='pending'
        ).all()

        sent = Friendship.query.filter_by(
            requester_id=current_user.id,
            status='pending'
        ).all()

        received_list = []
        for f in received:
            received_list.append({
                'friendship_id': f.id,
                'from_user': _user_info(f.requester),
                'created_at': f.created_at.isoformat()
            })

        sent_list = []
        for f in sent:
            sent_list.append({
                'friendship_id': f.id,
                'to_user': _user_info(f.addressee),
                'created_at': f.created_at.isoformat()
            })

        return jsonify({
            'received': received_list,
            'sent': sent_list,
            'pending_count': len(received_list)
        }), 200

    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/api/friends/status/<int:target_user_id>', methods=['GET'])
@token_required
def get_friendship_status(current_user, target_user_id):
    """Zwraca status relacji z konkretnym użytkownikiem."""
    try:
        status_info = _friendship_status_for(current_user.id, target_user_id)
        return jsonify(status_info), 200
    except Exception as e:
        return jsonify({'error': str(e)}), 500


# ============================================================
# --- HEALTH ---
# ============================================================
@app.route('/api/health', methods=['GET'])
def health():
    return jsonify({'status': 'healthy'}), 200


@app.route('/api/users/search', methods=['GET'])
@token_required
def search_users(current_user):
    """
    Wyszukuje użytkowników po nicku (case-insensitive, częściowe dopasowanie).
    ?q=<fraza>&limit=<int>
    Nie zwraca samego siebie.
    """
    try:
        query = request.args.get('q', '').strip()
        limit = request.args.get('limit', 20, type=int)

        if len(query) < 2:
            return jsonify({'error': 'Query must be at least 2 characters'}), 400

        users = User.query.filter(
            User.nick.ilike(f'%{query}%'),
            User.id != current_user.id
        ).limit(limit).all()

        results = []
        for u in users:
            status_info = _friendship_status_for(current_user.id, u.id)
            results.append({
                **_user_info(u),
                'friendship_status': status_info['status'],
                'friendship_id': status_info['friendship_id']
            })

        return jsonify({'users': results, 'total_count': len(results)}), 200

    except Exception as e:
        return jsonify({'error': str(e)}), 500

if __name__ == '__main__':
    with app.app_context():
        db.create_all()
    app.run(debug=True, host='0.0.0.0', port=5000)