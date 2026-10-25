package com.keep.aio

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.ScaleAnimation
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
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
import java.util.Base64
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

// ============================================================
// APP CONFIG
// ============================================================
object AppConfig {
    val BG_OBSIDIAN = Color.parseColor("#0B0C10")
    val CARD_DARK = Color.parseColor("#1F2833")
    val GOLD_PRIMARY = Color.parseColor("#D4AF37")
    val GOLD_LIGHT = Color.parseColor("#F3E5AB")
    val GOLD_DARK = Color.parseColor("#B8860B")
    val TEXT_WHITE = Color.parseColor("#FFFFFF")
    val TEXT_MUTED = Color.parseColor("#8A8F9C")
    val DANGER_RED = Color.parseColor("#FF4444")
}

// ============================================================
// CRYPTO ENGINE
// ============================================================
object CryptoEngine {
    private const val ITERATIONS = 310000
    private const val VERSION = "v2.0"

    fun generateVault(message: String, password: String, media: List<JSONObject>, vaultType: String, fields: Map<String, String>): String {
        val vaultId = UUID.randomUUID().toString().replace("-", "").substring(0, 8)
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }

        val json = JSONObject().apply {
            put("version", VERSION)
            put("type", vaultType)
            put("message", message)
            put("fields", JSONObject(fields as Map<*, *>))
            if (media.isNotEmpty()) {
                val arr = JSONArray()
                media.forEach { arr.put(it) }
                put("media", arr)
            }
        }.toString()

