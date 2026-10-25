package com.keep.aio

import android.app.Activity
import android.app.AlertDialog
import android.app.KeyguardManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.provider.OpenableColumns
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Base64
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

// NOTE: minSdk must be >= 26 (PBKDF2WithHmacSHA256).

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

private const val REQ_SAVE_VAULT = 1001
private const val REQ_OPEN = 1002
private const val REQ_ATTACH = 2001
private const val REQ_SAVE_FILE = 3001
private const val REQ_AUTH = 4001

private const val MAX_FILE_BYTES = 10L * 1024 * 1024
private const val MAX_TOTAL_BYTES = 20L * 1024 * 1024
private const val MAX_OPEN_BYTES = 64L * 1024 * 1024
private const val CLIP_CLEAR_MS = 30_000L
private const val MASK = "••••••••"

private val rng = SecureRandom()

private fun EditText.watch(cb: (String) -> Unit) {
    addTextChangedListener(object : TextWatcher {
        override fun afterTextChanged(s: Editable?) { cb(s?.toString() ?: "") }
        override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
        override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
    })
}

// ============================================================
// APP CONFIG
// ============================================================
data class FieldSpec(
    val label: String,
    val secret: Boolean = false,
    val multiline: Boolean = false,
    val numeric: Boolean = false
)

data class VaultSpec(
    val type: String,
    val title: String,
    val fields: List<FieldSpec>,
    val allowMedia: Boolean = false,
    val dynamic: Boolean = false
)

object AppConfig {
    val BG_OBSIDIAN = Color.parseColor("#0B0C10")
    val CARD_DARK = Color.parseColor("#1F2833")
    val GOLD_PRIMARY = Color.parseColor("#D4AF37")
    val GOLD_LIGHT = Color.parseColor("#F3E5AB")
    val GOLD_DARK = Color.parseColor("#B8860B")
    val TEXT_WHITE = Color.parseColor("#FFFFFF")
    val TEXT_MUTED = Color.parseColor("#8A8F9C")
    val DANGER_RED = Color.parseColor("#FF4444")
    val WARN_ORANGE = Color.parseColor("#FF9800")
    val OK_GREEN = Color.parseColor("#4CAF50")
    val BORDER = Color.parseColor("#333A45")

    val VAULTS = listOf(
        VaultSpec("note", "ملاحظة سرية", listOf(
            FieldSpec("عنوان الملاحظة"),
            FieldSpec("الرسالة السرية", multiline = true))),
        VaultSpec("account", "حساب", listOf(
            FieldSpec("اسم المستخدم / البريد"),
            FieldSpec("كلمة المرور", secret = true),
            FieldSpec("الموقع / التطبيق"),
            FieldSpec("ملاحظات", multiline = true))),
        VaultSpec("document", "مستند / صورة", listOf(
            FieldSpec("وصف المستند")), allowMedia = true),
        VaultSpec("credit_card", "بطاقة ائتمان", listOf(
            FieldSpec("اسم صاحب البطاقة"),
            FieldSpec("رقم البطاقة", secret = true, numeric = true),
            FieldSpec("تاريخ الانتهاء"),
            FieldSpec("CVV", secret = true, numeric = true),
            FieldSpec("PIN", secret = true, numeric = true))),
        VaultSpec("wifi", "شبكة واي فاي", listOf(
            FieldSpec("اسم الشبكة (SSID)"),
            FieldSpec("كلمة المرور", secret = true))),
        VaultSpec("crypto", "محفظة كريبتو", listOf(
            FieldSpec("المحفظة / الشبكة"),
            FieldSpec("العنوان"),
            FieldSpec("العبارة السرية (Seed)", secret = true, multiline = true))),
        VaultSpec("bank", "حساب بنكي", listOf(
            FieldSpec("اسم البنك"),
            FieldSpec("اسم صاحب الحساب"),
            FieldSpec("رقم الحساب"),
            FieldSpec("IBAN"),
            FieldSpec("الرقم السري", secret = true, numeric = true))),
        VaultSpec("custom", "حقول مخصصة", emptyList(), allowMedia = true, dynamic = true)
    )

    fun spec(type: String): VaultSpec = VAULTS.firstOrNull { it.type == type } ?: VAULTS[0]

    val LOCK_LABELS = arrayOf("فوراً", "30 ثانية", "دقيقة", "5 دقائق", "بدون قفل")
    val LOCK_VALUES = intArrayOf(0, 30, 60, 300, -1)
}

// ============================================================
// CRYPTO ENGINE  (v5: AES-256-GCM + AAD, PBKDF2-600k; reads v4 too)
// ============================================================
object CryptoEngine {
    const val ITER_V5 = 600_000
    const val ITER_V4 = 310_000

    private fun aad(vaultId: String) = "KEYKEEP|v5|$vaultId".toByteArray(Charsets.UTF_8)
    private fun b64e(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun b64d(s: String) = Base64.decode(s, Base64.DEFAULT)

    private fun deriveKey(password: CharArray, salt: ByteArray, iter: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iter, 256)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun generateVault(
        vaultType: String,
        data: Map<String, String>,
        secrets: List<String>,
        password: CharArray,
        media: List<JSONObject>
    ): String {
        val vaultId = ByteArray(6).also { rng.nextBytes(it) }.joinToString("") { "%02x".format(it) }
        val salt = ByteArray(32).also { rng.nextBytes(it) }
        val iv = ByteArray(12).also { rng.nextBytes(it) }
        var key: ByteArray? = null
        try {
            val json = JSONObject().apply {
                put("version", "5")
                put("type", vaultType)
                put("created", SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date()))
                val d = JSONObject()
                for ((k, v) in data) d.put(k, v)
                put("data", d)
                put("secret", JSONArray(secrets))
                if (media.isNotEmpty()) {
                    val arr = JSONArray(); media.forEach { arr.put(it) }; put("media", arr)
                }
            }.toString()

            val compressed = gzip(json.toByteArray(Charsets.UTF_8))
            key = deriveKey(password, salt, ITER_V5)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            cipher.updateAAD(aad(vaultId))
            val ciphertext = cipher.doFinal(compressed)

            return TEMPLATE
                .replace("%%SALT%%", b64e(salt))
                .replace("%%IV%%", b64e(iv))
                .replace("%%CIPHER%%", b64e(ciphertext))
                .replace("%%VAULT_ID%%", vaultId)
                .replace("%%ITER%%", ITER_V5.toString())
        } finally {
            key?.fill(0); salt.fill(0); iv.fill(0); password.fill('\u0000')
        }
    }

    fun decryptVault(
        saltB64: String, ivB64: String, cipherB64: String, hmacB64: String,
        vaultId: String, version: String, iter: Int, password: CharArray
    ): String? {
        var key: ByteArray? = null
        try {
            val salt = b64d(saltB64); val iv = b64d(ivB64); val ct = b64d(cipherB64)
            if (ct.size < 17 || salt.size < 16 || iv.size != 12) return null
            key = deriveKey(password, salt, iter)
            val modern = version == "5"
            if (!modern) { // legacy v4 files: HMAC check
                val hk = MessageDigest.getInstance("SHA-256")
                    .digest((String(password) + "|" + vaultId + "|hmac").toByteArray(Charsets.UTF_8))
                val mac = Mac.getInstance("HmacSHA256"); mac.init(SecretKeySpec(hk, "HmacSHA256"))
                if (!MessageDigest.isEqual(mac.doFinal(ct), b64d(hmacB64))) return null
            }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            if (modern) cipher.updateAAD(aad(vaultId))
            val compressed = cipher.doFinal(ct)
            return GZIPInputStream(ByteArrayInputStream(compressed)).bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: Exception) {
            return null
        } finally {
            key?.fill(0); password.fill('\u0000')
        }
    }

