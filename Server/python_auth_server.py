from flask import Flask, request, jsonify
from flask_sqlalchemy import SQLAlchemy
from werkzeug.security import generate_password_hash, check_password_hash
import jwt
import datetime
from functools import wraps
import re
import math

app = Flask(__name__)
app.config['SECRET_KEY'] = 'your-secret-key-change-this'
app.config['SQLALCHEMY_DATABASE_URI'] = 'sqlite:///hearnear.db'
app.config['SQLALCHEMY_TRACK_MODIFICATIONS'] = False

db = SQLAlchemy(app)


# Model użytkownika
class User(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    nick = db.Column(db.String(50), unique=True, nullable=False)
    email = db.Column(db.String(100), unique=True, nullable=False)
    password_hash = db.Column(db.String(128), nullable=False)
    created_at = db.Column(db.DateTime, default=datetime.datetime.utcnow)


# Model aktywności użytkownika (lokalizacja + muzyka)
class UserActivity(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    user_id = db.Column(db.Integer, db.ForeignKey('user.id'), nullable=False)
    latitude = db.Column(db.Float, nullable=False)
    longitude = db.Column(db.Float, nullable=False)
    track_name = db.Column(db.String(200), nullable=False)
    artist_name = db.Column(db.String(200), nullable=False)
    album_name = db.Column(db.String(200), nullable=True)
    last_updated = db.Column(db.DateTime, default=datetime.datetime.utcnow)

    # Relacja do użytkownika
    user = db.relationship('User', backref=db.backref('activities', lazy=True))


def validate_email(email):
    pattern = r'^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$'
    return re.match(pattern, email) is not None


def calculate_distance(lat1, lon1, lat2, lon2):
    """
    Oblicza odległość między dwoma punktami używając formuły haversine
    Zwraca odległość w kilometrach
    """
    R = 6371  # Promień Ziemi w kilometrach

    lat1_rad = math.radians(lat1)
    lon1_rad = math.radians(lon1)
    lat2_rad = math.radians(lat2)
    lon2_rad = math.radians(lon2)

    dlat = lat2_rad - lat1_rad
    dlon = lon2_rad - lon1_rad

    a = math.sin(dlat / 2) ** 2 + math.cos(lat1_rad) * math.cos(lat2_rad) * math.sin(dlon / 2) ** 2
    c = 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))

    return R * c


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
        except:
            return jsonify({'error': 'Token is invalid'}), 401

        return f(current_user, *args, **kwargs)

    return decorated