        val compressed = gzip(json.toByteArray(Charsets.UTF_8))
        
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, 256)
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(compressed)

        val hmacKeyBytes = MessageDigest.getInstance("SHA-256").digest("$password|$vaultId|hmac".toByteArray(Charsets.UTF_8))
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(hmacKeyBytes, "HmacSHA256"))
        val hmac = mac.doFinal(ciphertext)

        val b64 = Base64.getEncoder()
        return getHtmlTemplate(
            b64.encodeToString(salt), b64.encodeToString(iv), b64.encodeToString(ciphertext), b64.encodeToString(hmac), vaultId
        )
    }

    fun decryptVault(saltB64: String, ivB64: String, cipherB64: String, hmacB64: String, vaultId: String, password: String): String? {
        try {
            val b64 = Base64.getDecoder()
            val salt = b64.decode(saltB64)
            val iv = b64.decode(ivB64)
            val ciphertext = b64.decode(cipherB64)
            val hmac = b64.decode(hmacB64)

            if (ciphertext.isEmpty() || salt.size < 16 || iv.size != 12) return null

            val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, 256)
            val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded

            val hmacKeyBytes = MessageDigest.getInstance("SHA-256").digest("$password|$vaultId|hmac".toByteArray(Charsets.UTF_8))
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(hmacKeyBytes, "HmacSHA256"))
            if (!mac.doFinal(ciphertext).contentEquals(hmac)) return null

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            val compressed = cipher.doFinal(ciphertext)

            val gzipIn = GZIPInputStream(ByteArrayInputStream(compressed))
            return gzipIn.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: Exception) { return null }
    }

    private fun gzip(data: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(data) }
        return bos.toByteArray()
    }

    private fun getHtmlTemplate(salt: String, iv: String, cipher: String, hmac: String, vaultId: String): String {
        return """<!DOCTYPE html>
<html lang="ar" dir="rtl">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>KEYKEEP ROYAL</title>
<style>
  body { background: #0B0C10; color: #FFF; font-family: 'Georgia', serif; display: flex; align-items: center; justify-content: center; min-height: 100vh; margin: 0; padding: 20px; }
  .box { background: #1F2833; border: 1px solid #D4AF37; border-radius: 16px; padding: 32px; width: 100%; max-width: 400px; box-shadow: 0 10px 40px rgba(212,175,55,0.2); }
  h1 { color: #D4AF37; text-align: center; margin-top: 0; font-size: 28px; letter-spacing: 2px; }
  input { width: 100%; padding: 14px; margin-bottom: 16px; border-radius: 8px; border: 1px solid #333A45; background: #151A20; color: #fff; box-sizing: border-box; outline: none; font-family: sans-serif; }
  input:focus { border-color: #D4AF37; }
  button.main-btn { width: 100%; padding: 14px; border-radius: 8px; border: none; background: linear-gradient(90deg, #F3E5AB, #D4AF37, #B8860B); color: #000; font-weight: bold; cursor: pointer; font-family: sans-serif; margin-bottom: 10px; }
  .content { display: none; margin-top: 20px; text-align: left; white-space: pre-wrap; line-height: 1.6; font-family: sans-serif; }
  .media { width: 100%; border-radius: 8px; margin-top: 10px; border: 1px solid #333A45; }
  .err { color: #ff7a7a; font-size: 13px; text-align: center; margin-top: 10px; line-height: 1.5; }
  .file-card { background: #151A20; border: 1px solid #333A45; border-radius: 12px; padding: 16px; margin-top: 16px; text-align: center; }
  .file-name { color: #FFF; font-size: 14px; word-break: break-all; margin-bottom: 12px; }
  .file-btn { width: 100%; padding: 10px; border-radius: 8px; border: none; background: linear-gradient(90deg, #F3E5AB, #D4AF37, #B8860B); color: #000; font-weight: bold; cursor: pointer; }
  .fields-box { background: #151A20; border: 1px solid #333A45; border-radius: 12px; padding: 16px; margin-bottom: 16px; }
  .field-row { display: flex; justify-content: space-between; margin-bottom: 8px; font-size: 14px; }
  .field-key { color: #D4AF37; }
  .field-val { color: #FFF; font-weight: bold; }
</style>
</head>
<body>
<div class="box">
  <h1>👑 ROYAL VAULT</h1>
  <div id="loginBox">
    <input type="password" id="pass" placeholder="أدخل مفتاح الخزنة" autocomplete="off">
    <button class="main-btn" onclick="unlock()">فتح الخزنة</button>
    <div class="err" id="err"></div>
  </div>
  <div class="content" id="resultBox"></div>
</div>
<script>
const SALT = "$salt"; const IV = "$iv"; const CIPHER = "$cipher"; const HMAC = "$hmac"; const VAULT_ID = "$vaultId"; const ITERATIONS = 310000;
function fromB64(b64) { return Uint8Array.from(atob(b64), c => c.charCodeAt(0)); }
async function deriveKey(password, saltBytes, usage) {
  const enc = new TextEncoder();
  const baseKey = await crypto.subtle.importKey('raw', enc.encode(password), { name:'PBKDF2' }, false, ['deriveKey']);
  return crypto.subtle.deriveKey({ name:'PBKDF2', salt: saltBytes, iterations: ITERATIONS, hash:'SHA-256' }, baseKey, { name:'AES-GCM', length:256 }, false, [usage]);
}
async function sha256(bytes) { return new Uint8Array(await crypto.subtle.digest('SHA-256', bytes)); }
function downloadFile(dataUrl, filename) {
    try {
        var arr = dataUrl.split(','), mime = arr[0].match(/:(.*?);/)[1], bstr = atob(arr[1]), n = bstr.length, u8arr = new Uint8Array(n);
        while(n--){ u8arr[n] = bstr.charCodeAt(n); }
        var blob = new Blob([u8arr], {type:mime}), url = window.URL.createObjectURL(blob);
        var a = document.createElement('a'); a.style.display = 'none'; a.href = url; a.download = filename;
        document.body.appendChild(a); a.click(); window.URL.revokeObjectURL(url); document.body.removeChild(a);
    } catch(e) { alert("فشل التحميل"); }
}
async function unlock() {
  const pass = document.getElementById('pass').value; if(!pass) return;
  if (!window.isSecureContext) { document.getElementById('err').innerHTML = "⚠️ يرجى فتح الملف باستخدام تطبيق KEYKEEP لفك التشفير بأمان."; return; }
  document.getElementById('err').textContent = 'جاري فك التشفير...';
  try {
    const key = await deriveKey(pass, fromB64(SALT), 'decrypt');
    const plainBuf = await crypto.subtle.decrypt({ name:'AES-GCM', iv: fromB64(IV) }, key, fromB64(CIPHER));
    const hmacKeyBytes = await sha256(new TextEncoder().encode(pass + '|' + VAULT_ID + '|hmac'));
    const hmacKey = await crypto.subtle.importKey('raw', hmacKeyBytes, { name:'HMAC', hash:'SHA-256' }, false, ['verify']);
    if (!(await crypto.subtle.verify('HMAC', hmacKey, fromB64(HMAC), fromB64(CIPHER)))) throw new Error('HMAC Fail');
    const ds = new DecompressionStream('gzip'); const w = ds.writable.getWriter(); w.write(new Uint8Array(plainBuf)); w.close();
    const decompressedBuf = await new Response(ds.readable).arrayBuffer();
    const data = JSON.parse(new TextDecoder().decode(decompressedBuf));
    document.getElementById('loginBox').style.display = 'none';
    document.getElementById('resultBox').style.display = 'block';
    
    if (data.type === 'account' && data.fields) {
      const fbox = document.createElement('div'); fbox.className = 'fields-box';
      Object.keys(data.fields).forEach(k => {
        if(data.fields[k]) {
          const row = document.createElement('div'); row.className = 'field-row';
          row.innerHTML = '<span class="field-key">'+k+'</span><span class="field-val">'+data.fields[k]+'</span>';
          fbox.appendChild(row);
        }
      });
      document.getElementById('resultBox').appendChild(fbox);
    }
    
    if (data.message) {
      const p = document.createElement('p'); p.textContent = data.message; document.getElementById('resultBox').appendChild(p);
    }
    
    if (data.media) {
      data.media.forEach(item => {
        if (item.kind === 'image') {
          const img = document.createElement('img'); img.src = item.data; img.className = 'media';
          document.getElementById('resultBox').appendChild(img);
          const btn = document.createElement('button'); btn.className = 'main-btn'; btn.textContent = 'حفظ الصورة';
          btn.onclick = () => downloadFile(item.data, item.name);
          document.getElementById('resultBox').appendChild(btn);
        } else {
          const card = document.createElement('div'); card.className = 'file-card';
          const name = document.createElement('div'); name.className = 'file-name'; name.textContent = item.name;
          const btn = document.createElement('button'); btn.className = 'file-btn'; btn.textContent = '⬇ تحميل الملف';
          btn.onclick = () => downloadFile(item.data, item.name);
          card.appendChild(name); card.appendChild(btn);
          document.getElementById('resultBox').appendChild(card);
        }
      });
    }
  } catch (e) { document.getElementById('err').textContent = 'مفتاح خاطئ أو ملف تالف'; }
}
</script>
</body>
</html>"""
    }
}