    private fun gzip(data: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(data) }
        return bos.toByteArray()
    }

    // Standalone viewer. No template literals / no innerHTML => no XSS, no Kotlin "$" escaping needed.
    // Strict CSP: the page cannot talk to the network at all.
    private val TEMPLATE = """<!DOCTYPE html>
<html lang="ar" dir="rtl">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; img-src data: blob:; base-uri 'none'; form-action 'none'">
<meta name="referrer" content="no-referrer">
<meta name="robots" content="noindex,nofollow">
<title>KEYKEEP ROYAL</title>
<style>
  *{box-sizing:border-box}
  body{margin:0;min-height:100vh;display:flex;flex-direction:column;background:#0B0C10;color:#FFF;font-family:system-ui,-apple-system,sans-serif}
  .bg{position:fixed;inset:0;background:radial-gradient(circle at 50% 0%,#1F2833 0%,#0B0C10 70%);z-index:-1}
  .header{text-align:center;padding:36px 20px 12px}
  .crown{font-size:48px;text-shadow:0 0 20px rgba(212,175,55,.5);animation:float 3s ease-in-out infinite}
  @keyframes float{0%,100%{transform:translateY(0)}50%{transform:translateY(-10px)}}
  h1{color:#D4AF37;margin:8px 0 0;font-family:Georgia,serif;letter-spacing:2px;font-size:24px}
  .container{flex:1;padding:20px;max-width:600px;margin:0 auto;width:100%}
  .glass-card{background:rgba(31,40,51,.75);border:1px solid rgba(212,175,55,.3);border-radius:16px;padding:18px;margin-bottom:16px;box-shadow:0 10px 30px rgba(0,0,0,.5)}
  input{width:100%;padding:16px;margin-bottom:14px;border-radius:12px;border:1px solid #333A45;background:rgba(0,0,0,.4);color:#fff;outline:none;font-size:16px}
  input:focus{border-color:#D4AF37;box-shadow:0 0 10px rgba(212,175,55,.2)}
  button{font-family:inherit}
  .main-btn{width:100%;padding:16px;border-radius:12px;border:none;background:linear-gradient(135deg,#F3E5AB,#D4AF37,#B8860B);color:#000;font-weight:bold;font-size:16px;cursor:pointer}
  .main-btn:disabled{opacity:.5}
  .err{color:#ff7a7a;font-size:14px;text-align:center;margin-top:10px;min-height:20px}
  .meta{color:#8A8F9C;font-size:12px;text-align:center;margin-bottom:10px}
  .data-row{display:flex;gap:8px;align-items:flex-start;padding:12px 0;border-bottom:1px solid rgba(255,255,255,.1)}
  .data-row:last-child{border-bottom:none}
  .data-key{color:#D4AF37;font-weight:bold;min-width:90px;font-size:14px}
  .data-val{flex:1;font-family:monospace;font-size:16px;word-break:break-all;white-space:pre-wrap}
  .tools{display:flex;gap:4px}
  .mini{background:transparent;border:1px solid #333A45;color:#fff;border-radius:8px;padding:4px 8px;cursor:pointer}
  .media{width:100%;border-radius:12px;margin-top:10px;border:1px solid #333A45;display:block}
  .file-name{font-size:14px;word-break:break-all;margin-bottom:8px}
  .file-btn{width:100%;padding:12px;border-radius:8px;border:1px solid #D4AF37;background:transparent;color:#D4AF37;font-weight:bold;cursor:pointer;margin-top:10px}
  .lock-btn{background:transparent;border:1px solid #FF4444;color:#FF4444;border-radius:12px;padding:12px;width:100%;cursor:pointer;margin-top:4px}
  #resultBox{display:none}
</style>
</head>
<body>
<div class="bg"></div>
<div class="header"><div class="crown">👑</div><h1>ROYAL VAULT</h1></div>
<div class="container">
  <div id="loginBox" class="glass-card">
    <input type="password" id="pass" placeholder="أدخل مفتاح الخزنة" autocomplete="off">
    <button class="main-btn" id="goBtn">فتح الخزنة</button>
    <div class="err" id="err"></div>
  </div>
  <div id="resultBox"></div>
</div>
<script>
const SALT = "%%SALT%%"; const IV = "%%IV%%"; const CIPHER = "%%CIPHER%%"; const VAULT_ID = "%%VAULT_ID%%"; const VERSION = "5"; const ITER = "%%ITER%%";
var enc = new TextEncoder();
var fails = 0, busy = false, idleTimer = null, hiddenAt = 0;
function byId(i) { return document.getElementById(i); }
function mk(tag, cls, txt) { var e = document.createElement(tag); if (cls) e.className = cls; if (txt !== undefined && txt !== null) e.textContent = txt; return e; }
function fromB64(s) { var b = atob(s), u = new Uint8Array(b.length); for (var i = 0; i < b.length; i++) u[i] = b.charCodeAt(i); return u; }
function setErr(t) { byId('err').textContent = t; }

async function decryptVault(pass) {
  var iter = parseInt(ITER, 10) || 310000;
  var base = await crypto.subtle.importKey('raw', enc.encode(pass), { name: 'PBKDF2' }, false, ['deriveKey']);
  var key = await crypto.subtle.deriveKey({ name: 'PBKDF2', salt: fromB64(SALT), iterations: iter, hash: 'SHA-256' }, base, { name: 'AES-GCM', length: 256 }, false, ['decrypt']);
  var params = { name: 'AES-GCM', iv: fromB64(IV), additionalData: enc.encode('KEYKEEP|v5|' + VAULT_ID) };
  var plain = await crypto.subtle.decrypt(params, key, fromB64(CIPHER));
  var ds = new DecompressionStream('gzip'); var w = ds.writable.getWriter(); w.write(new Uint8Array(plain)); w.close();
  var out = await new Response(ds.readable).arrayBuffer();
  return JSON.parse(new TextDecoder().decode(out));
}

function isSecret(key, list) {
  if (list) return list.indexOf(key) >= 0;
  return /password|pass|cvv|pin|seed|secret|كلمة المرور|رقم البطاقة|العبارة/i.test(key);
}

function copyText(t, btn) {
  function done() {
    var o = btn.textContent; btn.textContent = '✔';
    setTimeout(function () { btn.textContent = o; }, 1200);
    setTimeout(function () { try { navigator.clipboard.writeText(' '); } catch (e) {} }, 30000);
  }
  function fallback() {
    var ta = document.createElement('textarea'); ta.value = t; ta.style.position = 'fixed'; ta.style.opacity = '0';
    document.body.appendChild(ta); ta.select();
    try { document.execCommand('copy'); done(); } catch (e) {}
    document.body.removeChild(ta);
  }
  if (navigator.clipboard && navigator.clipboard.writeText) navigator.clipboard.writeText(t).then(done, fallback); else fallback();
}

function toBlob(item) {
  var mime = item.mime || 'application/octet-stream', b64 = item.data || '';
  if (b64.indexOf('data:') === 0) {
    var m = /^data:([^;,]*)/.exec(b64); if (m && m[1]) mime = m[1];
    b64 = b64.substring(b64.indexOf(',') + 1);
  }
  return new Blob([fromB64(b64)], { type: mime });
}

function saveBlob(blob, name) {
  var url = URL.createObjectURL(blob); var a = document.createElement('a');
  a.href = url; a.download = name || 'file'; document.body.appendChild(a); a.click(); document.body.removeChild(a);
  setTimeout(function () { URL.revokeObjectURL(url); }, 10000);
}

function show(data) {
  byId('loginBox').style.display = 'none';
  var box = byId('resultBox'); box.textContent = ''; box.style.display = 'block';
  if (data.created) box.appendChild(mk('div', 'meta', 'أُنشئت: ' + data.created));
  var secrets = Array.isArray(data.secret) ? data.secret : null;
  var f = data.data || {};
  var keys = Object.keys(f).filter(function (k) { return f[k]; });
  if (keys.length) {
    var card = mk('div', 'glass-card');
    keys.forEach(function (k) {
      var v = String(f[k]); var hide = isSecret(k, secrets); var shown = !hide;
      var row = mk('div', 'data-row');
      var val = mk('span', 'data-val', hide ? MASK_TXT : v);
      var tools = mk('span', 'tools');
      if (hide) {
        var eye = mk('button', 'mini', '👁');
        eye.onclick = function () { shown = !shown; val.textContent = shown ? v : MASK_TXT; };
        tools.appendChild(eye);
      }
      var cp = mk('button', 'mini', '📋'); cp.onclick = function () { copyText(v, cp); }; tools.appendChild(cp);
      row.appendChild(mk('span', 'data-key', k)); row.appendChild(val); row.appendChild(tools); card.appendChild(row);
    });
    box.appendChild(card);
  }
  (data.media || []).forEach(function (item) {
    var c = mk('div', 'glass-card');
    c.appendChild(mk('div', 'file-name', (item.kind === 'image' ? '🖼 ' : '📎 ') + (item.name || 'file')));
    var blob = toBlob(item);
    if (item.kind === 'image' && /^image\//.test(blob.type)) {
      var img = mk('img', 'media'); img.src = URL.createObjectURL(blob); c.appendChild(img);
    }
    var b = mk('button', 'file-btn', '⬇ حفظ'); b.onclick = function () { saveBlob(blob, item.name); }; c.appendChild(b);
    box.appendChild(c);
  });
  var lk = mk('button', 'lock-btn', '🔒 قفل الآن'); lk.onclick = function () { location.reload(); }; box.appendChild(lk);
  armIdle();
}
var MASK_TXT = '••••••••';

function armIdle() {
  function reset() { clearTimeout(idleTimer); idleTimer = setTimeout(function () { location.reload(); }, 90000); }
  ['click', 'touchstart', 'keydown', 'scroll'].forEach(function (ev) { document.addEventListener(ev, reset, { passive: true }); });
  reset();
}

function cooldown(sec) {
  var btn = byId('goBtn');
  if (sec <= 0) { btn.disabled = false; btn.textContent = 'فتح الخزنة'; return; }
  btn.disabled = true; btn.textContent = 'انتظر ' + sec + ' ث';
  setTimeout(function () { cooldown(sec - 1); }, 1000);
}

async function unlock() {
  if (busy) return;
  var pass = byId('pass').value; if (!pass) return;
  if (!window.crypto || !crypto.subtle) { setErr('⚠️ المتصفح يمنع التشفير. افتح الملف بمتصفح حديث أو عبر تطبيق KEYKEEP.'); return; }
  busy = true; byId('goBtn').disabled = true; setErr('جاري فك التشفير...');
  try {
    var data = await decryptVault(pass);
    byId('pass').value = ''; setErr(''); show(data);
  } catch (e) {
    fails++; setErr('مفتاح خاطئ أو ملف تالف');
    cooldown(fails >= 3 ? Math.min(Math.pow(2, fails - 2), 60) : 0);
  }
  busy = false;
  if (byId('goBtn').textContent === 'فتح الخزنة' || fails < 3) byId('goBtn').disabled = false;
}

byId('goBtn').addEventListener('click', unlock);
byId('pass').addEventListener('keydown', function (e) { if (e.key === 'Enter') unlock(); });
document.addEventListener('visibilitychange', function () {
  if (document.hidden) hiddenAt = Date.now();
  else if (hiddenAt && byId('resultBox').style.display === 'block' && Date.now() - hiddenAt > 30000) location.reload();
});
</script>
</body>
</html>"""
}

