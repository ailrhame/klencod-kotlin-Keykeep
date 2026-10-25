package com.keep.aio

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class MainActivity : Activity() {

    private var currentScreen = "home"
    private var pendingSaveHtml = ""
    private val mediaItems = mutableListOf<JSONObject>()

    private val BG = Color.parseColor("#0a0a0d")
    private val CARD_BG = Color.parseColor("#16171b")
    private val ACCENT = Color.parseColor("#a855f7")
    private val TEXT_MAIN = Color.parseColor("#e8e9ee")
    private val TEXT_MUTED = Color.parseColor("#8a8f9c")
    private val BORDER = Color.parseColor("#3a3f4a")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        renderUI()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun roundedBg(color: Int, radius: Int, stroke: Int = 0, strokeColor: Int = Color.TRANSPARENT): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            if (stroke > 0) setStroke(dp(stroke), strokeColor)
        }
    }

    private fun renderUI() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
            setPadding(dp(20), dp(40), dp(20), dp(20))
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        val header = TextView(this).apply {
            text = "🔒 Keykeep"
            textSize = 28f
            setTextColor(ACCENT)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(8))
        }
        root.addView(header)
        
        val subHeader = TextView(this).apply {
            text = "Vault Creator & Opener"
            textSize = 14f
            setTextColor(TEXT_MUTED)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(32))
        }
        root.addView(subHeader)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        root.addView(content)

        when (currentScreen) {
            "home" -> buildHome(content)
            "create" -> buildCreate(content)
            "open" -> buildOpen(content)
        }

        setContentView(root)
    }

    private fun buildHome(parent: LinearLayout) {
        val btnCreate = Button(this).apply {
            text = "➕ إنشاء خزنة جديدة"
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            background = roundedBg(ACCENT, 12)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setOnClickListener { 
                mediaItems.clear() // Clear previous media items
                currentScreen = "create"
                renderUI() 
            }
        }
        parent.addView(btnCreate)

        val spacer = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(16)) }
        parent.addView(spacer)

        val btnOpen = Button(this).apply {
            text = "🔓 فتح خزنة موجودة"
            setTextColor(TEXT_MAIN)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            background = roundedBg(CARD_BG, 12, 1, BORDER)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setOnClickListener { currentScreen = "open"; renderUI() }
        }
        parent.addView(btnOpen)
    }

    private fun buildCreate(parent: LinearLayout) {
        val scroll = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(layout)
        parent.addView(scroll)

        layout.addView(createLabel("الرسالة السرية"))
        val msgInput = createInput("اكتب رسالتك هنا...", true)
        layout.addView(msgInput)

        layout.addView(createLabel("كلمة المرور"))
        val passInput = createInput("كلمة مرور قوية", false)
        passInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        layout.addView(passInput)

        layout.addView(createLabel("تأكيد كلمة المرور"))
        val passConfirmInput = createInput("أعد كتابة كلمة المرور", false)
        passConfirmInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        layout.addView(passConfirmInput)

        val btnAddMedia = Button(this).apply {
            text = "📎 إضافة ملف/صورة"
            setTextColor(TEXT_MAIN)
            background = roundedBg(CARD_BG, 8, 1, BORDER)
            setOnClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }
                startActivityForResult(intent, 2001)
            }
        }
        layout.addView(btnAddMedia)

        val mediaStatus = TextView(this).apply {
            text = if (mediaItems.isEmpty()) "لا توجد مرفقات" else "تم إضافة ${mediaItems.size} مرفق"
            setTextColor(TEXT_MUTED)
            textSize = 12f
            setPadding(0, dp(4), 0, dp(16))
        }
        layout.addView(mediaStatus)

        val btnGenerate = Button(this).apply {
            text = "🔐 تشفير وحفظ الملف"
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            background = roundedBg(ACCENT, 12)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setOnClickListener {
                val msg = msgInput.text.toString()
                val pass = passInput.text.toString()
                val confirm = passConfirmInput.text.toString()

                if (pass.isEmpty() || pass != confirm) {
                    Toast.makeText(this@MainActivity, "كلمات المرور غير متطابقة", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                try {
                    pendingSaveHtml = generateVaultHtml(msg, pass, mediaItems)
                    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "text/html"
                        putExtra(Intent.EXTRA_TITLE, "vault_${UUID.randomUUID().toString().take(6)}.html")
                    }
                    startActivityForResult(intent, 1001)
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "خطأ في التشفير: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
        layout.addView(btnGenerate)

        val btnBack = Button(this).apply {
            text = "رجوع"
            setTextColor(TEXT_MUTED)
            background = null
            setOnClickListener { currentScreen = "home"; renderUI() }
        }
        layout.addView(btnBack)
    }

    private fun buildOpen(parent: LinearLayout) {
        val btnPick = Button(this).apply {
            text = "📂 اختر ملف الخزنة (.html)"
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            background = roundedBg(ACCENT, 12)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setOnClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*" // Allow any file to be selected, we will parse text
                }
                startActivityForResult(intent, 1002)
            }
        }
        parent.addView(btnPick)

        val btnBack = Button(this).apply {
            text = "رجوع"
            setTextColor(TEXT_MUTED)
            background = null
            setOnClickListener { currentScreen = "home"; renderUI() }
            setPadding(0, dp(20), 0, 0)
        }
        parent.addView(btnBack)
    }

    private fun createLabel(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(TEXT_MUTED)
            textSize = 13f
            setPadding(0, dp(12), 0, dp(6))
        }
    }

    private fun createInput(hint: String, multiLine: Boolean): EditText {
        return EditText(this).apply {
            this.hint = hint
            setTextColor(TEXT_MAIN)
            setHintTextColor(TEXT_MUTED)
            background = roundedBg(CARD_BG, 8, 1, BORDER)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            if (multiLine) {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                minLines = 3
            } else {
                inputType = InputType.TYPE_CLASS_TEXT
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return

        when (requestCode) {
            2001 -> handleMediaSelection(uri)
            1001 -> handleSaveHtml(uri)
            1002 -> handleOpenVault(uri)
        }
    }

    private fun handleMediaSelection(uri: Uri) {
        try {
            val mime = contentResolver.getType(uri) ?: "application/octet-stream"
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "file"
            val bytes = contentResolver.openInputStream(uri)?.readBytes() ?: return
            val b64 = Base64.getEncoder().encodeToString(bytes)
            val kind = if (mime.startsWith("image/")) "image" else "file"
            
            val item = JSONObject().apply {
                put("kind", kind)
                put("data", "data:$mime;base64,$b64")
                put("name", name)
            }
            mediaItems.add(item)
            Toast.makeText(this, "تم إضافة المرفق", Toast.LENGTH_SHORT).show()
            renderUI()
        } catch (e: Exception) {
            Toast.makeText(this, "فشل قراءة الملف", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleSaveHtml(uri: Uri) {
        try {
            contentResolver.openOutputStream(uri)?.use { out ->
                out.write(pendingSaveHtml.toByteArray(Charsets.UTF_8))
            }
            Toast.makeText(this, "✔ تم حفظ الخزنة بنجاح", Toast.LENGTH_LONG).show()
            currentScreen = "home"
            renderUI()
        } catch (e: Exception) {
            Toast.makeText(this, "فشل الحفظ: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun handleOpenVault(uri: Uri) {
        try {
            val html = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.readText()
            if (html.isNullOrEmpty()) {
                Toast.makeText(this, "الملف فارغ أو غير قابل للقراءة", Toast.LENGTH_SHORT).show()
                return
            }

            // Robust regex to handle both old HTML vars (SALT_B64) and new Kotlin vars (SALT)
            val salt = extractString(html, "SALT")
            val iv = extractString(html, "IV")
            val cipher = extractString(html, "CIPHER")
            val hmac = extractString(html, "HMAC")
            val vaultId = extractString(html, "VAULT_ID")
            val iterations = extractInt(html, "ITERATIONS") ?: 310000

            if (salt == null || iv == null || cipher == null || hmac == null || vaultId == null) {
                Toast.makeText(this, "تنسيق الملف غير مدعوم أو البيانات تالفة", Toast.LENGTH_LONG).show()
                return
            }

            promptPassword { pass ->
                Toast.makeText(this, "جاري فك التشفير...", Toast.LENGTH_SHORT).show()
                Thread {
                    try {
                        val decryptedJson = decryptVault(salt, iv, cipher, hmac, vaultId, iterations, pass)
                        runOnUiThread {
                            if (decryptedJson != null) {
                                showDecryptedContent(decryptedJson)
                            } else {
                                Toast.makeText(this, "كلمة المرور خاطئة أو الملف تالف", Toast.LENGTH_LONG).show()
                            }
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            Toast.makeText(this, "فشل فك التشفير: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }.start()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "ملف غير صالح: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun extractString(html: String, varName: String): String? {
        val regex = Regex("""(?:var|const)\s+$varName(?:_B64)?\s*=\s*["'](.*?)["']""")
        return regex.find(html)?.groupValues?.get(1)
    }

    private fun extractInt(html: String, varName: String): Int? {
        val regex = Regex("""(?:var|const)\s+$varName\s*=\s*(\d+)""")
        return regex.find(html)?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun promptPassword(onPass: (String) -> Unit) {
        val input = EditText(this).apply {
            hint = "أدخل كلمة المرور"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setTextColor(Color.BLACK)
        }
        AlertDialog.Builder(this)
            .setTitle("🔒 فتح الخزنة")
            .setView(input)
            .setPositiveButton("فتح") { _, _ -> onPass(input.text.toString()) }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun showDecryptedContent(jsonStr: String) {
        val json = JSONObject(jsonStr)
        val msg = json.optString("message", "")
        
        val builder = StringBuilder()
        if (msg.isNotEmpty()) builder.append(msg).append("\n\n")
        
        val media = json.optJSONArray("media")
        if (media != null) {
            for (i in 0 until media.length()) {
                val item = media.getJSONObject(i)
                builder.append("📎 مرفق: ").append(item.optString("name", "ملف")).append("\n")
            }
        }
        
        AlertDialog.Builder(this)
            .setTitle("📄 محتوى الخزنة")
            .setMessage(if (builder.isEmpty()) "لا يوجد محتوى نصي" else builder.toString())
            .setPositiveButton("حسناً", null)
            .show()
    }

    // ---------------------------------------------------------
    // NATIVE CRYPTO ENGINE
    // ---------------------------------------------------------
    private fun generateVaultHtml(message: String, password: String, media: List<JSONObject>): String {
        val vaultId = UUID.randomUUID().toString().replace("-", "").substring(0, 8)
        val iterations = 310000
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }

        val json = JSONObject().apply {
            put("message", message)
            if (media.isNotEmpty()) {
                val arr = JSONArray()
                media.forEach { arr.put(it) }
                put("media", arr)
            }
        }.toString()

        val compressed = gzip(json.toByteArray())
        
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(compressed)

        val hmacKeyBytes = MessageDigest.getInstance("SHA-256").digest("$password|$vaultId|hmac".toByteArray())
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(hmacKeyBytes, "HmacSHA256"))
        val hmac = mac.doFinal(ciphertext)

        val b64 = Base64.getEncoder()
        val saltB64 = b64.encodeToString(salt)
        val ivB64 = b64.encodeToString(iv)
        val cipherB64 = b64.encodeToString(ciphertext)
        val hmacB64 = b64.encodeToString(hmac)

        return getHtmlTemplate(saltB64, ivB64, cipherB64, hmacB64, vaultId, iterations)
    }

    private fun decryptVault(saltB64: String, ivB64: String, cipherB64: String, hmacB64: String, vaultId: String, iterations: Int, password: String): String? {
        try {
            val b64 = Base64.getDecoder()
            val salt = b64.decode(saltB64)
            val iv = b64.decode(ivB64)
            val ciphertext = b64.decode(cipherB64)
            val hmac = b64.decode(hmacB64)

            val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
            val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded

            val hmacKeyBytes = MessageDigest.getInstance("SHA-256").digest("$password|$vaultId|hmac".toByteArray())
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(hmacKeyBytes, "HmacSHA256"))
            val computedHmac = mac.doFinal(ciphertext)
            
            if (!computedHmac.contentEquals(hmac)) return null

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            val compressed = cipher.doFinal(ciphertext)

            val gzipIn = GZIPInputStream(ByteArrayInputStream(compressed))
            return gzipIn.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: Exception) {
            return null
        }
    }

    private fun gzip(data: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(data) }
        return bos.toByteArray()
    }

    private fun getHtmlTemplate(salt: String, iv: String, cipher: String, hmac: String, vaultId: String, iterations: Int): String {
        return """<!DOCTYPE html>
<html lang="ar" dir="rtl">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Keykeep Vault</title>
<style>
  body { background: #0a0a0d; color: #e8e9ee; font-family: system-ui, sans-serif; display: flex; align-items: center; justify-content: center; min-height: 100vh; margin: 0; padding: 20px; }
  .box { background: #16171b; border: 1px solid #2a2d34; border-radius: 16px; padding: 32px; width: 100%; max-width: 400px; box-shadow: 0 10px 30px rgba(0,0,0,0.5); }
  h1 { color: #a855f7; text-align: center; margin-top: 0; }
  input { width: 100%; padding: 14px; margin-bottom: 16px; border-radius: 8px; border: 1px solid #3a3f4a; background: #101114; color: #fff; box-sizing: border-box; outline: none; }
  input:focus { border-color: #a855f7; }
  button { width: 100%; padding: 14px; border-radius: 8px; border: none; background: #a855f7; color: #fff; font-weight: bold; cursor: pointer; transition: 0.2s; }
  button:hover { background: #7c3aed; }
  .content { display: none; margin-top: 20px; text-align: left; white-space: pre-wrap; line-height: 1.6; }
  .media { width: 100%; border-radius: 8px; margin-top: 10px; }
  .err { color: #ff7a7a; font-size: 12px; text-align: center; margin-top: 10px; min-height: 16px; }
</style>
</head>
<body>
<div class="box">
  <h1>🔒 Keykeep</h1>
  <div id="loginBox">
    <input type="password" id="pass" placeholder="أدخل كلمة المرور" autocomplete="off">
    <button onclick="unlock()">فتح الخزنة</button>
    <div class="err" id="err"></div>
  </div>
  <div class="content" id="resultBox"></div>
</div>
<script>
const SALT = "$salt";
const IV = "$iv";
const CIPHER = "$cipher";
const HMAC = "$hmac";
const VAULT_ID = "$vaultId";
const ITERATIONS = $iterations;

function fromB64(b64) { return Uint8Array.from(atob(b64), c => c.charCodeAt(0)); }

async function deriveKey(password, saltBytes, usage) {
  const enc = new TextEncoder();
  const baseKey = await crypto.subtle.importKey('raw', enc.encode(password), { name:'PBKDF2' }, false, ['deriveKey']);
  return crypto.subtle.deriveKey(
    { name:'PBKDF2', salt: saltBytes, iterations: ITERATIONS, hash:'SHA-256' },
    baseKey, { name:'AES-GCM', length:256 }, false, [usage]
  );
}

async function sha256(bytes) { return new Uint8Array(await crypto.subtle.digest('SHA-256', bytes)); }

async function unlock() {
  const pass = document.getElementById('pass').value;
  if(!pass) return;
  document.getElementById('err').textContent = 'جاري فك التشفير...';
  try {
    const key = await deriveKey(pass, fromB64(SALT), 'decrypt');
    const plainBuf = await crypto.subtle.decrypt({ name:'AES-GCM', iv: fromB64(IV) }, key, fromB64(CIPHER));
    
    const hmacKeyBytes = await sha256(new TextEncoder().encode(pass + '|' + VAULT_ID + '|hmac'));
    const hmacKey = await crypto.subtle.importKey('raw', hmacKeyBytes, { name:'HMAC', hash:'SHA-256' }, false, ['verify']);
    const valid = await crypto.subtle.verify('HMAC', hmacKey, fromB64(HMAC), fromB64(CIPHER));
    if (!valid) throw new Error('فشل التحقق من السلامة');

    const ds = new DecompressionStream('gzip');
    const w = ds.writable.getWriter(); w.write(new Uint8Array(plainBuf)); w.close();
    const decompressedBuf = await new Response(ds.readable).arrayBuffer();
    
    const data = JSON.parse(new TextDecoder().decode(decompressedBuf));
    document.getElementById('loginBox').style.display = 'none';
    document.getElementById('resultBox').style.display = 'block';
    
    if (data.message) {
      const p = document.createElement('p');
      p.textContent = data.message;
      document.getElementById('resultBox').appendChild(p);
    }
    
    if (data.media) {
      data.media.forEach(item => {
        if (item.kind === 'image') {
          const img = document.createElement('img');
          img.src = item.data;
          img.className = 'media';
          document.getElementById('resultBox').appendChild(img);
        } else {
          const a = document.createElement('a');
          a.href = item.data;
          a.download = item.name;
          a.textContent = 'تحميل ' + item.name;
          a.style.display = 'block';
          a.style.marginTop = '10px';
          a.style.color = '#a855f7';
          document.getElementById('resultBox').appendChild(a);
        }
      });
    }
  } catch (e) {
    document.getElementById('err').textContent = 'كلمة مرور خاطئة أو ملف تالف';
  }
}
</script>
</body>
</html>"""
    }
}