// ============================================================
// STORAGE & STATS
// ============================================================
object VaultStore {
    private const val PREFS = "keykeep_royal_v3"
    private lateinit var prefs: SharedPreferences

    var vaultCount = 0
    var attachmentCount = 0
    var usedSpace = 0L
    var lastActivity = "لا يوجد"

    fun init(ctx: Context) {
        prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        loadStats()
    }

    fun loadStats() {
        vaultCount = prefs.getInt("v_count", 0)
        attachmentCount = prefs.getInt("a_count", 0)
        usedSpace = prefs.getLong("u_space", 0L)
        lastActivity = prefs.getString("l_act", "لا يوجد") ?: "لا يوجد"
    }

    fun addVaultStats(attachments: Int, size: Long) {
        vaultCount++
        attachmentCount += attachments
        usedSpace += size
        lastActivity = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date())
        prefs.edit()
            .putInt("v_count", vaultCount)
            .putInt("a_count", attachmentCount)
            .putLong("u_space", usedSpace)
            .putString("l_act", lastActivity)
            .apply()
    }

    fun isScreenSecure(): Boolean = prefs.getBoolean("secure_screen", false)
    fun setScreenSecure(v: Boolean) { prefs.edit().putBoolean("secure_screen", v).apply() }
    
    fun isAutoLock(): Boolean = prefs.getBoolean("auto_lock", true)
    fun setAutoLock(v: Boolean) { prefs.edit().putBoolean("auto_lock", v).apply() }
}

// ============================================================
// MAIN ACTIVITY
// ============================================================
class MainActivity : Activity() {

    private var currentScreen = "splash"
    private var pendingSaveHtml = ""
    private val mediaItems = mutableListOf<JSONObject>()
    
    private var decryptedPayloadJson: String? = null
    private var pendingSaveFileData: String? = null
    
    private var selectedVaultType = "note"
    private val customFields = mutableMapOf<String, String>()
    
    // Draft states
    private var draftMessage = ""
    private var draftPass = ""
    private var draftPassConfirm = ""

    private var currentSearchQuery = ""
    private lateinit var rootContainer: FrameLayout
    private var lastPausedTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        VaultStore.init(this)
        applyScreenSecurity()
        
        rootContainer = FrameLayout(this)
        setContentView(rootContainer)
        