// ============================================================
// STORAGE & STATS & SETTINGS
// ============================================================
object VaultStore {
    private const val PREFS = "keykeep_royal_v4"
    private lateinit var prefs: SharedPreferences

    var vaultCount = 0
    var attachmentCount = 0
    var usedSpace = 0L
    var lastActivity = "لا يوجد"

    fun init(ctx: Context) {
        prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        loadStats()
    }

    private fun loadStats() {
        vaultCount = prefs.getInt("v_count", 0); attachmentCount = prefs.getInt("a_count", 0)
        usedSpace = prefs.getLong("u_space", 0L); lastActivity = prefs.getString("l_act", "لا يوجد") ?: "لا يوجد"
    }

    fun addVaultStats(attachments: Int, size: Long) {
        vaultCount++; attachmentCount += attachments; usedSpace += size
        lastActivity = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date())
        prefs.edit().putInt("v_count", vaultCount).putInt("a_count", attachmentCount)
            .putLong("u_space", usedSpace).putString("l_act", lastActivity).apply()
    }

    fun resetStats() {
        vaultCount = 0; attachmentCount = 0; usedSpace = 0L; lastActivity = "لا يوجد"
        prefs.edit().putInt("v_count", 0).putInt("a_count", 0).putLong("u_space", 0L).putString("l_act", lastActivity).apply()
    }

    fun isScreenSecure(): Boolean = prefs.getBoolean("secure_screen", true) // secure by default
    fun setScreenSecure(v: Boolean) { prefs.edit().putBoolean("secure_screen", v).apply() }

    fun autoLockSeconds(): Int = prefs.getInt("auto_lock_s", 30)
    fun setAutoLockSeconds(v: Int) { prefs.edit().putInt("auto_lock_s", v).apply() }

    fun isAppLock(): Boolean = prefs.getBoolean("app_lock", false)
    fun setAppLock(v: Boolean) { prefs.edit().putBoolean("app_lock", v).apply() }

    // UI-level throttling of wrong passwords (the real protection is PBKDF2 + a strong password)
    fun lockRemainingMs(): Long = (prefs.getLong("lock_until", 0L) - System.currentTimeMillis()).coerceAtLeast(0L)
    fun recordFailure() {
        val n = prefs.getInt("fail_count", 0) + 1
        val delay = if (n < 3) 0L else minOf(5_000L shl minOf(n - 3, 6), 300_000L)
        prefs.edit().putInt("fail_count", n).putLong("lock_until", System.currentTimeMillis() + delay).apply()
    }
    fun resetFailures() { prefs.edit().putInt("fail_count", 0).putLong("lock_until", 0L).apply() }
}

// ============================================================
// MAIN ACTIVITY
// ============================================================
class MainActivity : Activity() {

    private class CustomRow(var name: String = "", var value: String = "", var secret: Boolean = false)

    private var currentScreen = "home"
    private var pendingSaveHtml: String? = null
    private var pendingSaveFileData: String? = null
    private var decryptedPayloadJson: String? = null
    private val mediaItems = mutableListOf<JSONObject>()

    private var selectedVaultType = "note"
    private val fieldValues = mutableMapOf<String, MutableMap<String, String>>()
    private val customRows = mutableListOf(CustomRow())

    private var draftPass = ""
    private var draftPassConfirm = ""

    private var lastPausedTime = 0L
    private var suppressLockOnce = false
    private var appUnlocked = false

    private val handler = Handler(Looper.getMainLooper())
    private val clearClipRunnable = Runnable { clearClipboard() }
    private lateinit var rootContainer: FrameLayout