@app.route('/api/register', methods=['POST'])
def register():
    try:
        data = request.get_json()

        # Walidacja danych
        if not data or not all(k in data for k in ('nick', 'email', 'password', 'terms_accepted')):
            return jsonify({'error': 'Missing required fields'}), 400

        nick = data['nick'].strip()
        email = data['email'].strip().lower()
        password = data['password']
        terms_accepted = data['terms_accepted']

        # Sprawdzenie regulaminu
        if not terms_accepted:
            return jsonify({'error': 'Terms must be accepted'}), 400

        # Walidacja długości
        if len(nick) < 3 or len(nick) > 50:
            return jsonify({'error': 'Nick must be between 3 and 50 characters'}), 400

        if len(password) < 6:
            return jsonify({'error': 'Password must be at least 6 characters'}), 400

        # Walidacja email
        if not validate_email(email):
            return jsonify({'error': 'Invalid email format'}), 400

        # Sprawdzenie czy użytkownik już istnieje
        if User.query.filter_by(nick=nick).first():
            return jsonify({'error': 'Nick already exists'}), 409

        if User.query.filter_by(email=email).first():
            return jsonify({'error': 'Email already exists'}), 409

        # Tworzenie nowego użytkownika
        password_hash = generate_password_hash(password)
        new_user = User(
            nick=nick,
            email=email,
            password_hash=password_hash
        )

        db.session.add(new_user)
        db.session.commit()

        # Generowanie tokenu
        token = jwt.encode({
            'user_id': new_user.id,
            'exp': datetime.datetime.utcnow() + datetime.timedelta(days=30)
        }, app.config['SECRET_KEY'], algorithm='HS256')

        return jsonify({
            'message': 'User registered successfully',
            'token': token,
            'user': {
                'id': new_user.id,
                'nick': new_user.nick,
                'email': new_user.email
            }
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

        # Znajdowanie użytkownika
        user = User.query.filter_by(email=email).first()

        if not user or not check_password_hash(user.password_hash, password):
            return jsonify({'error': 'Invalid credentials'}), 401

        # Generowanie tokenu
        token = jwt.encode({
            'user_id': user.id,
            'exp': datetime.datetime.utcnow() + datetime.timedelta(days=30)
        }, app.config['SECRET_KEY'], algorithm='HS256')

        return jsonify({
            'message': 'Login successful',
            'token': token,
            'user': {
                'id': user.id,
                'nick': user.nick,
                'email': user.email
            }
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
            'email': current_user.email
        }
    }), 200


@app.route('/api/logout', methods=['POST'])
@token_required
def logout(current_user):
    # W przypadku JWT, logout jest po stronie klienta (usunięcie tokenu)
    return jsonify({'message': 'Logout successful'}), 200


@app.route('/api/update-activity', methods=['POST'])
@token_required
def update_activity(current_user):
    """
    Aktualizuje lokalizację i aktualnie słuchany utwór użytkownika
    """
    try:
        data = request.get_json()

        # Walidacja wymaganych pól
        required_fields = ['latitude', 'longitude', 'track_name', 'artist_name']
        if not data or not all(k in data for k in required_fields):
            return jsonify({'error': 'Missing required fields: latitude, longitude, track_name, artist_name'}), 400

        latitude = float(data['latitude'])
        longitude = float(data['longitude'])
        track_name = data['track_name'].strip()
        artist_name = data['artist_name'].strip()
        album_name = data.get('album_name', '').strip() if data.get('album_name') else None

        # Walidacja współrzędnych
        if not (-90 <= latitude <= 90):
            return jsonify({'error': 'Invalid latitude'}), 400
        if not (-180 <= longitude <= 180):
            return jsonify({'error': 'Invalid longitude'}), 400

        # Walidacja nazw
        if len(track_name) < 1 or len(track_name) > 200:
            return jsonify({'error': 'Track name must be between 1 and 200 characters'}), 400
        if len(artist_name) < 1 or len(artist_name) > 200:
            return jsonify({'error': 'Artist name must be between 1 and 200 characters'}), 400

        # Sprawdzenie czy użytkownik ma już aktywność
        existing_activity = UserActivity.query.filter_by(user_id=current_user.id).first()

        if existing_activity:
            # Aktualizacja istniejącej aktywności
            existing_activity.latitude = latitude
            existing_activity.longitude = longitude
            existing_activity.track_name = track_name
            existing_activity.artist_name = artist_name
            existing_activity.album_name = album_name
            existing_activity.last_updated = datetime.datetime.utcnow()
        else:
            # Tworzenie nowej aktywności
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


@app.route('/api/nearby-listeners', methods=['GET'])
@token_required
def get_nearby_listeners(current_user):
    """
    Zwraca listę słuchaczy w okolicy z ich lokalizacją i aktualnie słuchaną muzyką
    """
    try:
        # Pobieranie aktywności obecnego użytkownika
        current_activity = UserActivity.query.filter_by(user_id=current_user.id).first()
        if not current_activity:
            return jsonify({'error': 'User location not found. Please update your activity first.'}), 400

        # Parametry zapytania
        max_distance = request.args.get('max_distance', 50, type=float)  # domyślnie 50km
        max_age_minutes = request.args.get('max_age_minutes', 60, type=int)  # domyślnie 60 minut

        # Obliczanie czasu od którego aktywności są aktualne
        cutoff_time = datetime.datetime.utcnow() - datetime.timedelta(minutes=max_age_minutes)

        # Pobieranie wszystkich aktywnych aktywności (oprócz obecnego użytkownika)
        all_activities = UserActivity.query.join(User).filter(
            UserActivity.user_id != current_user.id,
            UserActivity.last_updated >= cutoff_time
        ).all()

        nearby_listeners = []

        for activity in all_activities:
            # Obliczanie odległości
            distance = calculate_distance(
                current_activity.latitude, current_activity.longitude,
                activity.latitude, activity.longitude
            )

            # Sprawdzenie czy jest w zasięgu
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
                    'minutes_ago': int((datetime.datetime.utcnow() - activity.last_updated).total_seconds() / 60)
                }
                nearby_listeners.append(listener_data)

        # Sortowanie po odległości
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
    """
    Zwraca aktualną aktywność użytkownika
    """
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
    """
    Usuwa aktywność użytkownika (przestaje być widoczny dla innych)
    """
    try:
        activity = UserActivity.query.filter_by(user_id=current_user.id).first()

        if not activity:
            return jsonify({'message': 'No activity to delete'}), 404

        db.session.delete(activity)
        db.session.commit()

        return jsonify({'message': 'Activity deleted successfully'}), 200

    except Exception as e:
        return jsonify({'error': str(e)}), 500


# Endpoint do czyszczenia starych aktywności (można uruchomić cron-em)
@app.route('/api/cleanup-old-activities', methods=['POST'])
def cleanup_old_activities():
    """
    Usuwa aktywności starsze niż określony czas (domyślnie 24 godziny)
    """
    try:
        # Można dodać autoryzację dla tego endpointu jeśli potrzeba
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


# Endpoint do testowania
@app.route('/api/health', methods=['GET'])
def health():
    return jsonify({'status': 'healthy'}), 200


if __name__ == '__main__':
    with app.app_context():
        db.create_all()
    app.run(debug=True, host='0.0.0.0', port=5000)