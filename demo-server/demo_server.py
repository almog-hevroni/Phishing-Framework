import os
import re
from flask import Flask, request, jsonify
from flask_cors import CORS
from dotenv import load_dotenv

# טוען את קובץ ‎.env‎ (אם קיים) ומוסיף את המשתנים ל-os.environ
load_dotenv()

app = Flask(__name__)
CORS(app)  # מאפשר CORS מכל מקור

# --- Regex לוולידציית קלט ---------------------------------------------------
ID_RE   = re.compile(r"^\d{9}$")          # ת״ז = 9 ספרות בדיוק
CODE_RE = re.compile(r"^[A-Z]{2}\d{4}$")  # 2 אותיות גדולות + 4 ספרות
# ---------------------------------------------------------------------------

@app.route("/", methods=["GET"])
def home():
    return jsonify({"message": "Demo server is running"}), 200


@app.route("/api/credentials", methods=["GET", "POST"])
def receive_credentials():
    """
    POST: מצפה ל-JSON עם:
          id (9 ספרות), password (≥6 תווים), code (אופציונלי – AA1234)
    GET : מחזיר הודעת מידע (כדי שלא תתקבל שגיאת ‎405‎ ממפתח בדפדפן).
    """
    # ――― טיפול בבקשת GET ―――
    if request.method == "GET":
        return jsonify({
            "message": "Send a POST request with JSON: {id,password,code}"
        }), 200

    # ――― קבלת הנתונים מה-POST ―――
    data = request.get_json(silent=True)
    if data is None:
        return jsonify(error="Body must be valid JSON"), 400

    id_number  = data.get("id", "")
    password   = data.get("password", "")
    code_value = data.get("code", "")

    # ――― ולידציה מפורטת ―――
    if not ID_RE.fullmatch(id_number):
        return jsonify(error="id must be exactly 9 digits"), 400
    if len(password) < 6:
        return jsonify(error="password must contain at least 6 characters"), 400
    if code_value and not CODE_RE.fullmatch(code_value):
        return jsonify(
            error="code must be 2 capital letters followed by 4 digits"
        ), 400

    # הדפסת הערכים לקונסול לצורך בדיקה
    print(
        f"[Demo-Server] Received credentials → "
        f"ID: {id_number}, Password: {password}, Code: {code_value or 'None'}"
    )

    return jsonify({"status": "success", "message": "Credentials received"}), 200


if __name__ == "__main__":
    # מוציאים את הערך של PORT מ-.env, ואם לא קיים – 5000
    port = int(os.getenv("SERVER_PORT", 5000))

    # DEBUG? (true/false)
    debug_mode = os.getenv("DEBUG", "true").lower() in ("1", "true", "yes")

    # מאזין על כל הכתובות (0.0.0.0) כדי שמכשירים אחרים ברשת יוכלו לגשת
    app.run(host="0.0.0.0", port=port, debug=debug_mode)