    // ---------------------------------------------------------- lifecycle
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            VaultStore.init(this)
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            applyScreenSecurity()
            rootContainer = FrameLayout(this)
            setContentView(rootContainer)
            if (needsAppLock()) {
                currentScreen = "locked"; renderUI()
                rootContainer.post { requestDeviceAuth() }
            } else {
                appUnlocked = true; renderUI()
            }
        } catch (e: Throwable) {
            setContentView(TextView(this).apply {
                text = "FATAL ERROR:\n${e.message}\n\n${e.stackTraceToString()}"
                setTextColor(Color.RED); setBackgroundColor(Color.BLACK); setPadding(32, 32, 32, 32); textSize = 12f
            })
        }
    }

    override fun onResume() {
        super.onResume()
        if (suppressLockOnce) { suppressLockOnce = false; return }
        val limit = VaultStore.autoLockSeconds()
        if (limit < 0 || lastPausedTime == 0L) return
        if (System.currentTimeMillis() - lastPausedTime >= limit * 1000L) lockNow()
    }

    override fun onPause() { super.onPause(); lastPausedTime = System.currentTimeMillis() }

    override fun onDestroy() {
        handler.removeCallbacks(clearClipRunnable)
        super.onDestroy()
    }

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun onBackPressed() {
        if (currentScreen == "create" || currentScreen == "open" || currentScreen == "view") goHome()
        else super.onBackPressed()
    }

    private fun goHome() { currentScreen = "home"; decryptedPayloadJson = null; renderUI() }

    private fun lockNow() {
        lastPausedTime = 0L
        decryptedPayloadJson = null; pendingSaveHtml = null; pendingSaveFileData = null
        if (needsAppLock()) {
            wipeDrafts(); appUnlocked = false; currentScreen = "locked"; renderUI()
        } else if (currentScreen == "view") {
            currentScreen = "home"; renderUI()
        }
    }

    private fun wipeDrafts() {
        mediaItems.clear(); fieldValues.clear(); customRows.clear(); customRows.add(CustomRow())
        draftPass = ""; draftPassConfirm = ""; pendingSaveHtml = null
    }

    // ---------------------------------------------------------- device lock
    private fun deviceSecure(): Boolean {
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        return km.isDeviceSecure
    }

    private fun needsAppLock() = VaultStore.isAppLock() && deviceSecure()

    private fun requestDeviceAuth() {
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        @Suppress("DEPRECATION")
        val intent = km.createConfirmDeviceCredentialIntent("KEYKEEP", "أكّد هويتك لفتح التطبيق")
        if (intent == null) { appUnlocked = true; currentScreen = "home"; renderUI(); return }
        suppressLockOnce = true
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQ_AUTH)
    }

    // ---------------------------------------------------------- ui helpers
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun toast(msg: String, long: Boolean = false) =
        Toast.makeText(this, msg, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()

    private fun lp(w: Int = MATCH, h: Int = WRAP, top: Int = 0, bottom: Int = 0) =
        LinearLayout.LayoutParams(w, h).also { it.topMargin = dp(top); it.bottomMargin = dp(bottom) }

    private fun applyScreenSecurity() {
        try {
            if (VaultStore.isScreenSecure()) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } catch (e: Exception) {}
    }

    private fun luxuryBg(color: Int, radius: Int, strokeWidth: Int = 1, strokeColor: Int = AppConfig.GOLD_PRIMARY) =
        GradientDrawable().apply {
            setColor(color); cornerRadius = dp(radius).toFloat()
            if (strokeWidth > 0) setStroke(dp(strokeWidth), strokeColor)
        }

    private fun goldButton(label: String, heightDp: Int = 56): Button = Button(this).apply {
        text = label; setTextColor(Color.BLACK); textSize = 16f; typeface = Typeface.SERIF
        isAllCaps = false; stateListAnimator = null
        background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(AppConfig.GOLD_LIGHT, AppConfig.GOLD_PRIMARY, AppConfig.GOLD_DARK)).apply { cornerRadius = dp(12).toFloat() }
        layoutParams = lp(h = dp(heightDp))
        setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(100).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
            }
            false
        }
    }

    private fun outlineButton(label: String, heightDp: Int = 48, color: Int = AppConfig.GOLD_PRIMARY): Button = Button(this).apply {
        text = label; setTextColor(color); textSize = 15f; typeface = Typeface.SERIF
        isAllCaps = false; stateListAnimator = null
        background = luxuryBg(Color.TRANSPARENT, 12, 1, color)
        layoutParams = lp(h = dp(heightDp))
    }

    private fun createLabel(text: String) = TextView(this).apply {
        this.text = text; setTextColor(AppConfig.GOLD_PRIMARY); textSize = 13f
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD); setPadding(0, dp(16), 0, dp(8))
    }

    private fun createLuxuryInput(hint: String, multiLine: Boolean) = EditText(this).apply {
        this.hint = hint; setTextColor(AppConfig.TEXT_WHITE); setHintTextColor(Color.parseColor("#555555"))
        background = luxuryBg(Color.parseColor("#151A20"), 8, 1, AppConfig.BORDER)
        setPadding(dp(14), dp(14), dp(14), dp(14))
        if (multiLine) { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE; minLines = 3; gravity = Gravity.TOP or Gravity.START }
        else inputType = InputType.TYPE_CLASS_TEXT
    }

    private fun createBackButton() = Button(this).apply {
        text = "→ رجوع للوحة التحكم"; setTextColor(AppConfig.TEXT_MUTED); background = null
        isAllCaps = false; gravity = Gravity.START; setPadding(0, 0, 0, dp(16))
        setOnClickListener { goHome() }
    }

    private fun setVisible(et: EditText, visible: Boolean, numeric: Boolean) {
        val sel = et.selectionEnd
        et.inputType = if (numeric) {
            InputType.TYPE_CLASS_NUMBER or (if (visible) 0 else InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        } else {
            InputType.TYPE_CLASS_TEXT or (if (visible) InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD else InputType.TYPE_TEXT_VARIATION_PASSWORD)
        }
        et.typeface = Typeface.DEFAULT
        if (sel >= 0) et.setSelection(sel.coerceAtMost(et.text.length))
    }

    /** Hidden-by-default input with an eye toggle. Returns (row, edit). */
    private fun secretInput(hint: String, initial: String, numeric: Boolean = false, onChange: (String) -> Unit): Pair<LinearLayout, EditText> {
        val et = createLuxuryInput(hint, false)
        setVisible(et, false, numeric)
        et.setText(initial); et.watch(onChange)
        et.layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        val eye = TextView(this).apply { text = "👁"; textSize = 20f; setPadding(dp(12), 0, dp(4), 0); alpha = 0.5f; tag = false }
        eye.setOnClickListener {
            val v = !(eye.tag as Boolean); eye.tag = v; eye.alpha = if (v) 1f else 0.5f
            setVisible(et, v, numeric)
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(et); row.addView(eye)
        return Pair(row, et)
    }

    // ---------------------------------------------------------- render
    private fun renderUI() {
        applyScreenSecurity()
        val root = FrameLayout(this).apply {
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(AppConfig.BG_OBSIDIAN, Color.parseColor("#151A20")))
        }
        val scrollView = ScrollView(this).apply { layoutParams = FrameLayout.LayoutParams(MATCH, MATCH) }
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(50), dp(20), dp(32)) }
        scrollView.addView(container); root.addView(scrollView)

        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, 0, dp(20)) }
        header.addView(TextView(this).apply {
            text = "👑 KEYKEEP"; textSize = 24f; setTextColor(AppConfig.GOLD_PRIMARY)
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD); letterSpacing = 0.1f
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        })
        if (currentScreen != "locked") header.addView(TextView(this).apply { text = "⚙️"; textSize = 24f; setOnClickListener { showSettingsDialog() } })
        container.addView(header)

        when (currentScreen) {
            "locked" -> buildLocked(container)
            "home" -> buildDashboard(container)
            "create" -> buildCreate(container)
            "open" -> buildOpen(container)
            "view" -> buildView(container)
        }
        rootContainer.removeAllViews(); rootContainer.addView(root)
    }

    private fun buildLocked(parent: LinearLayout) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            background = luxuryBg(AppConfig.CARD_DARK, 16, 1, AppConfig.BORDER); setPadding(dp(32), dp(48), dp(32), dp(48))
        }
        card.addView(TextView(this).apply { text = "🔐"; textSize = 64f; gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(16)) })
        card.addView(TextView(this).apply { text = "التطبيق مقفل"; setTextColor(AppConfig.TEXT_WHITE); textSize = 18f; gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(24)) })
        card.addView(goldButton("فتح ببصمة / رمز الجهاز", 52).apply { setOnClickListener { requestDeviceAuth() } })
        parent.addView(card)
    }

    private fun buildDashboard(parent: LinearLayout) {
        val statsCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = luxuryBg(AppConfig.CARD_DARK, 16, 1, AppConfig.BORDER)
            setPadding(dp(20), dp(20), dp(20), dp(20)); layoutParams = lp(bottom = 24)
        }
        statsCard.addView(TextView(this).apply { text = "لوحة التحكم الملكية"; setTextColor(AppConfig.GOLD_PRIMARY); textSize = 16f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(16)) })
        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        row1.addView(createStatBox("الخزائن", VaultStore.vaultCount.toString()))
        row1.addView(createStatBox("المرفقات", VaultStore.attachmentCount.toString()))
        statsCard.addView(row1)
        statsCard.addView(TextView(this).apply { text = "المساحة المستخدمة: ${formatSize(VaultStore.usedSpace)}"; setTextColor(AppConfig.TEXT_MUTED); textSize = 12f; gravity = Gravity.CENTER; setPadding(0, dp(12), 0, dp(4)) })
        statsCard.addView(TextView(this).apply { text = "آخر نشاط: ${VaultStore.lastActivity}"; setTextColor(AppConfig.TEXT_MUTED); textSize = 12f; gravity = Gravity.CENTER })
        parent.addView(statsCard)

        parent.addView(goldButton("إنشاء خزنة جديدة").apply {
            layoutParams = lp(h = dp(56), bottom = 16)
            setOnClickListener { wipeDrafts(); currentScreen = "create"; renderUI() }
        })
        parent.addView(outlineButton("فتح خزنة موجودة", 56).apply { setOnClickListener { currentScreen = "open"; renderUI() } })

        parent.addView(TextView(this).apply {
            text = "🛡 AES-256-GCM  •  PBKDF2 × 600,000  •  بدون إنترنت  •  بدون سيرفرات"
            setTextColor(AppConfig.TEXT_MUTED); textSize = 11f; gravity = Gravity.CENTER; setPadding(0, dp(24), 0, 0)
        })
    }

    private fun createStatBox(label: String, value: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        addView(TextView(this@MainActivity).apply { text = value; textSize = 28f; setTextColor(AppConfig.GOLD_LIGHT); typeface = Typeface.DEFAULT_BOLD })
        addView(TextView(this@MainActivity).apply { text = label; textSize = 12f; setTextColor(AppConfig.TEXT_MUTED) })
    }

    // ---------------------------------------------------------- create
    private fun valuesFor(type: String) = fieldValues.getOrPut(type) { mutableMapOf() }

    private fun buildCreate(parent: LinearLayout) {
        val spec = AppConfig.spec(selectedVaultType)
        parent.addView(createBackButton())
        parent.addView(createLabel("نوع البيانات"))
        parent.addView(Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, AppConfig.VAULTS.map { it.title })
            setSelection(AppConfig.VAULTS.indexOfFirst { it.type == selectedVaultType }.coerceAtLeast(0), false)
            background = luxuryBg(AppConfig.CARD_DARK, 8, 1, AppConfig.BORDER)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                    val t = AppConfig.VAULTS[p2].type
                    if (selectedVaultType != t) { selectedVaultType = t; parent.post { renderUI() } }
                }
                override fun onNothingSelected(p0: AdapterView<*>?) {}
            }
        })

        if (spec.dynamic) buildCustomRows(parent) else {
            val values = valuesFor(spec.type)
            for (f in spec.fields) {
                parent.addView(createLabel(f.label))
                if (f.secret) {
                    parent.addView(secretInput(f.label, values[f.label] ?: "", f.numeric) { values[f.label] = it }.first)
                } else {
                    val et = createLuxuryInput(f.label, f.multiline)
                    et.setText(values[f.label] ?: ""); et.watch { values[f.label] = it }
                    parent.addView(et)
                }
            }
        }

        if (spec.allowMedia) buildMediaSection(parent)

        // ---- encryption key
        parent.addView(createLabel("مفتاح التشفير"))
        val strengthBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; progress = 0; layoutParams = lp(h = dp(4), top = 6)
        }
        val strengthLabel = TextView(this).apply { textSize = 12f; setTextColor(AppConfig.TEXT_MUTED); setPadding(0, dp(4), 0, 0) }
        fun updateStrength(p: String) {
            val s = strengthOf(p)
            val (label, color) = when {
                p.isEmpty() -> Pair("", AppConfig.TEXT_MUTED)
                s < 40 -> Pair("ضعيفة", AppConfig.DANGER_RED)
                s < 65 -> Pair("متوسطة", AppConfig.WARN_ORANGE)
                s < 85 -> Pair("قوية", AppConfig.GOLD_PRIMARY)
                else -> Pair("ممتازة", AppConfig.OK_GREEN)
            }
            strengthBar.progress = s; strengthBar.progressTintList = ColorStateList.valueOf(color)
            strengthLabel.text = if (label.isEmpty()) "" else "القوة: $label"; strengthLabel.setTextColor(color)
        }

        val (passRow, passEt) = secretInput("كلمة مرور قوية", draftPass) { draftPass = it; updateStrength(it) }
        parent.addView(passRow); parent.addView(strengthBar); parent.addView(strengthLabel)
        updateStrength(draftPass)

        parent.addView(createLabel("تأكيد المفتاح"))
        val (confirmRow, confirmEt) = secretInput("أعد كتابة كلمة المرور", draftPassConfirm) { draftPassConfirm = it }
        parent.addView(confirmRow)

        parent.addView(Button(this).apply {
            text = "🎲 مولّد كلمات المرور"; setTextColor(AppConfig.TEXT_MUTED); textSize = 13f; background = null; isAllCaps = false
            setOnClickListener {
                showGeneratorDialog { pw ->
                    passEt.setText(pw); confirmEt.setText(pw)
                    setVisible(passEt, true, false); setVisible(confirmEt, true, false)
                    toast("تم استخدام كلمة المرور — احفظها في مكان آمن")
                }
            }
        })

        parent.addView(createLabel("تذكير: لا يمكن استرجاع الخزنة إذا نسيت المفتاح").apply { setTextColor(AppConfig.TEXT_MUTED); textSize = 11f })

        parent.addView(goldButton("🔒 تشفير وحفظ الملف").apply {
            layoutParams = lp(h = dp(56), top = 16)
            setOnClickListener { onEncryptClicked(spec) }
        })
    }

    private fun buildCustomRows(parent: LinearLayout) {
        customRows.forEachIndexed { i, r ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; background = luxuryBg(AppConfig.CARD_DARK, 10, 1, AppConfig.BORDER)
                setPadding(dp(12), dp(10), dp(12), dp(10)); layoutParams = lp(top = 12)
            }
            val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val nameEt = createLuxuryInput("اسم الحقل", false).apply { setText(r.name); watch { r.name = it }; layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f) }
            top.addView(nameEt)
            if (customRows.size > 1) top.addView(TextView(this).apply {
                text = "✖"; setTextColor(AppConfig.DANGER_RED); setPadding(dp(14), 0, dp(4), 0)
                setOnClickListener { customRows.removeAt(i); renderUI() }
            })
            card.addView(top)
            card.addView(createLuxuryInput("القيمة", true).apply { setText(r.value); watch { r.value = it }; layoutParams = lp(top = 8) })
            card.addView(CheckBox(this).apply {
                text = "حقل سري (يُخفى عند العرض)"; setTextColor(AppConfig.TEXT_MUTED); isChecked = r.secret
                setOnCheckedChangeListener { _: CompoundButton, c: Boolean -> r.secret = c }
            })
            parent.addView(card)
        }
        parent.addView(outlineButton("＋ إضافة حقل").apply { layoutParams = lp(h = dp(44), top = 12); setOnClickListener { customRows.add(CustomRow()); renderUI() } })
    }

    private fun buildMediaSection(parent: LinearLayout) {
        parent.addView(outlineButton("📎 إرفاق مستندات / صور").apply {
            layoutParams = lp(h = dp(48), top = 12)
            setOnClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"; putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }
                suppressLockOnce = true
                @Suppress("DEPRECATION") startActivityForResult(intent, REQ_ATTACH)
            }
        })
        val total = mediaItems.sumOf { it.optLong("size", 0L) }
        parent.addView(TextView(this).apply {
            text = "الحد: ${formatSize(MAX_FILE_BYTES)} للملف، ${formatSize(MAX_TOTAL_BYTES)} إجمالاً  •  المرفق الآن: ${formatSize(total)}"
            setTextColor(AppConfig.TEXT_MUTED); textSize = 11f; setPadding(0, dp(6), 0, dp(6))
        })
        for ((i, item) in mediaItems.withIndex()) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; background = luxuryBg(AppConfig.CARD_DARK, 8)
                setPadding(dp(12), dp(8), dp(12), dp(8)); gravity = Gravity.CENTER_VERTICAL; layoutParams = lp(bottom = 6)
            }
            row.addView(TextView(this).apply { text = if (item.optString("kind") == "image") "🖼" else "📎"; setPadding(0, 0, dp(10), 0) })
            row.addView(TextView(this).apply {
                text = "${item.optString("name")}  (${formatSize(item.optLong("size", 0L))})"
                setTextColor(AppConfig.TEXT_WHITE); textSize = 14f; layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
            })
            row.addView(TextView(this).apply { text = "✖"; setTextColor(AppConfig.DANGER_RED); setOnClickListener { mediaItems.removeAt(i); renderUI() } })
            parent.addView(row)
        }
    }

    private fun collectData(spec: VaultSpec): Pair<LinkedHashMap<String, String>, List<String>> {
        val data = LinkedHashMap<String, String>(); val secrets = mutableListOf<String>()
        if (spec.dynamic) {
            customRows.forEachIndexed { i, r ->
                if (r.value.isBlank()) return@forEachIndexed
                val base = r.name.trim().ifEmpty { "حقل ${i + 1}" }
                var name = base; var n = 2
                while (data.containsKey(name)) { name = "$base ($n)"; n++ }
                data[name] = r.value; if (r.secret) secrets.add(name)
            }
        } else {
            val values = valuesFor(spec.type)
            for (f in spec.fields) {
                val raw = values[f.label].orEmpty()
                if (raw.isBlank()) continue
                data[f.label] = if (f.secret) raw else raw.trim()
                if (f.secret) secrets.add(f.label)
            }
        }
        return Pair(data, secrets)
    }

    private fun onEncryptClicked(spec: VaultSpec) {
        val pass = draftPass
        if (pass.length < 8) { toast("كلمة المرور يجب ألا تقل عن 8 أحرف"); return }
        if (pass != draftPassConfirm) { toast("المفاتيح غير متطابقة"); return }
        val (data, secrets) = collectData(spec)
        if (data.isEmpty() && mediaItems.isEmpty()) { toast("الخزنة فارغة"); return }
        if (strengthOf(pass) < 50) {
            AlertDialog.Builder(this).setTitle("كلمة مرور ضعيفة")
                .setMessage("قوة التشفير تعتمد على قوة كلمة المرور. يُنصح باستخدام المولّد. هل تريد المتابعة؟")
                .setPositiveButton("متابعة") { _, _ -> encryptAndSave(spec, data, secrets, pass) }
                .setNegativeButton("تعديل", null).show()
        } else encryptAndSave(spec, data, secrets, pass)
    }

    private fun encryptAndSave(spec: VaultSpec, data: Map<String, String>, secrets: List<String>, pass: String) {
        toast("جاري التشفير...")
        val snapshot = ArrayList(mediaItems)
        Thread {
            try {
                val html = CryptoEngine.generateVault(spec.type, data, secrets, pass.toCharArray(), snapshot)
                runOnUiThread {
                    pendingSaveHtml = html
                    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE); type = "text/html"
                        putExtra(Intent.EXTRA_TITLE, "Keykeep_${spec.type}_${UUID.randomUUID().toString().take(4)}.html")
                    }
                    suppressLockOnce = true
                    @Suppress("DEPRECATION") startActivityForResult(intent, REQ_SAVE_VAULT)
                }
            } catch (e: Throwable) {
                runOnUiThread { toast("خطأ: ${e.message ?: "ذاكرة غير كافية"}", true) }
            }
        }.start()
    }

    private fun strengthOf(p: String): Int {
        if (p.isEmpty()) return 0
        var s = minOf(p.length, 20) * 3
        var classes = 0
        if (p.any { it.isLowerCase() }) classes++
        if (p.any { it.isUpperCase() }) classes++
        if (p.any { it.isDigit() }) classes++
        if (p.any { !it.isLetterOrDigit() }) classes++
        s += classes * 10
        if (p.toSet().size < p.length / 2) s -= 20
        if (p.length < 8) s = minOf(s, 30)
        val low = p.lowercase(Locale.ROOT)
        if (listOf("password", "123456", "qwerty", "admin", "111111", "000000", "letmein").any { low.contains(it) }) s = minOf(s, 15)
        return s.coerceIn(0, 100)
    }

    private fun generatePassword(len: Int, symbols: Boolean): String {
        val sets = mutableListOf("ABCDEFGHJKLMNPQRSTUVWXYZ", "abcdefghijkmnopqrstuvwxyz", "23456789")
        if (symbols) sets.add("!@#%^&*-_=+?")
        val all = sets.joinToString("")
        val out = mutableListOf<Char>()
        sets.forEach { out.add(it[rng.nextInt(it.length)]) }
        while (out.size < len) out.add(all[rng.nextInt(all.length)])
        out.shuffle(rng)
        return out.joinToString("")
    }

    private fun showGeneratorDialog(onUse: (String) -> Unit) {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(12), dp(20), dp(4)) }
        val preview = TextView(this).apply { textSize = 17f; typeface = Typeface.MONOSPACE; setTextColor(AppConfig.GOLD_LIGHT); setTextIsSelectable(true); setPadding(0, dp(8), 0, dp(8)) }
        val lenLabel = TextView(this).apply { setTextColor(AppConfig.TEXT_WHITE) }
        val seek = SeekBar(this).apply { max = 40; progress = 12 } // 8..48 -> default 20
        val chkSym = CheckBox(this).apply { text = "رموز خاصة"; isChecked = true; setTextColor(AppConfig.TEXT_WHITE) }
        var current = ""
        fun regen() {
            val len = seek.progress + 8
            lenLabel.text = "الطول: $len"; current = generatePassword(len, chkSym.isChecked); preview.text = current
        }
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) { regen() }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        chkSym.setOnCheckedChangeListener { _, _ -> regen() }
        layout.addView(preview); layout.addView(lenLabel); layout.addView(seek); layout.addView(chkSym)
        layout.addView(Button(this).apply { text = "🔄 توليد جديد"; isAllCaps = false; setOnClickListener { regen() } })
        regen()
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("🎲 مولّد كلمات المرور").setView(layout)
            .setPositiveButton("استخدام") { _, _ -> onUse(current) }
            .setNegativeButton("إلغاء", null).show()
    }

    // ---------------------------------------------------------- open
    private fun buildOpen(parent: LinearLayout) {
        parent.addView(createBackButton())
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            background = luxuryBg(AppConfig.CARD_DARK, 16, 1, AppConfig.BORDER); setPadding(dp(32), dp(48), dp(32), dp(48))
        }
        card.addView(TextView(this).apply { text = "📂"; textSize = 64f; gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(16)) })
        card.addView(TextView(this).apply { text = "اختر ملف الخزنة المشفرة (.html)"; setTextColor(AppConfig.TEXT_WHITE); textSize = 16f; gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(8)) })
        card.addView(TextView(this).apply { text = "يدعم الملفات القديمة (v4) والجديدة (v5)"; setTextColor(AppConfig.TEXT_MUTED); textSize = 12f; gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(24)) })
        card.addView(goldButton("تصفح الملفات", 48).apply {
            setOnClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "*/*" }
                suppressLockOnce = true
                @Suppress("DEPRECATION") startActivityForResult(intent, REQ_OPEN)
            }
        })
        parent.addView(card)
    }

    private fun extractString(html: String, varName: String): String? =
        Regex("""(?:var|const)\s+${Regex.escape(varName)}\s*=\s*["']([^"']*)["']""").find(html)?.groupValues?.get(1)

    private fun openVaultFile(uri: Uri) {
        val remaining = VaultStore.lockRemainingMs()
        if (remaining > 0) { toast("محاولات خاطئة كثيرة. انتظر ${remaining / 1000 + 1} ثانية", true); return }
        try {
            if (queryMeta(uri).second > MAX_OPEN_BYTES) { toast("الملف كبير جداً"); return }
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return
            if (bytes.size > MAX_OPEN_BYTES) { toast("الملف كبير جداً"); return }
            val html = String(bytes, Charsets.UTF_8)
            val salt = extractString(html, "SALT"); val iv = extractString(html, "IV")
            val cipher = extractString(html, "CIPHER"); val vaultId = extractString(html, "VAULT_ID")
            val version = extractString(html, "VERSION") ?: "4"
            val hmac = extractString(html, "HMAC") ?: ""
            val iter = (extractString(html, "ITER")?.toIntOrNull() ?: CryptoEngine.ITER_V4).coerceIn(100_000, 5_000_000)
            if (salt == null || iv == null || cipher == null || vaultId == null || (version != "5" && hmac.isEmpty())) {
                toast("ملف غير صالح"); return
            }
            promptPassword { pass ->
                toast("جاري فك التشفير...")
                Thread {
                    val dec = CryptoEngine.decryptVault(salt, iv, cipher, hmac, vaultId, version, iter, pass.toCharArray())
                    runOnUiThread {
                        if (dec != null) {
                            VaultStore.resetFailures(); decryptedPayloadJson = dec; currentScreen = "view"; renderUI()
                        } else {
                            VaultStore.recordFailure(); toast("مفتاح خاطئ أو ملف تالف", true)
                        }
                    }
                }.start()
            }
        } catch (e: Throwable) { toast("خطأ في القراءة") }
    }

    private fun promptPassword(onPass: (String) -> Unit) {
        val (row, et) = secretInput("أدخل المفتاح", "") {}
        row.setPadding(dp(20), dp(8), dp(20), 0)
        et.setTextColor(Color.BLACK); et.setHintTextColor(Color.GRAY)
        et.background = luxuryBg(Color.WHITE, 8, 1, Color.LTGRAY)
        AlertDialog.Builder(this).setTitle("🔒 فتح الخزنة").setView(row)
            .setPositiveButton("فتح") { _, _ -> onPass(et.text.toString()) }
            .setNegativeButton("إلغاء", null).show()
    }

    // ---------------------------------------------------------- view
    private fun mediaMime(item: JSONObject): String {
        val d = item.optString("data")
        return if (d.startsWith("data:")) d.substringAfter("data:").substringBefore(";").ifEmpty { "application/octet-stream" }
        else item.optString("mime", "application/octet-stream")
    }

    private fun mediaB64(item: JSONObject): String {
        val d = item.optString("data"); return if (d.startsWith("data:")) d.substringAfter(",") else d
    }

    private fun decodeSampled(bytes: ByteArray, maxDim: Int): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        var s = 1
        while (o.outWidth / s > maxDim * 2 || o.outHeight / s > maxDim * 2) s *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = s })
    }

    private fun buildView(parent: LinearLayout) {
        parent.addView(createBackButton())
        val jsonStr = decryptedPayloadJson ?: return
        val json = try { JSONObject(jsonStr) } catch (e: Exception) { toast("بيانات تالفة"); goHome(); return }
        val fields = json.optJSONObject("data")
        val secretArr = json.optJSONArray("secret")
        val secretSet = HashSet<String>().also { s -> if (secretArr != null) for (i in 0 until secretArr.length()) s.add(secretArr.optString(i)) }
        val legacy = Regex("password|pass|cvv|pin|seed|secret|كلمة المرور|رقم البطاقة|العبارة", RegexOption.IGNORE_CASE)

        parent.addView(TextView(this).apply {
            text = AppConfig.spec(json.optString("type")).title + (json.optString("created").let { if (it.isEmpty()) "" else "  •  $it" })
            setTextColor(AppConfig.TEXT_MUTED); textSize = 12f; gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(12))
        })

        if (fields != null && fields.length() > 0) {
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; background = luxuryBg(AppConfig.CARD_DARK, 12, 1, AppConfig.GOLD_PRIMARY)
                setPadding(dp(16), dp(16), dp(16), dp(16)); layoutParams = lp(bottom = 16)
            }
            for (key in fields.keys()) {
                val valStr = fields.optString(key); if (valStr.isEmpty()) continue
                val hide = if (secretArr != null) secretSet.contains(key) else legacy.containsMatchIn(key)
                val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(8), 0, dp(8)) }
                row.addView(TextView(this).apply { text = key; setTextColor(AppConfig.TEXT_MUTED); textSize = 12f; typeface = Typeface.DEFAULT_BOLD })
                val valRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(4), 0, 0) }
                val valueView = TextView(this).apply {
                    text = if (hide) MASK else valStr; setTextColor(AppConfig.TEXT_WHITE); textSize = 16f
                    typeface = Typeface.DEFAULT_BOLD; layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
                }
                valRow.addView(valueView)
                if (hide) {
                    var shown = false
                    valRow.addView(TextView(this).apply {
                        text = "👁"; textSize = 18f; setPadding(dp(8), 0, dp(8), 0)
                        setOnClickListener { shown = !shown; valueView.text = if (shown) valStr else MASK }
                    })
                }
                valRow.addView(TextView(this).apply { text = "📋"; textSize = 18f; setPadding(dp(8), 0, 0, 0); setOnClickListener { copyToClipboard(valStr) } })
                row.addView(valRow); box.addView(row)
            }
            parent.addView(box)
        }

        val media = json.optJSONArray("media")
        if (media != null) for (i in 0 until media.length()) {
            val item = media.getJSONObject(i)
            val name = item.optString("name"); val kind = item.optString("kind"); val mime = mediaMime(item); val b64 = mediaB64(item)
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; background = luxuryBg(AppConfig.CARD_DARK, 12, 1, AppConfig.BORDER)
                setPadding(dp(16), dp(16), dp(16), dp(16)); layoutParams = lp(bottom = 12)
            }
            if (kind == "image") {
                card.addView(ImageView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(MATCH, dp(220)); scaleType = ImageView.ScaleType.FIT_CENTER
                    try { setImageBitmap(decodeSampled(Base64.decode(b64, Base64.DEFAULT), 1024)) } catch (e: Throwable) {}
                })
            }
            card.addView(TextView(this).apply { text = name; setTextColor(AppConfig.TEXT_WHITE); textSize = 12f; setPadding(0, dp(8), 0, dp(8)); gravity = Gravity.CENTER })
            card.addView(goldButton("حفظ الملف", 48).apply {
                setOnClickListener {
                    pendingSaveFileData = b64
                    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = mime; putExtra(Intent.EXTRA_TITLE, name) }
                    suppressLockOnce = true
                    @Suppress("DEPRECATION") startActivityForResult(intent, REQ_SAVE_FILE)
                }
            })
            parent.addView(card)
        }

        parent.addView(outlineButton("🔒 قفل وإغلاق الخزنة", 48, AppConfig.DANGER_RED).apply { layoutParams = lp(h = dp(48), top = 8); setOnClickListener { goHome() } })
    }

    // ---------------------------------------------------------- settings
    private fun showSettingsDialog() {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(20), dp(20), dp(20)) }
        layout.addView(CheckBox(this).apply {
            text = "منع لقطات الشاشة (FLAG_SECURE)"; setTextColor(AppConfig.TEXT_WHITE); isChecked = VaultStore.isScreenSecure()
            setOnCheckedChangeListener { _, c -> VaultStore.setScreenSecure(c); applyScreenSecurity() }
        })
        layout.addView(CheckBox(this).apply {
            text = "قفل التطبيق ببصمة / رمز الجهاز"; setTextColor(AppConfig.TEXT_WHITE); isChecked = VaultStore.isAppLock()
            setOnCheckedChangeListener { btn, c ->
                if (c && !deviceSecure()) { btn.isChecked = false; toast("فعّل قفل الشاشة في إعدادات الجهاز أولاً", true) }
                else VaultStore.setAppLock(c)
            }
        })
        layout.addView(TextView(this).apply { text = "القفل التلقائي عند مغادرة التطبيق"; setTextColor(AppConfig.TEXT_MUTED); textSize = 12f; setPadding(0, dp(14), 0, dp(4)) })
        layout.addView(Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, AppConfig.LOCK_LABELS)
            setSelection(AppConfig.LOCK_VALUES.indexOf(VaultStore.autoLockSeconds()).coerceAtLeast(0), false)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) { VaultStore.setAutoLockSeconds(AppConfig.LOCK_VALUES[p2]) }
                override fun onNothingSelected(p0: AdapterView<*>?) {}
            }
        })
        layout.addView(Button(this).apply {
            text = "إعادة تعيين الإحصائيات"; isAllCaps = false
            setOnClickListener { VaultStore.resetStats(); toast("تمت إعادة التعيين"); if (currentScreen == "home") renderUI() }
        })
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("⚙️ إعدادات الخصوصية").setView(layout).setPositiveButton("حسناً", null).show()
    }

    // ---------------------------------------------------------- clipboard
    private fun copyToClipboard(text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Keykeep", text)
        if (Build.VERSION.SDK_INT >= 33) {
            clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
        }
        cm.setPrimaryClip(clip)
        toast("تم النسخ — سيُمسح بعد 30 ثانية")
        handler.removeCallbacks(clearClipRunnable); handler.postDelayed(clearClipRunnable, CLIP_CLEAR_MS)
    }

    private fun clearClipboard() {
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            if (Build.VERSION.SDK_INT >= 28) cm.clearPrimaryClip() else cm.setPrimaryClip(ClipData.newPlainText("", ""))
        } catch (e: Exception) {}
    }

    // ---------------------------------------------------------- files
    private fun queryMeta(uri: Uri): Pair<String, Long> {
        var name: String? = null; var size = -1L
        if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME); if (ni != -1) name = c.getString(ni)
                    val si = c.getColumnIndex(OpenableColumns.SIZE); if (si != -1 && !c.isNull(si)) size = c.getLong(si)
                }
            }
        }
        return Pair(name ?: "file_${System.currentTimeMillis()}", size)
    }

    private fun formatSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB"); var s = size.toDouble(); var i = 0
        while (s >= 1024 && i < units.size - 1) { s /= 1024; i++ }
        return String.format(Locale.US, "%.1f %s", s, units[i])
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) {
            if (requestCode == REQ_SAVE_VAULT) pendingSaveHtml = null
            return
        }
        when (requestCode) {
            REQ_AUTH -> { appUnlocked = true; currentScreen = "home"; renderUI() }

            REQ_ATTACH -> {
                if (data == null) return
                val uris = mutableListOf<Uri>()
                val clip = data.clipData
                if (clip != null) { for (i in 0 until clip.itemCount) uris.add(clip.getItemAt(i).uri) }
                else data.data?.let { uris.add(it) }
                val baseBytes = mediaItems.sumOf { it.optLong("size", 0L) }
                Thread {
                    var total = baseBytes; var skipped = 0
                    val added = mutableListOf<JSONObject>()
                    for (uri in uris) {
                        try {
                            val (name, declared) = queryMeta(uri)
                            if (declared > MAX_FILE_BYTES || (declared > 0 && total + declared > MAX_TOTAL_BYTES)) { skipped++; continue }
                            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: continue
                            if (bytes.size > MAX_FILE_BYTES || total + bytes.size > MAX_TOTAL_BYTES) { skipped++; continue }
                            val mime = contentResolver.getType(uri) ?: "application/octet-stream"
                            total += bytes.size
                            added.add(JSONObject().apply {
                                put("kind", if (mime.startsWith("image/")) "image" else "file")
                                put("mime", mime); put("name", name); put("size", bytes.size.toLong())
                                put("data", Base64.encodeToString(bytes, Base64.NO_WRAP))
                            })
                        } catch (e: Throwable) { skipped++ }
                    }
                    runOnUiThread {
                        mediaItems.addAll(added)
                        if (skipped > 0) toast("تم تجاهل $skipped ملف (حجم كبير أو خطأ قراءة)", true)
                        renderUI()
                    }
                }.start()
            }

            REQ_SAVE_VAULT -> data?.data?.let { uri ->
                val html = pendingSaveHtml ?: return@let
                try {
                    contentResolver.openOutputStream(uri, "wt")?.use { it.write(html.toByteArray(Charsets.UTF_8)) }
                    VaultStore.addVaultStats(mediaItems.size, html.length.toLong())
                    toast("👑 تم تشفير وحفظ الخزنة", true)
                    wipeDrafts(); currentScreen = "home"; renderUI()
                } catch (e: Exception) { toast("فشل الحفظ") }
            }

            REQ_OPEN -> data?.data?.let { openVaultFile(it) }

            REQ_SAVE_FILE -> data?.data?.let { uri ->
                val d = pendingSaveFileData ?: return@let
                try {
                    contentResolver.openOutputStream(uri, "wt")?.use { it.write(Base64.decode(d, Base64.DEFAULT)) }
                    toast("✔ تم حفظ الملف")
                } catch (e: Exception) { toast("فشل الحفظ") }
                finally { pendingSaveFileData = null }
            }
        }
    }
}