        showSplashScreen()
    }

    override fun onResume() {
        super.onResume()
        if (currentScreen != "splash" && VaultStore.isAutoLock() && lastPausedTime > 0) {
            if (System.currentTimeMillis() - lastPausedTime > 30000) {
                currentScreen = "home"
                decryptedPayloadJson = null
                renderUI()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        lastPausedTime = System.currentTimeMillis()
    }

    private fun applyScreenSecurity() {
        if (VaultStore.isScreenSecure()) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun luxuryBg(color: Int, radius: Int, strokeWidth: Int = 1, strokeColor: Int = AppConfig.GOLD_PRIMARY): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            if (strokeWidth > 0) setStroke(dp(strokeWidth), strokeColor)
        }
    }

    private fun applyGoldGradient(view: View) {
        val gradient = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(AppConfig.GOLD_LIGHT, AppConfig.GOLD_PRIMARY, AppConfig.GOLD_DARK)
        ).apply {
            cornerRadius = dp(12).toFloat()
        }
        view.background = gradient
        view.setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).start()
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
            }
            false
        }
    }

    // ---------------------------------------------------------
    // SPLASH SCREEN
    // ---------------------------------------------------------
    private fun showSplashScreen() {
        val root = FrameLayout(this).apply { setBackgroundColor(AppConfig.BG_OBSIDIAN) }
        
        val center = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        val crown = TextView(this).apply {
            text = "👑"; textSize = 72f; gravity = Gravity.CENTER
        }
        
        val title = TextView(this).apply {
            text = "KEYKEEP"; textSize = 40f; setTextColor(AppConfig.GOLD_PRIMARY)
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD); letterSpacing = 0.3f; gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, 0)
        }

        center.addView(crown)
        center.addView(title)
        root.addView(center)
        setContentView(root)

        val anim = ScaleAnimation(0.5f, 1f, 0.5f, 1f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f)
        anim.duration = 1000
        anim.fillAfter = true
        
        val fade = AlphaAnimation(0f, 1f)
        fade.duration = 1000
        fade.fillAfter = true

        crown.startAnimation(anim)
        title.startAnimation(fade)

        Handler(Looper.getMainLooper()).postDelayed({
            currentScreen = "home"
            renderUI()
        }, 2000)
    }

    // ---------------------------------------------------------
    // MAIN RENDERER
    // ---------------------------------------------------------
    private fun renderUI() {
        applyScreenSecurity()
        val root = FrameLayout(this).apply { setBackgroundColor(AppConfig.BG_OBSIDIAN) }
        root.addView(RoyalBackgroundView(this))

        val scrollView = ScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(60), dp(24), dp(24))
        }
        scrollView.addView(container)
        root.addView(scrollView)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(20))
        }
        header.addView(TextView(this).apply {
            text = "👑 KEYKEEP"; textSize = 24f; setTextColor(AppConfig.GOLD_PRIMARY)
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD); letterSpacing = 0.1f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        header.addView(TextView(this).apply {
            text = "⚙️"; textSize = 24f
            setOnClickListener { showSettingsDialog() }
        })
        container.addView(header)

        when (currentScreen) {
            "home" -> buildDashboard(container)
            "create" -> buildCreate(container)
            "open" -> buildOpen(container)
            "view" -> buildView(container)
        }

        rootContainer.removeAllViews()
        rootContainer.addView(root)
    }

    // ---------------------------------------------------------
    // ROYAL DASHBOARD
    // ---------------------------------------------------------
    private fun buildDashboard(parent: LinearLayout) {
        val statsCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = luxuryBg(AppConfig.CARD_DARK, 16, 1, Color.parseColor("#333A45"))
            setPadding(dp(20), dp(20), dp(20), dp(20))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.bottomMargin = dp(24) }
        }

        statsCard.addView(TextView(this).apply {
            text = "لوحة التحكم الملكية"
            setTextColor(AppConfig.GOLD_PRIMARY); textSize = 16f; typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(16))
        })

        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        row1.addView(createStatBox("الخزائن", VaultStore.vaultCount.toString()))
        row1.addView(createStatBox("المرفقات", VaultStore.attachmentCount.toString()))
        statsCard.addView(row1)

        statsCard.addView(TextView(this).apply {
            text = "المساحة المستخدمة: ${formatSize(VaultStore.usedSpace)}"
            setTextColor(AppConfig.TEXT_MUTED); textSize = 12f; gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(4))
        })
        statsCard.addView(TextView(this).apply {
            text = "آخر نشاط: ${VaultStore.lastActivity}"
            setTextColor(AppConfig.TEXT_MUTED); textSize = 12f; gravity = Gravity.CENTER
        })

        parent.addView(statsCard)

        val btnCreate = Button(this).apply {
            text = "إنشاء خزنة جديدة"
            setTextColor(Color.BLACK); textSize = 16f; typeface = Typeface.SERIF
            applyGoldGradient(this)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)).also { it.bottomMargin = dp(16) }
            setOnClickListener { mediaItems.clear(); draftMessage = ""; draftPass = ""; draftPassConfirm = ""; customFields.clear(); currentScreen = "create"; renderUI() }
        }
        parent.addView(btnCreate)

        val btnOpen = Button(this).apply {
            text = "فتح خزنة موجودة"
            setTextColor(AppConfig.GOLD_PRIMARY); textSize = 16f; typeface = Typeface.SERIF
            background = luxuryBg(Color.TRANSPARENT, 12, 1, AppConfig.GOLD_PRIMARY)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56))
            setOnClickListener { currentScreen = "open"; renderUI() }
        }
        parent.addView(btnOpen)
    }

    private fun createStatBox(label: String, value: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(this@MainActivity).apply { text = value; textSize = 28f; setTextColor(AppConfig.GOLD_LIGHT); typeface = Typeface.DEFAULT_BOLD })
            addView(TextView(this@MainActivity).apply { text = label; textSize = 12f; setTextColor(AppConfig.TEXT_MUTED) })
        }
    }

    // ---------------------------------------------------------
    // CREATE VAULT
    // ---------------------------------------------------------
    private fun buildCreate(parent: LinearLayout) {
        parent.addView(createBackButton())

        parent.addView(createLabel("نوع البيانات"))
        val typeSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, arrayOf("ملاحظة سرية", "بيانات حساب", "مستند / صور"))
            background = luxuryBg(AppConfig.CARD_DARK, 8, 1, Color.parseColor("#333A45"))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                    val newType = when(p2) { 0 -> "note"; 1 -> "account"; else -> "document" }
                    if (selectedVaultType != newType) {
                        selectedVaultType = newType
                        rootContainer.post { renderUI() }
                    }
                }
                override fun onNothingSelected(p0: AdapterView<*>?) {}
            }
        }
        parent.addView(typeSpinner)

        if (selectedVaultType == "account") {
            parent.addView(createLabel("بيانات الحساب"))
            val accFields = listOf("اسم المستخدم", "كلمة المرور", "الموقع / التطبيق")
            for (f in accFields) {
                val input = createLuxuryInput(f, false)
                input.setText(customFields[f] ?: "")
                input.addTextChangedListener(object : TextWatcher {
                    override fun afterTextChanged(s: Editable?) { customFields[f] = s.toString() }
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                })
                parent.addView(input)
            }
        } else {
            parent.addView(createLabel(if (selectedVaultType == "note") "الرسالة السرية" else "الوصف"))
            val msgInput = createLuxuryInput("اكتب هنا...", true)
            msgInput.setText(draftMessage)
            msgInput.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) { draftMessage = s.toString() }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })
            parent.addView(msgInput)
        }

        // Password
        parent.addView(createLabel("مفتاح التشفير"))
        val passRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val passInput = createLuxuryInput("كلمة مرور قوية", false)
        passInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        passInput.setText(draftPass)
        passInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { draftPass = s.toString() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        passInput.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        passRow.addView(passInput)
        passRow.addView(TextView(this).apply {
            text = "👁"; textSize = 20f; setPadding(dp(12), 0, 0, 0)
            setOnClickListener {
                if (passInput.inputType == InputType.TYPE_TEXT_VARIATION_PASSWORD) {
                    passInput.inputType = InputType.TYPE_CLASS_TEXT
                } else {
                    passInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                }
            }
        })
        parent.addView(passRow)

        // Strength meter
        val strengthBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; progress = 0
            progressDrawable.setColorFilter(AppConfig.GOLD_PRIMARY, android.graphics.PorterDuff.Mode.SRC_IN)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4)).also { it.topMargin = dp(4) }
        }
        parent.addView(strengthBar)

        val genBtn = Button(this).apply {
            text = "اقتراح كلمة مرور عشوائية"
            setTextColor(AppConfig.TEXT_MUTED); textSize = 12f; background = null
            setOnClickListener {
                val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*"
                val random = SecureRandom()
                val generated = (1..16).map { chars[random.nextInt(chars.length)] }.joinToString("")
                passInput.setText(generated)
                Toast.makeText(this@MainActivity, "تم توليد كلمة مرور قوية", Toast.LENGTH_SHORT).show()
            }
        }
        parent.addView(genBtn)

        passInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val score = calculateStrength(s.toString())
                strengthBar.progress = score
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        parent.addView(createLabel("تأكيد المفتاح"))
        val passConfirmInput = createLuxuryInput("أعد كتابة كلمة المرور", false)
        passConfirmInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        passConfirmInput.setText(draftPassConfirm)
        passConfirmInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { draftPassConfirm = s.toString() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        parent.addView(passConfirmInput)

        // Attachments
        if (selectedVaultType == "document") {
            val btnAddMedia = Button(this).apply {
                text = "إرفاق مستندات / صور"
                setTextColor(AppConfig.GOLD_PRIMARY); background = luxuryBg(Color.TRANSPARENT, 8, 1, AppConfig.GOLD_PRIMARY)
                setOnClickListener {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"
                        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                    }
                    startActivityForResult(intent, 2001)
                }
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).also { it.topMargin = dp(12) }
            }
            parent.addView(btnAddMedia)

            val attContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(12), 0, 0) }
            for ((i, item) in mediaItems.withIndex()) {
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL; background = luxuryBg(AppConfig.CARD_DARK, 8)
                    setPadding(dp(12), dp(8), dp(12), dp(8)); gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.bottomMargin = dp(6) }
                }
                row.addView(TextView(this).apply { text = if (item.optString("kind") == "image") "🖼" else "📎"; setPadding(0, 0, dp(10), 0) })
                row.addView(TextView(this).apply { text = item.optString("name"); setTextColor(AppConfig.TEXT_WHITE); textSize = 14f; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) })
                row.addView(TextView(this).apply { text = "✖"; setTextColor(AppConfig.DANGER_RED); setOnClickListener { mediaItems.removeAt(i); renderUI() } })
                attContainer.addView(row)
            }
            parent.addView(attContainer)
        }

        // Generate
        val btnGenerate = Button(this).apply {
            text = "🔒 تشفير وحفظ الملف"
            setTextColor(Color.BLACK); textSize = 16f; typeface = Typeface.SERIF
            applyGoldGradient(this)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)).also { it.topMargin = dp(24) }
            setOnClickListener {
                val pass = passInput.text.toString()
                val confirm = passConfirmInput.text.toString()
                if (pass.isEmpty() || pass != confirm) {
                    Toast.makeText(this@MainActivity, "المفاتيح غير متطابقة", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                try {
                    val msg = if (selectedVaultType != "account") draftMessage else ""
                    pendingSaveHtml = CryptoEngine.generateVault(msg, pass, mediaItems, selectedVaultType, customFields)
                    
                    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE); type = "text/html"
                        putExtra(Intent.EXTRA_TITLE, "Keykeep_${UUID.randomUUID().toString().take(4)}.html")
                    }
                    startActivityForResult(intent, 1001)
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "خطأ: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
        parent.addView(btnGenerate)
    }

    // ---------------------------------------------------------
    // OPEN VAULT
    // ---------------------------------------------------------
    private fun buildOpen(parent: LinearLayout) {
        parent.addView(createBackButton())

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            background = luxuryBg(AppConfig.CARD_DARK, 16, 1, Color.parseColor("#333A45"))
            setPadding(dp(32), dp(48), dp(32), dp(48))
        }

        card.addView(TextView(this).apply { text = "📂"; textSize = 64f; gravity = Gravity.CENTER; setPadding(0,0,0,dp(16)) })
        card.addView(TextView(this).apply {
            text = "اختر ملف الخزنة المشفرة (.html)"
            setTextColor(AppConfig.TEXT_WHITE); textSize = 16f; gravity = Gravity.CENTER; setPadding(0,0,0,dp(24))
        })

        val btnPick = Button(this).apply {
            text = "تصفح الملفات"
            setTextColor(Color.BLACK); typeface = Typeface.SERIF
            applyGoldGradient(this)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48))
            setOnClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"
                }
                startActivityForResult(intent, 1002)
            }
        }
        card.addView(btnPick)

        parent.addView(card)
    }

    // ---------------------------------------------------------
    // VIEW DECRYPTED VAULT
    // ---------------------------------------------------------
    private fun buildView(parent: LinearLayout) {
        parent.addView(createBackButton())
        val jsonStr = decryptedPayloadJson ?: return

        val json = JSONObject(jsonStr)
        val msg = json.optString("message", "")
        val fields = json.optJSONObject("fields")

        if (fields != null && fields.length() > 0) {
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; background = luxuryBg(AppConfig.CARD_DARK, 12, 1, AppConfig.GOLD_PRIMARY)
                setPadding(dp(16), dp(16), dp(16), dp(16)); layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.bottomMargin = dp(16) }
            }
            box.addView(TextView(this).apply { text = "بيانات الحساب"; setTextColor(AppConfig.GOLD_PRIMARY); typeface = Typeface.DEFAULT_BOLD; setPadding(0,0,0,dp(12)) })
            for (key in fields.keys()) {
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0,dp(4),0,dp(4)) }
                row.addView(TextView(this).apply { text = "$key : "; setTextColor(AppConfig.TEXT_MUTED); textSize = 14f })
                row.addView(TextView(this).apply { text = fields.optString(key); setTextColor(AppConfig.TEXT_WHITE); textSize = 14f; typeface = Typeface.DEFAULT_BOLD; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) })
                row.addView(TextView(this).apply { text = "📋"; setPadding(dp(8),0,0,0); setOnClickListener { copyToClipboard(fields.optString(key)) } })
                box.addView(row)
            }
            parent.addView(box)
        }

        if (msg.isNotEmpty()) {
            val msgBox = TextView(this).apply {
                text = msg; setTextColor(AppConfig.TEXT_WHITE); textSize = 16f
                setLineSpacing(0f, 1.3f); setPadding(dp(16), dp(16), dp(16), dp(16))
                background = luxuryBg(AppConfig.CARD_DARK, 12, 1, AppConfig.GOLD_PRIMARY)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.bottomMargin = dp(20) }
            }
            parent.addView(msgBox)
        }

        val media = json.optJSONArray("media")
        if (media != null && media.length() > 0) {
            val searchRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0,dp(8),0,dp(8)) }
            val searchInput = EditText(this).apply {
                hint = "بحث بالاسم..."; setTextColor(AppConfig.TEXT_WHITE); setHintTextColor(AppConfig.TEXT_MUTED)
                background = luxuryBg(AppConfig.CARD_DARK, 8); setPadding(dp(12),dp(8),dp(12),dp(8))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                addTextChangedListener(object : TextWatcher {
                    override fun afterTextChanged(s: Editable?) { currentSearchQuery = s.toString(); renderUI() }
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                })
            }
            searchRow.addView(searchInput)
            parent.addView(searchRow)

            for (i in 0 until media.length()) {
                val item = media.getJSONObject(i)
                val name = item.optString("name")
                if (currentSearchQuery.isNotEmpty() && !name.contains(currentSearchQuery, ignoreCase = true)) continue

                val kind = item.optString("kind")
                val data = item.optString("data")
                val size = item.optLong("size", 0)

                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL; background = luxuryBg(AppConfig.CARD_DARK, 12, 1, Color.parseColor("#333A45"))
                    setPadding(dp(16), dp(16), dp(16), dp(16)); layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.bottomMargin = dp(12) }
                }

                if (kind == "image") {
                    val img = ImageView(this).apply {
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(200))
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        try {
                            val bytes = Base64.getDecoder().decode(data.substringAfter(","))
                            setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
                        } catch (e: Exception) {}
                    }
                    card.addView(img)
                }

                card.addView(TextView(this).apply { text = "$name (${formatSize(size)})"; setTextColor(AppConfig.TEXT_WHITE); textSize = 12f; setPadding(0,dp(8),0,dp(8)); gravity = Gravity.CENTER })

                val btnSave = Button(this).apply {
                    text = "حفظ الملف"
                    setTextColor(Color.BLACK); typeface = Typeface.SERIF; applyGoldGradient(this)
                    setOnClickListener {
                        val mime = if (data.startsWith("data:")) data.substringAfter("data:").substringBefore(";") else "*/*"
                        pendingSaveFileData = if (data.contains(",")) data.substringAfter(",") else data
                        
                        // No 'type' val shadowing here anymore
                        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE); type = mime
                            putExtra(Intent.EXTRA_TITLE, name)
                        }
                        startActivityForResult(intent, 3001)
                    }
                }
                card.addView(btnSave)
                parent.addView(card)
            }
        }
    }

    // ---------------------------------------------------------
    // SETTINGS DIALOG
    // ---------------------------------------------------------
    private fun showSettingsDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(20), dp(20), dp(20))
        }

        val chkSecure = CheckBox(this).apply {
            text = "منع لقطات الشاشة (FLAG_SECURE)"
            setTextColor(AppConfig.TEXT_WHITE)
            isChecked = VaultStore.isScreenSecure()
            setOnCheckedChangeListener { _, isChecked -> VaultStore.setScreenSecure(isChecked); applyScreenSecurity() }
        }
        layout.addView(chkSecure)

        val chkLock = CheckBox(this).apply {
            text = "قفل تلقائي عند الخروج"
            setTextColor(AppConfig.TEXT_WHITE)
            isChecked = VaultStore.isAutoLock()
            setOnCheckedChangeListener { _, isChecked -> VaultStore.setAutoLock(isChecked) }
        }
        layout.addView(chkLock)

        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("⚙️ إعدادات الخصوصية")
            .setView(layout)
            .setPositiveButton("حسناً", null)
            .show()
    }

    // ---------------------------------------------------------
    // HELPERS & UTILS
    // ---------------------------------------------------------
    private fun createBackButton(): Button {
        return Button(this).apply {
            text = "← رجوع للوحة التحكم"
            setTextColor(AppConfig.TEXT_MUTED); background = null; gravity = Gravity.START
            setPadding(0, 0, 0, dp(16))
            setOnClickListener { currentScreen = "home"; decryptedPayloadJson = null; renderUI() }
        }
    }

    private fun createLabel(text: String): TextView {
        return TextView(this).apply {
            this.text = text; setTextColor(AppConfig.GOLD_PRIMARY); textSize = 13f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD); setPadding(0, dp(16), 0, dp(8))
        }
    }

    private fun createLuxuryInput(hint: String, multiLine: Boolean): EditText {
        return EditText(this).apply {
            this.hint = hint; setTextColor(AppConfig.TEXT_WHITE); setHintTextColor(Color.parseColor("#555555"))
            background = luxuryBg(Color.parseColor("#151A20"), 8, 1, Color.parseColor("#333A45"))
            setPadding(dp(14), dp(14), dp(14), dp(14))
            if (multiLine) { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE; minLines = 3 }
            else { inputType = InputType.TYPE_CLASS_TEXT }
        }
    }

    private fun calculateStrength(password: String): Int {
        var score = 0
        if (password.length >= 8) score += 25
        if (password.length >= 12) score += 25
        if (password.any { it.isDigit() }) score += 25
        if (password.any { !it.isLetterOrDigit() }) score += 25
        return score
    }

    private fun formatSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        var s = size.toDouble(); var i = 0
        while (s >= 1024 && i < units.size - 1) { s /= 1024; i++ }
        return String.format(Locale.US, "%.1f %s", s, units[i])
    }

    private fun copyToClipboard(text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Keykeep", text))
        Toast.makeText(this, "تم النسخ للحافظة", Toast.LENGTH_SHORT).show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        when (requestCode) {
            2001 -> {
                if (data == null) return
                val uris = mutableListOf<Uri>()
                if (data.clipData != null) {
                    for (i in 0 until data.clipData!!.itemCount) uris.add(data.clipData!!.getItemAt(i).uri)
                } else if (data.data != null) { uris.add(data.data!!) }
                
                Thread {
                    for (uri in uris) {
                        try {
                            val mime = contentResolver.getType(uri) ?: "*/*"
                            val name = getFileName(uri)
                            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: continue
                            if (bytes.size > 20 * 1024 * 1024) continue
                            
                            val b64 = Base64.getEncoder().encodeToString(bytes)
                            val kind = if (mime.startsWith("image/")) "image" else "file"
                            mediaItems.add(JSONObject().apply {
                                put("kind", kind); put("data", "data:$mime;base64,$b64"); put("name", name); put("size", bytes.size)
                            })
                        } catch (e: Exception) {}
                    }
                    runOnUiThread { renderUI() }
                }.start()
            }
            1001 -> data?.data?.let { uri ->
                try {
                    contentResolver.openOutputStream(uri)?.use { out -> out.write(pendingSaveHtml.toByteArray(Charsets.UTF_8)) }
                    VaultStore.addVaultStats(mediaItems.size, mediaItems.sumOf { it.optLong("size", 0) })
                    Toast.makeText(this, "👑 تم تشفير وحفظ الخزنة", Toast.LENGTH_LONG).show()
                    currentScreen = "home"
                    mediaItems.clear()
                    draftMessage = ""
                    draftPass = ""
                    draftPassConfirm = ""
                    customFields.clear()
                    renderUI()
                } catch (e: Exception) { Toast.makeText(this, "فشل الحفظ", Toast.LENGTH_SHORT).show() }
            }
            1002 -> data?.data?.let { uri ->
                try {
                    val html = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.readText() ?: return@let
                    val salt = extractString(html, "SALT")
                    val iv = extractString(html, "IV")
                    val cipher = extractString(html, "CIPHER")
                    val hmac = extractString(html, "HMAC")
                    val vaultId = extractString(html, "VAULT_ID")
                    if (salt != null && iv != null && cipher != null && hmac != null && vaultId != null) {
                        promptPassword { pass ->
                            Toast.makeText(this, "جاري فك التشفير...", Toast.LENGTH_SHORT).show()
                            Thread {
                                val dec = CryptoEngine.decryptVault(salt, iv, cipher, hmac, vaultId, pass)
                                runOnUiThread {
                                    if (dec != null) {
                                        decryptedPayloadJson = dec
                                        currentScreen = "view"
                                        renderUI()
                                    } else { Toast.makeText(this, "مفتاح خاطئ أو ملف تالف", Toast.LENGTH_LONG).show() }
                                }
                            }.start()
                        }
                    } else { Toast.makeText(this, "ملف غير صالح", Toast.LENGTH_SHORT).show() }
                } catch (e: Exception) { Toast.makeText(this, "خطأ في القراءة", Toast.LENGTH_SHORT).show() }
            }
            3001 -> data?.data?.let { uri ->
                val d = pendingSaveFileData ?: return@let
                try {
                    contentResolver.openOutputStream(uri)?.use { it.write(Base64.getDecoder().decode(d)) }
                    Toast.makeText(this, "✔ تم حفظ الملف", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) { Toast.makeText(this, "فشل الحفظ", Toast.LENGTH_SHORT).show() }
                finally { pendingSaveFileData = null }
            }
        }
    }

    private fun promptPassword(onPass: (String) -> Unit) {
        val input = EditText(this).apply {
            hint = "أدخل المفتاح"; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setTextColor(Color.BLACK)
        }
        AlertDialog.Builder(this).setTitle("🔒 فتح الخزنة").setView(input)
            .setPositiveButton("فتح") { _, _ -> onPass(input.text.toString()) }
            .setNegativeButton("إلغاء", null).show()
    }

    private fun extractString(html: String, varName: String): String? {
        return Regex("""(?:var|const)\s+$varName(?:_B64)?\s*=\s*["'](.*?)["']""").find(html)?.groupValues?.get(1)
    }

    private fun getFileName(uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) result = cursor.getString(index)
                }
            } finally { cursor?.close() }
        }
        return result ?: "file_${System.currentTimeMillis()}"
    }
}

// ============================================================
// CUSTOM VIEW: ROYAL BACKGROUND
// ============================================================
class RoyalBackgroundView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.parseColor("#222533")
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        
        val path = Path()
        // Top Left
        path.moveTo(40f, 80f); path.lineTo(40f, 40f); path.lineTo(80f, 40f)
        // Top Right
        path.moveTo(w - 80f, 40f); path.lineTo(w - 40f, 40f); path.lineTo(w - 40f, 80f)
        // Bottom Left
        path.moveTo(40f, h - 80f); path.lineTo(40f, h - 40f); path.lineTo(80f, h - 40f)
        // Bottom Right
        path.moveTo(w - 80f, h - 40f); path.lineTo(w - 40f, h - 40f); path.lineTo(w - 40f, h - 80f)
        
        canvas.drawPath(path, paint)
        
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = android.graphics.RadialGradient(w / 2f, 100f, 200f,
                intArrayOf(Color.parseColor("#33D4AF37"), Color.TRANSPARENT),
                floatArrayOf(0f, 1f), android.graphics.Shader.TileMode.CLAMP)
        }
        canvas.drawCircle(w / 2f, 100f, 200f, glowPaint)
    }
}