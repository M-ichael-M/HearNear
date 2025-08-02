from flask import Flask, request, jsonify
from flask_sqlalchemy import SQLAlchemy
from werkzeug.security import generate_password_hash, check_password_hash
import jwt
import datetime
from functools import wraps
import re

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


def validate_email(email):
    pattern = r'^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$'
    return re.match(pattern, email) is not None


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


# Endpoint do testowania
@app.route('/api/health', methods=['GET'])
def health():
    return jsonify({'status': 'healthy'}), 200


if __name__ == '__main__':
    with app.app_context():
        db.create_all()
    app.run(debug=True, host='0.0.0.0', port=5000)