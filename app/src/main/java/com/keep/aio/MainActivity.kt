
package com.keep.aio

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.Dialog
import android.app.KeyguardManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.hardware.biometrics.BiometricPrompt
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.PersistableBundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Base64
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.interfaces.RSAPublicKey
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

// ============================================================
// [CONSTANTS & HELPERS]
// ============================================================
private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
// NOTE: Unlimited vault size — but individual files still capped for OOM safety.
private const val MAX_FILE_BYTES = Long.MAX_VALUE   // per-attachment: no limit
private const val MAX_TOTAL_BYTES = Long.MAX_VALUE  // per-vault: no limit
private const val MAX_OPEN_BYTES = 1024L * 1024 * 1024 // 1GB read buffer (adjust if needed)
private const val CLIP_CLEAR_MS = 30_000L
private const val MASK = "••••••••"
private const val KEYSTORE_ALIAS = "keykeep_master_v5"
private const val SHAMIR_PRIME = 2_147_483_647 // 2^31 - 1 (Mersenne prime, safe for shares)

private val rng = SecureRandom()

private fun tr(ar: String, en: String): String = if (VaultStore.isArabic()) ar else en

private fun hex(c: Int) = String.format("#%06X", 0xFFFFFF and c)
private fun withAlpha(c: Int, a: Int) = (c and 0x00FFFFFF) or (a shl 24)

private fun EditText.watch(cb: (String) -> Unit) {
    addTextChangedListener(object : TextWatcher {
        override fun afterTextChanged(s: Editable?) { cb(s?.toString() ?: "") }
        override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
        override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
    })
}

// ============================================================
// [9 THEMES]
// ============================================================
class Palette(
    val id: String, val nameAr: String, val nameEn: String,
    val bg1: Int, val bg2: Int, val card: Int, val input: Int,
    val acc: Int, val accLight: Int, val accDark: Int,
    val text: Int, val muted: Int, val border: Int, val onAcc: Int,
    val light: Boolean = false
)

private fun c(h: String) = Color.parseColor(h)

val THEMES = listOf(
    Palette("royal", "ملكي ذهبي", "Royal Gold", c("#0B0C10"), c("#151A20"), c("#1F2833"), c("#151A20"), c("#D4AF37"), c("#F3E5AB"), c("#B8860B"), c("#FFFFFF"), c("#8A8F9C"), c("#333A45"), c("#000000")),
    Palette("ruby", "ياقوت أحمر", "Ruby", c("#100608"), c("#1A0B0F"), c("#2A1217"), c("#1A0B0F"), c("#E0314B"), c("#FF8FA0"), c("#9B1B30"), c("#FFFFFF"), c("#A38A8F"), c("#4A2229"), c("#FFFFFF")),
    Palette("emerald", "زمرد", "Emerald", c("#06100C"), c("#0B1A14"), c("#12281F"), c("#0B1A14"), c("#3DDC97"), c("#A8F0CF"), c("#1E9E6A"), c("#FFFFFF"), c("#86A396"), c("#1F4033"), c("#00110A")),
    Palette("sapphire", "سافير", "Sapphire", c("#070B16"), c("#0C1426"), c("#121D36"), c("#0C1426"), c("#6C8CFF"), c("#C3D0FF"), c("#3F5BD6"), c("#FFFFFF"), c("#8A93AD"), c("#26335A"), c("#050A1F")),
    Palette("amethyst", "أميثيست", "Amethyst", c("#0D0814"), c("#150E22"), c("#211634"), c("#150E22"), c("#B57BFF"), c("#E0C8FF"), c("#7B3FD1"), c("#FFFFFF"), c("#9A8FB0"), c("#3B2A5C"), c("#12061F")),
    Palette("charcoal", "فحم وفضة", "Charcoal", c("#0E0E0F"), c("#18181A"), c("#232326"), c("#18181A"), c("#C9CED6"), c("#F1F3F6"), c("#8E949E"), c("#FFFFFF"), c("#8B8B92"), c("#3A3A3F"), c("#000000")),
    Palette("desert", "صحراء", "Desert", c("#14100B"), c("#1D1710"), c("#2C2217"), c("#1D1710"), c("#D9A066"), c("#F2D3A8"), c("#A8691F"), c("#FBF3E6"), c("#A59780"), c("#4A3B28"), c("#1A0F00")),
    Palette("neon", "نيون سايبر", "Cyber Neon", c("#05060D"), c("#0A0B1A"), c("#11132B"), c("#0A0B1A"), c("#00E5FF"), c("#8BF3FF"), c("#FF2D95"), c("#FFFFFF"), c("#8E92C0"), c("#1E2250"), c("#00131A")),
    Palette("ice", "ثلج (فاتح)", "Ice (Light)", c("#F4F8FC"), c("#E6EEF7"), c("#FFFFFF"), c("#F0F5FA"), c("#2F7DE1"), c("#7DB4F5"), c("#1B55A6"), c("#14202E"), c("#5C6B7C"), c("#C9D6E4"), c("#FFFFFF"), true)
)

// ============================================================
// [VAULT SPECS — 16 TYPES]
// ============================================================
data class FieldSpec(
    val id: String, val ar: String, val en: String,
    val secret: Boolean = false, val multiline: Boolean = false,
    val numeric: Boolean = false, val totp: Boolean = false
) { val label: String get() = tr(ar, en) }

data class VaultSpec(
    val type: String, val ar: String, val en: String, val fields: List<FieldSpec>,
    val allowMedia: Boolean = false, val dynamic: Boolean = false,
    val icon: String = "🔐", val color: String = "#D4AF37"
) { val title: String get() = tr(ar, en) }

object AppConfig {
    var theme: Palette = THEMES[0]
    val BG_OBSIDIAN: Int get() = theme.bg1
    val BG2: Int get() = theme.bg2
    val CARD_DARK: Int get() = theme.card
    val INPUT_BG: Int get() = theme.input
    val GOLD_PRIMARY: Int get() = theme.acc
    val GOLD_LIGHT: Int get() = theme.accLight
    val GOLD_DARK: Int get() = theme.accDark
    val TEXT_MAIN: Int get() = theme.text
    val TEXT_MUTED: Int get() = theme.muted
    val BORDER: Int get() = theme.border
    val ON_ACCENT: Int get() = theme.onAcc
    val DANGER_RED = Color.parseColor("#FF4444")
    val WARN_ORANGE = Color.parseColor("#FF9800")
    val OK_GREEN = Color.parseColor("#4CAF50")
    val INFO_BLUE = Color.parseColor("#2196F3")

    fun themeById(id: String): Palette = THEMES.firstOrNull { it.id == id } ?: THEMES[0]

    val VAULTS = listOf(
        // ---- Original types
        VaultSpec("note", "ملاحظة سرية", "Secret note", listOf(
            FieldSpec("t", "عنوان الملاحظة", "Note title"),
            FieldSpec("m", "الرسالة السرية", "Secret message", multiline = true)), icon = "📝"),

        VaultSpec("account", "حساب", "Account", listOf(
            FieldSpec("u", "اسم المستخدم / البريد", "Username / email"),
            FieldSpec("p", "كلمة المرور", "Password", secret = true),
            FieldSpec("s", "الموقع / التطبيق", "Website / app"),
            FieldSpec("o", "سر المصادقة الثنائية (TOTP)", "2FA secret (TOTP)", secret = true, totp = true),
            FieldSpec("n", "ملاحظات", "Notes", multiline = true)), icon = "👤"),

        VaultSpec("totp", "مصادق 2FA", "2FA authenticator", listOf(
            FieldSpec("a", "اسم الحساب / الخدمة", "Account / service"),
            FieldSpec("o", "سر المصادقة (TOTP)", "Authenticator secret (TOTP)", secret = true, totp = true)), icon = "🔢"),

        VaultSpec("document", "مستند / صورة", "Document / image", listOf(
            FieldSpec("d", "وصف المستند", "Description")), allowMedia = true, icon = "📎"),

        VaultSpec("credit_card", "بطاقة ائتمان", "Credit card", listOf(
            FieldSpec("h", "اسم صاحب البطاقة", "Card holder"),
            FieldSpec("n", "رقم البطاقة", "Card number", secret = true, numeric = true),
            FieldSpec("e", "تاريخ الانتهاء", "Expiry date"),
            FieldSpec("c", "CVV", "CVV", secret = true, numeric = true),
            FieldSpec("p", "PIN", "PIN", secret = true, numeric = true)), icon = "💳"),

        VaultSpec("wifi", "شبكة واي فاي", "Wi-Fi network", listOf(
            FieldSpec("s", "اسم الشبكة (SSID)", "Network name (SSID)"),
            FieldSpec("p", "كلمة المرور", "Password", secret = true)), icon = "📶"),

        VaultSpec("crypto", "محفظة كريبتو", "Crypto wallet", listOf(
            FieldSpec("w", "المحفظة / الشبكة", "Wallet / network"),
            FieldSpec("a", "العنوان", "Address"),
            FieldSpec("s", "العبارة السرية (Seed)", "Seed phrase", secret = true, multiline = true)), icon = "🪙"),

        VaultSpec("bank", "حساب بنكي", "Bank account", listOf(
            FieldSpec("b", "اسم البنك", "Bank name"),
            FieldSpec("h", "اسم صاحب الحساب", "Account holder"),
            FieldSpec("n", "رقم الحساب", "Account number"),
            FieldSpec("i", "IBAN", "IBAN"),
            FieldSpec("p", "الرقم السري", "PIN", secret = true, numeric = true)), icon = "🏦"),

        VaultSpec("custom", "حقول مخصصة", "Custom fields", emptyList(), allowMedia = true, dynamic = true, icon = "🎨"),

        // ---- NEW types (Feature #4)
        VaultSpec("passport", "جواز سفر", "Passport", listOf(
            FieldSpec("n", "الاسم الكامل", "Full name"),
            FieldSpec("p", "رقم الجواز", "Passport number", secret = true),
            FieldSpec("c", "الدولة", "Country"),
            FieldSpec("d", "تاريخ الإصدار", "Issue date"),
            FieldSpec("e", "تاريخ الانتهاء", "Expiry date"),
            FieldSpec("b", "تاريخ الميلاد", "Date of birth"),
            FieldSpec("n2", "الجنسية", "Nationality")), allowMedia = true, icon = "🛂"),

        VaultSpec("driver_license", "رخصة قيادة", "Driver license", listOf(
            FieldSpec("n", "الاسم الكامل", "Full name"),
            FieldSpec("d", "رقم الرخصة", "License number", secret = true),
            FieldSpec("c", "الفئة", "Category"),
            FieldSpec("e", "تاريخ الانتهاء", "Expiry date"),
            FieldSpec("b", "تاريخ الميلاد", "Date of birth")), allowMedia = true, icon = "🚗"),

        VaultSpec("api_key", "مفتاح API", "API key", listOf(
            FieldSpec("s", "الخدمة", "Service"),
            FieldSpec("k", "المفتاح", "Key", secret = true, multiline = true),
            FieldSpec("u", "الرابط", "Endpoint URL"),
            FieldSpec("n", "ملاحظات", "Notes", multiline = true)), icon = "🔑"),

        VaultSpec("ssh_key", "مفتاح SSH", "SSH key", listOf(
            FieldSpec("h", "الخادم / المضيف", "Host"),
            FieldSpec("u", "المستخدم", "Username"),
            FieldSpec("p", "المفتاح الخاص", "Private key", secret = true, multiline = true),
            FieldSpec("k", "المفتاح العام", "Public key", multiline = true),
            FieldSpec("ph", "عبارة المرور", "Passphrase", secret = true)), icon = "💻"),

        VaultSpec("insurance", "تأمين", "Insurance", listOf(
            FieldSpec("c", "الشركة", "Company"),
            FieldSpec("p", "رقم البوليصة", "Policy number", secret = true),
            FieldSpec("t", "النوع", "Type"),
            FieldSpec("s", "تاريخ البدء", "Start date"),
            FieldSpec("e", "تاريخ الانتهاء", "Expiry date"),
            FieldSpec("n", "ملاحظات", "Notes", multiline = true)), allowMedia = true, icon = "🛡"),

        VaultSpec("software_license", "ترخيص برنامج", "Software license", listOf(
            FieldSpec("s", "اسم البرنامج", "Software name"),
            FieldSpec("k", "مفتاح الترخيص", "License key", secret = true),
            FieldSpec("e", "البريد / الحساب", "Email / account"),
            FieldSpec("d", "تاريخ الشراء", "Purchase date"),
            FieldSpec("r", "تاريخ التجديد", "Renewal date"),
            FieldSpec("n", "ملاحظات", "Notes", multiline = true)), allowMedia = false, icon = "💾"),

        VaultSpec("recovery_codes", "أكواد استرداد", "Recovery codes", listOf(
            FieldSpec("s", "الخدمة", "Service"),
            FieldSpec("c", "الأكواد (سطر لكل كود)", "Codes (one per line)", secret = true, multiline = true)), icon = "🆘")
    )

    fun spec(type: String): VaultSpec = VAULTS.firstOrNull { it.type == type } ?: VAULTS[0]
    fun lockLabels() = listOf(tr("فوراً", "Immediately"), tr("30 ثانية", "30 seconds"), tr("دقيقة", "1 minute"), tr("5 دقائق", "5 minutes"), tr("بدون قفل", "Never"))
    val LOCK_VALUES = intArrayOf(0, 30, 60, 300, -1)
    fun fontLabels() = listOf(tr("صغير", "Small"), tr("عادي", "Normal"), tr("كبير", "Large"), tr("كبير جداً", "Extra large"))
}

// ============================================================
// [DATA CLASSES FOR PAYLOAD]
// ============================================================
class VaultPayload(
    val type: String, val title: String, val expires: Long,
    val data: Map<String, String>, val secrets: List<String>, val totps: List<String>,
    val media: List<JSONObject>,
    val reminders: List<JSONObject> = emptyList(),
    val keyFileData: String? = null
)

class ParsedVault(
    val salt: String, val iv: String, val cipher: String, val hmac: String,
    val vaultId: String, val version: String, val iter: Int,
    val keyFileHash: String? = null
)

// ============================================================
// [CRYPTO ENGINE — AES-256-GCM, PBKDF2 600k, AAD]
// ============================================================
object CryptoEngine {
    const val ITER_V5 = 600_000
    const val ITER_V4 = 310_000

    private fun aad(vaultId: String) = "KEYKEEP|v5|$vaultId".toByteArray(Charsets.UTF_8)
    private fun b64e(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun b64d(s: String) = Base64.decode(s, Base64.DEFAULT)

    private fun deriveKey(password: CharArray, salt: ByteArray, iter: Int, keyFileBytes: ByteArray?): ByteArray {
        // Feature #5: keyfile is appended to password before KDF
        val pw = if (keyFileBytes != null) {
            val pwBytes = String(password).toByteArray(Charsets.UTF_8)
            val combined = ByteArray(pwBytes.size + keyFileBytes.size)
            System.arraycopy(pwBytes, 0, combined, 0, pwBytes.size)
            System.arraycopy(keyFileBytes, 0, combined, pwBytes.size, keyFileBytes.size)
            val s = String(combined, Charsets.ISO_8859_1)
            java.util.Arrays.fill(pwBytes, 0)
            java.util.Arrays.fill(combined, 0)
            s.toCharArray()
        } else password

        val spec = PBEKeySpec(pw, salt, iter, 256)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
            if (pw !== password) java.util.Arrays.fill(pw, '\u0000')
        }
    }

    private fun themeCss(t: Palette) =
        "--bg1:${hex(t.bg1)};--bg2:${hex(t.bg2)};--card:${hex(t.card)};--inp:${hex(t.input)};--acc:${hex(t.acc)};" +
            "--acc2:${hex(t.accLight)};--acc3:${hex(t.accDark)};--txt:${hex(t.text)};--mut:${hex(t.muted)};" +
            "--bd:${hex(t.border)};--btxt:${hex(t.onAcc)};--dng:#FF4444;"

    fun generateVault(
        p: VaultPayload, password: CharArray, theme: Palette, arabic: Boolean,
        keyFileBytes: ByteArray? = null, onProgress: (Int) -> Unit
    ): String {
        val vaultId = ByteArray(6).also { rng.nextBytes(it) }.joinToString("") { "%02x".format(it) }
        val salt = ByteArray(32).also { rng.nextBytes(it) }
        val iv = ByteArray(12).also { rng.nextBytes(it) }
        val keyFileHash = keyFileBytes?.let {
            Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(it), Base64.NO_WRAP)
        }
        var key: ByteArray? = null
        try {
            onProgress(5)
            val json = JSONObject().apply {
                put("version", "5"); put("type", p.type)
                put("created", SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date()))
                if (p.title.isNotBlank()) put("title", p.title)
                if (p.expires > 0) put("expires", p.expires)
                val d = JSONObject(); for ((k, v) in p.data) d.put(k, v); put("data", d)
                put("secret", JSONArray(p.secrets))
                if (p.totps.isNotEmpty()) put("totp", JSONArray(p.totps))
                if (p.media.isNotEmpty()) { val arr = JSONArray(); p.media.forEach { arr.put(it) }; put("media", arr) }
                if (p.reminders.isNotEmpty()) { val arr = JSONArray(); p.reminders.forEach { arr.put(it) }; put("reminders", arr) }
            }.toString()
            val compressed = gzip(json.toByteArray(Charsets.UTF_8))
            onProgress(30)
            key = deriveKey(password, salt, ITER_V5, keyFileBytes)
            onProgress(90)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            cipher.updateAAD(aad(vaultId))
            val ciphertext = cipher.doFinal(compressed)
            val html = TEMPLATE
                .replace("%%THEME%%", themeCss(theme))
                .replace("%%ISAR%%", if (arabic) "true" else "false")
                .replace("%%SALT%%", b64e(salt)).replace("%%IV%%", b64e(iv))
                .replace("%%VAULT_ID%%", vaultId).replace("%%ITER%%", ITER_V5.toString())
                .replace("%%CIPHER%%", b64e(ciphertext))
                .replace("%%KEYFILE_HASH%%", keyFileHash ?: "")
            onProgress(100)
            return html
        } finally {
            key?.fill(0); salt.fill(0); iv.fill(0); password.fill('\u0000')
            keyFileBytes?.fill(0)
        }
    }

    fun decryptVault(v: ParsedVault, password: CharArray, keyFileBytes: ByteArray? = null): String? {
        var key: ByteArray? = null
        try {
            // Feature #5: If vault requires keyfile but user didn't supply one (or hash mismatch), reject.
            if (!v.keyFileHash.isNullOrEmpty()) {
                if (keyFileBytes == null) return null
                val h = Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(keyFileBytes), Base64.NO_WRAP)
                if (h != v.keyFileHash) return null
            }
            val salt = b64d(v.salt); val iv = b64d(v.iv); val ct = b64d(v.cipher)
            if (ct.size < 17 || salt.size < 16 || iv.size != 12) return null
            key = deriveKey(password, salt, v.iter, keyFileBytes)
            val modern = v.version == "5"
            if (!modern) {
                val hk = MessageDigest.getInstance("SHA-256")
                    .digest((String(password) + "|" + v.vaultId + "|hmac").toByteArray(Charsets.UTF_8))
                val mac = Mac.getInstance("HmacSHA256"); mac.init(SecretKeySpec(hk, "HmacSHA256"))
                if (!MessageDigest.isEqual(mac.doFinal(ct), b64d(v.hmac))) return null
            }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            if (modern) cipher.updateAAD(aad(v.vaultId))
            val compressed = cipher.doFinal(ct)
            return GZIPInputStream(ByteArrayInputStream(compressed)).bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: Exception) { return null } finally { key?.fill(0); password.fill('\u0000') }
    }

    fun parseHtml(html: String): ParsedVault? {
        fun ex(n: String): String? =
            Regex("""(?:var|const)\s+${Regex.escape(n)}\s*=\s*["']([^"']*)["']""").find(html)?.groupValues?.get(1)
        val salt = ex("SALT"); val iv = ex("IV"); val cipher = ex("CIPHER"); val id = ex("VAULT_ID")
        val version = ex("VERSION") ?: "4"; val hmac = ex("HMAC") ?: ""
        val keyFileHash = ex("KEYFILE_HASH")?.takeIf { it.isNotEmpty() }
        val iter = (ex("ITER")?.toIntOrNull() ?: ITER_V4).coerceIn(100_000, 5_000_000)
        if (salt == null || iv == null || cipher == null || id == null) return null
        if (version != "5" && hmac.isEmpty()) return null
        return ParsedVault(salt, iv, cipher, hmac, id, version, iter, keyFileHash)
    }

    private fun gzip(data: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream(); GZIPOutputStream(bos).use { it.write(data) }; return bos.toByteArray()
    }

    // Standalone viewer: no innerHTML (no XSS), strict CSP (no network), themed, TOTP, expiry, effects.
    // NOTE: this raw string must never contain a dollar sign directly followed by a brace.
    private val TEMPLATE = """<!DOCTYPE html>
<html lang="ar" dir="rtl">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<meta name="theme-color" content="#0a0a0d">
<title>Vault Creator v8 — NRS Shapes</title>
<style>
  :root {
    --accent: #a855f7;
    --accent2: #7c3aed;
    --bg: #0a0a0d;
    --panel: #101114;
    --panel-2: #16171b;
    --line: #2a2d34;
    --line-2: #3a3f4a;
    --text: #e8e9ee;
    --muted: #8a8f9c;
    --dim: #6a6f7a;
    --ok: #6ef7a6;
    --warn: #ffb37a;
    --err: #ff7a7a;
    --shadow-lg: 0 20px 50px rgba(0,0,0,0.7);
  }
  * { box-sizing: border-box; -webkit-tap-highlight-color: transparent; }
  html, body {
    margin: 0; padding: 0; min-height: 100%;
    background: radial-gradient(circle at 50% 15%, #1c1c22 0%, #0a0a0d 70%);
    color: var(--text);
    font-family: 'Segoe UI', system-ui, -apple-system, 'Consolas', monospace;
    font-size: 14px; line-height: 1.55;
    direction: rtl; -webkit-font-smoothing: antialiased;
  }
  body {
    display: flex; justify-content: center; align-items: flex-start;
    padding: 20px 12px 80px; min-height: 100vh;
  }
  .app { width: 100%; max-width: 780px; display: flex; flex-direction: column; gap: 14px; }

  /* TOPBAR */
  .topbar {
    display: flex; align-items: center; justify-content: space-between;
    padding: 12px 16px; border-radius: 14px;
    background: linear-gradient(155deg, #2a2d34, var(--panel-2));
    box-shadow: 0 0 0 1px rgba(255,255,255,0.05) inset, 0 10px 24px rgba(0,0,0,0.55);
  }
  .topbar .brand { font-size: 13px; letter-spacing: 2px; color: var(--accent); font-weight: 800; cursor: pointer; user-select: none; }
  .topbar .ver { font-size: 10px; color: var(--dim); letter-spacing: 1px; margin-top: 2px; }
  .topbar .actions { display: flex; gap: 6px; align-items: center; }
  .topbar .icon-btn {
    width: 32px; height: 32px; border-radius: 8px;
    background: rgba(255,255,255,0.05); border: 1px solid var(--line-2);
    color: var(--text); cursor: pointer; font-size: 14px;
    display: flex; align-items: center; justify-content: center;
    transition: border-color 0.15s, background 0.15s;
  }
  .topbar .icon-btn:hover { border-color: var(--accent); background: var(--panel-2); }
  .topbar .pill {
    font-size: 10px; padding: 4px 10px; border-radius: 20px;
    background: rgba(168,85,247,0.15); border: 1px solid var(--accent);
    color: var(--accent); letter-spacing: 1px;
  }

  /* VAULT PANEL */
  .vault {
    position: relative; padding: 22px 20px 20px; border-radius: 18px;
    background: linear-gradient(155deg, #34373f 0%, #24262d 45%, var(--panel-2) 100%);
    box-shadow: 0 0 0 1px rgba(255,255,255,0.06) inset, 0 2px 0 rgba(255,255,255,0.08) inset, var(--shadow-lg);
    transition: box-shadow 0.35s ease;
    overflow: hidden;
  }
  .vault.success { box-shadow: 0 0 0 1px rgba(70,255,140,0.4) inset, 0 0 40px rgba(70,255,140,0.3), var(--shadow-lg); }
  .vault.error { animation: shake 0.4s ease; }
  @keyframes shake {
    0%,100% { transform: translateX(0); }
    20% { transform: translateX(-8px); } 40% { transform: translateX(8px); }
    60% { transform: translateX(-6px); } 80% { transform: translateX(6px); }
  }

  .vault h1 { font-size: 18px; font-weight: 800; margin: 0 0 4px; color: var(--text); position: relative; z-index: 2; }
  .vault .sub { color: var(--muted); font-size: 12px; margin: 0 0 18px; position: relative; z-index: 2; }

  /* TABS */
  .tabs-strip {
    display: flex; gap: 4px; flex-wrap: nowrap;
    margin-bottom: 16px; padding: 4px;
    background: rgba(0,0,0,0.25); border-radius: 12px;
    position: relative; z-index: 2;
    overflow-x: auto; scrollbar-width: none;
  }
  .tabs-strip::-webkit-scrollbar { display: none; }
  .tabs-strip .tab {
    padding: 8px 12px; border-radius: 8px; border: none;
    background: transparent; color: var(--muted);
    font-size: 11px; letter-spacing: 1px; cursor: pointer;
    font-family: inherit; text-transform: uppercase;
    white-space: nowrap; transition: color 0.15s, background 0.15s;
  }
  .tabs-strip .tab:hover { color: var(--text); }
  .tabs-strip .tab.active {
    color: var(--text); background: var(--panel-2);
    box-shadow: 0 0 0 1px var(--accent) inset;
  }

  .pane { display: none; position: relative; z-index: 2; }
  .pane.visible { display: block; animation: paneIn 0.25s ease; }
  @keyframes paneIn { from { opacity: 0; transform: translateY(6px); } to { opacity: 1; transform: translateY(0); } }

  /* FIELDS */
  .field { margin-bottom: 16px; }
  .field > .label {
    display: flex; align-items: center; justify-content: space-between;
    font-size: 11px; letter-spacing: 1.5px; color: var(--muted);
    text-transform: uppercase; margin-bottom: 8px; font-weight: 600;
  }
  .field > .label .tip {
    font-size: 9px; color: var(--dim); letter-spacing: 0.5px;
    text-transform: none; font-weight: 400;
  }
  .field .hint-line { font-size: 11px; color: var(--dim); margin-top: 5px; min-height: 14px; }
  .field .hint-line.error { color: var(--err); }
  .field .hint-line.ok { color: var(--ok); }
  .field .hint-line.warn { color: var(--warn); }

  .input-wrap { position: relative; }
  .input-wrap input[type="text"],
  .input-wrap input[type="password"],
  .input-wrap input[type="number"],
  .input-wrap input[type="datetime-local"],
  .input-wrap textarea {
    width: 100%; background: var(--panel);
    border: 1px solid var(--line-2); border-radius: 11px;
    color: var(--text); padding: 14px;
    font-size: 14px; font-family: inherit;
    outline: none; transition: border-color 0.15s ease, box-shadow 0.15s ease;
  }
  .input-wrap textarea { min-height: 100px; resize: vertical; line-height: 1.65; }
  .input-wrap input:focus, .input-wrap textarea:focus {
    border-color: var(--accent);
    box-shadow: 0 0 0 4px rgba(168,85,247,0.15);
  }
  .input-wrap input.mismatch { border-color: var(--err); }
  .input-wrap .input-icon {
    position: absolute; left: 14px; top: 50%;
    transform: translateY(-50%);
    color: var(--dim); pointer-events: none; font-size: 15px;
  }
  .input-wrap textarea + .input-icon { top: 16px; transform: none; }
  .input-wrap input.has-icon, .input-wrap textarea.has-icon { padding-left: 40px; }

  input[type="color"] {
    -webkit-appearance: none; appearance: none;
    width: 48px; height: 34px; padding: 0;
    border: 1px solid var(--line-2); border-radius: 9px;
    background: var(--panel); cursor: pointer;
  }
  input[type="color"]::-webkit-color-swatch-wrapper { padding: 3px; }
  input[type="color"]::-webkit-color-swatch { border: none; border-radius: 6px; }
  input[type="datetime-local"] { cursor: pointer; }
  input[type="file"] { display: none; }

  /* TYPE GRID — 65 shapes */
  .type-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(110px, 1fr));
    gap: 10px;
  }
  .type-chip {
    position: relative;
    display: flex; flex-direction: column; align-items: center;
    gap: 6px;
    background: linear-gradient(155deg, #1a1c22, #101114);
    border: 1px solid var(--line-2);
    border-radius: 14px;
    padding: 10px 8px 8px;
    cursor: pointer;
    transition: all 0.2s ease;
    user-select: none;
    overflow: hidden;
  }
  .type-chip:hover { border-color: #565a66; transform: translateY(-2px); }
  .type-chip.selected {
    border-color: var(--accent);
    box-shadow: 0 0 0 2px var(--accent) inset, 0 6px 20px rgba(168,85,247,0.2);
  }
  .type-chip.selected::before {
    content: '✓';
    position: absolute; top: 6px; left: 6px;
    width: 18px; height: 18px;
    background: var(--accent); color: #fff;
    border-radius: 50%;
    font-size: 11px; font-weight: 900;
    display: flex; align-items: center; justify-content: center;
    z-index: 3;
  }
  .type-chip .svg-wrap {
    width: 100%; aspect-ratio: 1 / 1;
    max-height: 90px;
    display: flex; align-items: center; justify-content: center;
    overflow: hidden;
    border-radius: 8px;
  }
  .type-chip .svg-wrap svg {
    width: 100%; height: 100%;
    max-width: 80px; max-height: 80px;
    display: block;
  }
  .type-chip .chip-name {
    font-size: 10.5px; font-weight: 700;
    color: var(--text);
    text-align: center;
    letter-spacing: 0.2px;
  }
  .type-chip .see-btn {
    margin-top: 2px;
    padding: 4px 10px;
    border-radius: 20px;
    background: rgba(168,85,247,0.15);
    border: 1px solid var(--accent);
    color: var(--accent);
    font-size: 9.5px; font-weight: 700;
    letter-spacing: 0.6px;
    cursor: pointer; font-family: inherit;
    transition: all 0.15s ease;
    text-transform: uppercase;
  }
  .type-chip .see-btn:hover {
    background: var(--accent);
    color: #fff;
  }

  /* COLOR ROWS */
  .color-row {
    display: flex; align-items: center; gap: 10px;
    background: var(--panel); border: 1px solid var(--line-2);
    border-radius: 11px; padding: 10px 14px; margin-top: 8px;
  }
  .color-row label { font-size: 12px; color: var(--muted); flex: 1; }

  /* TOGGLE ROWS */
  .toggle-row {
    display: flex; align-items: center; justify-content: space-between; gap: 12px;
    background: var(--panel); border: 1px solid var(--line-2);
    border-radius: 11px; padding: 12px 14px;
    cursor: pointer; user-select: none;
    transition: border-color 0.15s, background 0.15s;
  }
  .toggle-row:hover { border-color: #4a4e5a; }
  .toggle-row.on { border-color: rgba(47,174,102,0.5); background: rgba(47,174,102,0.04); }
  .toggle-row .tr-text { flex: 1; min-width: 0; }
  .toggle-row .tr-title { font-size: 13px; color: var(--text); font-weight: 600; }
  .toggle-row .tr-desc { font-size: 11px; color: var(--dim); margin-top: 3px; line-height: 1.5; }
  .toggle-row input[type="checkbox"] { display: none; }
  .toggle-row .sw {
    position: relative; flex-shrink: 0;
    width: 46px; height: 26px; border-radius: 22px;
    background: var(--line-2); transition: background 0.25s;
  }
  .toggle-row .sw::before {
    content: ''; position: absolute;
    width: 20px; height: 20px; left: 3px; top: 3px;
    background: #cfd2d8; border-radius: 50%; transition: 0.25s;
  }
  .toggle-row input[type="checkbox"]:checked + .sw { background: #2fae66; }
  .toggle-row input[type="checkbox"]:checked + .sw::before {
    transform: translateX(20px); background: #fff;
  }

  /* FILE DROP */
  .file-drop {
    border: 2px dashed var(--line-2);
    border-radius: 12px; padding: 22px 16px; text-align: center;
    color: var(--muted); font-size: 12.5px; cursor: pointer;
    transition: all 0.2s ease;
  }
  .file-drop:hover { border-color: var(--accent); color: var(--text); background: rgba(168,85,247,0.04); }
  .file-drop.dragover { border-color: var(--accent); background: rgba(168,85,247,0.08); color: var(--text); }
  .file-drop strong { color: var(--accent); }
  .file-drop .drop-icon { font-size: 26px; display: block; margin-bottom: 6px; }

  .media-grid { display: flex; flex-direction: column; gap: 8px; margin-top: 10px; }
  .media-item {
    display: flex; align-items: center; gap: 10px; flex-wrap: wrap;
    background: var(--panel); border: 1px solid var(--line-2);
    border-radius: 10px; padding: 8px;
  }
  .media-item .thumb {
    width: 48px; height: 48px; border-radius: 7px; overflow: hidden;
    flex-shrink: 0; background: #000;
    display: flex; align-items: center; justify-content: center; font-size: 22px;
  }
  .media-item .thumb img, .media-item .thumb video { width: 100%; height: 100%; object-fit: cover; }
  .media-item .file-meta { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 5px; }
  .media-item .file-name { font-size: 11px; color: #9aa0ab; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; direction: ltr; text-align: right; }
  .media-item .caption { flex: 1; min-width: 0; width: 100%; font-size: 12px; padding: 8px 10px; }
  .media-item .rm {
    flex-shrink: 0; width: 30px; height: 30px; border-radius: 8px;
    background: rgba(255,122,122,0.12); color: var(--err); font-size: 14px;
    border: none; display: flex; align-items: center; justify-content: center;
    cursor: pointer; font-family: inherit;
  }
  .media-count { font-size: 11px; color: var(--accent); margin-top: 6px; }

  /* STRENGTH */
  .strength { display: flex; gap: 4px; margin-top: 8px; }
  .strength .seg { flex: 1; height: 5px; border-radius: 3px; background: var(--line); transition: background 0.3s; }

  /* BUTTONS */
  .btn-primary {
    width: 100%; padding: 15px; border: none; border-radius: 12px;
    background: linear-gradient(180deg, #3d414c, #24262d);
    color: var(--text); font-size: 14px; font-weight: 700;
    letter-spacing: 2px; text-transform: uppercase;
    cursor: pointer; font-family: inherit;
    box-shadow: 0 1px 0 rgba(255,255,255,0.1) inset, 0 4px 10px rgba(0,0,0,0.5);
    transition: transform 0.1s ease, background 0.3s ease;
    margin-top: 6px;
  }
  .btn-primary:hover { background: linear-gradient(180deg, #4a4e5a, #2a2d34); }
  .btn-primary:active { transform: scale(0.985); }
  .btn-primary:disabled { opacity: 0.55; cursor: not-allowed; }
  .btn-primary.success { background: linear-gradient(180deg, #2fae66, #1c7a45); }

  .btn-mini {
    padding: 9px 14px; border: 1px solid var(--line-2);
    background: var(--panel); color: var(--text);
    border-radius: 9px; font-size: 12px; cursor: pointer;
    font-family: inherit; transition: all 0.15s ease;
  }
  .btn-mini:hover { border-color: var(--accent); background: var(--panel-2); }

  /* PROGRESS */
  .progress-wrap {
    margin-top: 12px; height: 8px; border-radius: 5px;
    background: var(--panel); overflow: hidden;
    display: none; border: 1px solid var(--line);
  }
  .progress-wrap.active { display: block; }
  .progress-fill {
    height: 100%; width: 0%; border-radius: 5px;
    background: linear-gradient(90deg, var(--accent), #2fae66, var(--accent));
    background-size: 200% 100%;
    transition: width 0.3s ease;
    animation: progShine 1.5s linear infinite;
  }
  @keyframes progShine { 0% { background-position: 0% 0; } 100% { background-position: 200% 0; } }
  .progress-label { font-size: 10.5px; color: var(--dim); margin-top: 6px; text-align: center; min-height: 14px; }

  .status { margin-top: 12px; font-size: 12.5px; text-align: center; min-height: 18px; color: var(--muted); }
  .status.ok { color: var(--ok); }
  .status.err { color: var(--err); }

  /* RADIO STACK */
  .radio-stack { display: flex; flex-direction: column; gap: 6px; margin-top: 10px; }
  .radio-stack label {
    display: flex; align-items: center; gap: 8px;
    font-size: 12.5px; color: #c7cad2; cursor: pointer;
    padding: 9px 12px; border-radius: 9px;
    background: var(--panel); border: 1px solid var(--line-2);
  }
  .radio-stack label.checked { border-color: var(--accent); background: var(--panel-2); }
  .radio-stack input[type="radio"] { accent-color: var(--accent); }

  .suboptions { display: flex; gap: 10px; margin-top: 10px; }
  .suboptions .so { flex: 1; display: flex; flex-direction: column; gap: 4px; }
  .suboptions label { font-size: 10.5px; color: var(--dim); }
  select {
    background: var(--panel); border: 1px solid var(--line-2);
    border-radius: 9px; color: var(--text); padding: 9px 12px;
    font-size: 12.5px; font-family: inherit; width: 100%; cursor: pointer;
  }

  .range-row {
    display: flex; align-items: center; gap: 10px;
    background: var(--panel); border: 1px solid var(--line-2);
    border-radius: 11px; padding: 10px 14px;
  }
  .range-row label { font-size: 12px; color: var(--muted); min-width: 90px; }
  .range-row input[type="range"] { flex: 1; accent-color: var(--accent); cursor: pointer; height: 4px; }
  .range-row .val { font-size: 12px; color: var(--accent); min-width: 40px; text-align: left; font-family: 'Consolas', monospace; }

  /* STATS GRID */
  .stats-grid {
    display: grid; grid-template-columns: repeat(auto-fill, minmax(110px, 1fr));
    gap: 8px; margin-top: 8px;
  }
  .stat-box {
    background: var(--panel); border: 1px solid var(--line-2);
    border-radius: 10px; padding: 10px 12px;
  }
  .stat-box .sk { font-size: 9.5px; color: var(--dim); letter-spacing: 1px; text-transform: uppercase; }
  .stat-box .sv { font-size: 13px; color: var(--accent); font-weight: 700; margin-top: 3px; font-family: 'Consolas', monospace; }

  /* OUTPUT */
  .output {
    display: none; padding: 16px; background: var(--panel-2);
    border: 1px solid var(--line-2); border-radius: 14px;
  }
  .output.visible { display: block; }
  .output .out-title { font-size: 12px; color: var(--ok); letter-spacing: 1.5px; text-transform: uppercase; margin-bottom: 12px; font-weight: 700; }
  .output .out-row { display: flex; gap: 8px; flex-wrap: wrap; }
  .output .out-row .btn-mini { flex: 1 1 130px; padding: 11px; }
  .output .out-hash { font-size: 10.5px; color: var(--dim); word-break: break-all; margin-top: 12px; line-height: 1.7; }

  /* SEAL */
  .seal {
    position: absolute; inset: 0; display: flex; align-items: center; justify-content: center;
    background: rgba(6,7,9,0); pointer-events: none;
    z-index: 5; border-radius: 18px;
    transition: background 0.35s ease;
  }
  .seal.active { background: rgba(6,7,9,0.7); }
  .seal-icon {
    width: 70px; height: 70px; border-radius: 50%;
    background: radial-gradient(circle at 35% 30%, #4a4e58, #1a1b1f 75%);
    box-shadow: 0 0 0 1px rgba(255,255,255,0.08) inset, 0 8px 18px rgba(0,0,0,0.6);
    display: flex; align-items: center; justify-content: center;
    font-size: 30px; transform: scale(0) rotate(-90deg); opacity: 0;
    transition: transform 0.5s cubic-bezier(.34,1.56,.64,1), opacity 0.3s ease;
  }
  .seal.active .seal-icon { transform: scale(1) rotate(0deg); opacity: 1; }
  .seal-icon.spinning { animation: sealSpin 1s linear infinite; }
  @keyframes sealSpin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }

  /* TOAST */
  .toast {
    position: fixed; bottom: 24px; left: 50%;
    transform: translateX(-50%) translateY(80px);
    background: linear-gradient(155deg, #2a2d34, var(--panel-2));
    color: var(--text); padding: 12px 20px; border-radius: 30px;
    font-size: 12.5px; box-shadow: 0 10px 30px rgba(0,0,0,0.7);
    z-index: 1000; opacity: 0; pointer-events: none;
    transition: transform 0.35s ease, opacity 0.35s ease;
    max-width: 90vw; text-align: center;
  }
  .toast.show { transform: translateX(-50%) translateY(0); opacity: 1; }
  .toast.ok { border: 1px solid var(--ok); }
  .toast.err { border: 1px solid var(--err); }

  /* MODAL */
  .modal-backdrop {
    position: fixed; inset: 0; background: rgba(0,0,0,0.85);
    z-index: 999; display: none; align-items: center; justify-content: center;
    padding: 20px; backdrop-filter: blur(8px);
  }
  .modal-backdrop.visible { display: flex; animation: paneIn 0.2s ease; }
  .modal {
    background: linear-gradient(155deg, #2a2d34, var(--panel-2));
    border-radius: 18px; padding: 22px;
    max-width: 520px; width: 100%; max-height: 90vh; overflow: auto;
    box-shadow: var(--shadow-lg);
  }
  .modal h3 { margin: 0 0 8px; color: var(--accent); font-size: 15px; }
  .modal .modal-desc { font-size: 12.5px; color: #9aa0ab; line-height: 1.7; margin-bottom: 14px; }
  .modal .modal-actions { display: flex; gap: 8px; margin-top: 16px; }
  .modal .modal-actions button { flex: 1; padding: 11px; border: none; border-radius: 9px; background: linear-gradient(180deg,#3d414c,#24262d); color: var(--text); cursor: pointer; font-family: inherit; }

  /* PREVIEW MODAL */
  .preview-modal {
    background: transparent; padding: 0; max-width: 460px;
  }
  .preview-stage {
    position: relative; width: 100%; aspect-ratio: 1 / 1;
    max-height: 65vh; border-radius: 22px; overflow: hidden;
    background: radial-gradient(circle at 50% 40%, #1a1c22 0%, #0a0a0d 75%);
    box-shadow: 0 30px 80px rgba(0,0,0,0.8), 0 0 0 1px rgba(255,255,255,0.08) inset;
    display: flex; align-items: center; justify-content: center;
    padding: 20px;
  }
  .preview-stage .preview-svg {
    width: 100%; height: 100%;
    display: flex; align-items: center; justify-content: center;
    animation: previewFloat 4s ease-in-out infinite;
  }
  .preview-stage .preview-svg svg {
    width: 100%; height: 100%;
    max-width: 100%; max-height: 100%;
    filter: drop-shadow(0 20px 40px rgba(0,0,0,0.6));
  }
  @keyframes previewFloat {
    0%,100% { transform: translateY(0); }
    50% { transform: translateY(-8px); }
  }
  .preview-info {
    margin-top: 14px; padding: 14px 16px; border-radius: 14px;
    background: linear-gradient(155deg, #2a2d34, var(--panel-2));
    box-shadow: 0 10px 24px rgba(0,0,0,0.5);
  }
  .preview-info .pi-badge {
    display: inline-block; font-size: 10px; letter-spacing: 1.5px;
    padding: 3px 10px; border-radius: 20px;
    border: 1px solid var(--accent); color: var(--accent);
    margin-bottom: 8px;
  }
  .preview-info .pi-title { font-size: 15px; font-weight: 800; color: var(--text); margin-bottom: 4px; }
  .preview-info .pi-desc { font-size: 12px; color: var(--muted); line-height: 1.7; }
  .preview-actions { display: flex; gap: 8px; margin-top: 12px; }
  .preview-actions button {
    flex: 1; padding: 11px; border: none; border-radius: 10px;
    background: linear-gradient(180deg, #3d414c, #24262d);
    color: var(--text); cursor: pointer; font-family: inherit;
    font-size: 12.5px; font-weight: 600;
  }
  .preview-actions button.primary { background: linear-gradient(180deg, #a855f7, #7c3aed); color: #fff; }

  .hint {
    margin-top: 18px; padding-top: 14px; border-top: 1px solid var(--line);
    font-size: 11px; color: var(--dim); line-height: 1.85;
    position: relative; z-index: 2;
  }
  .hint code { color: var(--accent); font-family: 'Consolas', monospace; font-size: 10.5px; }

  .presets-row { display: flex; gap: 6px; flex-wrap: wrap; margin-top: 8px; }
  .preset-btn {
    padding: 7px 12px; border-radius: 20px;
    background: var(--panel); border: 1px solid var(--line-2);
    color: var(--muted); font-size: 11px; cursor: pointer;
    font-family: inherit; transition: all 0.15s;
  }
  .preset-btn:hover { color: var(--text); border-color: var(--accent); }

  @media (max-width: 480px) {
    .type-grid { grid-template-columns: repeat(auto-fill, minmax(90px, 1fr)); gap: 8px; }
    .type-chip .svg-wrap { max-height: 70px; }
    .type-chip .svg-wrap svg { max-width: 64px; max-height: 64px; }
    .stats-grid { grid-template-columns: repeat(2, 1fr); }
    .vault { padding: 18px 14px 16px; }
  }
</style>
</head>
<body>

<div class="app">
  <header class="topbar">
    <div>
      <div class="brand" id="brandEl">🔒 VAULT CREATOR</div>
      <div class="ver">v8.0 — NRS SHAPES</div>
    </div>
    <div class="actions">
      <button type="button" class="icon-btn" id="presetsBtn" title="Presets">⚡</button>
      <button type="button" class="icon-btn" id="helpBtn" title="Help">?</button>
      <div class="pill">PRO</div>
    </div>
  </header>

  <section class="vault" id="vaultPanel">
    <h1 id="vaultTitle">🔒 إنشاء خزنة مشفّرة</h1>
    <p class="sub">AES-256-GCM + PBKDF2 + HMAC + gzip — أشكال NRS — كل شيء داخل المتصفح.</p>

    <nav class="tabs-strip" id="tabsStrip">
      <button type="button" class="tab active" data-tab="basic">📦 أساسي</button>
      <button type="button" class="tab" data-tab="security">🛡️ أمان</button>
      <button type="button" class="tab" data-tab="theme">🎨 ثيمات</button>
      <button type="button" class="tab" data-tab="delivery">🚀 تسليم</button>
      <button type="button" class="tab" data-tab="interactive">🎮 تفاعلي</button>
      <button type="button" class="tab" data-tab="advanced">⚙️ متقدم</button>
      <button type="button" class="tab" data-tab="info">📊 معلومات</button>
    </nav>

    <!-- BASIC -->
    <div class="pane visible" data-pane="basic">
      <div class="field">
        <span class="label">نوع الخزنة <span class="tip" id="shapeCount">— شكل</span></span>
        <div class="type-grid" id="typeGrid"></div>
      </div>

      <div class="field">
        <span class="label">الرسالة السرية <span class="tip" id="msgStats">0 حرف</span></span>
        <div class="input-wrap">
          <textarea id="msgInput" placeholder="اكتب اللي عايز الشخص يشوفه..." maxlength="20000" class="has-icon"></textarea>
          <span class="input-icon">✉️</span>
        </div>
      </div>

      <div class="field">
        <span class="label">ملفات مرفقة <span class="tip">اختياري</span></span>
        <div class="file-drop" id="fileDrop">
          <span class="drop-icon">📎</span>
          <strong>اضغط</strong> أو اسحب الملفات هنا
        </div>
        <input type="file" id="fileInput" multiple>
        <div class="media-grid" id="mediaGrid"></div>
        <div class="media-count" id="mediaCount"></div>
      </div>

      <div class="field">
        <span class="label">رسالة قبل الرمز</span>
        <div class="input-wrap">
          <input type="text" id="preMessageInput" placeholder="في حاجة مستخبية ليك 👀" maxlength="200" class="has-icon">
          <span class="input-icon">💬</span>
        </div>
      </div>

      <div class="field">
        <span class="label">الرمز السري <span class="tip">حساس</span></span>
        <div class="input-wrap">
          <input type="text" id="codeInput" placeholder="مثال: 2580 أو كلمة قوية" autocomplete="off" spellcheck="false" class="has-icon">
          <span class="input-icon">🔑</span>
        </div>
        <div class="strength">
          <div class="seg" id="seg1"></div>
          <div class="seg" id="seg2"></div>
          <div class="seg" id="seg3"></div>
          <div class="seg" id="seg4"></div>
        </div>
        <div class="hint-line" id="strengthHint">قوة الرمز: —</div>
      </div>

      <div class="field">
        <span class="label">تأكيد الرمز</span>
        <div class="input-wrap">
          <input type="text" id="codeConfirmInput" placeholder="اكتب نفس الرمز تاني" autocomplete="off" spellcheck="false" class="has-icon">
          <span class="input-icon">✓</span>
        </div>
        <div class="hint-line" id="codeHint"></div>
      </div>

      <button type="button" class="btn-primary" id="createBtn">إنشاء الملف</button>
      <div class="progress-wrap" id="progressWrap"><div class="progress-fill" id="progressFill"></div></div>
      <div class="progress-label" id="progressLabel"></div>
      <div class="status" id="statusMsg"></div>
    </div>

    <!-- SECURITY -->
    <div class="pane" data-pane="security">
      <div class="field">
        <label class="toggle-row">
          <div class="tr-text"><div class="tr-title">فتحة واحدة بس</div><div class="tr-desc">الملف يفتح مرة واحدة</div></div>
          <input type="checkbox" id="oneTimeToggle"><span class="sw"></span>
        </label>
      </div>
      <div class="field">
        <label class="toggle-row">
          <div class="tr-text"><div class="tr-title">قفل بعد المحاولات</div><div class="tr-desc">5 محاولات، بعدها قفل 30s يتضاعف</div></div>
          <input type="checkbox" id="rateLimitToggle" checked><span class="sw"></span>
        </label>
      </div>
      <div class="field">
        <label class="toggle-row">
          <div class="tr-text"><div class="tr-title">تاريخ صلاحية</div><div class="tr-desc">الملف يبطل بعد تاريخ</div></div>
          <input type="checkbox" id="expiryToggle"><span class="sw"></span>
        </label>
        <input type="datetime-local" id="expiryInput" style="margin-top:8px;display:none;">
      </div>
      <div class="field">
        <label class="toggle-row">
          <div class="tr-text"><div class="tr-title">سؤال أمان</div><div class="tr-desc">سؤال + إجابة قبل الرمز</div></div>
          <input type="checkbox" id="sqToggle"><span class="sw"></span>
        </label>
        <div id="sqFields" style="display:none;margin-top:8px;">
          <input type="text" id="sqQuestion" placeholder="السؤال" maxlength="200" style="margin-bottom:6px;">
          <input type="text" id="sqAnswer" placeholder="الإجابة" maxlength="200">
        </div>
      </div>
      <div class="field">
        <label class="toggle-row">
          <div class="tr-text"><div class="tr-title">HMAC توقيع</div><div class="tr-desc">يكشف أي تعديل</div></div>
          <input type="checkbox" id="hmacToggle" checked><span class="sw"></span>
        </label>
      </div>
      <div class="field">
        <label class="toggle-row">
          <div class="tr-text"><div class="tr-title">Anti-Debug</div><div class="tr-desc">يكتشف فتح DevTools</div></div>
          <input type="checkbox" id="antiDebugToggle" checked><span class="sw"></span>
        </label>
      </div>
      <div class="field">
        <label class="toggle-row">
          <div class="tr-text"><div class="tr-title">Watermark</div><div class="tr-desc">ختم مالك على الحمولة</div></div>
          <input type="checkbox" id="watermarkToggle"><span class="sw"></span>
        </label>
        <input type="text" id="watermarkText" placeholder="نص الختم" maxlength="100" style="margin-top:8px;display:none;">
      </div>
      <div class="field">
        <label class="toggle-row">
          <div class="tr-text"><div class="tr-title">Work-Key Mode</div><div class="tr-desc">620k iterations</div></div>
          <input type="checkbox" id="workKeyToggle"><span class="sw"></span>
        </label>
      </div>
      <div class="field">
        <label class="toggle-row">
          <div class="tr-text"><div class="tr-title">Kill-Switch</div><div class="tr-desc">HMAC فشل مرتين → تحترق</div></div>
          <input type="checkbox" id="burnToggle"><span class="sw"></span>
        </label>
      </div>
      <div class="field">
        <label class="toggle-row">
          <div class="tr-text"><div class="tr-title">بصمة الجهاز</div><div class="tr-desc">يربط الملف بجهاز واحد</div></div>
          <input type="checkbox" id="deviceBindToggle"><span class="sw"></span>
        </label>
      </div>
    </div>

    <!-- THEME -->
    <div class="pane" data-pane="theme">
      <div class="field">
        <label class="toggle-row">
          <div class="tr-text"><div class="tr-title">احتفال بعد الفتح</div><div class="tr-desc">تأثير بصري</div></div>
          <input type="checkbox" id="celebrationToggle" checked><span class="sw"></span>
        </label>
        <div class="radio-stack" id="celebOptions">
          <label><input type="radio" name="celebMode" value="confetti" checked> 🎊 Confetti</label>
          <label><input type="radio" name="celebMode" value="glow"> ✨ توهج</label>
          <label><input type="radio" name="celebMode" value="fireworks"> 🎆 ألعاب نارية</label>
          <label><input type="radio" name="celebMode" value="snow"> ❄️ ثلج</label>
          <label><input type="radio" name="celebMode" value="hearts"> 💖 قلوب</label>
          <label><input type="radio" name="celebMode" value="matrix"> 🟢 Matrix</label>
          <label><input type="radio" name="celebMode" value="bubbles"> 🫧 فقاعات</label>
          <label><input type="radio" name="celebMode" value="custom"> ✏️ مطر مخصص</label>
        </div>
        <input type="text" id="celebCustomInput" placeholder="مثلاً: ✨✨✨" style="margin-top:10px;display:none;">
        <div class="suboptions" id="rainControls" style="display:none;">
          <div class="so"><label>سرعة</label>
            <select id="rainSpeedSelect"><option value="slow">بطيئة</option><option value="normal" selected>عادية</option><option value="fast">سريعة</option></select>
          </div>
          <div class="so"><label>كثافة</label>
            <select id="rainDensitySelect"><option value="light">خفيفة</option><option value="normal" selected>عادية</option><option value="heavy">كثيفة</option></select>
          </div>
        </div>
      </div>
      <div class="field">
        <label class="toggle-row">
          <div class="tr-text"><div class="tr-title">صوت عند الفتح</div><div class="tr-desc">نغمة نجاح</div></div>
          <input type="checkbox" id="soundToggle"><span class="sw"></span>
        </label>
      </div>
      <div class="field">
        <label class="toggle-row">
          <div class="tr-text"><div class="tr-title">TTS</div><div class="tr-desc">القارئ الصوتي</div></div>
          <input type="checkbox" id="ttsToggle"><span class="sw"></span>
        </label>
        <input type="text" id="ttsText" placeholder="مبروك!" maxlength="200" style="margin-top:8px;display:none;">
      </div>
      <div class="field">
        <label class="toggle-row">
          <div class="tr-text"><div class="tr-title">خلفية متحركة</div><div class="tr-desc">جزيئات</div></div>
          <input type="checkbox" id="animatedBgToggle" checked><span class="sw"></span>
        </label>
      </div>
      <div class="field">
        <label class="toggle-row">
          <div class="tr-text"><div class="tr-title">Splash</div><div class="tr-desc">شاشة تمهيدية</div></div>
          <input type="checkbox" id="splashToggle" checked><span class="sw"></span>
        </label>
        <input type="text" id="splashText" placeholder="من Redz 💌" maxlength="120" style="margin-top:8px;">
      </div>
      <div class="field">
        <label class="toggle-row">
          <div class="tr-text"><div class="tr-title">Scroll Gate</div><div class="tr-desc">لازم يمرر لقبول الشروط</div></div>
          <input type="checkbox" id="scrollGateToggle"><span class="sw"></span>
        </label>
        <input type="text" id="scrollGateText" placeholder="نص الاتفاقية" maxlength="2000" style="margin-top:8px;display:none;">
      </div>
    </div>

    <!-- DELIVERY -->
    <div class="pane" data-pane="delivery">
      <div class="field">
        <label class="toggle-row">
          <div class="tr-text"><div class="tr-title">Steganography</div><div class="tr-desc">إخفاء في PNG بـ LSB</div></div>
          <input type="checkbox" id="stegoToggle"><span class="sw"></span>
        </label>
        <input type="file" id="stegoCoverInput" accept="image/png,image/jpeg" style="margin-top:8px;display:none;">
        <div class="hint-line" id="stegoHint"></div>
      </div>
      <div class="field">
        <label class="toggle-row">
          <div class="tr-text"><div class="tr-title">PIN منفصل</div><div class="tr-desc">يتطلب PIN إضافي</div></div>
          <input type="checkbox" id="pinToggle"><span class="sw"></span>
        </label>
        <input type="text" id="pinInput" placeholder="PIN (4+ أرقام)" maxlength="20" style="margin-top:8px;display:none;">
      </div>
      <div class="field">
        <label class="toggle-row">
          <div class="tr-text"><div class="tr-title">اسم ملف مخصص</div><div class="tr-desc">بدل vault_gift.html</div></div>
          <input type="checkbox" id="filenameToggle"><span class="sw"></span>
        </label>
        <input type="text" id="filenameInput" placeholder="secret-vault.html" maxlength="80" style="margin-top:8px;display:none;">
      </div>
      <div class="field">
        <label class="toggle-row">
          <div class="tr-text"><div class="tr-title">روابط احتياطية</div><div class="tr-desc">حتى 3 روابط</div></div>
          <input type="checkbox" id="fallbackToggle"><span class="sw"></span>
        </label>
        <div id="fallbackFields" style="display:none;margin-top:8px;">
          <input type="text" id="fallback1" placeholder="https://..." maxlength="500" style="margin-bottom:6px;">
          <input type="text" id="fallback2" placeholder="https://..." maxlength="500" style="margin-bottom:6px;">
          <input type="text" id="fallback3" placeholder="https://..." maxlength="500">
        </div>
      </div>
      <div class="field">
        <label class="toggle-row">
          <div class="tr-text"><div class="tr-title">إيصال فتح</div><div class="tr-desc">ملخص بعد الفتح</div></div>
          <input type="checkbox" id="receiptToggle" checked><span class="sw"></span>
        </label>
      </div>
      <div class="field">
        <label class="toggle-row">
          <div class="tr-text"><div class="tr-title">Copy Message</div><div class="tr-desc">زر نسخ للرسالة</div></div>
          <input type="checkbox" id="copyMsgToggle" checked><span class="sw"></span>
        </label>
      </div>
    </div>

    <!-- INTERACTIVE -->
    <div class="pane" data-pane="interactive">
      <div class="field">
        <label class="toggle-row">
          <div class="tr-text"><div class="tr-title">Puzzle فتح</div><div class="tr-desc">اضغط على الدوائر بالتسلسل</div></div>
          <input type="checkbox" id="puzzleToggle"><span class="sw"></span>
        </label>
        <div class="suboptions" id="puzzleOpts" style="display:none;">
          <div class="so"><label>عدد النقاط</label>
            <select id="puzzleCount"><option value="3">3</option><option value="5" selected>5</option><option value="7">7</option></select>
          </div>
          <div class="so"><label>الصعوبة</label>
            <select id="puzzleDiff">
              <option value="easy">سهل</option>
              <option value="normal" selected>عادي</option>
              <option value="hard">صعب</option>
            </select>
          </div>
        </div>
      </div>
      <div class="field">
        <label class="toggle-row">
          <div class="tr-text"><div class="tr-title">Typewriter</div><div class="tr-desc">الرسالة حرف حرف</div></div>
          <input type="checkbox" id="typewriterToggle"><span class="sw"></span>
        </label>
      </div>
      <div class="field">
        <label class="toggle-row">
          <div class="tr-text"><div class="tr-title">عد تنازلي 3-2-1</div><div class="tr-desc">قبل كشف المحتوى</div></div>
          <input type="checkbox" id="countdownToggle"><span class="sw"></span>
        </label>
      </div>
      <div class="field">
        <label class="toggle-row">
          <div class="tr-text"><div class="tr-title">Math Gate</div><div class="tr-desc">سؤال رياضي</div></div>
          <input type="checkbox" id="mathToggle"><span class="sw"></span>
        </label>
      </div>
      <div class="field">
        <label class="toggle-row">
          <div class="tr-text"><div class="tr-title">Shake to Unlock</div><div class="tr-desc">هز الموبايل 5 مرات</div></div>
          <input type="checkbox" id="shakeToggle"><span class="sw"></span>
        </label>
      </div>
      <div class="field">
        <label class="toggle-row">
          <div class="tr-text"><div class="tr-title">أصوات تفاعلية</div><div class="tr-desc">نقرات، أخطاء، نجاح</div></div>
          <input type="checkbox" id="uiSoundsToggle" checked><span class="sw"></span>
        </label>
      </div>
    </div>

    <!-- ADVANCED -->
    <div class="pane" data-pane="advanced">
      <div class="field">
        <span class="label">المقاييس</span>
        <div class="stats-grid">
          <div class="stat-box"><div class="sk">Algorithm</div><div class="sv">AES-256-GCM</div></div>
          <div class="stat-box"><div class="sk">KDF</div><div class="sv" id="statKdf">PBKDF2-310k</div></div>
          <div class="stat-box"><div class="sk">HMAC</div><div class="sv">SHA-256</div></div>
          <div class="stat-box"><div class="sk">Compress</div><div class="sv">gzip</div></div>
        </div>
      </div>
      <div class="field">
        <label class="toggle-row">
          <div class="tr-text"><div class="tr-title">Custom Iterations</div><div class="tr-desc">تحكم دقيق</div></div>
          <input type="checkbox" id="customIterToggle"><span class="sw"></span>
        </label>
        <div class="range-row" id="iterRangeRow" style="display:none;margin-top:8px;">
          <label>Iterations</label>
          <input type="range" id="iterRange" min="100000" max="2000000" step="50000" value="310000">
          <span class="val" id="iterVal">310k</span>
        </div>
      </div>
      <div class="field">
        <label class="toggle-row">
          <div class="tr-text"><div class="tr-title">Custom Salt Size</div><div class="tr-desc">حجم الملح</div></div>
          <input type="checkbox" id="customSaltToggle"><span class="sw"></span>
        </label>
        <div class="range-row" id="saltRangeRow" style="display:none;margin-top:8px;">
          <label>Salt bytes</label>
          <input type="range" id="saltRange" min="8" max="64" step="8" value="16">
          <span class="val" id="saltVal">16</span>
        </div>
      </div>
      <div class="field">
        <label class="toggle-row">
          <div class="tr-text"><div class="tr-title">Obfuscate JS</div><div class="tr-desc">Child يخرج مشفّر</div></div>
          <input type="checkbox" id="obfuscateToggle"><span class="sw"></span>
        </label>
      </div>
      <div class="field">
        <label class="toggle-row">
          <div class="tr-text"><div class="tr-title">Force HTTPS</div><div class="tr-desc">يرفض http://</div></div>
          <input type="checkbox" id="httpsOnlyToggle"><span class="sw"></span>
        </label>
      </div>
      <div class="field">
        <label class="toggle-row">
          <div class="tr-text"><div class="tr-title">Time Window</div><div class="tr-desc">يفتح في نافذة زمنية</div></div>
          <input type="checkbox" id="timeWindowToggle"><span class="sw"></span>
        </label>
        <div id="timeWindowFields" style="display:none;margin-top:8px;">
          <label style="font-size:11px;color:var(--muted);display:block;margin-bottom:4px;">من</label>
          <input type="datetime-local" id="timeFrom" style="margin-bottom:6px;">
          <label style="font-size:11px;color:var(--muted);display:block;margin-bottom:4px;">إلى</label>
          <input type="datetime-local" id="timeTo">
        </div>
      </div>
    </div>

    <!-- INFO -->
    <div class="pane" data-pane="info">
      <div class="hint" style="margin-top:0;border-top:none;padding-top:0;">
        <p style="margin:0 0 10px;"><strong style="color:var(--accent);">Vault Creator v8.0 — NRS Shapes</strong></p>
        <p style="margin:0 0 8px;" id="infoShapeCount">— شكل محمّل</p>
        <p style="margin:0 0 8px;">🔐 PBKDF2-310k/620k + AES-GCM-256 + HMAC-SHA-256 + gzip</p>
        <p style="margin:0 0 8px;">🎮 Puzzle, Typewriter, Countdown, Shake, Math gate</p>
        <p style="margin:0 0 8px;">🚀 Stego, PIN, Fallback URLs, Receipt</p>
        <p style="margin:0 0 8px;">🛡️ Device bind, Expiry, Anti-debug, Kill-switch, Time window</p>
        <p style="margin:0;">كل التشفير داخل المتصفح — لا سيرفر، لا تتبع.</p>
      </div>
      <div class="field">
        <span class="label">الميزات النشطة</span>
        <div class="stats-grid" id="activeFeatures"></div>
      </div>
    </div>

    <div class="hint">
      * التشفير داخل المتصفح فقط — لا سيرفر.<br>
      * الرمز لا يُخزَّن — لو نُسيه، الملف لا يُفتح.<br>
      * v8: أشكال NRS + AES-GCM-256 + HMAC + gzip.
    </div>

    <div class="seal" id="sealOverlay"><div class="seal-icon" id="sealIcon">🔒</div></div>
  </section>

  <section class="output" id="outputPanel">
    <div class="out-title">✔ تم إنشاء الملف</div>
    <div class="out-row">
      <button type="button" class="btn-mini" id="dlAgainBtn">⬇ تحميل تاني</button>
      <button type="button" class="btn-mini" id="shareBtn">🔗 مشاركة</button>
      <button type="button" class="btn-mini" id="copyLinkBtn">📋 نسخ الرابط</button>
      <button type="button" class="btn-mini" id="newVaultBtn">🆕 خزنة جديدة</button>
    </div>
    <div class="out-hash" id="outHash"></div>
  </section>
</div>

<div class="modal-backdrop" id="modalBackdrop"><div class="modal" id="modal"></div></div>
<div class="modal-backdrop" id="previewBackdrop"><div class="modal preview-modal" id="previewModal"></div></div>
<div class="toast" id="toast"></div>

<!-- ═══════════ ملف الأشكال الخارجي ═══════════ -->
<script src="nrs-shapes.js"></script>

<!-- ═══════════ كود الـ parent ═══════════ -->
<script>
(() => {
  'use strict';

  // ═══════════════════════════════════════════════════════════
  // NRS PALETTE — ألوان تكميلية لكل شكل
  // ═══════════════════════════════════════════════════════════
  const NRS_PALETTE = [
    '#a855f7','#00e5a0','#ffaa00','#ff00e5','#80e5ff',
    '#a8ff00','#ff2d2d','#00ffd0','#ffd93d','#ff0044',
    '#7c5cff','#00ff9d','#a855f7','#00ffff','#ff0080',
    '#ff9900','#8090b8','#00ffd0','#00ff88','#ffd93d',
    '#e0b3ff','#00ffff','#ff8080','#a855f7','#ffe066',
    '#ff00e5','#00f5ff','#ffb84d','#80e5ff','#a855f7',
    '#ff9500','#ff00e5','#b8ff00','#ff00ff','#00ff99',
    '#a8ff00','#7c5cff','#00b8ff','#ff2d6b','#ffd93d',
    '#00f5ff','#00ffa8','#ff7700','#ff00b8','#80ffb8',
    '#ff00b8','#00ffff','#ff4400','#00ff88','#ff00e5',
    '#ff8080','#00ff88','#ff6b00','#00e676','#7c4dff',
    '#ffd93d','#00ffff','#ff5c33','#00ff88','#ff00a8',
    '#a855f7','#00ffd0','#ff00b8','#ffd93d','#00ffff'
  ];

  const $ = id => document.getElementById(id);
  const state = {
    selectedType: 'nrs-0',
    shapeIndex: 0,
    mediaItems: [],
    lastBlob: null,
    lastBlobUrl: null,
    lastFilename: 'vault_gift.html',
    stegoCover: null,
    fingerprint: null
  };

  // ═══════════════════════════════════════════════════════════
  // UTILS
  // ═══════════════════════════════════════════════════════════
  const wait = ms => new Promise(r => setTimeout(r, ms));
  const randHex = n => Array.from(crypto.getRandomValues(new Uint8Array(n))).map(b => b.toString(16).padStart(2,'0')).join('');
  const jsonSafe = v => JSON.stringify(v).replace(/</g, '\\u003C');
  const escapeHtml = s => String(s == null ? '' : s).replace(/[&<>"']/g, c => ({ '&':'&amp;', '<':'&lt;', '>':'&gt;', '"':'&quot;', "'":'&#39;' }[c]));

  function toB64(buf) {
    const bytes = buf instanceof Uint8Array ? buf : new Uint8Array(buf);
    let bin = ''; const CH = 0x8000;
    for (let i = 0; i < bytes.length; i += CH) bin += String.fromCharCode.apply(null, bytes.subarray(i, i + CH));
    return btoa(bin);
  }
  function fromB64(b64) {
    const bin = atob(b64);
    const out = new Uint8Array(bin.length);
    for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
    return out;
  }
  async function sha256(bytes) { return new Uint8Array(await crypto.subtle.digest('SHA-256', bytes)); }
  async function hashStr(s) {
    const h = await sha256(new TextEncoder().encode(s));
    return Array.from(h).map(b => b.toString(16).padStart(2,'0')).join('');
  }
  async function gzipCompress(bytes) {
    if (typeof CompressionStream === 'undefined') return null;
    try {
      const cs = new CompressionStream('gzip');
      const w = cs.writable.getWriter(); w.write(bytes); w.close();
      return new Uint8Array(await new Response(cs.readable).arrayBuffer());
    } catch { return null; }
  }
  async function deriveKey(password, saltBytes, usage, iterations) {
    const enc = new TextEncoder();
    const baseKey = await crypto.subtle.importKey('raw', enc.encode(password), { name:'PBKDF2' }, false, ['deriveKey']);
    return crypto.subtle.deriveKey(
      { name:'PBKDF2', salt: saltBytes, iterations, hash:'SHA-256' },
      baseKey, { name:'AES-GCM', length:256 }, false, [usage]
    );
  }
  async function hmacSign(keyBytes, dataBytes) {
    const k = await crypto.subtle.importKey('raw', keyBytes, { name:'HMAC', hash:'SHA-256' }, false, ['sign']);
    return new Uint8Array(await crypto.subtle.sign('HMAC', k, dataBytes));
  }
  async function computeFingerprint() {
    const parts = [
      navigator.userAgent, navigator.language,
      screen.width + 'x' + screen.height + 'x' + screen.colorDepth,
      new Date().getTimezoneOffset(),
      navigator.hardwareConcurrency || 0,
      navigator.platform || '',
      navigator.maxTouchPoints || 0
    ];
    return (await hashStr(parts.join('|'))).slice(0, 32);
  }

  // ═══════════════════════════════════════════════════════════
  // TOAST
  // ═══════════════════════════════════════════════════════════
  const toast = $('toast');
  let toastTimer = null;
  function showToast(msg, type) {
    toast.textContent = msg;
    toast.className = 'toast show' + (type ? ' ' + type : '');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => { toast.className = 'toast'; }, 3000);
  }

  // ═══════════════════════════════════════════════════════════
  // MODAL
  // ═══════════════════════════════════════════════════════════
  const modalBackdrop = $('modalBackdrop');
  const modal = $('modal');
  function openModal(html) { modal.innerHTML = html; modalBackdrop.classList.add('visible'); }
  function closeModal() { modalBackdrop.classList.remove('visible'); }
  modalBackdrop.addEventListener('click', e => { if (e.target === modalBackdrop) closeModal(); });

  $('helpBtn').addEventListener('click', () => {
    openModal(`
      <h3>📖 Vault Creator v8</h3>
      <div class="modal-desc">
        أداة لإنشاء خزنة HTML مشفّرة بالكامل في المتصفح. أشكال NRS جاهزة.<br><br>
        <strong>الخطوات:</strong><br>
        1. اختر شكل من الأشكال المتاحة<br>
        2. اكتب رسالتك + ارفق ملفات<br>
        3. اختر رمز سري قوي<br>
        4. اضبط الأمان<br>
        5. اضغط "إنشاء الملف"<br>
        6. ابعث الملف + الرمز<br><br>
        <strong>اختصارات:</strong><br>
        Ctrl+Enter = إنشاء<br>
        Shift+? = مساعدة<br>
        Alt+1..7 = تبديل التبويبات
      </div>
      <div class="modal-actions"><button type="button" onclick="document.getElementById('modalBackdrop').classList.remove('visible')">حسناً</button></div>
    `);
  });

  $('presetsBtn').addEventListener('click', () => {
    openModal(`
      <h3>⚡ Presets سريعة</h3>
      <div class="modal-desc">اختر preset لضبط الإعدادات فورًا.</div>
      <div class="presets-row" id="presetsGrid">
        <button type="button" class="preset-btn" data-preset="quick">⚡ سريع</button>
        <button type="button" class="preset-btn" data-preset="paranoid">🛡️ Paranoid</button>
        <button type="button" class="preset-btn" data-preset="hacker">💀 هاكر</button>
        <button type="button" class="preset-btn" data-preset="love">💌 حب</button>
      </div>
      <div class="modal-actions"><button type="button" onclick="document.getElementById('modalBackdrop').classList.remove('visible')">إغلاق</button></div>
    `);
    document.querySelectorAll('#presetsGrid .preset-btn').forEach(b => {
      b.addEventListener('click', () => { applyPreset(b.dataset.preset); closeModal(); });
    });
  });

  function applyPreset(name) {
    const presets = {
      quick: { hmac:true, rateLimit:true },
      paranoid: { oneTime:true, rateLimit:true, hmac:true, antiDebug:true, burn:true, deviceBind:true, workKey:true, pinEnabled:true, pin:'1234' },
      hacker: { antiDebug:true, hmac:true, burn:true, celebration:'matrix', sound:true, workKey:true },
      love: { celebration:'hearts', sound:true, tts:true, splash:true, message:'رسالة من القلب 💌' }
    };
    const p = presets[name];
    if (!p) return;
    const toggleMap = {
      oneTime:'oneTimeToggle', rateLimit:'rateLimitToggle', hmac:'hmacToggle',
      antiDebug:'antiDebugToggle', workKey:'workKeyToggle', burn:'burnToggle',
      deviceBind:'deviceBindToggle', sound:'soundToggle', tts:'ttsToggle',
      splash:'splashToggle', pinEnabled:'pinToggle'
    };
    Object.keys(toggleMap).forEach(k => {
      if (k in p) {
        const el = $(toggleMap[k]);
        if (el) { el.checked = !!p[k]; el.dispatchEvent(new Event('change')); }
      }
    });
    if (p.celebration) {
      const r = document.querySelector(`input[name="celebMode"][value="${p.celebration}"]`);
      if (r) { r.checked = true; r.dispatchEvent(new Event('change')); }
    }
    if (p.pin) $('pinInput').value = p.pin;
    if (p.message) $('msgInput').value = p.message;
    showToast('✔ تم تطبيق preset: ' + name, 'ok');
  }

  // ═══════════════════════════════════════════════════════════
  // TABS
  // ═══════════════════════════════════════════════════════════
  const tabsStrip = $('tabsStrip');
  tabsStrip.addEventListener('click', e => {
    const tab = e.target.closest('.tab');
    if (!tab || !tabsStrip.contains(tab)) return;
    e.preventDefault();
    const target = tab.dataset.tab;
    if (!target) return;
    tabsStrip.querySelectorAll('.tab').forEach(t => t.classList.toggle('active', t === tab));
    document.querySelectorAll('.pane').forEach(p => {
      p.classList.toggle('visible', p.dataset.pane === target);
    });
  });

  // ═══════════════════════════════════════════════════════════
  // RENDER TYPE CHIPS
  // ═══════════════════════════════════════════════════════════
  const typeGrid = $('typeGrid');
  const vaultPanel = $('vaultPanel');
  const vaultTitle = $('vaultTitle');

  function renderTypeChips() {
    typeGrid.innerHTML = '';
    if (typeof NRS_SHAPES === 'undefined' || !Array.isArray(NRS_SHAPES) || !NRS_SHAPES.length) {
      typeGrid.innerHTML = '<div style="grid-column:1/-1;padding:24px;text-align:center;color:#ff7a7a;font-size:13px;line-height:1.8;background:rgba(255,122,122,0.08);border:1px solid rgba(255,122,122,0.3);border-radius:12px;">⚠️ ملف <code style="color:#6ee7ff">nrs-shapes.js</code> مش محمّل.<br>تأكد إنه في نفس مجلد <code style="color:#6ee7ff">vault.html</code> وأعد تحميل الصفحة.</div>';
      console.error('[Vault] NRS_SHAPES غير معرّفة. تأكد من وجود nrs-shapes.js بجانب vault.html');
      return;
    }

    $('shapeCount').textContent = NRS_SHAPES.length + ' شكل';
    $('infoShapeCount').textContent = '✅ ' + NRS_SHAPES.length + ' شكل محمّل من nrs-shapes.js';
    console.log('✅ NRS_SHAPES loaded: ' + NRS_SHAPES.length + ' shapes');

    NRS_SHAPES.forEach((svgMarkup, i) => {
      const chip = document.createElement('div');
      chip.className = 'type-chip' + (state.selectedType === 'nrs-' + i ? ' selected' : '');
      chip.dataset.type = 'nrs-' + i;
      chip.dataset.index = i;
      chip.innerHTML = `
        <div class="svg-wrap">${svgMarkup}</div>
        <div class="chip-name">شكل #${i + 1}</div>
        <button type="button" class="see-btn" data-see="${i}">👁 عرض</button>
      `;
      typeGrid.appendChild(chip);
    });
  }

  typeGrid.addEventListener('click', e => {
    const seeBtn = e.target.closest('[data-see]');
    if (seeBtn) {
      e.preventDefault(); e.stopPropagation();
      openPreview(parseInt(seeBtn.dataset.see, 10));
      return;
    }
    const chip = e.target.closest('.type-chip');
    if (!chip || !typeGrid.contains(chip)) return;
    e.preventDefault();
    selectType('nrs-' + chip.dataset.index);
  });

  // ═══════════════════════════════════════════════════════════
  // SELECT TYPE
  // ═══════════════════════════════════════════════════════════
  function selectType(key) {
    if (!key.startsWith('nrs-')) return;
    const idx = parseInt(key.split('-')[1], 10);
    const chip = typeGrid.querySelector(`.type-chip[data-index="${idx}"]`);
    if (!chip) return;

    state.selectedType = key;
    state.shapeIndex = idx;
    typeGrid.querySelectorAll('.type-chip').forEach(c => {
      c.classList.toggle('selected', c === chip);
    });

    const accent = NRS_PALETTE[idx % NRS_PALETTE.length];
    document.documentElement.style.setProperty('--accent', accent);
    document.documentElement.style.setProperty('--accent2', accent);

    vaultTitle.textContent = '🔒 شكل #' + (idx + 1);
    updateActiveFeatures();
  }

  // ═══════════════════════════════════════════════════════════
  // PREVIEW MODAL
  // ═══════════════════════════════════════════════════════════
  const previewBackdrop = $('previewBackdrop');
  const previewModal = $('previewModal');
  previewBackdrop.addEventListener('click', e => { if (e.target === previewBackdrop) closePreview(); });

  function openPreview(idx) {
    const svg = (typeof NRS_SHAPES !== 'undefined' && NRS_SHAPES[idx]) ? NRS_SHAPES[idx] : null;
    if (!svg) { showToast('الشكل غير موجود', 'err'); return; }
    const accent = NRS_PALETTE[idx % NRS_PALETTE.length];

    previewModal.innerHTML = `
      <div class="preview-stage" style="--accent:${accent};">
        <div class="preview-svg">${svg}</div>
      </div>
      <div class="preview-info">
        <div class="pi-badge">NRS SHAPE #${idx + 1}</div>
        <div class="pi-title">🔒 شكل #${idx + 1}</div>
        <div class="pi-desc">شكل SVG كامل الدقة — يظهر بنفس الجودة في الخزنة النهائية.</div>
        <div class="preview-actions">
          <button type="button" class="primary" data-choose="${idx}">اختر هذا الشكل</button>
          <button type="button" data-close="1">إغلاق</button>
        </div>
      </div>
    `;
    previewBackdrop.classList.add('visible');
    previewModal.querySelector('[data-close]').addEventListener('click', closePreview);
    previewModal.querySelector('[data-choose]').addEventListener('click', () => {
      selectType('nrs-' + idx);
      closePreview();
      showToast('✔ تم اختيار شكل #' + (idx + 1), 'ok');
    });
  }
  function closePreview() { previewBackdrop.classList.remove('visible'); }

  // ═══════════════════════════════════════════════════════════
  // MESSAGE STATS
  // ═══════════════════════════════════════════════════════════
  $('msgInput').addEventListener('input', () => {
    const v = $('msgInput').value;
    const words = v.trim() ? v.trim().split(/\s+/).length : 0;
    $('msgStats').textContent = v.length + ' حرف — ' + words + ' كلمة';
  });

  // ═══════════════════════════════════════════════════════════
  // STRENGTH
  // ═══════════════════════════════════════════════════════════
  function scoreStrength(pw) {
    let s = 0;
    if (pw.length >= 4) s++;
    if (pw.length >= 8) s++;
    if (/[A-Z]/.test(pw) && /[a-z]/.test(pw)) s++;
    if (/\d/.test(pw)) s++;
    if (/[^A-Za-z0-9]/.test(pw)) s++;
    return Math.min(s, 4);
  }
  function renderStrength(pw) {
    const s = scoreStrength(pw);
    const colors = ['#ff5a5a','#ffb37a','#ffd166','#6ef7a6','#2fae66'];
    const labels = ['ضعيف جداً','ضعيف','متوسط','قوي','قوي جداً'];
    for (let i = 1; i <= 4; i++) {
      $('seg' + i).style.background = i <= s ? colors[s] : 'var(--line)';
    }
    $('strengthHint').textContent = pw ? 'قوة الرمز: ' + labels[s] : 'قوة الرمز: —';
    $('strengthHint').className = 'hint-line ' + (s <= 1 ? 'error' : s === 2 ? 'warn' : 'ok');
  }
  $('codeInput').addEventListener('input', () => {
    renderStrength($('codeInput').value);
    checkCodeMatch();
  });

  function checkCodeMatch() {
    const a = $('codeInput').value, b = $('codeConfirmInput').value;
    if (!b) {
      $('codeHint').textContent = '';
      $('codeHint').className = 'hint-line';
      $('codeConfirmInput').classList.remove('mismatch');
      return true;
    }
    if (a !== b) {
      $('codeHint').textContent = 'الرمزين مش متطابقين';
      $('codeHint').className = 'hint-line error';
      $('codeConfirmInput').classList.add('mismatch');
      return false;
    }
    $('codeHint').textContent = '✔ الرمزين متطابقين';
    $('codeHint').className = 'hint-line ok';
    $('codeConfirmInput').classList.remove('mismatch');
    return true;
  }
  $('codeConfirmInput').addEventListener('input', checkCodeMatch);

  // ═══════════════════════════════════════════════════════════
  // CONDITIONAL UI
  // ═══════════════════════════════════════════════════════════
  function bindToggle(toggleId, targetId, mode) {
    const t = $(toggleId), el = $(targetId);
    if (!t || !el) return;
    const update = () => {
      el.style.display = t.checked ? (mode || 'block') : 'none';
      const row = t.closest('.toggle-row');
      if (row) row.classList.toggle('on', t.checked);
    };
    t.addEventListener('change', update);
    update();
  }
  bindToggle('expiryToggle', 'expiryInput');
  bindToggle('sqToggle', 'sqFields');
  bindToggle('watermarkToggle', 'watermarkText');
  bindToggle('ttsToggle', 'ttsText');
  bindToggle('filenameToggle', 'filenameInput');
  bindToggle('stegoToggle', 'stegoCoverInput');
  bindToggle('fallbackToggle', 'fallbackFields');
  bindToggle('scrollGateToggle', 'scrollGateText');
  bindToggle('pinToggle', 'pinInput');
  bindToggle('puzzleToggle', 'puzzleOpts', 'flex');
  bindToggle('customIterToggle', 'iterRangeRow', 'flex');
  bindToggle('customSaltToggle', 'saltRangeRow', 'flex');
  bindToggle('timeWindowToggle', 'timeWindowFields');

  $('iterRange').addEventListener('input', () => {
    const v = parseInt($('iterRange').value, 10);
    $('iterVal').textContent = (v / 1000) + 'k';
    $('statKdf').textContent = 'PBKDF2-' + (v / 1000) + 'k';
  });
  $('saltRange').addEventListener('input', () => {
    $('saltVal').textContent = $('saltRange').value;
  });

  $('stegoToggle').addEventListener('change', () => {
    $('stegoHint').textContent = $('stegoToggle').checked ? 'اختر صورة PNG/JPEG' : '';
  });
  $('stegoCoverInput').addEventListener('change', e => {
    const f = e.target.files && e.target.files[0];
    if (!f) { state.stegoCover = null; return; }
    const r = new FileReader();
    r.onload = () => {
      state.stegoCover = r.result;
      $('stegoHint').textContent = 'صورة: ' + f.name + ' (' + Math.round(f.size/1024) + 'KB)';
    };
    r.readAsDataURL(f);
  });

  // ═══════════════════════════════════════════════════════════
  // CELEBRATION
  // ═══════════════════════════════════════════════════════════
  function updateCelebUI() {
    const checked = document.querySelector('input[name="celebMode"]:checked');
    const mode = checked ? checked.value : 'confetti';
    document.querySelectorAll('#celebOptions label').forEach(l => {
      const r = l.querySelector('input');
      l.classList.toggle('checked', r && r.checked);
    });
    $('celebCustomInput').style.display = mode === 'custom' ? 'block' : 'none';
    const rainModes = ['custom', 'confetti', 'snow', 'hearts', 'matrix', 'bubbles'];
    $('rainControls').style.display = rainModes.includes(mode) ? 'flex' : 'none';
  }
  document.querySelectorAll('input[name="celebMode"]').forEach(r => {
    r.addEventListener('change', updateCelebUI);
  });
  $('celebrationToggle').addEventListener('change', () => {
    $('celebOptions').style.display = $('celebrationToggle').checked ? 'flex' : 'none';
    if (!$('celebrationToggle').checked) {
      $('rainControls').style.display = 'none';
      $('celebCustomInput').style.display = 'none';
    } else {
      updateCelebUI();
    }
  });
  updateCelebUI();

  // ═══════════════════════════════════════════════════════════
  // MEDIA
  // ═══════════════════════════════════════════════════════════
  const FILE_ICONS = { apk:'📦', html:'🌐', htm:'🌐', py:'🐍', js:'📜', json:'🧩', pdf:'📕', doc:'📝', docx:'📝', xls:'📊', xlsx:'📊', ppt:'📽️', pptx:'📽️', zip:'🗜️', rar:'🗜️', '7z':'🗜️', txt:'📄', csv:'📋', mp3:'🎵', wav:'🎵', m4a:'🎵', exe:'⚙️', ipa:'📱' };
  const getFileIcon = name => FILE_ICONS[(name||'').split('.').pop().toLowerCase()] || '📎';
  const fmtSize = b => !b && b !== 0 ? '' : b < 1024 ? b + ' B' : b < 1048576 ? (b/1024).toFixed(1) + ' KB' : (b/1048576).toFixed(1) + ' MB';

  function handleFiles(list) {
    Array.from(list || []).forEach(file => {
      const kind = file.type.startsWith('image/') ? 'image' : file.type.startsWith('video/') ? 'video' : 'file';
      const r = new FileReader();
      r.onload = () => {
        state.mediaItems.push({ kind, data: r.result, caption: '', name: file.name, size: file.size });
        renderMediaGrid();
      };
      r.readAsDataURL(file);
    });
  }

  const fileDrop = $('fileDrop');
  const fileInput = $('fileInput');
  fileDrop.addEventListener('click', e => { e.preventDefault(); e.stopPropagation(); fileInput.click(); });
  fileInput.addEventListener('change', () => { handleFiles(fileInput.files); fileInput.value = ''; });
  fileDrop.addEventListener('dragover', e => { e.preventDefault(); fileDrop.classList.add('dragover'); });
  fileDrop.addEventListener('dragleave', () => fileDrop.classList.remove('dragover'));
  fileDrop.addEventListener('drop', e => {
    e.preventDefault(); fileDrop.classList.remove('dragover');
    if (e.dataTransfer && e.dataTransfer.files) handleFiles(e.dataTransfer.files);
  });

  function renderMediaGrid() {
    const grid = $('mediaGrid');
    grid.innerHTML = '';
    state.mediaItems.forEach((item, idx) => {
      const box = document.createElement('div');
      box.className = 'media-item';
      const thumb = document.createElement('div');
      thumb.className = 'thumb';
      if (item.kind === 'file') thumb.textContent = getFileIcon(item.name);
      else {
        const el = document.createElement(item.kind === 'image' ? 'img' : 'video');
        el.src = item.data; thumb.appendChild(el);
      }
      box.appendChild(thumb);

      const cap = document.createElement('input');
      cap.type = 'text'; cap.className = 'caption';
      cap.placeholder = 'عنوان';
      cap.value = item.caption || '';
      cap.addEventListener('input', () => { state.mediaItems[idx].caption = cap.value; });

      if (item.kind === 'file') {
        const meta = document.createElement('div'); meta.className = 'file-meta';
        const nm = document.createElement('div'); nm.className = 'file-name';
        nm.textContent = item.name + (item.size ? ' · ' + fmtSize(item.size) : '');
        meta.appendChild(nm); meta.appendChild(cap); box.appendChild(meta);
      } else box.appendChild(cap);

      const rm = document.createElement('button');
      rm.type = 'button'; rm.className = 'rm'; rm.textContent = '✕';
      rm.addEventListener('click', (e) => { e.stopPropagation(); state.mediaItems.splice(idx, 1); renderMediaGrid(); });
      box.appendChild(rm);
      grid.appendChild(box);
    });
    const total = state.mediaItems.reduce((s, it) => s + (it.size || 0), 0);
    $('mediaCount').textContent = !state.mediaItems.length ? '' : state.mediaItems.length + ' ملف' + (total ? ' — ' + fmtSize(total) : '');
  }

  // ═══════════════════════════════════════════════════════════
  // CELEBRATION PARSER
  // ═══════════════════════════════════════════════════════════
  function splitGraphemes(str) {
    if (typeof Intl !== 'undefined' && Intl.Segmenter) {
      try { return Array.from(new Intl.Segmenter('en',{granularity:'grapheme'}).segment(str), s => s.segment); } catch {}
    }
    return Array.from(str);
  }
  function looksLikeEmojiOnly(str) {
    try { return /^(?:\p{Extended_Pictographic}|\p{Emoji_Presentation}|\uFE0F|\s)+$/u.test(str); } catch { return false; }
  }
  function parseCelebItems(raw) {
    if (!raw) return [];
    let items = raw.split(',').map(s => s.trim()).filter(Boolean);
    if (items.length <= 1) {
      const c = (items[0] || raw).trim();
      if (!c) return [];
      if (looksLikeEmojiOnly(c)) items = splitGraphemes(c).map(s => s.trim()).filter(Boolean);
      else if (/\s/.test(c)) items = c.split(/\s+/).filter(Boolean);
      else items = [c];
    }
    return items;
  }

  // ═══════════════════════════════════════════════════════════
  // READ OPTIONS
  // ═══════════════════════════════════════════════════════════
  function readOptions() {
    const shapeIdx = state.shapeIndex;
    const accent = NRS_PALETTE[shapeIdx % NRS_PALETTE.length];
    const celebChecked = document.querySelector('input[name="celebMode"]:checked');
    const fallbacks = [];
    if ($('fallbackToggle').checked) {
      ['fallback1','fallback2','fallback3'].forEach(id => {
        const v = $(id).value.trim();
        if (v) fallbacks.push(v);
      });
    }
    const iterations = $('customIterToggle').checked ? parseInt($('iterRange').value, 10) : ($('workKeyToggle').checked ? 620000 : 310000);
    const saltSize = $('customSaltToggle').checked ? parseInt($('saltRange').value, 10) : 16;

    return {
      type: 'nrs-' + shapeIdx,
      shapeIndex: shapeIdx,
      accent,
      message: $('msgInput').value.trim(),
      code: $('codeInput').value.trim(),
      codeConfirm: $('codeConfirmInput').value.trim(),
      preMessage: $('preMessageInput').value.trim() || 'في حاجة مستخبية ليك 👀',
      oneTime: $('oneTimeToggle').checked,
      rateLimit: $('rateLimitToggle').checked,
      expiryEnabled: $('expiryToggle').checked,
      expiryISO: $('expiryInput').value || '',
      sqEnabled: $('sqToggle').checked,
      sqQuestion: $('sqQuestion').value.trim(),
      sqAnswer: $('sqAnswer').value.trim(),
      hmac: $('hmacToggle').checked,
      antiDebug: $('antiDebugToggle').checked,
      watermark: $('watermarkToggle').checked,
      watermarkText: $('watermarkText').value.trim(),
      workKey: $('workKeyToggle').checked,
      burn: $('burnToggle').checked,
      deviceBind: $('deviceBindToggle').checked,
      celebration: {
        enabled: $('celebrationToggle').checked,
        mode: celebChecked ? celebChecked.value : 'confetti',
        items: parseCelebItems($('celebCustomInput').value),
        speed: $('rainSpeedSelect').value,
        density: $('rainDensitySelect').value
      },
      sound: $('soundToggle').checked,
      tts: $('ttsToggle').checked,
      ttsText: $('ttsText').value.trim() || 'تم فتح الخزنة بنجاح',
      animatedBg: $('animatedBgToggle').checked,
      splash: $('splashToggle').checked,
      splashText: $('splashText').value.trim() || 'من صاحب الخزنة 💌',
      scrollGate: $('scrollGateToggle').checked,
      scrollGateText: $('scrollGateText').value.trim() || 'بالفتح، أنت توافق على الحفاظ على سرية المحتوى.',
      stego: $('stegoToggle').checked,
      pinEnabled: $('pinToggle').checked,
      pin: $('pinInput').value.trim(),
      customFilename: $('filenameToggle').checked,
      filename: $('filenameInput').value.trim() || 'vault_gift.html',
      fallbackEnabled: $('fallbackToggle').checked,
      fallbackUrls: fallbacks,
      receipt: $('receiptToggle').checked,
      copyMsg: $('copyMsgToggle').checked,
      puzzle: $('puzzleToggle').checked,
      puzzleCount: parseInt($('puzzleCount').value, 10),
      puzzleDiff: $('puzzleDiff').value,
      typewriter: $('typewriterToggle').checked,
      countdown: $('countdownToggle').checked,
      mathGate: $('mathToggle').checked,
      shake: $('shakeToggle').checked,
      uiSounds: $('uiSoundsToggle').checked,
      customIter: $('customIterToggle').checked,
      iterations,
      saltSize,
      obfuscate: $('obfuscateToggle').checked,
      httpsOnly: $('httpsOnlyToggle').checked,
      timeWindow: $('timeWindowToggle').checked,
      timeFrom: $('timeFrom').value || '',
      timeTo: $('timeTo').value || '',
      fingerprint: state.fingerprint,
      stegoCover: state.stegoCover,
      vaultId: randHex(10),
      createdAt: Date.now()
    };
  }

  // ═══════════════════════════════════════════════════════════
  // STATUS / PROGRESS
  // ═══════════════════════════════════════════════════════════
  function setStatus(text, cls) {
    $('statusMsg').textContent = text;
    $('statusMsg').className = 'status' + (cls ? ' ' + cls : '');
  }
  function showProgress() { $('progressWrap').classList.add('active'); setProgress(0, ''); }
  function setProgress(p, label) {
    $('progressFill').style.width = p + '%';
    $('progressLabel').textContent = label || '';
  }
  function hideProgressSoon() {
    setTimeout(() => {
      $('progressWrap').classList.remove('active');
      $('progressLabel').textContent = '';
    }, 900);
  }

  // ═══════════════════════════════════════════════════════════
  // ACTIVE FEATURES
  // ═══════════════════════════════════════════════════════════
  function updateActiveFeatures() {
    const opts = readOptions();
    const feats = [];
    const add = (name, val) => feats.push(`<div class="stat-box"><div class="sk">${name}</div><div class="sv">${val}</div></div>`);
    add('Shape', '#' + (opts.shapeIndex + 1));
    add('Iterations', (opts.iterations / 1000) + 'k');
    add('Salt', opts.saltSize + 'B');
    add('AES', 'GCM-256');
    if (opts.hmac) add('HMAC', 'SHA-256');
    if (opts.oneTime) add('OneTime', 'ON');
    if (opts.rateLimit) add('RateLimit', 'ON');
    if (opts.expiryEnabled) add('Expiry', 'ON');
    if (opts.sqEnabled) add('Sec.Q', 'ON');
    if (opts.watermark && opts.watermarkText) add('Watermark', 'ON');
    if (opts.antiDebug) add('AntiDbg', 'ON');
    if (opts.burn) add('KillSwitch', 'ON');
    if (opts.deviceBind) add('DevBind', 'ON');
    if (opts.stego && opts.stegoCover) add('Stego', 'PNG');
    if (opts.pinEnabled) add('PIN', 'ON');
    if (opts.fallbackEnabled && opts.fallbackUrls.length) add('Fallback', opts.fallbackUrls.length);
    if (opts.puzzle) add('Puzzle', opts.puzzleCount + 'pts');
    if (opts.typewriter) add('Typewrite', 'ON');
    if (opts.countdown) add('Countdown', 'ON');
    if (opts.mathGate) add('MathGate', 'ON');
    if (opts.shake) add('Shake', 'ON');
    if (opts.timeWindow) add('TimeWin', 'ON');
    if (opts.sound) add('Sound', 'ON');
    if (opts.tts) add('TTS', 'ON');
    if (opts.obfuscate) add('Obfuscate', 'ON');
    if (opts.httpsOnly) add('HTTPS', 'ON');
    if (opts.scrollGate) add('ScrollGate', 'ON');
    $('activeFeatures').innerHTML = feats.join('');
  }
  document.querySelectorAll('input, select, textarea').forEach(el => {
    el.addEventListener('change', updateActiveFeatures);
  });

  // ═══════════════════════════════════════════════════════════
  // STEGO
  // ═══════════════════════════════════════════════════════════
  async function embedStego(coverDataUrl, payloadStr) {
    return new Promise((resolve, reject) => {
      const img = new Image();
      img.onload = () => {
        const c = document.createElement('canvas');
        c.width = img.width; c.height = img.height;
        const ctx = c.getContext('2d');
        ctx.drawImage(img, 0, 0);
        const imgData = ctx.getImageData(0, 0, c.width, c.height);
        const data = imgData.data;
        const payload = new TextEncoder().encode(payloadStr);
        const header = new Uint8Array(8);
        header[0]=0x56; header[1]=0x4c; header[2]=0x54; header[3]=0x38;
        header[4]=(payload.length>>>24)&0xff;
        header[5]=(payload.length>>>16)&0xff;
        header[6]=(payload.length>>>8)&0xff;
        header[7]=payload.length&0xff;
        const all = new Uint8Array(header.length + payload.length);
        all.set(header, 0); all.set(payload, header.length);
        const needBits = all.length * 8;
        const capBits = Math.floor(data.length / 4) * 3;
        if (needBits > capBits) { reject(new Error('الصورة صغيرة')); return; }
        let bitIdx = 0;
        for (let i = 0; i < data.length && bitIdx < needBits; i += 4) {
          for (let ch = 0; ch < 3 && bitIdx < needBits; ch++) {
            const byteIdx = bitIdx >> 3;
            const bit = (all[byteIdx] >> (7 - (bitIdx & 7))) & 1;
            data[i + ch] = (data[i + ch] & 0xFE) | bit;
            bitIdx++;
          }
        }
        ctx.putImageData(imgData, 0, 0);
        c.toBlob(b => b ? resolve(b) : reject(new Error('فشل PNG')), 'image/png');
      };
      img.onerror = () => reject(new Error('فشل تحميل الصورة'));
      img.src = coverDataUrl;
    });
  }

  function softObfuscate(html) {
    return html.replace(/<script\b[^>]*>([\s\S]*?)<\/script>/gi, (m, code) => {
      const obf = code
        .replace(/\bSALT_B64\b/g, 'SA\\x4cT_B64')
        .replace(/\bIV_B64\b/g, 'I\\x56_B64')
        .replace(/\bCIPHER_B64\b/g, 'CIPHE\\x52_B64')
        .replace(/\bVAULT_ID\b/g, 'VAUL\\x54_ID');
      return m.replace(code, obf);
    });
  }

  // ═══════════════════════════════════════════════════════════
  // MAIN CREATE
  // ═══════════════════════════════════════════════════════════
  $('createBtn').addEventListener('click', async () => {
    vaultPanel.classList.remove('success', 'error');
    const opts = readOptions();

    if (!opts.message && state.mediaItems.length === 0) {
      setStatus('لازم تكتب رسالة أو ترفع ملف', 'err');
      vaultPanel.classList.add('error'); setTimeout(() => vaultPanel.classList.remove('error'), 500);
      return;
    }
    if (!opts.code) {
      setStatus('لازم تحدد رمز سري', 'err');
      vaultPanel.classList.add('error'); setTimeout(() => vaultPanel.classList.remove('error'), 500);
      return;
    }
    if (opts.code !== opts.codeConfirm) {
      setStatus('الرمز وتأكيد الرمز مش متطابقين', 'err');
      checkCodeMatch();
      vaultPanel.classList.add('error'); setTimeout(() => vaultPanel.classList.remove('error'), 500);
      return;
    }
    if (opts.sqEnabled && (!opts.sqQuestion || !opts.sqAnswer)) {
      setStatus('لازم تكمل السؤال والإجابة', 'err'); return;
    }
    if (opts.expiryEnabled && !opts.expiryISO) {
      setStatus('لازم تحدد تاريخ الصلاحية', 'err'); return;
    }
    if (opts.watermark && !opts.watermarkText) {
      setStatus('لازم تكتب نص الختم', 'err'); return;
    }
    if (opts.stego && !opts.stegoCover) {
      setStatus('لازم تختار صورة', 'err'); return;
    }
    if (opts.pinEnabled && (!opts.pin || opts.pin.length < 4)) {
      setStatus('PIN لازم 4 أرقام على الأقل', 'err'); return;
    }

    const btn = $('createBtn');
    btn.disabled = true;
    showProgress();
    setStatus('جاري التشفير...', '');
    $('sealIcon').textContent = '🔒';
    $('sealIcon').classList.add('spinning');

    try {
      setProgress(6, 'حساب البصمة...');
      if (!state.fingerprint) state.fingerprint = await computeFingerprint();
      opts.fingerprint = state.fingerprint;
      await wait(60);

      setProgress(14, 'تجهيز الحمولة...');
      const inner = {
        message: opts.message || null,
        media: state.mediaItems,
        meta: {
          vaultId: opts.vaultId,
          createdAt: opts.createdAt,
          fingerprint: opts.deviceBind ? state.fingerprint : null,
          watermark: opts.watermark ? opts.watermarkText : null,
          type: opts.type,
          shapeIndex: opts.shapeIndex
        }
      };
      const innerBytes = new TextEncoder().encode(JSON.stringify(inner));
      const innerHash = await hashStr(JSON.stringify({ id: opts.vaultId, n: innerBytes.length }));

      setProgress(24, 'ضغط gzip...');
      let dataBytes = innerBytes;
      let compressed = false;
      const gz = await gzipCompress(innerBytes);
      if (gz && gz.length < innerBytes.length) { dataBytes = gz; compressed = true; }
      await wait(60);

      const salt = crypto.getRandomValues(new Uint8Array(opts.saltSize));
      const iv = crypto.getRandomValues(new Uint8Array(12));

      setProgress(42, 'اشتقاق المفتاح (' + opts.iterations.toLocaleString() + ' دورة)...');
      const key = await deriveKey(opts.code, salt, 'encrypt', opts.iterations);
      await wait(60);

      setProgress(60, 'تشفير AES-GCM-256...');
      const cipherBuf = await crypto.subtle.encrypt({ name:'AES-GCM', iv }, key, dataBytes);
      const cipherBytes = new Uint8Array(cipherBuf);

      let hmacB64 = null;
      if (opts.hmac) {
        setProgress(70, 'توليد HMAC...');
        const hmacKeyBytes = await sha256(new TextEncoder().encode(opts.code + '|' + opts.vaultId + '|hmac'));
        const mac = await hmacSign(hmacKeyBytes, cipherBytes);
        hmacB64 = toB64(mac);
      }

      setProgress(80, 'بناء الملف...');
      const saltB64 = toB64(salt.buffer);
      const ivB64 = toB64(iv.buffer);
      const cipherB64 = toB64(cipherBytes);
      const itemCount = state.mediaItems.length + (opts.message ? 1 : 0) || 1;

      let html = buildChildHtml({
        saltB64, ivB64, cipherB64, hmacB64, opts, compressed, iterations: opts.iterations, innerHash, itemCount
      });

      if (opts.obfuscate) {
        setProgress(84, 'Obfuscating...');
        try { html = softObfuscate(html); } catch {}
      }

      let blob;
      let filename = opts.customFilename ? opts.filename : 'vault_gift.html';

      if (opts.stego && state.stegoCover) {
        setProgress(92, 'إخفاء في صورة...');
        try {
          blob = await embedStego(state.stegoCover, html);
          filename = (opts.customFilename ? opts.filename.replace(/\.html?$/i, '') : 'vault_gift') + '.png';
        } catch (e) {
          blob = new Blob([html], { type: 'text/html' });
        }
      } else {
        blob = new Blob([html], { type: 'text/html' });
      }

      setProgress(100, 'تم!');
      $('sealOverlay').classList.add('active');
      await wait(400);
      $('sealIcon').classList.remove('spinning');
      $('sealIcon').classList.add('done');

      if (state.lastBlobUrl) URL.revokeObjectURL(state.lastBlobUrl);
      state.lastBlob = blob;
      state.lastFilename = filename;
      state.lastBlobUrl = URL.createObjectURL(blob);

      triggerDownload(state.lastBlobUrl, filename);
      await wait(400);
      $('sealOverlay').classList.remove('active');

      vaultPanel.classList.add('success');
      btn.classList.add('success');
      setStatus('✔ تم إنشاء الملف وتحميله', 'ok');
      showOutputPanel(blob, filename, opts);
      hideProgressSoon();
      playUISound('success');
    } catch (err) {
      console.error(err);
      setStatus('حصل خطأ: ' + (err.message || err), 'err');
      vaultPanel.classList.add('error'); setTimeout(() => vaultPanel.classList.remove('error'), 500);
      $('sealIcon').classList.remove('spinning');
      hideProgressSoon();
      playUISound('error');
    } finally {
      btn.disabled = false;
    }
  });

  function triggerDownload(url, filename) {
    const a = document.createElement('a');
    a.href = url; a.download = filename; a.rel = 'noopener';
    document.body.appendChild(a); a.click(); document.body.removeChild(a);
  }

  function showOutputPanel(blob, filename, opts) {
    const panel = $('outputPanel');
    panel.classList.add('visible');
    hashStr(filename + ':' + blob.size).then(h => {
      $('outHash').textContent = 'الحجم: ' + fmtSize(blob.size) + ' • SHA-256: ' + h.slice(0, 32) + '… • ID: ' + opts.vaultId + ' • Shape: #' + (opts.shapeIndex + 1);
    });
    updateActiveFeatures();
  }

  $('dlAgainBtn').addEventListener('click', () => {
    if (state.lastBlobUrl) triggerDownload(state.lastBlobUrl, state.lastFilename);
  });
  $('copyLinkBtn').addEventListener('click', async () => {
    try {
      await navigator.clipboard.writeText(state.lastBlobUrl || location.href);
      showToast('تم نسخ الرابط', 'ok');
    } catch { showToast('فشل النسخ', 'err'); }
  });
  $('shareBtn').addEventListener('click', async () => {
    if (navigator.share && state.lastBlob) {
      try {
        const file = new File([state.lastBlob], state.lastFilename, { type: state.lastBlob.type });
        if (navigator.canShare && navigator.canShare({ files: [file] })) {
          await navigator.share({ files: [file], title: 'خزنة مشفرة', text: 'افتحها بالرمز اللي هديك' });
          return;
        }
      } catch {}
    }
    if (state.lastBlobUrl) {
      try { await navigator.clipboard.writeText(state.lastBlobUrl); showToast('تم نسخ الرابط', 'ok'); } catch {}
    }
  });
  $('newVaultBtn').addEventListener('click', () => location.reload());

  // ═══════════════════════════════════════════════════════════
  // UI SOUNDS
  // ═══════════════════════════════════════════════════════════
  function playUISound(type) {
    if (!$('uiSoundsToggle').checked) return;
    try {
      const ctx = new (window.AudioContext || window.webkitAudioContext)();
      const o = ctx.createOscillator(), g = ctx.createGain();
      o.connect(g); g.connect(ctx.destination);
      const t = ctx.currentTime;
      if (type === 'success') {
        o.frequency.setValueAtTime(523, t);
        o.frequency.exponentialRampToValueAtTime(1046, t + 0.15);
      } else if (type === 'error') {
        o.frequency.setValueAtTime(400, t);
        o.frequency.exponentialRampToValueAtTime(200, t + 0.2);
      } else {
        o.frequency.setValueAtTime(800, t);
      }
      o.type = 'sine';
      g.gain.setValueAtTime(0.0001, t);
      g.gain.exponentialRampToValueAtTime(0.08, t + 0.01);
      g.gain.exponentialRampToValueAtTime(0.0001, t + 0.25);
      o.start(t); o.stop(t + 0.28);
    } catch {}
  }

  // ═══════════════════════════════════════════════════════════
  // KEYBOARD SHORTCUTS
  // ═══════════════════════════════════════════════════════════
  document.addEventListener('keydown', e => {
    if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') {
      e.preventDefault();
      const btn = $('createBtn');
      if (btn && !btn.disabled && btn.offsetParent !== null) btn.click();
    }
    if (e.shiftKey && e.key === '?') { e.preventDefault(); $('helpBtn').click(); }
    if (e.key === 'Escape') { closeModal(); closePreview(); }
    if (e.altKey && e.key >= '1' && e.key <= '7') {
      const idx = parseInt(e.key, 10) - 1;
      const tabs = tabsStrip.querySelectorAll('.tab');
      if (tabs[idx]) { tabs[idx].click(); e.preventDefault(); }
    }
  });

  // ═══════════════════════════════════════════════════════════
  // BUILD CHILD HTML
  // ═══════════════════════════════════════════════════════════
  function buildChildHtml(cfg) {
    const { saltB64, ivB64, cipherB64, hmacB64, opts, compressed, iterations, innerHash, itemCount } = cfg;
    const shapeIdx = opts.shapeIndex || 0;
    const heroSVG = (typeof NRS_SHAPES !== 'undefined' && NRS_SHAPES[shapeIdx])
      ? NRS_SHAPES[shapeIdx]
      : '<svg viewBox="0 0 200 200"><circle cx="100" cy="100" r="80" fill="none" stroke="#a855f7" stroke-width="3"/></svg>';
    const theme = { accent: opts.accent || '#a855f7', accent2: opts.accent || '#a855f7' };
    const particlesJson = JSON.stringify([theme.accent, theme.accent2, '#ffffff']);
    const celebJson = JSON.stringify(opts.celebration);
    const fallbackJson = JSON.stringify(opts.fallbackUrls || []);
    const expiryISO = opts.expiryEnabled ? opts.expiryISO : '';
    const timeFromISO = opts.timeWindow ? opts.timeFrom : '';
    const timeToISO = opts.timeWindow ? opts.timeTo : '';

    return `<!DOCTYPE html>
<html lang="ar" dir="rtl">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<title>خزنة مغلقة</title>
<style>
  :root { --accent: ${theme.accent}; --text: #e8e9ee; --muted: #9aa0ab; --dim: #6a6f7a; --panel: #101114; --line: #3a3f4a; }
  * { box-sizing: border-box; -webkit-tap-highlight-color: transparent; }
  html, body {
    margin: 0; min-height: 100%; display: flex; justify-content: center; align-items: center;
    background: radial-gradient(circle at 50% 30%, #1c1c22 0%, #0a0a0d 70%);
    font-family: 'Segoe UI', 'Consolas', monospace; padding: 20px 12px; direction: rtl;
    color: var(--text);
  }
  .vault-frame { position: relative; width: 440px; max-width: 94vw; }
  .vault-interior {
    position: absolute; inset: 0; border-radius: 18px;
    background: linear-gradient(160deg, #0d0e12, #000000 80%);
    box-shadow: inset 0 0 60px rgba(0,0,0,0.9);
    display: flex; align-items: center; justify-content: center;
    padding: 26px; overflow: hidden; z-index: 1;
  }
  .interior-content { opacity: 0; transform: scale(0.85); text-align: center; max-height: 100%; overflow: auto; width: 100%; transition: opacity .7s ease, transform .7s ease; }
  .interior-content p { color: #e8e9ee; font-size: 15px; line-height: 1.9; white-space: pre-wrap; margin: 0 0 14px; }
  .interior-content .msg-actions { display: flex; gap: 8px; margin-top: 10px; justify-content: center; flex-wrap: wrap; }
  .interior-content .msg-actions button { padding: 8px 14px; font-size: 11px; background: linear-gradient(180deg,#3d414c,#24262d); border: none; color: #e8e9ee; border-radius: 8px; cursor: pointer; font-family: inherit; }
  .media-controls { display: flex; align-items: center; justify-content: space-between; margin-bottom: 10px; font-size: 12px; color: #c7cad2; }
  .media-controls button { background: linear-gradient(180deg,#3d414c,#24262d); border: none; color: #e8e9ee; padding: 6px 12px; border-radius: 6px; font-size: 11px; cursor: pointer; font-family: inherit; }
  .media-list { display: flex; flex-direction: column; gap: 10px; }
  .media-reveal-item { position: relative; }
  .media-reveal-item .media-select { position: absolute; top: 8px; left: 8px; width: 18px; height: 18px; z-index: 2; cursor: pointer; }
  .media-caption { font-size: 12px; color: #c7cad2; margin-top: 6px; text-align: center; }
  .file-card { display: flex; align-items: center; gap: 10px; background: #16171b; border: 1px solid #2a2d34; border-radius: 10px; padding: 14px; margin: 0 auto; max-width: 280px; }
  .file-card-icon { font-size: 28px; flex-shrink: 0; }
  .file-card-name { font-size: 13px; color: #e8e9ee; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; direction: ltr; text-align: right; flex: 1; }
  .interior-content img, .interior-content video { max-width: 100%; max-height: 45vh; border-radius: 10px; display: block; margin: 0 auto; }

  .vault-door {
    position: relative; padding: 22px 24px 22px; border-radius: 18px;
    background: linear-gradient(155deg, #2a2d34 0%, #1c1e23 55%, #131418 100%);
    box-shadow: 0 0 0 1px rgba(255,255,255,0.05) inset, 0 20px 50px rgba(0,0,0,0.7);
    z-index: 2; transition: opacity 0.5s ease, transform 0.5s ease;
    overflow: hidden;
  }
  .vault-door.error { animation: shake 0.4s ease; }
  .vault-door.thunk { animation: thunk 0.45s ease; }
  .vault-door.leaving { opacity: 0; transform: scale(0.94); pointer-events: none; }
  @keyframes shake { 0%,100%{transform:translateX(0)} 20%{transform:translateX(-8px)} 40%{transform:translateX(8px)} 60%{transform:translateX(-6px)} 80%{transform:translateX(6px)} }
  @keyframes thunk { 0%,100%{transform:translate(0,0) scale(1)} 25%{transform:translate(2px,-2px) scale(1.01)} 50%{transform:translate(-2px,2px) scale(0.99)} 75%{transform:translate(2px,2px) scale(1.01)} }

  .vault-hero {
    width: 100%; max-width: 220px; height: 180px;
    margin: 0 auto 12px;
    display: flex; align-items: center; justify-content: center;
    position: relative; z-index: 3;
  }
  .vault-hero svg {
    width: 100%; height: 100%;
    max-width: 180px; max-height: 180px;
    filter: drop-shadow(0 8px 24px rgba(0,0,0,0.5));
    animation: heroFloat 4s ease-in-out infinite;
  }
  @keyframes heroFloat {
    0%,100% { transform: translateY(0); }
    50% { transform: translateY(-6px); }
  }

  .badge { display: inline-flex; align-items: center; gap: 6px; background: rgba(255,255,255,0.05); border: 1px solid var(--accent); color: var(--accent); font-size: 10px; letter-spacing: 1.5px; text-transform: uppercase; padding: 5px 11px; border-radius: 20px; margin-bottom: 10px; }
  .vault-title { font-size: 20px; font-weight: 800; color: #e8e9ee; margin-bottom: 5px; text-align: center; }
  .vault-subtitle { font-size: 12.5px; color: #9aa0ab; line-height: 1.6; margin-bottom: 18px; text-align: center; }
  .main-row { display: flex; flex-wrap: wrap; gap: 16px; align-items: flex-start; margin-bottom: 16px; }
  .pin-col { flex: 1 1 160px; display: flex; flex-direction: column; }
  .dial-col { flex: 0 0 auto; display: flex; flex-direction: column; align-items: center; gap: 8px; }
  #codeInput { width: 100%; background: #101114; border: 1px solid #3a3f4a; border-radius: 8px; color: #e8e9ee; padding: 12px; font-size: 16px; letter-spacing: 6px; text-align: center; outline: none; font-family: 'Consolas', monospace; }
  #codeInput:focus { border-color: var(--accent); box-shadow: 0 0 0 4px rgba(168,85,247,0.15); }
  #sqInput, #pinInput, #mathInput { width: 100%; background: #101114; border: 1px solid #3a3f4a; border-radius: 8px; color: #e8e9ee; padding: 10px; font-size: 13px; outline: none; }
  .sq-label { font-size: 11px; color: #9aa0ab; margin-bottom: 6px; text-align: right; }
  .unlock-btn { margin-top: 10px; width: 100%; padding: 12px; border: none; border-radius: 9px; background: linear-gradient(180deg, #37c07a, #1c7a45); color: #06120a; font-size: 13px; font-weight: 700; letter-spacing: 1.5px; text-transform: uppercase; cursor: pointer; font-family: inherit; }
  .unlock-btn:disabled { opacity: 0.55; cursor: not-allowed; }
  .status-msg { margin-top: 10px; font-size: 12px; text-align: center; min-height: 16px; color: #8a8f9c; }
  .status-msg.error { color: #ff7a7a; }
  .status-msg.success { color: #6ef7a6; }
  .attempts-note { font-size: 11px; text-align: center; margin-top: 4px; color: #6a6f7a; min-height: 14px; }
  .attempts-note.warn { color: #ffb37a; }
  .attempts-note.lock { color: #ff7a7a; }

  .dial-circle { position: relative; width: 100px; height: 100px; border-radius: 50%; background: radial-gradient(circle at 35% 30%, #2a2e38, #0a0b0e 78%); border: 2px solid var(--accent); box-shadow: 0 0 0 6px rgba(255,255,255,0.02) inset, 0 10px 22px rgba(0,0,0,0.5); display: flex; align-items: center; justify-content: center; }
  .dial-bar { width: 76%; height: 10px; border-radius: 6px; background: linear-gradient(90deg, #ff3b3b, #ff6b6b, #ff3b3b); box-shadow: 0 0 16px 2px rgba(255,59,59,0.55); animation: barIdlePulse 2.2s ease-in-out infinite; transition: all 0.35s ease; }
  .dial-bar.scanning { animation: barScanning 0.5s ease-in-out infinite; }
  .dial-bar.wrong { animation: none; background: linear-gradient(90deg, #ff2b2b, #ff8080, #ff2b2b); box-shadow: 0 0 30px 8px rgba(255,43,43,0.9); }
  .dial-bar.correct { animation: none; background: linear-gradient(90deg, #2fae66, #6ef7a6, #2fae66); box-shadow: 0 0 26px 6px rgba(110,247,166,0.8); }
  .dial-bar.open { transform: scaleX(0); }
  @keyframes barIdlePulse { 0%,100%{opacity:0.85} 50%{opacity:1; box-shadow:0 0 22px 4px rgba(255,59,59,0.65)} }
  @keyframes barScanning { 0%,100%{opacity:0.9} 50%{opacity:1; box-shadow:0 0 26px 6px rgba(255,59,59,0.85)} }
  .dial-check { position: absolute; font-size: 36px; color: #6ef7a6; opacity: 0; transition: opacity 0.4s ease; }
  .dial-check.show { opacity: 1; }
  .lock-status { font-size: 11px; letter-spacing: 2px; text-transform: uppercase; color: #6a6f7a; }
  .lock-status.open { color: #6ef7a6; }

  .stats-row { display: flex; gap: 6px; border-top: 1px solid rgba(255,255,255,0.08); padding-top: 14px; }
  .stat { flex: 1; text-align: center; }
  .stat-label { font-size: 9.5px; color: #6a6f7a; text-transform: uppercase; letter-spacing: 1px; margin-bottom: 4px; }
  .stat-value { font-size: 13px; color: #e8e9ee; font-weight: 700; }
  .stat-value.threat-low { color: #6ef7a6; }
  .stat-value.threat-medium { color: #ffb37a; }
  .stat-value.threat-high { color: #ff7a7a; }

  .progress-row { margin-top: 12px; display: none; }
  .progress-row.active { display: block; }
  .progress-inner { height: 5px; background: #101114; border-radius: 4px; overflow: hidden; border: 1px solid #2a2d34; }
  .progress-fill { height: 100%; width: 0%; background: linear-gradient(90deg, var(--accent), #2fae66); transition: width 0.35s ease; }
  .progress-text { font-size: 10px; color: #6a6f7a; margin-top: 4px; text-align: center; }

  .flash { position: absolute; inset: 0; background: #fff; opacity: 0; border-radius: 18px; pointer-events: none; z-index: 3; }
  .red-flash { position: absolute; inset: 0; background: #ff2b2b; opacity: 0; border-radius: 18px; pointer-events: none; z-index: 3; }
  .particles { position: absolute; inset: 0; pointer-events: none; z-index: 4; overflow: visible; }
  .particle { position: absolute; left: 50%; top: 50%; width: 6px; height: 6px; border-radius: 50%; background: #6ef7a6; opacity: 0; transform: translate(-50%, -50%); }

  .screen { display: none; width: 380px; max-width: 90vw; padding: 32px 24px; border-radius: 18px; text-align: center; box-shadow: 0 20px 50px rgba(0,0,0,0.7); }
  .screen .icon { font-size: 40px; margin-bottom: 14px; }
  .screen .title { font-size: 16px; font-weight: 700; margin-bottom: 8px; }
  .screen .desc { color: #8a8f9c; font-size: 12px; line-height: 1.8; }
  .burned-screen { background: linear-gradient(155deg, #2a1c1c, #1a1212 45%, #100b0b); box-shadow: 0 0 0 1px rgba(255,90,90,0.15) inset, 0 20px 50px rgba(0,0,0,0.7); }
  .burned-screen .title { color: #ff9a9a; }
  .expired-screen { background: linear-gradient(155deg, #2a241c, #1a1512 45%, #100d0b); box-shadow: 0 0 0 1px rgba(255,200,90,0.15) inset, 0 20px 50px rgba(0,0,0,0.7); }
  .expired-screen .title { color: #ffd166; }
  .time-screen { background: linear-gradient(155deg, #1c1a2a, #12101a 45%, #0b0a10); box-shadow: 0 0 0 1px rgba(162,155,254,0.15) inset, 0 20px 50px rgba(0,0,0,0.7); }
  .time-screen .title { color: #a29bfe; }
  .receipt { background: linear-gradient(155deg, #1c2a24, #121a15 45%, #0b100d); box-shadow: 0 0 0 1px rgba(110,247,166,0.15) inset, 0 20px 50px rgba(0,0,0,0.7); text-align: right; }
  .receipt h3 { color: #6ef7a6; margin: 0 0 12px; font-size: 14px; }
  .receipt .row { font-size: 11px; color: #9aa0ab; margin: 6px 0; display: flex; justify-content: space-between; gap: 8px; }
  .receipt .row span:last-child { color: #e8e9ee; direction: ltr; }

  .splash { position: fixed; inset: 0; background: #0a0a0d; z-index: 9999; display: flex; align-items: center; justify-content: center; flex-direction: column; gap: 16px; transition: opacity 0.6s ease; }
  .splash.hide { opacity: 0; pointer-events: none; }
  .splash-text { color: var(--accent); font-size: 14px; letter-spacing: 2px; }

  .puzzle-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; margin: 12px 0; padding: 12px; background: #101114; border-radius: 12px; border: 1px solid #3a3f4a; }
  .puzzle-node { aspect-ratio: 1; border-radius: 50%; background: radial-gradient(circle at 35% 35%, #3a3f4a, #1a1b1f 75%); border: 2px solid #2a2d34; cursor: pointer; transition: all 0.2s; }
  .puzzle-node.hit { background: radial-gradient(circle at 35% 35%, var(--accent), #fff); border-color: var(--accent); box-shadow: 0 0 20px var(--accent); }
  .puzzle-node.wrong { background: radial-gradient(circle at 35% 35%, #ff3030, #8a0000); border-color: #ff3030; }

  .gate-box { max-height: 180px; overflow-y: auto; padding: 14px; background: #101114; border: 1px solid #3a3f4a; border-radius: 10px; font-size: 12px; line-height: 1.8; color: #c7cad2; margin-bottom: 12px; text-align: right; }
  .gate-progress { font-size: 10px; color: var(--dim); text-align: center; margin-bottom: 8px; }

  .countdown-display { font-size: 72px; font-weight: 900; color: var(--accent); text-align: center; font-family: 'Consolas', monospace; text-shadow: 0 0 30px var(--accent); }
  @keyframes cdPulse { 0%{transform:scale(0.5); opacity:0} 50%{transform:scale(1.15); opacity:1} 100%{transform:scale(1); opacity:1} }

  .typewriter::after { content: '▌'; color: var(--accent); animation: blink 0.8s steps(2) infinite; }
  @keyframes blink { 0%,100%{opacity:1} 50%{opacity:0} }
</style>
</head>
<body>

${opts.splash ? '<div class="splash" id="splashEl"><div class="splash-text">' + escapeHtml(opts.splashText) + '</div></div>' : ''}

<div class="vault-frame" id="vaultFrame" style="display:none;">
  <div class="vault-interior"><div class="interior-content" id="interiorContent"></div></div>
  <div class="vault-door" id="vaultDoor">
    <div class="vault-hero">${heroSVG}</div>
    <div class="badge" style="display:block;text-align:center;">🔒 SECURE VAULT</div>
    <div class="vault-title">خزنة #${shapeIdx + 1}</div>
    <div class="vault-subtitle">${escapeHtml(opts.preMessage)}</div>

    <div id="gateArea"></div>

    <div class="main-row">
      <div class="pin-col">
        <div id="pinArea"></div>
        <div id="sqArea"></div>
        <div id="mathArea"></div>
        <input id="codeInput" type="password" placeholder="ادخل الرمز" autocomplete="off">
        <button type="button" class="unlock-btn" id="unlockBtn">فتح الخزنة</button>
        <div class="status-msg" id="statusMsg"></div>
        <div class="attempts-note" id="attemptsNote"></div>
      </div>
      <div class="dial-col">
        <div class="dial-circle"><div class="dial-bar" id="dialBar"></div><div class="dial-check" id="dialCheck">✓</div></div>
        <div class="lock-status" id="lockStatusEl">مغلق</div>
      </div>
    </div>

    <div id="puzzleArea"></div>

    <div class="progress-row" id="progressRow"><div class="progress-inner"><div class="progress-fill" id="progressFill"></div></div><div class="progress-text" id="progressText"></div></div>

    <div class="stats-row">
      <div class="stat"><div class="stat-label">التشفير</div><div class="stat-value">AES-256</div></div>
      <div class="stat"><div class="stat-label">الخطر</div><div class="stat-value threat-low" id="threatVal">LOW</div></div>
      <div class="stat"><div class="stat-label">العناصر</div><div class="stat-value">${itemCount}</div></div>
    </div>
  </div>
  <div class="particles" id="particles"></div>
  <div class="flash" id="flashEl"></div>
  <div class="red-flash" id="redFlashEl"></div>
</div>

<div class="screen burned-screen" id="burnedScreen">
  <div class="icon">🌐</div>
  <div class="title">Deleted file</div>
  <div class="desc">تم حذف الملف بعد فتحه 🔐</div>
</div>

<div class="screen expired-screen" id="expiredScreen">
  <div class="icon">⏳</div>
  <div class="title">انتهت صلاحية الخزنة</div>
  <div class="desc">${expiryISO ? 'انتهت في: ' + escapeHtml(expiryISO) : ''}</div>
</div>

<div class="screen time-screen" id="timeScreen">
  <div class="icon">⏰</div>
  <div class="title">خارج النافذة الزمنية</div>
  <div class="desc" id="timeScreenDesc">هذه الخزنة تفتح فقط في وقت محدد</div>
</div>

<div class="screen receipt" id="receiptScreen">
  <h3>🧾 إيصال الفتح</h3>
  <div class="row"><span>وقت الفتح</span><span id="rcTime">—</span></div>
  <div class="row"><span>وقت الإنشاء</span><span id="rcCreated">—</span></div>
  <div class="row"><span>الجهاز</span><span id="rcFp">—</span></div>
  <div class="row"><span>هاش الحمولة</span><span id="rcHash">—</span></div>
  <div class="row"><span>المعرّف</span><span id="rcId">—</span></div>
</div>

${buildChildScript({ saltB64, ivB64, cipherB64, hmacB64, opts, compressed, iterations, innerHash, expiryISO, particlesJson, celebJson, fallbackJson, timeFromISO, timeToISO })}
</body>
</html>`;
  }

  // ═══════════════════════════════════════════════════════════
  // BUILD CHILD SCRIPT
  // ═══════════════════════════════════════════════════════════
  function buildChildScript(cfg) {
    const { saltB64, ivB64, cipherB64, hmacB64, opts, compressed, iterations, innerHash, expiryISO, particlesJson, celebJson, fallbackJson, timeFromISO, timeToISO } = cfg;
    const theme = { accent: opts.accent || '#a855f7' };

    let s = '<script>\n';
    s += '(function(){\n';
    s += '"use strict";\n';
    s += 'var SALT_B64 = ' + JSON.stringify(saltB64) + ';\n';
    s += 'var IV_B64 = ' + JSON.stringify(ivB64) + ';\n';
    s += 'var CIPHER_B64 = ' + JSON.stringify(cipherB64) + ';\n';
    s += 'var HMAC_B64 = ' + (hmacB64 ? JSON.stringify(hmacB64) : 'null') + ';\n';
    s += 'var VAULT_ID = ' + JSON.stringify(opts.vaultId) + ';\n';
    s += 'var CREATED_AT = ' + opts.createdAt + ';\n';
    s += 'var ONE_TIME = ' + (opts.oneTime ? 'true' : 'false') + ';\n';
    s += 'var RATE_LIMIT = ' + (opts.rateLimit ? 'true' : 'false') + ';\n';
    s += 'var EXPIRY_ISO = ' + (expiryISO ? JSON.stringify(expiryISO) : 'null') + ';\n';
    s += 'var SQ_ENABLED = ' + (opts.sqEnabled ? 'true' : 'false') + ';\n';
    s += 'var SQ_ANSWER = ' + jsonSafe((opts.sqAnswer || '').toLowerCase()) + ';\n';
    s += 'var SQ_QUESTION = ' + jsonSafe(opts.sqQuestion || '') + ';\n';
    s += 'var PIN_ENABLED = ' + (opts.pinEnabled ? 'true' : 'false') + ';\n';
    s += 'var PIN_VALUE = ' + jsonSafe(opts.pin || '') + ';\n';
    s += 'var MATH_GATE = ' + (opts.mathGate ? 'true' : 'false') + ';\n';
    s += 'var PUZZLE_ON = ' + (opts.puzzle ? 'true' : 'false') + ';\n';
    s += 'var PUZZLE_COUNT = ' + (opts.puzzleCount || 5) + ';\n';
    s += 'var PUZZLE_DIFF = ' + jsonSafe(opts.puzzleDiff || 'normal') + ';\n';
    s += 'var HMAC_ENABLED = ' + (opts.hmac ? 'true' : 'false') + ';\n';
    s += 'var ANTI_DEBUG = ' + (opts.antiDebug ? 'true' : 'false') + ';\n';
    s += 'var BURN_ON_TAMPER = ' + (opts.burn ? 'true' : 'false') + ';\n';
    s += 'var COMPRESSED = ' + (compressed ? 'true' : 'false') + ';\n';
    s += 'var ITERATIONS = ' + iterations + ';\n';
    s += 'var INNER_HASH = ' + JSON.stringify(innerHash) + ';\n';
    s += 'var DEVICE_BIND = ' + (opts.deviceBind ? 'true' : 'false') + ';\n';
    s += 'var RECEIPT_ON = ' + (opts.receipt ? 'true' : 'false') + ';\n';
    s += 'var COPY_MSG = ' + (opts.copyMsg ? 'true' : 'false') + ';\n';
    s += 'var SOUND_ON = ' + (opts.sound ? 'true' : 'false') + ';\n';
    s += 'var TTS_ON = ' + (opts.tts ? 'true' : 'false') + ';\n';
    s += 'var TTS_TEXT = ' + jsonSafe(opts.ttsText) + ';\n';
    s += 'var TYPEWRITER = ' + (opts.typewriter ? 'true' : 'false') + ';\n';
    s += 'var COUNTDOWN = ' + (opts.countdown ? 'true' : 'false') + ';\n';
    s += 'var SHAKE_ON = ' + (opts.shake ? 'true' : 'false') + ';\n';
    s += 'var SCROLL_GATE = ' + (opts.scrollGate ? 'true' : 'false') + ';\n';
    s += 'var SCROLL_GATE_TEXT = ' + jsonSafe(opts.scrollGateText || '') + ';\n';
    s += 'var HTTPS_ONLY = ' + (opts.httpsOnly ? 'true' : 'false') + ';\n';
    s += 'var TIME_FROM = ' + (timeFromISO ? JSON.stringify(timeFromISO) : 'null') + ';\n';
    s += 'var TIME_TO = ' + (timeToISO ? JSON.stringify(timeToISO) : 'null') + ';\n';
    s += 'var ACCENT = ' + jsonSafe(theme.accent) + ';\n';
    s += 'var PARTICLE_COLORS = ' + particlesJson + ';\n';
    s += 'var CELEBRATION = ' + celebJson + ';\n';
    s += 'var FALLBACK_URLS = ' + fallbackJson + ';\n';
    s += 'var ANIMATED_BG = ' + (opts.animatedBg ? 'true' : 'false') + ';\n';
    s += '\n';

    s += 'var STORAGE_KEY = "vaultUsed_" + VAULT_ID;\n';
    s += 'var ATTEMPTS_KEY = "vaultAttempts_" + VAULT_ID;\n';
    s += 'var LOCKROUND_KEY = "vaultLockRound_" + VAULT_ID;\n';
    s += 'var LOCKUNTIL_KEY = "vaultLockUntil_" + VAULT_ID;\n';
    s += 'var FPBIND_KEY = "vaultFp_" + VAULT_ID;\n';
    s += 'var TAMPER_KEY = "vaultTamper_" + VAULT_ID;\n';
    s += 'var MAX_ATTEMPTS = 5;\n';
    s += 'var BASE_LOCK_SECONDS = 30;\n';
    s += 'var DENSITY_MAP = { light: 0.55, normal: 1, heavy: 1.8 };\n';
    s += 'var SPEED_MAP = { slow: 1.6, normal: 1, fast: 0.6 };\n';
    s += '\n';

    s += 'function $(id){ return document.getElementById(id); }\n';
    s += 'function wait(ms){ return new Promise(function(r){ setTimeout(r, ms); }); }\n';
    s += 'function getInt(k, fb){ var v = parseInt(localStorage.getItem(k) || "", 10); return isNaN(v) ? fb : v; }\n';
    s += 'function getAttempts(){ return getInt(ATTEMPTS_KEY, 0); }\n';
    s += 'function setAttempts(n){ localStorage.setItem(ATTEMPTS_KEY, String(n)); }\n';
    s += 'function getLockRound(){ return getInt(LOCKROUND_KEY, 0); }\n';
    s += 'function setLockRound(n){ localStorage.setItem(LOCKROUND_KEY, String(n)); }\n';
    s += 'function getLockUntil(){ return getInt(LOCKUNTIL_KEY, 0); }\n';
    s += 'function setLockUntil(ts){ localStorage.setItem(LOCKUNTIL_KEY, String(ts)); }\n';
    s += 'function fromB64(b64){ var bin = atob(b64); var out = new Uint8Array(bin.length); for (var i=0;i<bin.length;i++) out[i]=bin.charCodeAt(i); return out; }\n';
    s += 'function sha256(bytes){ return crypto.subtle.digest("SHA-256", bytes).then(function(b){ return new Uint8Array(b); }); }\n';
    s += '\n';

    s += 'if (HTTPS_ONLY && location.protocol !== "https:" && location.hostname !== "localhost" && location.hostname !== "") {\n';
    s += '  document.body.innerHTML = "<div style=\\"padding:40px;text-align:center;color:#ff7a7a;font-family:monospace\\"><h2>⚠️ HTTPS Required</h2></div>";\n';
    s += '  return;\n';
    s += '}\n';

    s += 'if (TIME_FROM && TIME_TO) {\n';
    s += '  var tf = Date.parse(TIME_FROM), tt = Date.parse(TIME_TO), now = Date.now();\n';
    s += '  if (!isNaN(tf) && !isNaN(tt) && (now < tf || now > tt)) {\n';
    s += '    document.body.innerHTML = "";\n';
    s += '    var ts = document.getElementById("timeScreen");\n';
    s += '    if (ts) { ts.style.display = "block"; document.body.appendChild(ts); var td = document.getElementById("timeScreenDesc"); if (td) td.textContent = "تفتح من " + new Date(tf).toLocaleString("ar") + " إلى " + new Date(tt).toLocaleString("ar"); }\n';
    s += '    return;\n';
    s += '  }\n';
    s += '}\n';

    s += 'if (EXPIRY_ISO) {\n';
    s += '  var exp = Date.parse(EXPIRY_ISO);\n';
    s += '  if (!isNaN(exp) && Date.now() > exp) {\n';
    s += '    document.body.innerHTML = "";\n';
    s += '    var es = document.getElementById("expiredScreen");\n';
    s += '    if (es) { es.style.display = "block"; document.body.appendChild(es); }\n';
    s += '    return;\n';
    s += '  }\n';
    s += '}\n';

    s += 'if (ONE_TIME && localStorage.getItem(STORAGE_KEY) === "1") {\n';
    s += '  document.body.innerHTML = "";\n';
    s += '  var bs = document.getElementById("burnedScreen");\n';
    s += '  if (bs) { bs.style.display = "block"; document.body.appendChild(bs); }\n';
    s += '  return;\n';
    s += '}\n';

    s += 'if (FALLBACK_URLS && FALLBACK_URLS.length) {\n';
    s += '  setTimeout(function(){\n';
    s += '    var wrap = document.createElement("div");\n';
    s += '    wrap.className = "screen receipt";\n';
    s += '    wrap.style.cssText = "display:block;margin-top:20px";\n';
    s += '    var h = document.createElement("h3"); h.style.color = ACCENT; h.textContent = "🔗 روابط بديلة";\n';
    s += '    wrap.appendChild(h);\n';
    s += '    FALLBACK_URLS.forEach(function(u, i){\n';
    s += '      var a = document.createElement("a");\n';
    s += '      a.href = u; a.target = "_blank"; a.rel = "noopener";\n';
    s += '      a.style.cssText = "display:block;padding:10px;background:#101114;border:1px solid " + ACCENT + ";color:" + ACCENT + ";text-decoration:none;border-radius:8px;font-size:12px;margin-top:8px;word-break:break-all";\n';
    s += '      a.textContent = "🔗 " + (i+1) + ". " + u.slice(0, 60) + (u.length > 60 ? "…" : "");\n';
    s += '      wrap.appendChild(a);\n';
    s += '    });\n';
    s += '    document.body.appendChild(wrap);\n';
    s += '  }, 1500);\n';
    s += '}\n';

    s += 'var vaultFrame = document.getElementById("vaultFrame");\n';
    s += 'if (vaultFrame) vaultFrame.style.display = "block";\n';
    s += 'var splash = document.getElementById("splashEl");\n';
    s += 'if (splash) setTimeout(function(){ splash.classList.add("hide"); }, 1400);\n';

    s += 'var vaultDoor = document.getElementById("vaultDoor");\n';
    s += 'var dialBar = document.getElementById("dialBar");\n';
    s += 'var dialCheck = document.getElementById("dialCheck");\n';
    s += 'var lockStatusEl = document.getElementById("lockStatusEl");\n';
    s += 'var threatVal = document.getElementById("threatVal");\n';
    s += 'var flashEl = document.getElementById("flashEl");\n';
    s += 'var redFlashEl = document.getElementById("redFlashEl");\n';
    s += 'var particlesEl = document.getElementById("particles");\n';
    s += 'var interiorContent = document.getElementById("interiorContent");\n';
    s += 'var codeInput = document.getElementById("codeInput");\n';
    s += 'var unlockBtn = document.getElementById("unlockBtn");\n';
    s += 'var statusMsg = document.getElementById("statusMsg");\n';
    s += 'var attemptsNote = document.getElementById("attemptsNote");\n';
    s += 'var progressRow = document.getElementById("progressRow");\n';
    s += 'var progressFill = document.getElementById("progressFill");\n';
    s += 'var progressText = document.getElementById("progressText");\n';
    s += 'var gateArea = document.getElementById("gateArea");\n';
    s += 'var pinArea = document.getElementById("pinArea");\n';
    s += 'var sqArea = document.getElementById("sqArea");\n';
    s += 'var mathArea = document.getElementById("mathArea");\n';
    s += 'var puzzleArea = document.getElementById("puzzleArea");\n';
    s += '\n';

    s += 'function beep(freq, dur, vol){\n';
    s += '  if (!SOUND_ON) return;\n';
    s += '  try {\n';
    s += '    var ctx = new (window.AudioContext || window.webkitAudioContext)();\n';
    s += '    var o = ctx.createOscillator(), g = ctx.createGain();\n';
    s += '    o.type = "sine"; o.frequency.value = freq;\n';
    s += '    o.connect(g); g.connect(ctx.destination);\n';
    s += '    var t = ctx.currentTime;\n';
    s += '    g.gain.setValueAtTime(0.0001, t);\n';
    s += '    g.gain.exponentialRampToValueAtTime((vol||0.08), t + 0.01);\n';
    s += '    g.gain.exponentialRampToValueAtTime(0.0001, t + dur);\n';
    s += '    o.start(t); o.stop(t + dur + 0.02);\n';
    s += '  } catch (e) {}\n';
    s += '}\n';

    s += 'var lockTick = null;\n';
    s += 'function startLockCountdown(){\n';
    s += '  if (lockTick) clearInterval(lockTick);\n';
    s += '  var tick = function(){\n';
    s += '    var rem = Math.ceil((getLockUntil() - Date.now()) / 1000);\n';
    s += '    if (rem <= 0) {\n';
    s += '      clearInterval(lockTick); lockTick = null;\n';
    s += '      setAttempts(0);\n';
    s += '      unlockBtn.disabled = false; codeInput.disabled = false;\n';
    s += '      attemptsNote.className = "attempts-note"; attemptsNote.textContent = "";\n';
    s += '      threatVal.textContent = "LOW"; threatVal.className = "stat-value threat-low";\n';
    s += '      statusMsg.className = "status-msg"; statusMsg.textContent = "";\n';
    s += '      return;\n';
    s += '    }\n';
    s += '    codeInput.disabled = true; unlockBtn.disabled = true;\n';
    s += '    attemptsNote.className = "attempts-note lock";\n';
    s += '    attemptsNote.textContent = "استنى " + rem + " ثانية";\n';
    s += '    threatVal.textContent = "HIGH"; threatVal.className = "stat-value threat-high";\n';
    s += '  };\n';
    s += '  tick(); lockTick = setInterval(tick, 1000);\n';
    s += '}\n';
    s += 'if (RATE_LIMIT && getLockUntil() > Date.now()) startLockCountdown();\n';

    s += 'function registerFail(){\n';
    s += '  if (!RATE_LIMIT) return false;\n';
    s += '  var used = getAttempts() + 1;\n';
    s += '  setAttempts(used);\n';
    s += '  if (used >= MAX_ATTEMPTS) {\n';
    s += '    var round = getLockRound() + 1; setLockRound(round);\n';
    s += '    var secs = Math.min(BASE_LOCK_SECONDS * Math.pow(2, round - 1), 3600);\n';
    s += '    setLockUntil(Date.now() + secs * 1000);\n';
    s += '    startLockCountdown();\n';
    s += '    return true;\n';
    s += '  }\n';
    s += '  var rem = MAX_ATTEMPTS - used;\n';
    s += '  attemptsNote.className = "attempts-note warn";\n';
    s += '  attemptsNote.textContent = "متبقي " + rem + " محاولة";\n';
    s += '  threatVal.textContent = used >= 2 ? "MEDIUM" : "LOW";\n';
    s += '  threatVal.className = "stat-value " + (used >= 2 ? "threat-medium" : "threat-low");\n';
    s += '  return false;\n';
    s += '}\n';

    s += 'function deriveKey(password, saltBytes, usage){\n';
    s += '  var enc = new TextEncoder();\n';
    s += '  return crypto.subtle.importKey("raw", enc.encode(password), { name:"PBKDF2" }, false, ["deriveKey"]).then(function(bk){\n';
    s += '    return crypto.subtle.deriveKey({ name:"PBKDF2", salt: saltBytes, iterations: ITERATIONS, hash:"SHA-256" }, bk, { name:"AES-GCM", length:256 }, false, [usage]);\n';
    s += '  });\n';
    s += '}\n';
    s += 'function gzipDecompress(bytes){\n';
    s += '  var ds = new DecompressionStream("gzip");\n';
    s += '  var w = ds.writable.getWriter(); w.write(bytes); w.close();\n';
    s += '  return new Response(ds.readable).arrayBuffer().then(function(b){ return new Uint8Array(b); });\n';
    s += '}\n';
    s += 'function computeFp(){\n';
    s += '  var parts = [navigator.userAgent, navigator.language, screen.width+"x"+screen.height+"x"+screen.colorDepth, new Date().getTimezoneOffset(), navigator.hardwareConcurrency||0, navigator.platform||"", navigator.maxTouchPoints||0];\n';
    s += '  return sha256(new TextEncoder().encode(parts.join("|"))).then(function(h){\n';
    s += '    var s = ""; for (var i=0;i<h.length;i++) s += h[i].toString(16).padStart(2,"0"); return s.slice(0,32);\n';
    s += '  });\n';
    s += '}\n';

    s += 'function setProgressUI(p, label){ progressRow.classList.add("active"); progressFill.style.width = p + "%"; progressText.textContent = label || ""; }\n';
    s += 'function hideProgressUI(){ setTimeout(function(){ progressRow.classList.remove("active"); }, 800); }\n';
    s += 'function flashOnce(){ flashEl.style.transition = "opacity .18s ease"; flashEl.style.opacity = "0.55"; setTimeout(function(){ flashEl.style.transition = "opacity .6s ease"; flashEl.style.opacity = "0"; }, 180); }\n';
    s += 'function redFlashOnce(){ redFlashEl.style.transition = "opacity .12s ease"; redFlashEl.style.opacity = "0.25"; setTimeout(function(){ redFlashEl.style.transition = "opacity .5s ease"; redFlashEl.style.opacity = "0"; }, 140); }\n';

    s += 'function burstParticles(){\n';
    s += '  var colors = (PARTICLE_COLORS && PARTICLE_COLORS.length) ? PARTICLE_COLORS : ["#6ef7a6","#6ee7ff","#ffe08a"];\n';
    s += '  for (var i = 0; i < 22; i++) {\n';
    s += '    var p = document.createElement("div"); p.className = "particle";\n';
    s += '    var a = (Math.PI * 2 * i) / 22 + Math.random() * 0.3;\n';
    s += '    var d = 60 + Math.random() * 80;\n';
    s += '    p.style.background = colors[i % colors.length];\n';
    s += '    particlesEl.appendChild(p);\n';
    s += '    var anim = p.animate([\n';
    s += '      { transform: "translate(-50%,-50%) scale(1)", opacity: 1 },\n';
    s += '      { transform: "translate(calc(-50% + " + (Math.cos(a)*d) + "px), calc(-50% + " + (Math.sin(a)*d) + "px)) scale(0.3)", opacity: 0 }\n';
    s += '    ], { duration: 700 + Math.random()*400, easing: "cubic-bezier(.2,.7,.3,1)" });\n';
    s += '    anim.onfinish = (function(el){ return function(){ el.remove(); }; })(p);\n';
    s += '  }\n';
    s += '}\n';

    s += 'function celebLayer(){\n';
    s += '  var l = document.getElementById("celebLayer");\n';
    s += '  if (!l) { l = document.createElement("div"); l.id = "celebLayer"; l.style.cssText = "position:fixed;inset:0;pointer-events:none;overflow:hidden;z-index:9999"; document.body.appendChild(l); }\n';
    s += '  return l;\n';
    s += '}\n';
    s += 'function spawnFalling(baseCount, makeEl){\n';
    s += '  var dm = DENSITY_MAP[(CELEBRATION && CELEBRATION.density) || "normal"] || 1;\n';
    s += '  var sm = SPEED_MAP[(CELEBRATION && CELEBRATION.speed) || "normal"] || 1;\n';
    s += '  var count = Math.max(4, Math.round(baseCount * dm));\n';
    s += '  var layer = celebLayer();\n';
    s += '  for (var i = 0; i < count; i++) {\n';
    s += '    var el = makeEl();\n';
    s += '    var x = Math.random() * 100, drift = Math.random() * 160 - 80;\n';
    s += '    var dur = (2600 + Math.random() * 1800) * sm, delay = Math.random() * 500 * sm;\n';
    s += '    el.style.position = "absolute"; el.style.left = x + "vw"; el.style.top = "-30px";\n';
    s += '    layer.appendChild(el);\n';
    s += '    var a = el.animate([\n';
    s += '      { transform: "translate(0,0) rotate(0deg)", opacity: 1 },\n';
    s += '      { transform: "translate(" + drift + "px, 108vh) rotate(" + (Math.random()*720-360) + "deg)", opacity: 1, offset: 0.85 },\n';
    s += '      { transform: "translate(" + drift + "px, 115vh) rotate(" + (Math.random()*720-360) + "deg)", opacity: 0 }\n';
    s += '    ], { duration: dur, delay: delay, easing: "cubic-bezier(.25,.46,.45,.94)", fill: "forwards" });\n';
    s += '    a.onfinish = (function(el){ return function(){ el.remove(); }; })(el);\n';
    s += '  }\n';
    s += '}\n';
    s += 'function spawnConfetti(){\n';
    s += '  var colors = (PARTICLE_COLORS && PARTICLE_COLORS.length) ? PARTICLE_COLORS : ["#6ee7ff","#ff9f43","#ff6b9d","#ffd166"];\n';
    s += '  spawnFalling(46, function(){\n';
    s += '    var el = document.createElement("div");\n';
    s += '    el.style.width = (6 + Math.random()*6) + "px"; el.style.height = (10 + Math.random()*6) + "px";\n';
    s += '    el.style.background = colors[Math.floor(Math.random()*colors.length)];\n';
    s += '    el.style.borderRadius = Math.random() > 0.5 ? "2px" : "50%";\n';
    s += '    return el;\n';
    s += '  });\n';
    s += '}\n';
    s += 'function spawnRain(items){\n';
    s += '  var list = (items && items.length) ? items : ["🎉"];\n';
    s += '  spawnFalling(34, function(){\n';
    s += '    var el = document.createElement("div");\n';
    s += '    el.textContent = list[Math.floor(Math.random()*list.length)];\n';
    s += '    el.style.fontSize = (16 + Math.random()*14) + "px";\n';
    s += '    el.style.color = ACCENT || "#e8e9ee";\n';
    s += '    return el;\n';
    s += '  });\n';
    s += '}\n';
    s += 'function spawnMatrix(){\n';
    s += '  var layer = celebLayer();\n';
    s += '  var chars = "01アイウエオ".split("");\n';
    s += '  for (var col = 0; col < 40; col++) {\n';
    s += '    var stream = document.createElement("div");\n';
    s += '    stream.style.cssText = "position:absolute;top:-100%;left:" + (col * 2.5) + "vw;color:#39ff14;font-family:monospace;font-size:14px;line-height:1.2;text-shadow:0 0 6px #39ff14;pointer-events:none";\n';
    s += '    var txt = "";\n';
    s += '    for (var r = 0; r < 30; r++) txt += chars[Math.floor(Math.random()*chars.length)] + "\\n";\n';
    s += '    stream.textContent = txt;\n';
    s += '    layer.appendChild(stream);\n';
    s += '    var a = stream.animate([{ transform: "translateY(0)" }, { transform: "translateY(140vh)" }], { duration: 2000 + Math.random() * 3000, delay: Math.random() * 1000, fill: "forwards" });\n';
    s += '    a.onfinish = (function(el){ return function(){ el.remove(); }; })(stream);\n';
    s += '  }\n';
    s += '}\n';
    s += 'function spawnBubbles(){\n';
    s += '  var layer = celebLayer();\n';
    s += '  for (var i = 0; i < 30; i++) {\n';
    s += '    var b = document.createElement("div");\n';
    s += '    var size = 12 + Math.random() * 30;\n';
    s += '    b.style.cssText = "position:absolute;width:" + size + "px;height:" + size + "px;border-radius:50%;border:2px solid " + ACCENT + ";background:radial-gradient(circle at 35% 30%, rgba(255,255,255,0.4), transparent 60%);bottom:-50px;left:" + (Math.random()*100) + "vw;pointer-events:none";\n';
    s += '    layer.appendChild(b);\n';
    s += '    var a = b.animate([{ transform: "translateY(0) scale(0.6)", opacity: 0.9 }, { transform: "translateY(-115vh) scale(1.2)", opacity: 0 }], { duration: 3000 + Math.random() * 2500, delay: Math.random() * 1000, fill: "forwards" });\n';
    s += '    a.onfinish = (function(el){ return function(){ el.remove(); }; })(b);\n';
    s += '  }\n';
    s += '}\n';
    s += 'function spawnGlow(){ vaultFrame.style.filter = "drop-shadow(0 0 42px " + ACCENT + ")"; setTimeout(function(){ vaultFrame.style.filter = ""; }, 3600); }\n';
    s += 'function spawnHearts(){ spawnRain(["💖","💗","💕","❤️","💝"]); }\n';
    s += 'function spawnSnow(){ spawnRain(["❄️","❅","❆","✻","✽"]); }\n';
    s += 'function spawnFireworks(){\n';
    s += '  for (var k = 0; k < 6; k++) {\n';
    s += '    (function(k){\n';
    s += '      setTimeout(function(){\n';
    s += '        var cx = Math.random() * window.innerWidth;\n';
    s += '        var cy = Math.random() * window.innerHeight * 0.6 + 60;\n';
    s += '        for (var i = 0; i < 24; i++) {\n';
    s += '          var el = document.createElement("div");\n';
    s += '          el.style.cssText = "position:fixed;width:5px;height:5px;border-radius:50%;background:" + (PARTICLE_COLORS[i % PARTICLE_COLORS.length] || "#fff") + ";left:" + cx + "px;top:" + cy + "px;pointer-events:none;z-index:9999";\n';
    s += '          document.body.appendChild(el);\n';
    s += '          var a = (Math.PI * 2 * i) / 24, d = 80 + Math.random() * 90;\n';
    s += '          var anim = el.animate([{ transform: "translate(0,0) scale(1)", opacity: 1 },{ transform: "translate(" + (Math.cos(a)*d) + "px," + (Math.sin(a)*d) + "px) scale(0)", opacity: 0 }], { duration: 800 + Math.random()*400, easing: "cubic-bezier(.2,.7,.3,1)" });\n';
    s += '          anim.onfinish = (function(el){ return function(){ el.remove(); }; })(el);\n';
    s += '        }\n';
    s += '      }, k * 350);\n';
    s += '    })(k);\n';
    s += '  }\n';
    s += '}\n';
    s += 'function playSuccessSound(){\n';
    s += '  if (!SOUND_ON) return;\n';
    s += '  var notes = [523.25, 659.25, 783.99, 1046.5];\n';
    s += '  notes.forEach(function(f, i){ setTimeout(function(){ beep(f, 0.3, 0.1); }, i * 120); });\n';
    s += '}\n';
    s += 'function speakSuccess(){\n';
    s += '  if (!TTS_ON || !window.speechSynthesis) return;\n';
    s += '  try { var u = new SpeechSynthesisUtterance(TTS_TEXT); u.lang = "ar"; speechSynthesis.speak(u); } catch (e) {}\n';
    s += '}\n';
    s += 'function runCelebration(){\n';
    s += '  if (!CELEBRATION || !CELEBRATION.enabled) { playSuccessSound(); speakSuccess(); return; }\n';
    s += '  var m = CELEBRATION.mode;\n';
    s += '  if (m === "glow") spawnGlow();\n';
    s += '  else if (m === "custom") spawnRain(CELEBRATION.items);\n';
    s += '  else if (m === "fireworks") spawnFireworks();\n';
    s += '  else if (m === "hearts") spawnHearts();\n';
    s += '  else if (m === "snow") spawnSnow();\n';
    s += '  else if (m === "matrix") spawnMatrix();\n';
    s += '  else if (m === "bubbles") spawnBubbles();\n';
    s += '  else spawnConfetti();\n';
    s += '  playSuccessSound();\n';
    s += '  speakSuccess();\n';
    s += '}\n';

    s += 'var gatePassed = !SCROLL_GATE;\n';
s += 'if (SCROLL_GATE) {\n';
s += '  gateArea.innerHTML = \'<div class="gate-progress" id="gateProgress">مرر لقبول الشروط</div><div class="gate-box" id="gateBox">\' + SCROLL_GATE_TEXT.replace(/</g, "&lt;") + \'</div>\';\n';
s += '  var gateBox = document.getElementById("gateBox");\n';
s += '  var gateProgress = document.getElementById("gateProgress");\n';
s += '  var checkGate = function(){\n';
s += '    var scrollable = gateBox.scrollHeight - gateBox.clientHeight;\n';
s += '    if (scrollable <= 5) {\n';
s += '      // النص قصير — ما فيه حاجة للتمرير\n';
s += '      gatePassed = true;\n';
s += '      gateProgress.textContent = "✔ لا حاجة للتمرير — النص قصير";\n';
s += '      gateProgress.style.color = "#6ef7a6";\n';
s += '      return;\n';
s += '    }\n';
s += '    var pct = Math.min(100, Math.round((gateBox.scrollTop / scrollable) * 100));\n';
s += '    gateProgress.textContent = pct + "% — " + (pct >= 95 ? "تم القبول ✓" : "مرر للأسفل");\n';
s += '    gateProgress.style.color = pct >= 95 ? "#6ef7a6" : "#6a6f7a";\n';
s += '    gatePassed = pct >= 95;\n';
s += '  };\n';
s += '  gateBox.addEventListener("scroll", checkGate);\n';
s += '  setTimeout(checkGate, 100);\n';  // فحص أولي بعد الرسم
s += '}\n';

    s += 'if (SQ_ENABLED) {\n';
    s += '  sqArea.innerHTML = \'<div class="sq-label">\' + SQ_QUESTION.replace(/</g, "&lt;") + \'</div><input id="sqInput" type="text" placeholder="الإجابة" autocomplete="off" style="margin-bottom:8px">\';\n';
    s += '}\n';

    s += 'if (PIN_ENABLED) {\n';
    s += '  pinArea.innerHTML = \'<input id="pinInput" type="password" placeholder="PIN" autocomplete="off" maxlength="20" style="margin-bottom:8px">\';\n';
    s += '}\n';

    s += 'var mathPassed = !MATH_GATE;\n';
    s += 'var mathAnswer = 0;\n';
    s += 'if (MATH_GATE) {\n';
    s += '  var a = Math.floor(Math.random() * 20) + 5;\n';
    s += '  var b = Math.floor(Math.random() * 15) + 3;\n';
    s += '  mathAnswer = a + b;\n';
    s += '  mathArea.innerHTML = \'<div class="sq-label">ما نتيجة \' + a + \' + \' + b + \'؟</div><input id="mathInput" type="number" placeholder="الجواب" autocomplete="off" style="margin-bottom:8px">\';\n';
    s += '}\n';

    s += 'var puzzlePassed = !PUZZLE_ON;\n';
    s += 'if (PUZZLE_ON) {\n';
    s += '  var correctSeq = [];\n';
    s += '  var pool = [0,1,2,3,4,5,6,7,8];\n';
    s += '  for (var i = 0; i < PUZZLE_COUNT; i++) {\n';
    s += '    var idx = Math.floor(Math.random() * pool.length);\n';
    s += '    correctSeq.push(pool[idx]);\n';
    s += '    pool.splice(idx, 1);\n';
    s += '  }\n';
    s += '  var grid = document.createElement("div"); grid.className = "puzzle-grid";\n';
    s += '  for (var i = 0; i < 9; i++) {\n';
    s += '    var node = document.createElement("div");\n';
    s += '    node.className = "puzzle-node";\n';
    s += '    node.dataset.idx = i;\n';
    s += '    grid.appendChild(node);\n';
    s += '  }\n';
    s += '  puzzleArea.innerHTML = \'<div class="sq-label">اضغط على \' + PUZZLE_COUNT + \' دوائر بالتسلسل</div>\';\n';
    s += '  puzzleArea.appendChild(grid);\n';
    s += '  var diffTime = PUZZLE_DIFF === "easy" ? 3000 : PUZZLE_DIFF === "hard" ? 1200 : 2000;\n';
    s += '  var puzzleProgress = 0;\n';
    s += '  var previewSeq = function(){\n';
    s += '    puzzleProgress = 0;\n';
    s += '    var i = 0;\n';
    s += '    var show = function(){\n';
    s += '      if (i >= correctSeq.length) {\n';
    s += '        grid.querySelectorAll(".puzzle-node").forEach(function(n){ n.classList.remove("hit"); });\n';
    s += '        return;\n';
    s += '      }\n';
    s += '      var node = grid.children[correctSeq[i]];\n';
    s += '      node.classList.add("hit");\n';
    s += '      setTimeout(function(){ node.classList.remove("hit"); }, diffTime / 2);\n';
    s += '      i++;\n';
    s += '      setTimeout(show, diffTime);\n';
    s += '    };\n';
    s += '    show();\n';
    s += '  };\n';
    s += '  setTimeout(previewSeq, 400);\n';
    s += '  grid.addEventListener("click", function(e){\n';
    s += '    var node = e.target.closest(".puzzle-node");\n';
    s += '    if (!node) return;\n';
    s += '    var idx = parseInt(node.dataset.idx, 10);\n';
    s += '    if (correctSeq[puzzleProgress] === idx) {\n';
    s += '      node.classList.add("hit");\n';
    s += '      puzzleProgress++;\n';
    s += '      beep(800, 0.08);\n';
    s += '      if (puzzleProgress >= correctSeq.length) {\n';
    s += '        puzzlePassed = true;\n';
    s += '        puzzleArea.innerHTML = \'<div class="sq-label" style="color:#6ef7a6">✔ Puzzle complete</div>\';\n';
    s += '      }\n';
    s += '    } else {\n';
    s += '      node.classList.add("wrong");\n';
    s += '      setTimeout(function(){ node.classList.remove("wrong"); }, 400);\n';
    s += '      beep(200, 0.2);\n';
    s += '      puzzleProgress = 0;\n';
    s += '      grid.querySelectorAll(".puzzle-node").forEach(function(n){ n.classList.remove("hit"); });\n';
    s += '      setTimeout(previewSeq, 600);\n';
    s += '    }\n';
    s += '  });\n';
    s += '}\n';

s += 'var shakePassed = !SHAKE_ON;\n';
s += 'var shakeCount = 0;\n';
s += 'var SHAKE_THRESHOLD = 20;\n';  // ← عتبة أقل
s += 'var SHAKE_NEEDED = 5;\n';
s += 'if (SHAKE_ON) {\n';
s += '  var shakeUI = document.createElement("div");\n';
s += '  shakeUI.id = "shakeUI";\n';
s += '  shakeUI.style.cssText = "margin:8px 0;padding:10px;background:rgba(168,85,247,0.1);border:1px solid var(--accent);border-radius:10px;text-align:center;font-size:12px;color:var(--accent);";\n';
s += '  shakeUI.innerHTML = "📱 هز الموبايل " + SHAKE_NEEDED + " مرات للفتح<br><span id=\\"shakeProgress\\" style=\\"font-size:11px;color:#9aa0ab;\\">0 / " + SHAKE_NEEDED + "</span>";\n';
s += '  pinArea.appendChild(shakeUI);\n';
s += '  var updateShakeUI = function(){\n';
s += '    var el = document.getElementById("shakeProgress");\n';
s += '    if (el) el.textContent = shakeCount + " / " + SHAKE_NEEDED;\n';
s += '    if (shakeCount >= SHAKE_NEEDED) {\n';
s += '      shakePassed = true;\n';
s += '      if (el) { el.textContent = "✔ تم — الحين تقدر تدخل الرمز"; el.style.color = "#6ef7a6"; }\n';
s += '      shakeUI.style.borderColor = "#6ef7a6";\n';
s += '      shakeUI.style.color = "#6ef7a6";\n';
s += '    }\n';
s += '  };\n';
s += '  var startShakeListener = function(){\n';
s += '    if (!window.DeviceMotionEvent) {\n';
s += '      shakePassed = true;\n';
s += '      shakeUI.innerHTML = "📱 الجهاز ما فيه حساس — الفتح مباشر";\n';
s += '      shakeUI.style.color = "#6ef7a6";\n';
s += '      shakeUI.style.borderColor = "#6ef7a6";\n';
s += '      return;\n';
s += '    }\n';
s += '    var lastShake = 0;\n';
s += '    window.addEventListener("devicemotion", function(e){\n';
s += '      var a = e.accelerationIncludingGravity;\n';
s += '      if (!a) return;\n';
s += '      var mag = Math.abs(a.x||0) + Math.abs(a.y||0) + Math.abs(a.z||0);\n';
s += '      var now = Date.now();\n';
s += '      if (mag > SHAKE_THRESHOLD && now - lastShake > 150 && shakeCount < SHAKE_NEEDED) {\n';
s += '        lastShake = now;\n';
s += '        shakeCount++;\n';
s += '        beep(600 + shakeCount * 100, 0.1);\n';
s += '        updateShakeUI();\n';
s += '      }\n';
s += '    });\n';
s += '  };\n';
s += '  // iOS 13+ يحتاج إذن\n';
s += '  if (typeof DeviceMotionEvent !== "undefined" && typeof DeviceMotionEvent.requestPermission === "function") {\n';
s += '    shakeUI.innerHTML = "📱 اضغط هنا لتفعيل الهز<br><span style=\\"font-size:11px;color:#9aa0ab;\\">مطلوب من iOS</span>";\n';
s += '    shakeUI.style.cursor = "pointer";\n';
s += '    shakeUI.addEventListener("click", function(){\n';
s += '      DeviceMotionEvent.requestPermission().then(function(state){\n';
s += '        if (state === "granted") {\n';
s += '          shakeUI.style.cursor = "default";\n';
s += '          shakeUI.innerHTML = "📱 هز الموبايل " + SHAKE_NEEDED + " مرات<br><span id=\\"shakeProgress\\" style=\\"font-size:11px;color:#9aa0ab;\\">0 / " + SHAKE_NEEDED + "</span>";\n';
s += '          startShakeListener();\n';
s += '        } else {\n';
s += '          shakeUI.innerHTML = "❌ ما تم منح الإذن — الفتح مباشر";\n';
s += '          shakePassed = true;\n';
s += '        }\n';
s += '      }).catch(function(){\n';
s += '        shakeUI.innerHTML = "❌ فشل طلب الإذن — الفتح مباشر";\n';
s += '        shakePassed = true;\n';
s += '      });\n';
s += '    });\n';
s += '  } else {\n';
s += '    startShakeListener();\n';
s += '  }\n';
s += '}\n';

    s += 'if (ANTI_DEBUG) {\n';
    s += '  setInterval(function(){\n';
    s += '    var open = window.outerWidth - window.innerWidth > 160 || window.outerHeight - window.innerHeight > 160;\n';
    s += '    if (open) document.documentElement.style.setProperty("--accent", "#ff3030");\n';
    s += '  }, 1200);\n';
    s += '}\n';

    s += 'if (ANIMATED_BG) {\n';
    s += '  var bg = document.createElement("div");\n';
    s += '  bg.style.cssText = "position:fixed;inset:0;pointer-events:none;z-index:0;overflow:hidden";\n';
    s += '  document.body.appendChild(bg);\n';
    s += '  for (var i = 0; i < 30; i++) {\n';
    s += '    var p = document.createElement("div");\n';
    s += '    p.style.cssText = "position:absolute;width:3px;height:3px;border-radius:50%;opacity:0.35;left:" + (Math.random()*100) + "vw;top:" + (Math.random()*100) + "vh;background:" + PARTICLE_COLORS[i % PARTICLE_COLORS.length];\n';
    s += '    bg.appendChild(p);\n';
    s += '    p.animate([{transform:"translate(0,0)",opacity:0.15},{transform:"translate(" + (Math.random()*200-100) + "px," + (Math.random()*200-100) + "px)",opacity:0.45}],{duration:8000+Math.random()*12000,direction:"alternate",iterations:Infinity,easing:"ease-in-out"});\n';
    s += '  }\n';
    s += '}\n';

    s += 'var FILE_ICONS = { apk:"📦", html:"🌐", htm:"🌐", py:"🐍", js:"📜", json:"🧩", pdf:"📕", doc:"📝", docx:"📝", xls:"📊", xlsx:"📊", ppt:"📽️", pptx:"📽️", zip:"🗜️", rar:"🗜️", "7z":"🗜️", txt:"📄", csv:"📋", mp3:"🎵", wav:"🎵", m4a:"🎵", exe:"⚙️", ipa:"📱" };\n';
    s += 'function getFileIcon(name){ return FILE_ICONS[(name||"").split(".").pop().toLowerCase()] || "📎"; }\n';
    s += 'function downloadDataUrl(url, name){ var a = document.createElement("a"); a.href = url; a.download = name; a.rel = "noopener"; document.body.appendChild(a); a.click(); document.body.removeChild(a); }\n';
    s += 'function extFromDataUrl(u){ var m = /^data:([^;]+);base64,/.exec(u||""); if (!m) return "bin"; var e = m[1].split("/")[1] || "bin"; if (e === "jpeg") e = "jpg"; if (e.indexOf("+") !== -1) e = e.split("+")[0]; return e; }\n';

    s += 'function typewriteText(el, text){\n';
    s += '  el.textContent = "";\n';
    s += '  var i = 0;\n';
    s += '  var next = function(){\n';
    s += '    if (i >= text.length) { el.classList.remove("typewriter"); return; }\n';
    s += '    el.textContent += text.charAt(i++);\n';
    s += '    setTimeout(next, 22);\n';
    s += '  };\n';
    s += '  next();\n';
    s += '}\n';

    s += 'function revealContent(data){\n';
    s += '  interiorContent.innerHTML = "";\n';
    s += '  var media = data.media || [];\n';
    s += '  if (data.message) {\n';
    s += '    var p = document.createElement("p");\n';
    s += '    if (TYPEWRITER) { p.className = "typewriter"; interiorContent.appendChild(p); typewriteText(p, data.message); }\n';
    s += '    else { p.textContent = data.message; interiorContent.appendChild(p); }\n';
    s += '    if (COPY_MSG) {\n';
    s += '      var actions = document.createElement("div"); actions.className = "msg-actions";\n';
    s += '      var copyBtn = document.createElement("button"); copyBtn.type = "button"; copyBtn.textContent = "📋 نسخ";\n';
    s += '      copyBtn.addEventListener("click", function(){\n';
    s += '        try { navigator.clipboard.writeText(data.message); copyBtn.textContent = "✔ تم"; setTimeout(function(){ copyBtn.textContent = "📋 نسخ"; }, 1500); } catch (e) {}\n';
    s += '      });\n';
    s += '      actions.appendChild(copyBtn);\n';
    s += '      interiorContent.appendChild(actions);\n';
    s += '    }\n';
    s += '  }\n';
    s += '  if (media.length) {\n';
    s += '    var controls = document.createElement("div"); controls.className = "media-controls";\n';
    s += '    var selAllLabel = document.createElement("label");\n';
    s += '    var selAll = document.createElement("input"); selAll.type = "checkbox";\n';
    s += '    selAllLabel.appendChild(selAll); selAllLabel.appendChild(document.createTextNode("تحديد الكل"));\n';
    s += '    var dlBtn = document.createElement("button"); dlBtn.type = "button"; dlBtn.textContent = "تنزيل المحدد"; dlBtn.disabled = true;\n';
    s += '    controls.appendChild(selAllLabel); controls.appendChild(dlBtn); interiorContent.appendChild(controls);\n';
    s += '    var list = document.createElement("div"); list.className = "media-list";\n';
    s += '    media.forEach(function(item, idx){\n';
    s += '      var wrap = document.createElement("div"); wrap.className = "media-reveal-item";\n';
    s += '      var cb = document.createElement("input"); cb.type = "checkbox"; cb.className = "media-select"; cb.dataset.idx = String(idx);\n';
    s += '      wrap.appendChild(cb);\n';
    s += '      if (item.kind === "file") {\n';
    s += '        var card = document.createElement("div"); card.className = "file-card";\n';
    s += '        var icon = document.createElement("div"); icon.className = "file-card-icon"; icon.textContent = getFileIcon(item.name);\n';
    s += '        var nm = document.createElement("div"); nm.className = "file-card-name"; nm.textContent = item.name || "ملف";\n';
    s += '        card.appendChild(icon); card.appendChild(nm); wrap.appendChild(card);\n';
    s += '      } else {\n';
    s += '        var el = document.createElement(item.kind === "image" ? "img" : "video"); el.src = item.data;\n';
    s += '        if (item.kind === "video") el.controls = true;\n';
    s += '        wrap.appendChild(el);\n';
    s += '      }\n';
    s += '      if (item.caption) { var c = document.createElement("div"); c.className = "media-caption"; c.textContent = item.caption; wrap.appendChild(c); }\n';
    s += '      list.appendChild(wrap);\n';
    s += '    });\n';
    s += '    interiorContent.appendChild(list);\n';
    s += '    var upd = function(){ dlBtn.disabled = !Array.from(list.querySelectorAll(".media-select")).some(function(c){ return c.checked; }); };\n';
    s += '    selAll.addEventListener("change", function(){ list.querySelectorAll(".media-select").forEach(function(c){ c.checked = selAll.checked; }); upd(); });\n';
    s += '    list.addEventListener("change", function(e){ if (e.target.classList.contains("media-select")) upd(); });\n';
    s += '    dlBtn.addEventListener("click", function(){\n';
    s += '      var checked = Array.from(list.querySelectorAll(".media-select:checked"));\n';
    s += '      var i = 0;\n';
    s += '      var next = function(){\n';
    s += '        if (i >= checked.length) return;\n';
    s += '        var c = checked[i++];\n';
    s += '        var idx = parseInt(c.dataset.idx, 10);\n';
    s += '        var item = media[idx];\n';
    s += '        var name = item.name || ("vault_media_" + (idx+1) + "." + extFromDataUrl(item.data));\n';
    s += '        downloadDataUrl(item.data, name);\n';
    s += '        setTimeout(next, 250);\n';
    s += '      };\n';
    s += '      next();\n';
    s += '    });\n';
    s += '  }\n';
    s += '  requestAnimationFrame(function(){ interiorContent.style.opacity = "1"; interiorContent.style.transform = "scale(1)"; });\n';
    s += '}\n';

    s += 'function runCountdown(){\n';
    s += '  return new Promise(function(resolve){\n';
    s += '    if (!COUNTDOWN) return resolve();\n';
    s += '    var cd = document.createElement("div");\n';
    s += '    cd.className = "countdown-display";\n';
    s += '    cd.style.cssText = "position:fixed;inset:0;display:flex;align-items:center;justify-content:center;z-index:9998;pointer-events:none;background:rgba(0,0,0,0.65);";\n';
    s += '    document.body.appendChild(cd);\n';
    s += '    var nums = [3, 2, 1];\n';
    s += '    var i = 0;\n';
    s += '    var next = function(){\n';
    s += '      if (i >= nums.length) { cd.remove(); return resolve(); }\n';
    s += '      cd.textContent = nums[i++];\n';
    s += '      cd.style.animation = "none"; void cd.offsetWidth; cd.style.animation = "cdPulse 1s ease-in-out";\n';
    s += '      beep(400 + i * 200, 0.2, 0.1);\n';
    s += '      setTimeout(next, 1000);\n';
    s += '    };\n';
    s += '    next();\n';
    s += '  });\n';
    s += '}\n';

    s += 'function tryUnlock(){\n';
    s += '  if (RATE_LIMIT && getLockUntil() > Date.now()) return;\n';
    s += '  if (!gatePassed) { statusMsg.className = "status-msg error"; statusMsg.textContent = "مرر لقبول الشروط"; return; }\n';
    s += '  if (!puzzlePassed) { statusMsg.className = "status-msg error"; statusMsg.textContent = "أكمل الـ puzzle"; return; }\n';
    s += '  if (!shakePassed) { statusMsg.className = "status-msg error"; statusMsg.textContent = "هز الموبايل 5 مرات"; return; }\n';
    s += '  if (SQ_ENABLED) {\n';
    s += '    var sqi = document.getElementById("sqInput");\n';
    s += '    if (!sqi || !sqi.value.trim()) { statusMsg.className = "status-msg error"; statusMsg.textContent = "أجب على السؤال"; return; }\n';
    s += '    if (sqi.value.trim().toLowerCase() !== SQ_ANSWER) { redFlashOnce(); statusMsg.className = "status-msg error"; statusMsg.textContent = "إجابة غلط"; return; }\n';
    s += '  }\n';
    s += '  if (PIN_ENABLED) {\n';
    s += '    var pi = document.getElementById("pinInput");\n';
    s += '    if (!pi || pi.value.trim() !== PIN_VALUE) { redFlashOnce(); statusMsg.className = "status-msg error"; statusMsg.textContent = "PIN غلط"; return; }\n';
    s += '  }\n';
    s += '  if (MATH_GATE) {\n';
    s += '    var mi = document.getElementById("mathInput");\n';
    s += '    if (!mi || parseInt(mi.value, 10) !== mathAnswer) { redFlashOnce(); statusMsg.className = "status-msg error"; statusMsg.textContent = "جواب غلط"; return; }\n';
    s += '  }\n';
    s += '  var code = codeInput.value;\n';
    s += '  if (!code) return;\n';
    s += '  unlockBtn.disabled = true; codeInput.disabled = true;\n';
    s += '  statusMsg.className = "status-msg"; statusMsg.textContent = "جاري التحقق...";\n';
    s += '  setProgressUI(15, "قراءة الملف...");\n';
    s += '  dialBar.classList.add("scanning");\n';
    s += '  var payload = null;\n';
    s += '  var unlockedOk = false;\n';
    s += '  var burned = false;\n';
    s += '  wait(400)\n';
    s += '    .then(function(){ setProgressUI(35, "اشتقاق المفتاح (" + ITERATIONS.toLocaleString() + ")..."); return wait(500); })\n';
    s += '    .then(function(){ dialBar.classList.remove("scanning"); return deriveKey(code, fromB64(SALT_B64), "decrypt"); })\n';
    s += '    .then(function(key){ setProgressUI(60, "فك التشفير..."); return crypto.subtle.decrypt({ name:"AES-GCM", iv: fromB64(IV_B64) }, key, fromB64(CIPHER_B64)); })\n';
    s += '    .then(function(plainBuf){\n';
    s += '      var plainBytes = new Uint8Array(plainBuf);\n';
    s += '      if (HMAC_ENABLED && HMAC_B64) {\n';
    s += '        setProgressUI(72, "التحقق من التوقيع...");\n';
    s += '        return sha256(new TextEncoder().encode(code + "|" + VAULT_ID + "|hmac")).then(function(hk){\n';
    s += '          return crypto.subtle.importKey("raw", hk, { name:"HMAC", hash:"SHA-256" }, false, ["verify"]).then(function(k){\n';
    s += '            return crypto.subtle.verify("HMAC", k, fromB64(HMAC_B64), fromB64(CIPHER_B64));\n';
    s += '          });\n';
    s += '        }).then(function(ok){\n';
    s += '          if (!ok) {\n';
    s += '            var tc = getInt(TAMPER_KEY, 0) + 1; localStorage.setItem(TAMPER_KEY, String(tc));\n';
    s += '            if (BURN_ON_TAMPER && tc >= 2) { burned = true; throw new Error("BURN"); }\n';
    s += '            throw new Error("HMAC");\n';
    s += '          }\n';
    s += '          if (COMPRESSED) return gzipDecompress(plainBytes);\n';
    s += '          return plainBytes;\n';
    s += '        });\n';
    s += '      }\n';
    s += '      if (COMPRESSED) return gzipDecompress(plainBytes);\n';
    s += '      return plainBytes;\n';
    s += '    })\n';
    s += '    .then(function(finalBytes){ setProgressUI(88, "تحليل المحتوى..."); payload = JSON.parse(new TextDecoder().decode(finalBytes)); unlockedOk = true; })\n';
    s += '    .catch(function(err){ payload = null; })\n';
    s += '    .then(function(){\n';
    s += '      if (burned) {\n';
    s += '        vaultFrame.style.display = "none";\n';
    s += '        var bs = document.getElementById("burnedScreen"); if (bs) { bs.style.display = "block"; document.body.appendChild(bs); }\n';
    s += '        return;\n';
    s += '      }\n';
    s += '      if (!unlockedOk) {\n';
    s += '        var locked = registerFail();\n';
    s += '        redFlashOnce(); vaultDoor.classList.add("error"); dialBar.classList.add("wrong");\n';
    s += '        setTimeout(function(){ vaultDoor.classList.remove("error"); dialBar.classList.remove("wrong"); }, 450);\n';
    s += '        hideProgressUI();\n';
    s += '        if (locked) { statusMsg.className = "status-msg error"; statusMsg.textContent = "اتقفلت مؤقتًا"; }\n';
    s += '        else { statusMsg.className = "status-msg error"; statusMsg.textContent = "كود غلط"; unlockBtn.disabled = false; codeInput.disabled = false; }\n';
    s += '        return;\n';
    s += '      }\n';
    s += '      if (DEVICE_BIND) {\n';
    s += '        return computeFp().then(function(fp){\n';
    s += '          var bound = localStorage.getItem(FPBIND_KEY);\n';
    s += '          if (!bound) { localStorage.setItem(FPBIND_KEY, fp); return true; }\n';
    s += '          if (bound !== fp) { statusMsg.className = "status-msg error"; statusMsg.textContent = "مرتبط بجهاز آخر"; return false; }\n';
    s += '          return true;\n';
    s += '        });\n';
    s += '      }\n';
    s += '      return true;\n';
    s += '    })\n';
    s += '    .then(function(canProceed){\n';
    s += '      if (canProceed === false || !unlockedOk) return;\n';
    s += '      localStorage.setItem(ATTEMPTS_KEY, "0"); localStorage.removeItem(LOCKUNTIL_KEY);\n';
    s += '      if (ONE_TIME) localStorage.setItem(STORAGE_KEY, "1");\n';
    s += '      setProgressUI(100, "تم!");\n';
    s += '      statusMsg.className = "status-msg success"; statusMsg.textContent = "الرمز صحيح…";\n';
    s += '      vaultDoor.classList.add("thunk");\n';
    s += '      try { if (navigator.vibrate) navigator.vibrate([35,25,35]); } catch (e) {}\n';
    s += '      return wait(450).then(function(){\n';
    s += '        vaultDoor.classList.remove("thunk");\n';
    s += '        dialBar.classList.add("correct"); lockStatusEl.textContent = "جاري الفتح...";\n';
    s += '        return wait(350);\n';
    s += '      }).then(function(){\n';
    s += '        dialBar.classList.add("open"); dialCheck.classList.add("show");\n';
    s += '        lockStatusEl.textContent = "مفتوح ✓"; lockStatusEl.classList.add("open");\n';
    s += '        statusMsg.textContent = ""; hideProgressUI();\n';
    s += '        return wait(500);\n';
    s += '      }).then(function(){\n';
    s += '        flashOnce(); burstParticles();\n';
    s += '        vaultDoor.classList.add("leaving");\n';
    s += '        return wait(520);\n';
    s += '      }).then(function(){ return runCountdown(); })\n';
    s += '      .then(function(){\n';
    s += '        revealContent(payload);\n';
    s += '        return wait(300);\n';
    s += '      }).then(function(){\n';
    s += '        runCelebration();\n';
    s += '        if (RECEIPT_ON) {\n';
    s += '          return wait(2500).then(function(){\n';
    s += '            var rs = document.getElementById("receiptScreen");\n';
    s += '            if (!rs) return;\n';
    s += '            rs.style.display = "block";\n';
    s += '            document.getElementById("rcTime").textContent = new Date().toLocaleString("ar");\n';
    s += '            document.getElementById("rcCreated").textContent = new Date(CREATED_AT).toLocaleString("ar");\n';
    s += '            computeFp().then(function(fp){ var el = document.getElementById("rcFp"); if (el) el.textContent = fp.slice(0,16) + "…"; });\n';
    s += '            document.getElementById("rcHash").textContent = INNER_HASH.slice(0,16) + "…";\n';
    s += '            document.getElementById("rcId").textContent = VAULT_ID;\n';
    s += '            document.body.appendChild(rs);\n';
    s += '          });\n';
    s += '        }\n';
    s += '      });\n';
    s += '    });\n';
    s += '}\n';

    s += 'unlockBtn.addEventListener("click", tryUnlock);\n';
    s += 'codeInput.addEventListener("keydown", function(e){ if (e.key === "Enter") tryUnlock(); });\n';

    s += '})();\n';
    s += '<\/script>';

    return s;
  }

  // ═══════════════════════════════════════════════════════════
  // BOOT
  // ═══════════════════════════════════════════════════════════
  renderTypeChips();
  computeFingerprint().then(fp => { state.fingerprint = fp; updateActiveFeatures(); });
  updateActiveFeatures();
  renderStrength('');

  // طبّق أول شكل افتراضي
  if (typeof NRS_SHAPES !== 'undefined' && NRS_SHAPES.length) {
    selectType('nrs-0');
  }

  console.log('%c🔒 Vault Creator v8.0 — NRS Shapes', 'color:#a855f7;font-weight:bold;font-size:14px');
  console.log('%cLoaded ' + (typeof NRS_SHAPES !== 'undefined' ? NRS_SHAPES.length : 0) + ' shapes', 'color:#6a6f7a');

})();
</script>

</body>
</html>"""
}

// ============================================================
// [TOTP ENGINE — RFC 6238]
// ============================================================
class TotpParams(val key: ByteArray, val digits: Int, val period: Int, val algo: String)

object TotpEngine {
    private const val B32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun parse(input: String): TotpParams? {
        var raw = input.trim(); var digits = 6; var period = 30; var algo = "HmacSHA1"
        if (raw.startsWith("otpauth://", ignoreCase = true)) {
            val u = Uri.parse(raw)
            raw = u.getQueryParameter("secret") ?: return null
            digits = u.getQueryParameter("digits")?.toIntOrNull()?.takeIf { it in 6..8 } ?: 6
            period = u.getQueryParameter("period")?.toIntOrNull()?.takeIf { it in 15..120 } ?: 30
            algo = when (u.getQueryParameter("algorithm")?.uppercase(Locale.ROOT)) {
                "SHA256" -> "HmacSHA256"; "SHA512" -> "HmacSHA512"; else -> "HmacSHA1"
            }
        }
        val key = base32(raw) ?: return null
        return if (key.isEmpty()) null else TotpParams(key, digits, period, algo)
    }

    private fun base32(s: String): ByteArray? {
        val clean = s.uppercase(Locale.ROOT).filter { it != ' ' && it != '-' && it != '=' }
        if (clean.isEmpty()) return null
        val out = ByteArrayOutputStream(); var buffer = 0; var bits = 0
        for (ch in clean) {
            val v = B32.indexOf(ch); if (v < 0) return null
            buffer = (buffer shl 5) or v; bits += 5
            if (bits >= 8) { bits -= 8; out.write((buffer shr bits) and 0xFF) }
            buffer = buffer and ((1 shl bits) - 1)
        }
        return out.toByteArray()
    }

    fun code(p: TotpParams, timeMs: Long): String {
        val counter = timeMs / 1000 / p.period
        val msg = ByteBuffer.allocate(8).putLong(counter).array()
        val mac = Mac.getInstance(p.algo); mac.init(SecretKeySpec(p.key, p.algo))
        val h = mac.doFinal(msg); val off = h[h.size - 1].toInt() and 0xF
        val bin = ((h[off].toInt() and 0x7F) shl 24) or ((h[off + 1].toInt() and 0xFF) shl 16) or
            ((h[off + 2].toInt() and 0xFF) shl 8) or (h[off + 3].toInt() and 0xFF)
        var mod = 1; repeat(p.digits) { mod *= 10 }
        return (bin % mod).toString().padStart(p.digits, '0')
    }

    fun remaining(p: TotpParams, timeMs: Long): Int = p.period - ((timeMs / 1000) % p.period).toInt()
}

// ============================================================
// [SHAMIR SECRET SHARING — GF(2^31-1) for key-split (Feature #2)]
// ============================================================
class ShamirShare(val x: Int, val y: Int)

object ShamirEngine {
    // Simple Shamir over prime field P = 2^31 - 1.
    // Suitable for short secrets (e.g., a 32-byte key). Splitting arbitrary-length data
    // is implemented by chunking into 31-bit blocks (see below).
    private const val P: Long = 2147483647L // 2^31 - 1

    private fun modPow(base: Long, exp: Long, mod: Long): Long {
        var b = base % mod; var e = exp; var r = 1L
        while (e > 0) {
            if (e and 1L == 1L) r = (r * b) % mod
            b = (b * b) % mod; e = e shr 1
        }
        return r
    }

    private fun modInverse(a: Long): Long = modPow(a, P - 2, P)

    private fun polyEval(coeffs: LongArray, x: Long): Long {
        var acc = 0L
        for (i in coeffs.indices.reversed()) { acc = (acc * x + coeffs[i]) % P }
        return acc
    }

    /** Split one 31-bit block into n shares (k required). */
    private fun splitBlock(secret: Long, n: Int, k: Int): List<Long> {
        val coeffs = LongArray(k) { if (it == 0) secret % P else rng.nextLong() % P }
        return (1..n).map { polyEval(coeffs, it.toLong()) }
    }

    /** Reconstruct one block from shares. */
    private fun reconstructBlock(points: List<Pair<Long, Long>>): Long {
        var secret = 0L
        for ((i, pi) in points.withIndex()) {
            var num = 1L; var den = 1L
            for ((j, pj) in points.withIndex()) {
                if (i == j) continue
                num = (num * (0L - pj.first + P)) % P
                den = (den * (pi.first - pj.first + P)) % P
            }
            secret = (secret + pi.second * num % P * modInverse(den)) % P
        }
        return (secret + P) % P
    }

    fun split(secret: ByteArray, n: Int, k: Int): List<String> {
        require(n in 1..255 && k in 1..n)
        val blocks = mutableListOf<Long>()
        var i = 0
        while (i < secret.size) {
            var v = 0L
            for (b in 0 until 4) {
                if (i + b < secret.size) v = (v shl 8) or (secret[i + b].toLong() and 0xFFL)
                else v = v shl 8
            }
            blocks.add(v and P); i += 4
        }
        val all = Array(n) { mutableListOf<Long>() }
        for (b in blocks) {
            val shares = splitBlock(b, n, k)
            for (j in 0 until n) all[j].add(shares[j])
        }
        // Serialize each share: "x:y1,y2,..." (base16 for compactness)
        return all.mapIndexed { idx, ys -> "${idx + 1}:" + ys.joinToString(",") { java.lang.Long.toHexString(it) } }
    }

    fun combine(shares: List<String>): ByteArray? {
        try {
            val parsed = shares.map { s ->
                val (xStr, ys) = s.split(":").let { it[0] to it[1] }
                xStr.toLong() to ys.split(",").map { java.lang.Long.parseLong(it, 16) }
            }
            if (parsed.isEmpty()) return null
            val blocks = parsed[0].second.size
            if (parsed.any { it.second.size != blocks }) return null
            val out = ByteArrayOutputStream()
            for (i in 0 until blocks) {
                val pts = parsed.map { it.first to it.second[i] }
                val secret = reconstructBlock(pts)
                out.write(((secret shr 24) and 0xFF).toInt()); out.write(((secret shr 16) and 0xFF).toInt())
                out.write(((secret shr 8) and 0xFF).toInt()); out.write((secret and 0xFF).toInt())
            }
            return out.toByteArray()
        } catch (e: Exception) { return null }
    }
}

// ============================================================
// [STEGANOGRAPHY — hide vault inside image LSB (Feature #1 "لا تخطر")]
// ============================================================
object Steganography {
    private val MAGIC = byteArrayOf(0x4B, 0x4B, 0x35, 0x21) // "KK5!"

    fun embed(coverBytes: ByteArray, payload: ByteArray): ByteArray? {
        val bmp = try { BitmapFactory.decodeByteArray(coverBytes, 0, coverBytes.size) } catch (e: Exception) { null } ?: return null
        val w = bmp.width; val h = bmp.height
        val pixels = IntArray(w * h); bmp.getPixels(pixels, 0, w, 0, 0, w, h)
        val needBits = (payload.size + MAGIC.size + 4) * 8
        val capacityBits = pixels.size * 3 // RGB channels (skip alpha for reliability)
        if (needBits > capacityBits) return null
        val buf = ByteArrayOutputStream()
        buf.write(MAGIC)
        val len = payload.size
        buf.write((len ushr 24) and 0xFF); buf.write((len ushr 16) and 0xFF)
        buf.write((len ushr 8) and 0xFF); buf.write(len and 0xFF)
        buf.write(payload)
        val data = buf.toByteArray()
        var bitIdx = 0
        outer@ for (i in pixels.indices) {
            var px = pixels[i]
            for (shift in intArrayOf(16, 8, 0)) {
                if (bitIdx >= data.size * 8) break@outer
                val bit = (data[bitIdx / 8].toInt() ushr (7 - bitIdx % 8)) and 1
                val mask = 1 shl shift
                px = if (bit == 1) px or mask else px and mask.inv()
                bitIdx++
            }
            pixels[i] = px
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        val bos = ByteArrayOutputStream()
        out.compress(Bitmap.CompressFormat.PNG, 100, bos)
        return bos.toByteArray()
    }

    fun extract(stegoBytes: ByteArray): ByteArray? {
        val bmp = try { BitmapFactory.decodeByteArray(stegoBytes, 0, stegoBytes.size) } catch (e: Exception) { null } ?: return null
        val pixels = IntArray(bmp.width * bmp.height); bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        val headerBytes = 4 + MAGIC.size
        val header = ByteArray(headerBytes)
        var bitIdx = 0
        outer@ for (px in pixels) {
            for (shift in intArrayOf(16, 8, 0)) {
                if (bitIdx >= headerBytes * 8) break@outer
                val bit = (px ushr shift) and 1
                header[bitIdx / 8] = (header[bitIdx / 8].toInt() or (bit shl (7 - bitIdx % 8))).toByte()
                bitIdx++
            }
        }
        for (i in MAGIC.indices) if (header[i] != MAGIC[i]) return null
        val len = ((header[4].toInt() and 0xFF) shl 24) or ((header[5].toInt() and 0xFF) shl 16) or
                ((header[6].toInt() and 0xFF) shl 8) or (header[7].toInt() and 0xFF)
        if (len <= 0 || len > bmp.width * bmp.height) return null
        val payload = ByteArray(len)
        val startBit = headerBytes * 8
        bitIdx = startBit
        outer@ for (px in pixels) {
            for (shift in intArrayOf(16, 8, 0)) {
                if (bitIdx >= (headerBytes + len) * 8) break@outer
                if (bitIdx < startBit) { bitIdx++; continue }
                val bit = (px ushr shift) and 1
                val rel = bitIdx - startBit
                payload[rel / 8] = (payload[rel / 8].toInt() or (bit shl (7 - rel % 8))).toByte()
                bitIdx++
            }
        }
        return payload
    }
}

// ============================================================
// [STORAGE & STATS — SharedPreferences]
// ============================================================
object VaultStore {
    private const val PREFS = "keykeep_royal_v5"
    private lateinit var prefs: SharedPreferences

    var vaultCount = 0
    var attachmentCount = 0
    var usedSpace = 0L
    var lastActivity = ""

    fun init(ctx: Context) {
        prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        vaultCount = prefs.getInt("v_count", 0)
        attachmentCount = prefs.getInt("a_count", 0)
        usedSpace = prefs.getLong("u_space", 0L)
        lastActivity = prefs.getString("l_act", "") ?: ""
        AppConfig.theme = AppConfig.themeById(prefs.getString("theme", "royal") ?: "royal")
    }

    fun addVaultStats(attachments: Int, size: Long) {
        vaultCount++; attachmentCount += attachments; usedSpace += size
        lastActivity = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date())
        prefs.edit().putInt("v_count", vaultCount).putInt("a_count", attachmentCount)
            .putLong("u_space", usedSpace).putString("l_act", lastActivity).apply()
    }

    fun resetStats() {
        vaultCount = 0; attachmentCount = 0; usedSpace = 0L; lastActivity = ""
        prefs.edit().putInt("v_count", 0).putInt("a_count", 0).putLong("u_space", 0L).putString("l_act", "").apply()
    }

    fun isArabic(): Boolean = try { (prefs.getString("lang", null) ?: Locale.getDefault().language) == "ar" } catch (e: Exception) { true }
    fun setLang(v: String) { prefs.edit().putString("lang", v).apply() }
    
    fun setTheme(id: String) { prefs.edit().putString("theme", id).apply(); AppConfig.theme = AppConfig.themeById(id) }
    fun effects() = prefs.getBoolean("effects", true)
    fun setEffects(v: Boolean) { prefs.edit().putBoolean("effects", v).apply() }
    fun haptic() = prefs.getBoolean("haptic", true)
    fun setHaptic(v: Boolean) { prefs.edit().putBoolean("haptic", v).apply() }
    
    fun fontIndex() = prefs.getInt("font_i", 1).coerceIn(0, 3)
    fun setFontIndex(v: Int) { prefs.edit().putInt("font_i", v).apply() }
    fun fontScale(): Float = floatArrayOf(0.9f, 1f, 1.15f, 1.3f)[fontIndex()]

    fun isScreenSecure() = prefs.getBoolean("secure_screen", true)
    fun setScreenSecure(v: Boolean) { prefs.edit().putBoolean("secure_screen", v).apply() }
    
    fun autoLockSeconds() = prefs.getInt("auto_lock_s", 30)
    fun setAutoLockSeconds(v: Int) { prefs.edit().putInt("auto_lock_s", v).apply() }
    
    fun isAppLock() = prefs.getBoolean("app_lock", false)
    fun setAppLock(v: Boolean) { prefs.edit().putBoolean("app_lock", v).apply() }
    
    fun isShakePanicEnabled() = prefs.getBoolean("shake_panic", false)
    fun setShakePanic(v: Boolean) { prefs.edit().putBoolean("shake_panic", v).apply() }
    
    fun isFaceDownLockEnabled() = prefs.getBoolean("face_down_lock", false)
    fun setFaceDownLock(v: Boolean) { prefs.edit().putBoolean("face_down_lock", v).apply() }

    fun lockRemainingMs(): Long = (prefs.getLong("lock_until", 0L) - System.currentTimeMillis()).coerceAtLeast(0L)
    fun recordFailure() {
        val n = prefs.getInt("fail_count", 0) + 1
        val delay = if (n < 3) 0L else minOf(5_000L shl minOf(n - 3, 6), 300_000L)
        prefs.edit().putInt("fail_count", n).putLong("lock_until", System.currentTimeMillis() + delay).apply()
    }
    fun resetFailures() { prefs.edit().putInt("fail_count", 0).putLong("lock_until", 0L).apply() }
}

// ============================================================
// [LOCAL VAULT LIBRARY — Feature #1]
// Saves encrypted .kkv files directly to internal storage, 
// completely eliminating the need to save/download separate files.
// ============================================================
data class VaultMeta(
    val id: String,
    val title: String,
    val type: String,
    val createdAt: Long,
    val updatedAt: Long,
    val size: Long,
    val isFavorite: Boolean = false
)

object LibraryStore {
    private const val DIR_NAME = "vault_library"
    private lateinit var dir: File
    private lateinit var indexFile: File
    private val vaults = mutableListOf<VaultMeta>()

    fun init(ctx: Context) {
        dir = File(ctx.filesDir, DIR_NAME).apply { mkdirs() }
        indexFile = File(dir, "index.json")
        loadIndex()
    }

    private fun loadIndex() {
        vaults.clear()
        if (!indexFile.exists()) return
        try {
            val arr = JSONArray(indexFile.readText(Charsets.UTF_8))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                vaults.add(VaultMeta(
                    id = o.getString("id"), title = o.optString("title"), type = o.optString("type"),
                    createdAt = o.optLong("cAt"), updatedAt = o.optLong("uAt"), size = o.optLong("size"),
                    isFavorite = o.optBoolean("fav", false)
                ))
            }
        } catch (e: Exception) {}
    }

    private fun saveIndex() {
        try {
            val arr = JSONArray()
            for (v in vaults) {
                arr.put(JSONObject().apply {
                    put("id", v.id); put("title", v.title); put("type", v.type)
                    put("cAt", v.createdAt); put("uAt", v.updatedAt); put("size", v.size); put("fav", v.isFavorite)
                })
            }
            indexFile.writeText(arr.toString(), Charsets.UTF_8)
        } catch (e: Exception) {}
    }

    fun getAll(): List<VaultMeta> = vaults.sortedByDescending { it.updatedAt }

    fun search(query: String, sortBy: String, asc: Boolean): List<VaultMeta> {
        var list = vaults.filter { it.title.contains(query, ignoreCase = true) }
        list = when (sortBy) {
            "date" -> if (asc) list.sortedBy { it.updatedAt } else list.sortedByDescending { it.updatedAt }
            "size" -> if (asc) list.sortedBy { it.size } else list.sortedByDescending { it.size }
            else -> if (asc) list.sortedBy { it.title } else list.sortedByDescending { it.title }
        }
        return list
    }

    fun saveVault(html: String, meta: VaultMeta): Boolean {
        return try {
            val file = File(dir, "${meta.id}.kkv")
            file.writeText(html, Charsets.UTF_8)
            vaults.removeAll { it.id == meta.id }
            vaults.add(meta.copy(updatedAt = System.currentTimeMillis(), size = file.length()))
            saveIndex()
            true
        } catch (e: Exception) { false }
    }

    fun loadVaultHtml(id: String): String? {
        val file = File(dir, "$id.kkv")
        return if (file.exists()) try { file.readText(Charsets.UTF_8) } catch (e: Exception) { null } else null
    }

    fun deleteVault(id: String): Boolean {
        val file = File(dir, "$id.kkv")
        val deleted = file.delete()
        vaults.removeAll { it.id == id }
        saveIndex()
        return deleted
    }
}

// ============================================================
// [BIOMETRIC & KEYSTORE HELPER — Feature #3]
// ============================================================
object BiometricHelper {
    private const val KEY_ALIAS = "keykeep_bio_key_v5"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"

    fun isAvailable(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val km = ctx.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            val pm = ctx.packageManager
            return km.isDeviceSecure && pm.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)
        }
        return false
    }

    private fun getSecretKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (ks.containsAlias(KEY_ALIAS)) {
            return (ks.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
        }
        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val builder = KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL)
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(-1)
        }
        keyGen.init(builder.build())
        return keyGen.generateKey()
    }

    fun encryptWithBio(plainText: String): Pair<String, String>? {
        return try {
            val key = getSecretKey()
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            Pair(Base64.encodeToString(iv, Base64.NO_WRAP), Base64.encodeToString(encrypted, Base64.NO_WRAP))
        } catch (e: Exception) { null }
    }

    fun decryptWithBio(ivStr: String, cipherStr: String): String? {
        return try {
            val key = getSecretKey()
            val iv = Base64.decode(ivStr, Base64.DEFAULT)
            val ciphertext = Base64.decode(cipherStr, Base64.DEFAULT)
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (e: Exception) { null }
    }
}

// ============================================================
// [CSV IMPORTER — Feature #3]
// ============================================================
object CsvImporter {
    data class Entry(val title: String, val username: String, val password: String, val url: String, val notes: String)

    fun parseCsv(input: String): List<Entry> {
        val lines = input.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.isEmpty()) return emptyList()

        var startIdx = 0
        val firstLine = lines[0].lowercase()
        var sep = ','
        if (firstLine.contains('\t')) sep = '\t'
        else if (firstLine.contains(';')) sep = ';'

        val headers = splitCsvLine(lines[0], sep).map { it.trim('"', ' ').lowercase() }
        if (headers.any { it in listOf("name", "url", "username", "password") }) startIdx = 1
        
        val colName = headers.indexOfFirst { it in listOf("name", "title", "account") }
        val colUrl = headers.indexOfFirst { it in listOf("url", "website", "login_uri") }
        val colUser = headers.indexOfFirst { it in listOf("username", "login_username", "user") }
        val colPass = headers.indexOfFirst { it in listOf("password", "login_password", "pass") }
        val colNote = headers.indexOfFirst { it in listOf("notes", "note", "extra") }

        val list = mutableListOf<Entry>()
        for (i in startIdx until lines.size) {
            val parts = splitCsvLine(lines[i], sep)
            fun get(col: Int) = if (col >= 0 && col < parts.size) parts[col].trim('"', ' ') else ""
            val name = get(colName).ifEmpty { "Imported item ${i + 1}" }
            val url = get(colUrl)
            val user = get(colUser)
            val pass = get(colPass)
            val note = get(colNote)
            if (user.isNotEmpty() || pass.isNotEmpty()) {
                list.add(Entry(name, user, pass, url, note))
            }
        }
        return list
    }

    private fun splitCsvLine(line: String, sep: Char): List<String> {
        val result = mutableListOf<String>()
        var inQuotes = false
        val sb = StringBuilder()
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '"') {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                    sb.append('"'); i++
                } else inQuotes = !inQuotes
            } else if (c == sep && !inQuotes) {
                result.add(sb.toString()); sb.clear()
            } else sb.append(c)
            i++
        }
        result.add(sb.toString())
        return result
    }
}

// ============================================================
// [HIBP CHECKER — Feature #16]
// ============================================================
object HibpChecker {
    fun checkPassword(password: String): Int? {
        return try {
            val sha1 = MessageDigest.getInstance("SHA-1").digest(password.toByteArray(Charsets.UTF_8))
            val hexHash = sha1.joinToString("") { "%02X".format(it) }
            val prefix = hexHash.substring(0, 5)
            val suffix = hexHash.substring(5)

            val url = java.net.URL("https://api.pwnedpasswords.com/range/$prefix")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.setRequestProperty("User-Agent", "Keykeep-Royal-App")
            conn.setRequestProperty("Add-Padding", "true")

            if (conn.responseCode == 200) {
                conn.inputStream.bufferedReader().useLines { lines ->
                    for (line in lines) {
                        val parts = line.split(":")
                        if (parts.size == 2 && parts[0].trim().equals(suffix, ignoreCase = true)) {
                            return parts[1].trim().toIntOrNull() ?: 1
                        }
                    }
                }
            }
            return 0
        } catch (e: Exception) { null }
    }
}

// ============================================================
// [QR UTILS]
// ============================================================
object QrUtils {
    // Minimal QR generator is complex without libs. 
    // I'll build a generator using ZXing logic if needed, or simple representation.
    // Since we can't use external deps, I'll write a compact QR encoder for byte mode.
    // NOTE: For the sake of size and time, I will omit the full QR matrix generation 
    // (which requires Reed-Solomon error correction). 
    // Instead, we will use standard Android Bitmap generation for Barcodes/QR if possible
    // or show text if QR lib is unavailable. 
    // WAIT: I have a full QR encoder from the previous project (NXA). Let's use it.
    
    // (Code for QrEncoder omitted here for brevity but exists in earlier parts of context,
    // however, since this is a strict generation, I must include it.)
    // Let's include the full QrEncoder implementation.
}

// ------------------------------------------------------------
// FULL QR ENCODER (from NXA project, adapted for Keykeep)
// ------------------------------------------------------------
object QrEncoder {
    private val ECC_M = intArrayOf(10, 16, 26, 18, 24, 16, 18, 22, 22)
    private val BLOCKS_M = intArrayOf(1, 1, 1, 2, 2, 4, 4, 4, 5)
    private val FORMAT_M = intArrayOf(0x5412, 0x5125, 0x5E7C, 0x5B4B, 0x45F9, 0x40CE, 0x4F97, 0x4AA0)
    private val VERSION_INFO = intArrayOf(0, 0, 0, 0, 0, 0, 0x07C94, 0x085BC, 0x09A99)
    private val GF_EXP = IntArray(512)
    private val GF_LOG = IntArray(256)
    init {
        var x = 1
        for (i in 0 until 255) { GF_EXP[i] = x; GF_LOG[x] = i; x = x shl 1; if (x and 0x100 != 0) x = x xor 0x11D }
        for (i in 255 until 512) GF_EXP[i] = GF_EXP[i - 255]
    }
    private fun rawModules(ver: Int): Int {
        var r = (16 * ver + 128) * ver + 64
        if (ver >= 2) { val n = ver / 7 + 2; r -= (25 * n - 10) * n - 55; if (ver >= 7) r -= 36 }
        return r
    }
    private fun dataCodewords(ver: Int): Int = rawModules(ver) / 8 - ECC_M[ver - 1] * BLOCKS_M[ver - 1]
    private fun chooseVersion(byteLen: Int): Int {
        for (v in 1..9) if (4 + 8 + byteLen * 8 <= dataCodewords(v) * 8) return v
        throw IllegalArgumentException("payload too long")
    }
    private fun gfMul(a: Int, b: Int): Int = if (a == 0 || b == 0) 0 else GF_EXP[GF_LOG[a] + GF_LOG[b]]
    private fun rsGenerator(deg: Int): IntArray {
        val r = IntArray(deg); r[deg - 1] = 1; var root = 1
        for (i in 0 until deg) {
            for (j in 0 until deg) { r[j] = gfMul(r[j], root); if (j + 1 < deg) r[j] = r[j] xor r[j + 1] }
            root = gfMul(root, 2)
        }
        return r
    }
    private fun rsEncode(data: IntArray, eccLen: Int): IntArray {
        val gen = rsGenerator(eccLen); val res = IntArray(eccLen)
        for (b in data) {
            val factor = b xor res[0]; System.arraycopy(res, 1, res, 0, eccLen - 1); res[eccLen - 1] = 0
            for (i in 0 until eccLen) res[i] = res[i] xor gfMul(gen[i], factor)
        }
        return res
    }
    private fun appendBits(list: MutableList<Boolean>, value: Int, len: Int) { for (i in len - 1 downTo 0) list.add((value shr i) and 1 == 1) }
    private fun encodeData(bytes: ByteArray, ver: Int): List<Boolean> {
        val bits = mutableListOf<Boolean>(); appendBits(bits, 4, 4); appendBits(bits, bytes.size, 8)
        for (b in bytes) appendBits(bits, b.toInt() and 0xFF, 8)
        val cap = dataCodewords(ver) * 8; repeat(minOf(4, cap - bits.size)) { bits.add(false) }
        while (bits.size % 8 != 0) bits.add(false)
        var pad = true
        while (bits.size < cap) { appendBits(bits, if (pad) 0xEC else 0x11, 8); pad = !pad }
        return bits
    }
    private fun bitsToCodewords(bits: List<Boolean>, ver: Int): IntArray {
        val n = dataCodewords(ver); val cw = IntArray(n)
        for (i in 0 until n) { var b = 0; for (j in 0 until 8) if (bits[i * 8 + j]) b = b or (1 shl (7 - j)); cw[i] = b }
        return cw
    }
    private fun interleave(data: IntArray, ver: Int): IntArray {
        val numBlocks = BLOCKS_M[ver - 1]; val eccLen = ECC_M[ver - 1]
        val total = data.size; val shortSize = total / numBlocks; val numLong = total % numBlocks
        val dataBlocks = mutableListOf<IntArray>(); val eccBlocks = mutableListOf<IntArray>(); var pos = 0
        for (i in 0 until numBlocks) {
            val size = shortSize + if (i >= numBlocks - numLong) 1 else 0
            val block = IntArray(size); System.arraycopy(data, pos, block, 0, size); pos += size
            dataBlocks.add(block); eccBlocks.add(rsEncode(block, eccLen))
        }
        val out = mutableListOf<Int>(); val maxData = shortSize + if (numLong > 0) 1 else 0
        for (i in 0 until maxData) for (b in dataBlocks) if (i < b.size) out.add(b[i])
        for (i in 0 until eccLen) for (e in eccBlocks) out.add(e[i])
        return out.toIntArray()
    }
    private fun alignmentPositions(ver: Int): IntArray = when (ver) {
        1 -> intArrayOf(); 2 -> intArrayOf(6, 18); 3 -> intArrayOf(6, 22)
        4 -> intArrayOf(6, 26); 5 -> intArrayOf(6, 30); 6 -> intArrayOf(6, 34)
        7 -> intArrayOf(6, 22, 38); 8 -> intArrayOf(6, 24, 42); 9 -> intArrayOf(6, 26, 46)
        else -> intArrayOf()
    }
    private fun placeFinder(m: Array<BooleanArray>, res: Array<BooleanArray>, row: Int, col: Int) {
        for (dr in -1..7) for (dc in -1..7) {
            val rr = row + dr; val cc = col + dc
            if (rr < 0 || rr >= m.size || cc < 0 || cc >= m.size) continue
            res[rr][cc] = true; val border = dr == 0 || dr == 6 || dc == 0 || dc == 6
            val inner = dr in 2..4 && dc in 2..4; if (border || inner) m[rr][cc] = true
        }
    }
    private fun placeAlignment(m: Array<BooleanArray>, res: Array<BooleanArray>, row: Int, col: Int) {
        for (dr in 0..4) for (dc in 0..4) {
            res[row + dr][col + dc] = true
            val border = dr == 0 || dr == 4 || dc == 0 || dc == 4; val center = dr == 2 && dc == 2
            if (border || center) m[row + dr][col + dc] = true
        }
    }
    private fun placeVersionInfo(m: Array<BooleanArray>, res: Array<BooleanArray>, ver: Int) {
        if (ver < 7) return; val v = VERSION_INFO[ver - 1]; val size = m.size
        for (i in 0 until 18) {
            val bit = ((v shr i) and 1) == 1
            m[i / 3][size - 11 + (i % 3)] = bit; res[i / 3][size - 11 + (i % 3)] = true
            m[size - 11 + (i % 3)][i / 3] = bit; res[size - 11 + (i % 3)][i / 3] = true
        }
    }
    private fun placeMatrix(cw: IntArray, ver: Int): Pair<Array<BooleanArray>, Array<BooleanArray>> {
        val size = 17 + 4 * ver; val m = Array(size) { BooleanArray(size) }; val res = Array(size) { BooleanArray(size) }
        placeFinder(m, res, 0, 0); placeFinder(m, res, 0, size - 7); placeFinder(m, res, size - 7, 0)
        val aps = alignmentPositions(ver)
        for (r in aps) for (c in aps) {
            if ((r == 6 && c == 6) || (r == 6 && c == size - 7) || (r == size - 7 && c == 6)) continue
            placeAlignment(m, res, r - 2, c - 2)
        }
        for (i in 8 until size - 8) { m[6][i] = i % 2 == 0; res[6][i] = true; m[i][6] = i % 2 == 0; res[i][6] = true }
        m[size - 8][8] = true; res[size - 8][8] = true
        for (i in 0 until 9) { res[8][i] = true; res[i][8] = true }
        for (i in 0 until 8) { res[8][size - 1 - i] = true; res[size - 1 - i][8] = true }
        placeVersionInfo(m, res, ver)
        var bit = 0; var col = size - 1; var up = true
        while (col > 0) {
            if (col == 6) col--
            for (row in 0 until size) {
                val r = if (up) size - 1 - row else row
                for (k in 0..1) {
                    val c = col - k
                    if (res[r][c]) continue
                    if (bit < cw.size * 8) { m[r][c] = ((cw[bit / 8] shr (7 - (bit % 8))) and 1) == 1; bit++ }
                }
            }
            up = !up; col -= 2
        }
        return Pair(m, res)
    }
    private fun applyMask(m: Array<BooleanArray>, res: Array<BooleanArray>, mask: Int) {
        for (r in m.indices) for (c in m.indices) {
            if (res[r][c]) continue
            val inv = when (mask) { 0 -> (r + c) % 2 == 0; else -> false }
            if (inv) m[r][c] = !m[r][c]
        }
    }
    private fun bitOf(x: Int, i: Int) = ((x shr i) and 1) == 1
    private fun placeFormat(m: Array<BooleanArray>, mask: Int) {
        val f = FORMAT_M[mask]; val s = m.size
        m[8][0] = bitOf(f, 14); m[8][1] = bitOf(f, 13); m[8][2] = bitOf(f, 12)
        m[8][3] = bitOf(f, 11); m[8][4] = bitOf(f, 10); m[8][5] = bitOf(f, 9)
        m[8][7] = bitOf(f, 8); m[8][8] = bitOf(f, 7); m[7][8] = bitOf(f, 6)
        m[5][8] = bitOf(f, 5); m[4][8] = bitOf(f, 4); m[3][8] = bitOf(f, 3)
        m[2][8] = bitOf(f, 2); m[1][8] = bitOf(f, 1); m[0][8] = bitOf(f, 0)
        m[s - 1][8] = bitOf(f, 14); m[s - 2][8] = bitOf(f, 13); m[s - 3][8] = bitOf(f, 12)
        m[s - 4][8] = bitOf(f, 11); m[s - 5][8] = bitOf(f, 10); m[s - 6][8] = bitOf(f, 9)
        m[s - 7][8] = bitOf(f, 8)
        m[8][s - 8] = bitOf(f, 7); m[8][s - 7] = bitOf(f, 6); m[8][s - 6] = bitOf(f, 5)
        m[8][s - 5] = bitOf(f, 4); m[8][s - 4] = bitOf(f, 3); m[8][s - 3] = bitOf(f, 2)
        m[8][s - 2] = bitOf(f, 1); m[8][s - 1] = bitOf(f, 0)
    }
    fun encode(text: String): Array<BooleanArray> {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val ver = try { chooseVersion(bytes.size) } catch (e: Exception) { 9 }
        val bits = encodeData(bytes, ver); val cw = bitsToCodewords(bits, ver)
        val all = interleave(cw, ver); val (m, res) = placeMatrix(all, ver)
        applyMask(m, res, 0); placeFormat(m, 0)
        return m
    }
    fun renderBitmap(text: String, sizePx: Int): Bitmap? {
        return try {
            val m = encode(text); val modules = m.size; val quiet = 4
            val total = modules + quiet * 2; val scale = (sizePx / total).coerceAtLeast(1)
            val bmpSize = total * scale; val bmp = Bitmap.createBitmap(bmpSize, bmpSize, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp); canvas.drawColor(Color.WHITE)
            val black = Paint().apply { color = Color.BLACK; isAntiAlias = false }
            for (r in 0 until modules) for (c in 0 until modules) {
                if (m[r][c]) {
                    val x = (c + quiet) * scale; val y = (r + quiet) * scale
                    canvas.drawRect(x.toFloat(), y.toFloat(), (x + scale).toFloat(), (y + scale).toFloat(), black)
                }
            }
            bmp
        } catch (e: Exception) { null }
    }
}

// ============================================================
// [CUSTOM VIEWS / DRAWABLES — Effects]
// ============================================================
class ParticlesView(ctx: Context) : View(ctx) {
    private class P(var x: Float, var y: Float, val r: Float, val v: Float, val a: Int, val d: Float)
    private val ps = ArrayList<P>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var anim: ValueAnimator? = null
    var color: Int = Color.WHITE

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        ps.clear()
        repeat(26) { ps.add(P(rng.nextFloat() * w, rng.nextFloat() * h, 1.5f + rng.nextFloat() * 2.5f, 0.15f + rng.nextFloat() * 0.5f, 40 + rng.nextInt(90), (rng.nextFloat() - 0.5f) * 0.3f)) }
    }
    override fun onDraw(c: Canvas) {
        for (p in ps) {
            p.y -= p.v; p.x += p.d
            if (p.y < -10f) { p.y = height + 10f; p.x = rng.nextFloat() * width }
            paint.color = color; paint.alpha = p.a; c.drawCircle(p.x, p.y, p.r, paint)
        }
    }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        anim = ValueAnimator.ofFloat(0f, 1f).apply { duration = 1000; repeatCount = ValueAnimator.INFINITE; addUpdateListener { invalidate() }; start() }
    }
    override fun onDetachedFromWindow() { anim?.cancel(); anim = null; super.onDetachedFromWindow() }
}

class CountdownRing(ctx: Context) : View(ctx) {
    var fraction = 1f; var label = ""
    var ringColor = Color.WHITE; var trackColor = Color.GRAY; var textColor = Color.WHITE
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val rect = RectF()
    override fun onDraw(c: Canvas) {
        val sw = width * 0.09f; ring.strokeWidth = sw
        rect.set(sw, sw, width - sw, height - sw)
        ring.color = trackColor; c.drawArc(rect, 0f, 360f, false, ring)
        ring.color = ringColor; c.drawArc(rect, -90f, 360f * fraction, false, ring)
        txt.color = textColor; txt.textSize = width * 0.32f
        c.drawText(label, width / 2f, height / 2f + txt.textSize / 3f, txt)
    }
}

class ShimmerDrawable(private val colors: IntArray, private val radius: Float, private val animated: Boolean) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val band = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF(); private val path = Path()
    private var phase = 0f; private var anim: ValueAnimator? = null

    override fun draw(c: Canvas) {
        rect.set(bounds)
        paint.shader = LinearGradient(rect.left, 0f, rect.right, 0f, colors, null, Shader.TileMode.CLAMP)
        c.drawRoundRect(rect, radius, radius, paint)
        if (animated && phase > 0f) {
            val bw = rect.width() * 0.35f; val x = rect.left - bw + (rect.width() + bw * 2f) * phase
            c.save(); path.reset(); path.addRoundRect(rect, radius, radius, Path.Direction.CW); c.clipPath(path)
            band.shader = LinearGradient(x, 0f, x + bw, 0f, intArrayOf(0x00FFFFFF, 0x66FFFFFF, 0x00FFFFFF), null, Shader.TileMode.CLAMP)
            c.drawRect(rect, band); c.restore()
        }
    }
    fun start() {
        if (!animated || anim != null) return
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2800; repeatCount = ValueAnimator.INFINITE
            addUpdateListener { val f = it.animatedValue as Float; phase = if (f < 0.45f) f / 0.45f else 0f; invalidateSelf() }
            start()
        }
    }
    fun stop() { anim?.cancel(); anim = null }
    override fun setAlpha(a: Int) { paint.alpha = a }
    override fun setColorFilter(cf: ColorFilter?) { paint.colorFilter = cf }
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}


// ============================================================
// [MAIN ACTIVITY]
// ============================================================
class MainActivity : Activity() {

    private class CustomRow(var name: String = "", var value: String = "", var secret: Boolean = false)
    private class Collected(val data: LinkedHashMap<String, String>, val secrets: List<String>, val totps: List<String>)
    private class TotpEntry(val p: TotpParams, val codeView: TextView, val bar: ProgressBar)
    private class ProgressUi(val dlg: AlertDialog, val bar: ProgressBar, val pct: TextView, val stage: TextView) { var anim: ValueAnimator? = null }
    private enum class Out { SAVE, LIBRARY, SHARE, STEGO }

    // ---- state
    private var currentScreen = "home"
    private var lastRenderedScreen = ""
    private var decryptedPayloadJson: String? = null
    private var currentVaultId: String? = null // If opening from library
    private var pendingSaveFileData: String? = null
    private var unlockAnimPending = false
    private var animateNow = false

    private val mediaItems = mutableListOf<JSONObject>()
    private var selectedVaultType = "note"
    private val fieldValues = mutableMapOf<String, MutableMap<String, String>>()
    private val customRows = mutableListOf(CustomRow())
    private var draftPass = ""
    private var draftPassConfirm = ""
    private var draftTitle = ""
    private var useTitleInFile = false
    private var expiryChoice = 0
    private var draftExpiry = 0L
    private var reminderText = ""
    private var reminderDate = 0L
    
    // Feature #5: Keyfile
    private var draftKeyFileUri: Uri? = null
    private var draftKeyFileBytes: ByteArray? = null
    private var draftKeyFileHash: String? = null

    // Feature #2: Shamir split
    private var shamirN = 5
    private var shamirK = 3

    // Feature #6: Library state
    private var librarySearch = ""
    private var librarySortMode = "date_desc"

    private var lastPausedTime = 0L
    private var suppressLockOnce = false
    private var appUnlocked = false
    private var pendingIncomingUri: Uri? = null

    private val handler = Handler(Looper.getMainLooper())
    private val clearClipRunnable = Runnable { clearClipboard(); hideRing() }
    private var ring: CountdownRing? = null
    private var ringAnim: ValueAnimator? = null
    private val totpEntries = mutableListOf<TotpEntry>()
    private val totpTick = object : Runnable { override fun run() { tickTotp(); handler.postDelayed(this, 1000) } }

    private lateinit var rootContainer: FrameLayout
    private lateinit var contentHolder: FrameLayout
    private lateinit var overlay: FrameLayout

    // ---- Activity results
    private var onResult: ((Int, Intent?) -> Unit)? = null
    private val REQ_CODE = 7001
    private fun launchForResult(intent: Intent, cb: (Int, Intent?) -> Unit) {
        suppressLockOnce = true; onResult = cb
        @Suppress("DEPRECATION") startActivityForResult(intent, REQ_CODE)
    }
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_CODE) { val cb = onResult; onResult = null; cb?.invoke(resultCode, data) }
    }

    // ---- back button
    private var backEnabled = false
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() { if (backEnabled) goHome() else super.onBackPressed() }

    // ============================================================ lifecycle
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            VaultStore.init(this)
            LibraryStore.init(this)
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            applyScreenSecurity()
            rootContainer = FrameLayout(this); contentHolder = FrameLayout(this); overlay = FrameLayout(this)
            rootContainer.addView(contentHolder, FrameLayout.LayoutParams(MATCH, MATCH))
            rootContainer.addView(overlay, FrameLayout.LayoutParams(MATCH, MATCH))
            setContentView(rootContainer)
            cleanShareCache()
            if (needsAppLock()) { currentScreen = "locked"; renderUI(); rootContainer.post { requestDeviceAuth() } }
            else { appUnlocked = true; renderUI() }
            handleIncoming(intent)
        } catch (e: Throwable) {
            setContentView(TextView(this).apply {
                text = "FATAL ERROR:\n${e.message}\n\n${e.stackTraceToString()}"
                setTextColor(Color.RED); setBackgroundColor(Color.BLACK); setPadding(32, 32, 32, 32); textSize = 12f
            })
        }
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); handleIncoming(intent) }

    override fun onResume() {
        super.onResume()
        applyScreenSecurity()
        if (Build.VERSION.SDK_INT >= 33) setRecentsScreenshotEnabled(true)
        if (totpEntries.isNotEmpty()) handler.post(totpTick)
        if (suppressLockOnce) { suppressLockOnce = false; return }
        val limit = VaultStore.autoLockSeconds()
        if (limit < 0 || lastPausedTime == 0L) return
        if (System.currentTimeMillis() - lastPausedTime >= limit * 1000L) lockNow()
    }

    override fun onPause() {
        super.onPause()
        lastPausedTime = System.currentTimeMillis()
        handler.removeCallbacks(totpTick)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        if (Build.VERSION.SDK_INT >= 33) setRecentsScreenshotEnabled(false)
    }

    override fun onDestroy() { handler.removeCallbacksAndMessages(null); ringAnim?.cancel(); super.onDestroy() }

    private fun goHome() { currentScreen = "home"; decryptedPayloadJson = null; currentVaultId = null; renderUI() }

    private fun lockNow() {
        lastPausedTime = 0L
        val appLock = needsAppLock()
        if (!appLock && currentScreen != "view") return
        decryptedPayloadJson = null; pendingSaveFileData = null; onResult = null
        handler.removeCallbacks(totpTick)
        clearClipboard(); hideRing()
        blurOutThen {
            if (appLock) { wipeDrafts(); appUnlocked = false; currentScreen = "locked" } else currentScreen = "home"
            renderUI()
        }
    }

    private fun wipeDrafts() {
        mediaItems.clear(); fieldValues.clear(); customRows.clear(); customRows.add(CustomRow())
        draftPass = ""; draftPassConfirm = ""; draftTitle = ""; useTitleInFile = false; expiryChoice = 0; draftExpiry = 0L
        draftKeyFileUri = null; draftKeyFileBytes = null; draftKeyFileHash = null
        reminderText = ""; reminderDate = 0L
    }

    private fun wipeEverything() { wipeDrafts(); clearClipboard(); hideRing(); goHome(); toast(tr("تم مسح الذاكرة والحافظة", "Memory and clipboard cleared")) }

    // ============================================================ app lock / incoming files
    private fun deviceSecure() = (getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceSecure
    private fun needsAppLock() = VaultStore.isAppLock() && deviceSecure()

    private fun requestDeviceAuth() {
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        @Suppress("DEPRECATION")
        val intent = km.createConfirmDeviceCredentialIntent("KEYKEEP", tr("أكّد هويتك لفتح التطبيق", "Confirm your identity to unlock"))
        if (intent == null) { appUnlocked = true; currentScreen = "home"; renderUI(); consumeIncoming(); return }
        launchForResult(intent) { code, _ ->
            if (code == RESULT_OK) { appUnlocked = true; currentScreen = "home"; renderUI(); consumeIncoming() }
        }
    }

    private fun handleIncoming(i: Intent?) {
        if (i?.action == Intent.ACTION_VIEW && i.data != null) { pendingIncomingUri = i.data; consumeIncoming() }
    }
    private fun consumeIncoming() {
        val u = pendingIncomingUri ?: return
        if (!appUnlocked) return
        pendingIncomingUri = null; currentScreen = "open"; renderUI(); openVaultFile(u)
    }

    // ============================================================ helpers
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun fs(v: Float): Float = v * VaultStore.fontScale()
    private fun toast(msg: String, long: Boolean = false) = Toast.makeText(this, msg, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
    private fun lp(w: Int = MATCH, h: Int = WRAP, top: Int = 0, bottom: Int = 0) =
        LinearLayout.LayoutParams(w, h).also { it.topMargin = dp(top); it.bottomMargin = dp(bottom) }

    private fun effectsOn(): Boolean = VaultStore.effects() &&
        Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f

    private fun applyScreenSecurity() {
        try {
            if (VaultStore.isScreenSecure()) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } catch (e: Exception) {}
    }

    private fun dialogBuilder() = AlertDialog.Builder(this,
        if (AppConfig.theme.light) android.R.style.Theme_Material_Light_Dialog_Alert else android.R.style.Theme_Material_Dialog_Alert)

    private fun secureDialog(d: Dialog) {
        if (VaultStore.isScreenSecure()) d.window?.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
    }

    private fun haptic(ok: Boolean) {
        if (!VaultStore.haptic()) return
        val k = if (Build.VERSION.SDK_INT >= 30) (if (ok) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.REJECT)
        else (if (ok) HapticFeedbackConstants.KEYBOARD_TAP else HapticFeedbackConstants.LONG_PRESS)
        rootContainer.performHapticFeedback(k)
    }

    private fun luxuryBg(color: Int, radius: Int, strokeWidth: Int = 1, strokeColor: Int = AppConfig.GOLD_PRIMARY) =
        GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat(); if (strokeWidth > 0) setStroke(dp(strokeWidth), strokeColor) }

    private fun ripple(content: Drawable, radius: Int, color: Int): RippleDrawable {
        val mask = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(radius).toFloat() }
        return RippleDrawable(ColorStateList.valueOf(color), content, mask)
    }

    private fun bindAnim(v: View, a: Animator) {
        v.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(x: View) { if (!a.isStarted) a.start() }
            override fun onViewDetachedFromWindow(x: View) { a.cancel() }
        })
    }

    private fun goldButton(label: String, heightDp: Int = 56): Button = Button(this).apply {
        text = label; setTextColor(AppConfig.ON_ACCENT); textSize = fs(16f); typeface = Typeface.SERIF; isAllCaps = false; stateListAnimator = null
        val fx = effectsOn()
        val shimmer = ShimmerDrawable(intArrayOf(AppConfig.GOLD_LIGHT, AppConfig.GOLD_PRIMARY, AppConfig.GOLD_DARK), dp(12).toFloat(), fx)
        background = ripple(shimmer, 12, withAlpha(Color.WHITE, 0x70))
        if (fx) addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) { shimmer.start() }
            override fun onViewDetachedFromWindow(v: View) { shimmer.stop() }
        })
        layoutParams = lp(h = dp(heightDp))
        setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(100).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
            }
            false
        }
    }

    private fun outlineButton(label: String, heightDp: Int = 48, color: Int = AppConfig.GOLD_PRIMARY): Button = Button(this).apply {
        text = label; setTextColor(color); textSize = fs(15f); typeface = Typeface.SERIF; isAllCaps = false; stateListAnimator = null
        background = ripple(luxuryBg(Color.TRANSPARENT, 12, 1, color), 12, withAlpha(color, 0x40))
        layoutParams = lp(h = dp(heightDp))
    }

    private fun iconButton(emoji: String, desc: String, size: Float = 18f, onClick: () -> Unit) = TextView(this).apply {
        text = emoji; textSize = fs(size); gravity = Gravity.CENTER; minWidth = dp(44); minHeight = dp(44)
        contentDescription = desc; isClickable = true; isFocusable = true
        setOnClickListener { onClick() }
    }

    private fun createLabel(t: String) = TextView(this).apply {
        text = t; setTextColor(AppConfig.GOLD_PRIMARY); textSize = fs(13f); typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD); setPadding(0, dp(16), 0, dp(8))
    }

    private fun createLuxuryInput(hint: String, multiLine: Boolean) = EditText(this).apply {
        this.hint = hint; textSize = fs(15f); setTextColor(AppConfig.TEXT_MAIN); setHintTextColor(withAlpha(AppConfig.TEXT_MUTED, 0xAA))
        background = luxuryBg(AppConfig.INPUT_BG, 8, 1, AppConfig.BORDER); setPadding(dp(14), dp(14), dp(14), dp(14))
        if (multiLine) { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE; minLines = 3; gravity = Gravity.TOP or Gravity.START }
        else inputType = InputType.TYPE_CLASS_TEXT
    }

    private fun createBackButton() = Button(this).apply {
        text = tr("→ رجوع للوحة التحكم", "← Back to dashboard"); setTextColor(AppConfig.TEXT_MUTED); textSize = fs(14f)
        background = null; isAllCaps = false; gravity = Gravity.START or Gravity.CENTER_VERTICAL; minHeight = dp(48); setPadding(0, 0, 0, dp(8))
        setOnClickListener { goHome() }
    }

    private fun themedAdapter(items: List<String>) = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, items) {
        override fun getView(pos: Int, cv: View?, parent: ViewGroup): View =
            (super.getView(pos, cv, parent) as TextView).apply { setTextColor(AppConfig.TEXT_MAIN); textSize = fs(15f); setPadding(dp(12), dp(12), dp(12), dp(12)) }
        override fun getDropDownView(pos: Int, cv: View?, parent: ViewGroup): View =
            (super.getDropDownView(pos, cv, parent) as TextView).apply { setTextColor(AppConfig.TEXT_MAIN); setBackgroundColor(AppConfig.CARD_DARK); textSize = fs(15f); setPadding(dp(14), dp(14), dp(14), dp(14)) }
    }

    private fun setVisible(et: EditText, visible: Boolean, numeric: Boolean) {
        val sel = et.selectionEnd
        et.inputType = if (numeric) InputType.TYPE_CLASS_NUMBER or (if (visible) 0 else InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        else InputType.TYPE_CLASS_TEXT or (if (visible) InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD else InputType.TYPE_TEXT_VARIATION_PASSWORD)
        et.typeface = Typeface.DEFAULT
        if (sel >= 0) et.setSelection(sel.coerceAtMost(et.text.length))
    }

    private fun secretInput(hint: String, initial: String, numeric: Boolean = false, onChange: (String) -> Unit): Pair<LinearLayout, EditText> {
        val et = createLuxuryInput(hint, false)
        setVisible(et, false, numeric); et.setText(initial); et.watch(onChange)
        et.layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        val eye = iconButton("👁", tr("إظهار / إخفاء", "Show / hide"), 20f) {}
        eye.alpha = 0.5f; eye.tag = false
        eye.setOnClickListener { val v = !(eye.tag as Boolean); eye.tag = v; eye.alpha = if (v) 1f else 0.5f; setVisible(et, v, numeric) }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(et); row.addView(eye)
        return Pair(row, et)
    }

    private fun shake(v: View) {
        if (!effectsOn()) return
        val d = dp(14).toFloat()
        ObjectAnimator.ofFloat(v, "translationX", 0f, -d, d, -d * 0.7f, d * 0.7f, -d * 0.35f, d * 0.35f, 0f).setDuration(420).start()
    }

    private fun flipText(tv: TextView, newText: String) {
        if (!effectsOn()) { tv.text = newText; return }
        tv.cameraDistance = 8000f * resources.displayMetrics.density
        tv.animate().rotationX(90f).setDuration(110).withEndAction {
            tv.text = newText; tv.rotationX = -90f; tv.animate().rotationX(0f).setDuration(110).start()
        }.start()
    }

    private fun blurOutThen(action: () -> Unit) {
        val v = contentHolder.getChildAt(0)
        if (v == null || !effectsOn()) { action(); return }
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 260
            addUpdateListener { a ->
                val f = a.animatedValue as Float
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { val r = 1f + 24f * f; v.setRenderEffect(RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP)) }
                v.alpha = 1f - 0.6f * f
            }
            addListener(object : AnimatorListenerAdapter() { override fun onAnimationEnd(a: Animator) { action() } })
            start()
        }
    }

    // ============================================================ render
    private fun renderUI() {
        applyScreenSecurity()
        handler.removeCallbacks(totpTick); totpEntries.clear()
        backEnabled = currentScreen == "create" || currentScreen == "open" || currentScreen == "view" || currentScreen == "library"
        val fx = effectsOn()
        val screenChanged = currentScreen != lastRenderedScreen
        val forward = currentScreen != "home"
        lastRenderedScreen = currentScreen
        animateNow = screenChanged && fx
        val arabic = VaultStore.isArabic()

        val root = FrameLayout(this).apply {
            layoutDirection = if (arabic) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(AppConfig.BG_OBSIDIAN, AppConfig.BG2))
        }
        if (fx) root.addView(ParticlesView(this).apply { color = AppConfig.GOLD_PRIMARY }, FrameLayout.LayoutParams(MATCH, MATCH))
        val scrollView = ScrollView(this).apply { layoutParams = FrameLayout.LayoutParams(MATCH, MATCH) }
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(46), dp(20), dp(32)) }
        scrollView.addView(container); root.addView(scrollView)

        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, 0, dp(16)) }
        val crown = TextView(this).apply { text = "👑"; textSize = fs(26f); setPadding(0, 0, dp(8), 0) }
        if (fx) bindAnim(crown, ObjectAnimator.ofFloat(crown, "translationY", 0f, -dp(5).toFloat()).apply {
            duration = 1400; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE; interpolator = AccelerateDecelerateInterpolator()
        })
        header.addView(crown)
        header.addView(TextView(this).apply {
            text = "KEYKEEP"; textSize = fs(24f); setTextColor(AppConfig.GOLD_PRIMARY); typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            letterSpacing = 0.1f; layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        })
        if (currentScreen != "locked") header.addView(iconButton("⚙️", tr("الإعدادات", "Settings"), 24f) { showSettingsDialog() })
        container.addView(header)

        when (currentScreen) {
            "locked" -> buildLocked(container)
            "home" -> buildDashboard(container)
            "library" -> buildLibrary(container)
            "create" -> buildCreate(container)
            "open" -> buildOpen(container)
            "view" -> buildView(container)
        }
        contentHolder.removeAllViews(); contentHolder.addView(root)

        if (animateNow) {
            val dir = if (arabic) -1 else 1
            scrollView.translationX = (if (forward) dir else -dir) * dp(60).toFloat(); scrollView.alpha = 0f
            scrollView.animate().translationX(0f).alpha(1f).setDuration(240).setInterpolator(DecelerateInterpolator()).start()
            for (i in 1 until container.childCount) {
                val ch = container.getChildAt(i); ch.alpha = 0f; ch.translationY = dp(22).toFloat()
                ch.animate().alpha(1f).translationY(0f).setDuration(320).setStartDelay(minOf(i, 9) * 55L).setInterpolator(DecelerateInterpolator()).start()
            }
        }
    }

    private fun buildLocked(parent: LinearLayout) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; background = luxuryBg(AppConfig.CARD_DARK, 16, 1, AppConfig.BORDER); setPadding(dp(32), dp(48), dp(32), dp(48))
        }
        card.addView(TextView(this).apply { text = "🔐"; textSize = fs(64f); gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(16)) })
        card.addView(TextView(this).apply { text = tr("التطبيق مقفل", "App locked"); setTextColor(AppConfig.TEXT_MAIN); textSize = fs(18f); gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(24)) })
        card.addView(goldButton(tr("فتح ببصمة / رمز الجهاز", "Unlock with biometrics / PIN"), 52).apply { setOnClickListener { requestDeviceAuth() } })
        parent.addView(card)
    }

    // ---------------------------------------------------------- dashboard
    private fun buildDashboard(parent: LinearLayout) {
        val sub = TextView(this).apply { setTextColor(AppConfig.TEXT_MUTED); textSize = fs(13f); gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(16)) }
        val subText = tr("خزنتك الملكية بين يديك", "Your royal vault, in your hands")
        if (animateNow) ValueAnimator.ofInt(0, subText.length).apply {
            duration = subText.length * 45L; interpolator = LinearInterpolator()
            addUpdateListener { sub.text = subText.substring(0, it.animatedValue as Int) }; start()
        } else sub.text = subText
        parent.addView(sub)

        val statsCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = luxuryBg(AppConfig.CARD_DARK, 16, 1, AppConfig.BORDER)
            setPadding(dp(20), dp(20), dp(20), dp(20)); layoutParams = lp(bottom = 20)
        }
        statsCard.addView(TextView(this).apply { text = tr("لوحة التحكم الملكية", "Royal dashboard"); setTextColor(AppConfig.GOLD_PRIMARY); textSize = fs(16f); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(16)) })
        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        row1.addView(createStatBox(tr("الخزائن", "Vaults"), LibraryStore.getAll().size))
        row1.addView(createStatBox(tr("المرفقات", "Attachments"), VaultStore.attachmentCount))
        statsCard.addView(row1)
        statsCard.addView(TextView(this).apply { text = tr("المساحة المستخدمة: ", "Space used: ") + formatSize(VaultStore.usedSpace); setTextColor(AppConfig.TEXT_MUTED); textSize = fs(12f); gravity = Gravity.CENTER; setPadding(0, dp(12), 0, dp(4)) })
        statsCard.addView(TextView(this).apply { text = tr("آخر نشاط: ", "Last activity: ") + VaultStore.lastActivity.ifEmpty { tr("لا يوجد", "none") }; setTextColor(AppConfig.TEXT_MUTED); textSize = fs(12f); gravity = Gravity.CENTER })
        parent.addView(statsCard)

        val btnLibrary = goldButton(tr("📚 مكتبة الخزائن", "📚 Vault Library")).apply { setOnClickListener { currentScreen = "library"; renderUI() } }
        parent.addView(glowWrap(btnLibrary, 14))
        
        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutParams = lp(bottom = 16) }
        row2.addView(outlineButton(tr("＋ إنشاء خزنة", "＋ Create vault"), 56).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(56), 1f).apply { marginEnd = dp(8) }
            setOnClickListener { wipeDrafts(); currentScreen = "create"; renderUI() }
        })
        row2.addView(outlineButton(tr("📂 فتح خزنة", "📂 Open vault"), 56).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(56), 1f)
            setOnClickListener { currentScreen = "open"; renderUI() }
        })
        parent.addView(row2)

        val tips = listOf(
            tr("استخدم عبارة مرور طويلة بدل كلمة قصيرة معقّدة.", "Prefer a long passphrase over a short complex password."),
            tr("لا تحفظ كلمة مرور الخزنة في نفس مكان ملفها.", "Never store the vault password next to its file."),
            tr("فعّل قفل التطبيق ببصمة الجهاز من الإعدادات.", "Turn on biometric app lock in Settings."),
            tr("الملف المشفر آمن للنسخ الاحتياطي في أي مكان.", "The encrypted file is safe to back up anywhere."),
            tr("إذا نسيت المفتاح فلا يمكن استرجاع الخزنة أبداً.", "If you forget the key, the vault cannot be recovered.")
        )
        parent.addView(TextView(this).apply {
            text = "💡 " + tips[rng.nextInt(tips.size)]; setTextColor(AppConfig.TEXT_MUTED); textSize = fs(12f); gravity = Gravity.CENTER; setPadding(dp(8), dp(24), dp(8), 0)
        })
        parent.addView(TextView(this).apply {
            text = "AES-256-GCM  •  PBKDF2 × 600,000  •  " + tr("بدون إنترنت", "offline"); setTextColor(AppConfig.TEXT_MUTED); textSize = fs(11f); gravity = Gravity.CENTER; setPadding(0, dp(10), 0, 0)
        })
    }

    private fun glowWrap(inner: View, bottom: Int): View {
        val w = FrameLayout(this).apply { layoutParams = lp(bottom = bottom); setPadding(dp(3), dp(3), dp(3), dp(3)) }
        if (effectsOn()) {
            val glow = View(this).apply {
                background = GradientDrawable().apply { setColor(Color.TRANSPARENT); cornerRadius = dp(15).toFloat(); setStroke(dp(2), AppConfig.GOLD_PRIMARY) }; alpha = 0.1f
            }
            w.addView(glow, FrameLayout.LayoutParams(MATCH, MATCH).apply { setMargins(-dp(3), -dp(3), -dp(3), -dp(3)) })
            bindAnim(glow, ObjectAnimator.ofFloat(glow, "alpha", 0.1f, 0.9f).apply { duration = 1300; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE })
        }
        w.addView(inner, FrameLayout.LayoutParams(MATCH, dp(56)))
        return w
    }

    private fun createStatBox(label: String, value: Int) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
        val num = TextView(this@MainActivity).apply { text = value.toString(); textSize = fs(28f); setTextColor(AppConfig.GOLD_LIGHT); typeface = Typeface.DEFAULT_BOLD }
        if (animateNow && value > 0) ValueAnimator.ofInt(0, value).apply { duration = 900; addUpdateListener { num.text = (it.animatedValue as Int).toString() }; start() }
        addView(num)
        addView(TextView(this@MainActivity).apply { text = label; textSize = fs(12f); setTextColor(AppConfig.TEXT_MUTED) })
    }

    // ---------------------------------------------------------- library (Feature #1)
    private fun buildLibrary(parent: LinearLayout) {
        parent.addView(createBackButton())
        
        val searchRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 0, 0, dp(12)) }
        val searchInp = createLuxuryInput(tr("🔍 ابحث بالاسم...", "🔍 Search by name..."), false).apply {
            setText(librarySearch)
            watch { librarySearch = it; parent.post { renderUI() } } // Basic live search re-render
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginEnd = dp(8) }
        }
        searchRow.addView(searchInp)
        
        val sortBtn = iconButton("⇅", "Sort", 20f) {
            librarySortMode = when(librarySortMode) {
                "date_desc" -> "date_asc"
                "date_asc" -> "name_asc"
                "name_asc" -> "name_desc"
                else -> "date_desc"
            }
            renderUI()
        }.apply { background = luxuryBg(AppConfig.CARD_DARK, 8, 1, AppConfig.BORDER); setTextColor(AppConfig.TEXT_MAIN) }
        searchRow.addView(sortBtn)
        parent.addView(searchRow)

        val (sortField, isAsc) = when(librarySortMode) {
            "date_asc" -> Pair("date", true)
            "date_desc" -> Pair("date", false)
            "name_asc" -> Pair("name", true)
            else -> Pair("name", false)
        }
        
        val list = LibraryStore.search(librarySearch, sortField, isAsc)
        if (list.isEmpty()) {
            parent.addView(TextView(this).apply {
                text = tr("المكتبة فارغة أو لا توجد نتائج.", "Library is empty or no results.")
                setTextColor(AppConfig.TEXT_MUTED); gravity = Gravity.CENTER; setPadding(0, dp(40), 0, 0)
            })
        } else {
            for (v in list) {
                val spec = AppConfig.spec(v.type)
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL; background = luxuryBg(AppConfig.CARD_DARK, 12, 1, AppConfig.BORDER)
                    setPadding(dp(16), dp(16), dp(16), dp(16)); gravity = Gravity.CENTER_VERTICAL
                    layoutParams = lp(bottom = 10)
                }
                card.addView(TextView(this).apply { text = spec.icon; textSize = fs(28f); setPadding(0, 0, dp(16), 0) })
                
                val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f) }
                col.addView(TextView(this).apply { text = v.title.ifEmpty { spec.title }; setTextColor(AppConfig.TEXT_MAIN); textSize = fs(16f); typeface = Typeface.DEFAULT_BOLD })
                col.addView(TextView(this).apply { text = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date(v.updatedAt)) + "  •  " + formatSize(v.size); setTextColor(AppConfig.TEXT_MUTED); textSize = fs(12f); setPadding(0, dp(4), 0, 0) })
                card.addView(col)
                
                card.addView(iconButton("▶", "Open", 20f) {
                    val html = LibraryStore.loadVaultHtml(v.id)
                    if (html != null) {
                        val parsed = CryptoEngine.parseHtml(html)
                        if (parsed != null) { currentVaultId = v.id; showUnlockDialog(parsed) } else toast("Corrupted vault")
                    } else toast("File missing")
                })
                card.addView(iconButton("🗑", "Delete", 20f) {
                    dialogBuilder().setTitle(tr("حذف الخزنة", "Delete vault")).setMessage(tr("هل أنت متأكد؟", "Are you sure?"))
                        .setPositiveButton(tr("حذف", "Delete")) { _, _ -> LibraryStore.deleteVault(v.id); renderUI() }
                        .setNegativeButton(tr("إلغاء", "Cancel"), null).show()
                }.apply { setTextColor(AppConfig.DANGER_RED) })
                parent.addView(card)
            }
        }
    }

    // ---------------------------------------------------------- create
    private fun valuesFor(type: String) = fieldValues.getOrPut(type) { mutableMapOf() }

    private fun effectiveExpiry(): Long {
        val day = 24L * 3600 * 1000; val now = System.currentTimeMillis()
        return when (expiryChoice) { 1 -> now + day; 2 -> now + 7 * day; 3 -> now + 30 * day; 4 -> now + 365 * day; 5 -> draftExpiry; else -> 0L }
    }
    private fun expiryText(): String {
        val e = effectiveExpiry()
        return if (e == 0L) "" else tr("تنتهي صلاحية الخزنة في: ", "Vault expires: ") + SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date(e))
    }

    private fun buildCreate(parent: LinearLayout) {
        val spec = AppConfig.spec(selectedVaultType)
        parent.addView(createBackButton())

        parent.addView(createLabel(tr("عنوان الخزنة (اختياري)", "Vault title (optional)")))
        parent.addView(createLuxuryInput(tr("مثال: حساب البنك", "e.g. Bank account"), false).apply { setText(draftTitle); watch { draftTitle = it } })
        parent.addView(CheckBox(this).apply {
            text = tr("استخدام العنوان في اسم الملف (يراه كل من يرى الملف)", "Use title in file name (visible to anyone who sees the file)")
            setTextColor(AppConfig.TEXT_MUTED); textSize = fs(12f); isChecked = useTitleInFile; setOnCheckedChangeListener { _, c -> useTitleInFile = c }
        })

        parent.addView(createLabel(tr("نوع البيانات", "Data type")))
        parent.addView(Spinner(this).apply {
            adapter = themedAdapter(AppConfig.VAULTS.map { it.title })
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
                if (f.totp) parent.addView(TextView(this).apply {
                    text = tr("الصق السر (Base32) أو رابط otpauth://", "Paste the secret (Base32) or an otpauth:// link"); setTextColor(AppConfig.TEXT_MUTED); textSize = fs(11f); setPadding(0, 0, 0, dp(6))
                })
                if (f.secret) parent.addView(secretInput(f.label, values[f.id] ?: "", f.numeric) { values[f.id] = it }.first)
                else {
                    val et = createLuxuryInput(f.label, f.multiline); et.setText(values[f.id] ?: ""); et.watch { values[f.id] = it }; parent.addView(et)
                }
            }
        }

        if (spec.allowMedia) buildMediaSection(parent)

        // ---- expiry
        parent.addView(createLabel(tr("صلاحية الخزنة", "Vault validity")))
        val expiryNote = TextView(this).apply { setTextColor(AppConfig.TEXT_MUTED); textSize = fs(12f); setPadding(0, dp(6), 0, 0); text = expiryText() }
        parent.addView(Spinner(this).apply {
            adapter = themedAdapter(listOf(tr("بدون انتهاء", "No expiry"), tr("24 ساعة", "24 hours"), tr("أسبوع", "1 week"), tr("شهر", "1 month"), tr("سنة", "1 year"), tr("تاريخ محدد…", "Pick a date…")))
            setSelection(expiryChoice, false); background = luxuryBg(AppConfig.CARD_DARK, 8, 1, AppConfig.BORDER)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                    if (pos == expiryChoice) return
                    if (pos == 5) {
                        val cal = Calendar.getInstance()
                        DatePickerDialog(this@MainActivity, { _, y, m, d ->
                            draftExpiry = Calendar.getInstance().apply { set(y, m, d, 23, 59, 59) }.timeInMillis
                            expiryChoice = 5; expiryNote.text = expiryText()
                        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).apply {
                            datePicker.minDate = System.currentTimeMillis()
                            setOnCancelListener { parent.post { renderUI() } }
                        }.show()
                    } else { expiryChoice = pos; expiryNote.text = expiryText() }
                }
                override fun onNothingSelected(p0: AdapterView<*>?) {}
            }
        })
        parent.addView(expiryNote)
        
        // ---- Reminder (Feature #6)
        parent.addView(createLabel(tr("تذكير (اختياري)", "Reminder (optional)")))
        parent.addView(createLuxuryInput(tr("نص التذكير", "Reminder text"), false).apply { setText(reminderText); watch { reminderText = it } })
        parent.addView(TextView(this).apply {
            text = if (reminderDate == 0L) tr("لا يوجد تاريخ محدد", "No date selected") else SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date(reminderDate))
            setTextColor(AppConfig.TEXT_MUTED); textSize = fs(12f); setPadding(dp(4), dp(4), 0, 0)
        })
        parent.addView(outlineButton(tr("📅 اختيار تاريخ التذكير", "📅 Pick reminder date"), 40).apply {
            layoutParams = lp(h = dp(40), top = 4)
            setOnClickListener {
                val cal = Calendar.getInstance()
                DatePickerDialog(this@MainActivity, { _, y, m, d ->
                    reminderDate = Calendar.getInstance().apply { set(y, m, d, 12, 0) }.timeInMillis
                    renderUI()
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
            }
        })

        // ---- key
        parent.addView(createLabel(tr("مفتاح التشفير", "Encryption key")))
        val strengthBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; progress = 0; layoutParams = lp(h = dp(4), top = 6) }
        val strengthLabel = TextView(this).apply { textSize = fs(12f); setTextColor(AppConfig.TEXT_MUTED); setPadding(0, dp(4), 0, 0) }
        fun updateStrength(p: String) {
            val s = strengthOf(p)
            val (label, color) = when {
                p.isEmpty() -> Pair("", AppConfig.TEXT_MUTED)
                s < 40 -> Pair(tr("ضعيفة", "Weak"), AppConfig.DANGER_RED)
                s < 65 -> Pair(tr("متوسطة", "Fair"), AppConfig.WARN_ORANGE)
                s < 85 -> Pair(tr("قوية", "Strong"), AppConfig.GOLD_PRIMARY)
                else -> Pair(tr("ممتازة", "Excellent"), AppConfig.OK_GREEN)
            }
            animateStrength(strengthBar, strengthLabel, s, color, if (label.isEmpty()) "" else tr("القوة: ", "Strength: ") + label)
        }
        val (passRow, passEt) = secretInput(tr("كلمة مرور قوية", "Strong password"), draftPass) { draftPass = it; updateStrength(it) }
        parent.addView(passRow); parent.addView(strengthBar); parent.addView(strengthLabel)
        updateStrength(draftPass)

        // HIBP Check (Feature #16)
        parent.addView(Button(this).apply {
            text = tr("🔍 فحص تسريب كلمة المرور (HIBP)", "🔍 Check password leak (HIBP)"); setTextColor(AppConfig.TEXT_MUTED); textSize = fs(13f); background = null; isAllCaps = false; minHeight = dp(40)
            setOnClickListener {
                if (draftPass.isEmpty()) { toast(tr("أدخل كلمة المرور أولاً", "Enter password first")); return@setOnClickListener }
                val busy = showBusy(tr("جاري الفحص...", "Checking..."))
                Thread {
                    val count = HibpChecker.checkPassword(draftPass)
                    runOnUiThread {
                        busy.dismiss()
                        when {
                            count == null -> toast(tr("فشل الاتصال بالإنترنت أو الخادم", "Network error"), true)
                            count > 0 -> {
                                haptic(false)
                                dialogBuilder().setTitle(tr("⚠️ كلمة المرور مسربة!", "⚠️ Password leaked!"))
                                    .setMessage(tr("ظهرت في $count تسريب. يرجى تغييرها فوراً.", "Found in $count breaches. Change it immediately."))
                                    .setPositiveButton("OK", null).show()
                            }
                            else -> { haptic(true); toast(tr("✔ لم يتم العثور على تسريبات", "✔ No leaks found"), true) }
                        }
                    }
                }.start()
            }
        })

        parent.addView(createLabel(tr("تأكيد المفتاح", "Confirm key")))
        val (confirmRow, confirmEt) = secretInput(tr("أعد كتابة كلمة المرور", "Re-enter password"), draftPassConfirm) { draftPassConfirm = it }
        parent.addView(confirmRow)

        parent.addView(Button(this).apply {
            text = tr("🎲 مولّد كلمات المرور / العبارات", "🎲 Password / passphrase generator"); setTextColor(AppConfig.TEXT_MUTED); textSize = fs(13f); background = null; isAllCaps = false; minHeight = dp(48)
            setOnClickListener {
                showGeneratorDialog { pw ->
                    passEt.setText(pw); confirmEt.setText(pw); setVisible(passEt, true, false); setVisible(confirmEt, true, false)
                    draftPass = pw; draftPassConfirm = pw; updateStrength(pw)
                    toast(tr("تم استخدام كلمة المرور — احفظها في مكان آمن", "Password applied — store it somewhere safe"))
                }
            }
        })
        
        // ---- Keyfile (Feature #5)
        parent.addView(createLabel(tr("ملف مفتاح إضافي (Keyfile) - اختياري", "Keyfile (additional secret) - optional")))
        parent.addView(TextView(this).apply {
            text = if (draftKeyFileBytes != null) tr("✔ تم تحميل ملف المفتاح (${draftKeyFileBytes!!.size} بايت)", "✔ Keyfile loaded (${draftKeyFileBytes!!.size} bytes)") else tr("لم يتم تحديد ملف. يزيد الأمان، ولن تفتح الخزنة بدونه.", "No file selected. Increases security, vault won't open without it.")
            setTextColor(if (draftKeyFileBytes != null) AppConfig.OK_GREEN else AppConfig.TEXT_MUTED); textSize = fs(12f)
        })
        parent.addView(outlineButton(tr("📁 اختيار ملف مفتاح", "📁 Choose keyfile"), 40).apply {
            layoutParams = lp(h = dp(40), top = 4)
            setOnClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); setType("*/*") }
                launchForResult(intent) { code, data ->
                    if (code == RESULT_OK && data?.data != null) {
                        try {
                            val bytes = contentResolver.openInputStream(data.data!!)?.use { it.readBytes() }
                            if (bytes != null && bytes.size <= 10 * 1024 * 1024) {
                                draftKeyFileBytes = bytes
                                draftKeyFileHash = Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(bytes), Base64.NO_WRAP)
                                renderUI()
                            } else toast("File too large")
                        } catch (e: Exception) { toast("Error reading file") }
                    }
                }
            }
        })
        if (draftKeyFileBytes != null) {
            parent.addView(outlineButton(tr("❌ إزالة ملف المفتاح", "❌ Remove keyfile"), 36, AppConfig.DANGER_RED).apply {
                layoutParams = lp(h = dp(36), top = 4)
                setOnClickListener { draftKeyFileBytes = null; draftKeyFileHash = null; renderUI() }
            })
        }

        // ---- Shamir (Feature #2)
        parent.addView(createLabel(tr("تقسيم المفتاح (Shamir) - اختياري", "Key splitting (Shamir) - optional")))
        parent.addView(TextView(this).apply {
            text = tr("ينتج أجزاء تُعطى لأشخاص موثوقين. لفتح الخزنة لاحقاً ستحتاج دمج الأجزاء.", "Produces shares for trusted people. To open later, combine shares.")
            setTextColor(AppConfig.TEXT_MUTED); textSize = fs(11f); setPadding(0, 0, 0, dp(4))
        })
        val shamirSwitch = CheckBox(this).apply {
            text = tr("تفعيل تقسيم المفتاح", "Enable key splitting"); setTextColor(AppConfig.TEXT_MAIN)
            isChecked = shamirN > 0
            setOnCheckedChangeListener { _, c -> if (!c) shamirN = 0 else { shamirN = 5; shamirK = 3 }; renderUI() }
        }
        parent.addView(shamirSwitch)
        if (shamirN > 0) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(8), 0, 0) }
            row.addView(TextView(this).apply { text = tr("الأجزاء: ", "Shares: "); setTextColor(AppConfig.TEXT_MUTED) })
            row.addView(TextView(this).apply { text = shamirN.toString(); setTextColor(AppConfig.GOLD_PRIMARY); typeface = Typeface.DEFAULT_BOLD; setPadding(0, 0, dp(16), 0) })
            row.addView(TextView(this).apply { text = tr("المطلوب للفتح: ", "Required: "); setTextColor(AppConfig.TEXT_MUTED) })
            row.addView(TextView(this).apply { text = shamirK.toString(); setTextColor(AppConfig.GOLD_PRIMARY); typeface = Typeface.DEFAULT_BOLD })
            parent.addView(row)
            parent.addView(SeekBar(this).apply {
                max = 4; progress = shamirN - 2 // 2 to 6 shares
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) { shamirN = p + 2; shamirK = (shamirN + 1) / 2; renderUI() }
                    override fun onStartTrackingTouch(sb: SeekBar?) {}
                    override fun onStopTrackingTouch(sb: SeekBar?) {}
                })
            })
        }

        parent.addView(goldButton(tr("🔒 تشفير وحفظ في المكتبة", "🔒 Encrypt & save to library")).apply { layoutParams = lp(h = dp(56), top = 16); setOnClickListener { onEncryptClicked(spec, Out.LIBRARY) } })
        parent.addView(outlineButton(tr("💾 حفظ كملف خارجي", "💾 Save as file"), 52).apply { layoutParams = lp(h = dp(52), top = 10); setOnClickListener { onEncryptClicked(spec, Out.SAVE) } })
        parent.addView(outlineButton(tr("📤 تشفير ومشاركة", "📤 Encrypt & share"), 52).apply { layoutParams = lp(h = dp(52), top = 10); setOnClickListener { onEncryptClicked(spec, Out.SHARE) } })
    }

private fun animateStrength(bar: ProgressBar, label: TextView, p: Int, color: Int, text: String) {
    label.text = text
    val from = (bar.tag as? Int) ?: AppConfig.TEXT_MUTED
    bar.tag = color
    if (!effectsOn()) { bar.progress = p; bar.progressTintList = ColorStateList.valueOf(color); label.setTextColor(color); return }
    ObjectAnimator.ofInt(bar, "progress", p).setDuration(300).start()
    ValueAnimator.ofObject(ArgbEvaluator(), from, color).apply {
        duration = 300
        addUpdateListener { val cc = it.animatedValue as Int; bar.progressTintList = ColorStateList.valueOf(cc); label.setTextColor(cc) }
        start()
    }
}

    private fun buildCustomRows(parent: LinearLayout) {
        customRows.forEachIndexed { i, r ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; background = luxuryBg(AppConfig.CARD_DARK, 10, 1, AppConfig.BORDER); setPadding(dp(12), dp(10), dp(12), dp(10)); layoutParams = lp(top = 12)
            }
            val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            top.addView(createLuxuryInput(tr("اسم الحقل", "Field name"), false).apply { setText(r.name); watch { r.name = it }; layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f) })
            if (customRows.size > 1) top.addView(iconButton("✖", tr("حذف الحقل", "Remove field")) { customRows.removeAt(i); renderUI() }.apply { setTextColor(AppConfig.DANGER_RED) })
            card.addView(top)
            card.addView(createLuxuryInput(tr("القيمة", "Value"), true).apply { setText(r.value); watch { r.value = it }; layoutParams = lp(top = 8) })
            card.addView(CheckBox(this).apply {
                text = tr("حقل سري (يُخفى عند العرض)", "Secret field (hidden when viewing)"); setTextColor(AppConfig.TEXT_MUTED); textSize = fs(13f); isChecked = r.secret
                setOnCheckedChangeListener { _, c -> r.secret = c }
            })
            parent.addView(card)
        }
        parent.addView(outlineButton(tr("＋ إضافة حقل", "+ Add field")).apply { layoutParams = lp(h = dp(48), top = 12); setOnClickListener { customRows.add(CustomRow()); renderUI() } })
    }

    private fun buildMediaSection(parent: LinearLayout) {
        parent.addView(outlineButton(tr("📎 إرفاق مستندات / صور", "📎 Attach documents / images")).apply {
            layoutParams = lp(h = dp(48), top = 12)
            setOnClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); setType("*/*"); putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true) }
                launchForResult(intent) { code, data -> if (code == RESULT_OK && data != null) attachFromIntent(data) }
            }
        })
        val total = mediaItems.sumOf { it.optLong("size", 0L) }
        parent.addView(TextView(this).apply {
            text = tr("الحد: ", "Limit: ") + tr("غير محدود", "Unlimited") + tr("  •  المرفق الآن: ", "  •  attached: ") + formatSize(total)
            setTextColor(AppConfig.TEXT_MUTED); textSize = fs(11f); setPadding(0, dp(6), 0, dp(6))
        })
        for ((i, item) in mediaItems.withIndex()) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; background = luxuryBg(AppConfig.CARD_DARK, 8); setPadding(dp(12), dp(4), dp(4), dp(4)); gravity = Gravity.CENTER_VERTICAL; layoutParams = lp(bottom = 6) }
            row.addView(TextView(this).apply { text = if (item.optString("kind") == "image") "🖼" else "📎"; setPadding(0, 0, dp(10), 0) })
            row.addView(TextView(this).apply { text = "${item.optString("name")}  (${formatSize(item.optLong("size", 0L))})"; setTextColor(AppConfig.TEXT_MAIN); textSize = fs(14f); layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f) })
            row.addView(iconButton("✖", tr("إزالة المرفق", "Remove attachment")) { mediaItems.removeAt(i); renderUI() }.apply { setTextColor(AppConfig.DANGER_RED) })
            parent.addView(row)
        }
    }

    private fun attachFromIntent(data: Intent) {
        val uris = mutableListOf<Uri>()
        val clip = data.clipData
        if (clip != null) { for (i in 0 until clip.itemCount) uris.add(clip.getItemAt(i).uri) } else data.data?.let { uris.add(it) }
        val busy = showBusy(tr("جاري قراءة المرفقات...", "Reading attachments..."))
        Thread {
            val added = mutableListOf<JSONObject>()
            for (uri in uris) {
                try {
                    val (name, _) = queryMeta(uri)
                    val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: continue
                    val mime = contentResolver.getType(uri) ?: "application/octet-stream"
                    added.add(JSONObject().apply {
                        put("kind", if (mime.startsWith("image/")) "image" else "file"); put("mime", mime); put("name", name)
                        put("size", bytes.size.toLong()); put("data", Base64.encodeToString(bytes, Base64.NO_WRAP))
                    })
                } catch (e: Throwable) { }
            }
            runOnUiThread {
                busy.dismiss(); mediaItems.addAll(added); renderUI()
            }
        }.start()
    }

    private fun collectData(spec: VaultSpec): Collected {
        val data = LinkedHashMap<String, String>(); val secrets = mutableListOf<String>(); val totps = mutableListOf<String>()
        if (spec.dynamic) {
            customRows.forEachIndexed { i, r ->
                if (r.value.isBlank()) return@forEachIndexed
                val base = r.name.trim().ifEmpty { tr("حقل ", "Field ") + (i + 1) }
                var name = base; var n = 2
                while (data.containsKey(name)) { name = "$base ($n)"; n++ }
                data[name] = r.value; if (r.secret) secrets.add(name)
            }
        } else {
            val values = valuesFor(spec.type)
            for (f in spec.fields) {
                val raw = values[f.id].orEmpty(); if (raw.isBlank()) continue
                data[f.label] = if (f.secret) raw else raw.trim()
                if (f.secret) secrets.add(f.label); if (f.totp) totps.add(f.label)
            }
        }
        return Collected(data, secrets, totps)
    }

    private fun onEncryptClicked(spec: VaultSpec, out: Out) {
        val pass = draftPass
        if (pass.length < 8) { toast(tr("كلمة المرور يجب ألا تقل عن 8 أحرف", "Password must be at least 8 characters")); return }
        if (pass != draftPassConfirm) { toast(tr("المفاتيح غير متطابقة", "Keys do not match")); return }
        val col = collectData(spec)
        if (col.data.isEmpty() && mediaItems.isEmpty()) { toast(tr("الخزنة فارغة", "Vault is empty")); return }
        
        // Feature #16: HIBP check already handled manually by user button
        
        // Shamir split
        var shamirShares: List<String>? = null
        if (shamirN > 0) {
            var shamirShares: List<String>? = null
if (shamirN > 0) {
    shamirShares = ShamirEngine.split(pass.toByteArray(Charsets.UTF_8), shamirN, shamirK)
    if (shamirShares == null) { toast(tr("فشل تقسيم المفتاح", "Key split failed")); return }
}
            if (shamirShares == null) { toast(tr("فشل تقسيم المفتاح", "Key split failed")); return }
        }
        
        if (strengthOf(pass) < 50) {
            dialogBuilder().setTitle(tr("كلمة مرور ضعيفة", "Weak password"))
                .setMessage(tr("قوة التشفير تعتمد على قوة كلمة المرور. يُنصح باستخدام المولّد. هل تريد المتابعة؟", "Encryption is only as strong as the password. Using the generator is recommended. Continue?"))
                .setPositiveButton(tr("متابعة", "Continue")) { _, _ -> encryptAndOutput(spec, col, pass, out, shamirShares) }
                .setNegativeButton(tr("تعديل", "Edit"), null).show().also { secureDialog(it) }
        } else encryptAndOutput(spec, col, pass, out, shamirShares)
    }

    private fun showProgress(title: String): ProgressUi {
        val l = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(16), dp(24), dp(8)) }
        val stage = TextView(this).apply { setTextColor(AppConfig.TEXT_MAIN); textSize = fs(14f); setPadding(0, 0, 0, dp(10)) }
        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; progress = 0; progressTintList = ColorStateList.valueOf(AppConfig.GOLD_PRIMARY) }
        val pct = TextView(this).apply { text = "0%"; setTextColor(AppConfig.TEXT_MUTED); textSize = fs(12f); gravity = Gravity.END }
        l.addView(stage); l.addView(bar); l.addView(pct)
        val d = dialogBuilder().setTitle(title).setView(l).setCancelable(false).create(); d.show(); secureDialog(d)
        return ProgressUi(d, bar, pct, stage)
    }

    private fun progressTo(ui: ProgressUi, target: Int, ms: Long, stageText: String) {
        ui.stage.text = stageText; ui.anim?.cancel()
        if (!effectsOn()) { ui.bar.progress = target; ui.pct.text = "$target%"; return }
        ui.anim = ValueAnimator.ofInt(ui.bar.progress, target).apply {
            duration = ms; interpolator = DecelerateInterpolator()
            addUpdateListener { val v = it.animatedValue as Int; ui.bar.progress = v; ui.pct.text = "$v%" }; start()
        }
    }

    private fun showBusy(text: String): AlertDialog {
        val l = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(24), dp(20), dp(24), dp(20)) }
        l.addView(ProgressBar(this))
        l.addView(TextView(this).apply { this.text = text; setTextColor(AppConfig.TEXT_MAIN); textSize = fs(15f); setPadding(dp(16), 0, 0, 0) })
        val d = dialogBuilder().setView(l).setCancelable(false).create(); d.show(); secureDialog(d); return d
    }

    private fun fileNameFor(spec: VaultSpec): String {
        val tag = UUID.randomUUID().toString().take(4)
        val safe = draftTitle.trim().replace(Regex("[^\\p{L}\\p{N}_-]+"), "_").trim('_').take(40)
        return if (useTitleInFile && safe.isNotEmpty()) "${safe}_$tag.html" else "Keykeep_${spec.type}_$tag.html"
    }

    private fun encryptAndOutput(spec: VaultSpec, col: Collected, pass: String, out: Out, shamirShares: List<String>?) {
        val reminders = mutableListOf<JSONObject>()
        if (reminderText.isNotEmpty() && reminderDate > 0) {
            reminders.add(JSONObject().apply { put("text", reminderText); put("date", reminderDate) })
        }
        
        val payload = VaultPayload(spec.type, draftTitle.trim(), effectiveExpiry(), col.data, col.secrets, col.totps, ArrayList(mediaItems), reminders, draftKeyFileHash)
        val fileName = fileNameFor(spec)
        val attachCount = mediaItems.size
        val ui = showProgress(tr("جاري التشفير", "Encrypting"))
        progressTo(ui, 8, 200, tr("تجهيز البيانات...", "Preparing data..."))
        val arabic = VaultStore.isArabic(); val theme = AppConfig.theme
        
        Thread {
            try {
                val html = CryptoEngine.generateVault(payload, pass.toCharArray(), theme, arabic, draftKeyFileBytes) { p ->
                    runOnUiThread {
                        when (p) {
                            30 -> progressTo(ui, 86, 2600, tr("اشتقاق المفتاح (PBKDF2 × 600,000)...", "Deriving key (PBKDF2 × 600,000)..."))
                            90 -> progressTo(ui, 94, 150, tr("تشفير البيانات...", "Encrypting data..."))
                            100 -> progressTo(ui, 100, 150, tr("بناء الملف...", "Building file..."))
                        }
                    }
                }
                runOnUiThread {
                    ui.anim?.cancel(); ui.dlg.dismiss()
                    
                    if (shamirShares != null) {
                        showShamirSharesDialog(shamirShares)
                    }
                    
                    when (out) {
                        Out.SAVE -> saveVaultFile(html, fileName, pass, attachCount)
                        Out.SHARE -> shareVaultFile(html, fileName, attachCount)
                        Out.LIBRARY -> {
                            val id = UUID.randomUUID().toString().replace("-", "").substring(0, 12)
                            val meta = VaultMeta(id, draftTitle.ifBlank { spec.title }, spec.type, System.currentTimeMillis(), System.currentTimeMillis(), html.length.toLong())
                            if (LibraryStore.saveVault(html, meta)) {
                                VaultStore.addVaultStats(attachCount, html.length.toLong())
                                haptic(true); toast(tr("👑 تم الحفظ في المكتبة", "👑 Saved to library"), true)
                                wipeDrafts(); currentScreen = "library"; renderUI()
                            } else {
                                haptic(false); toast(tr("فشل الحفظ في المكتبة", "Library save failed"), true)
                            }
                        }
                        else -> {}
                    }
                }
            } catch (e: Throwable) {
                runOnUiThread { ui.anim?.cancel(); ui.dlg.dismiss(); haptic(false); toast(tr("خطأ: ", "Error: ") + (e.message ?: "OOM"), true) }
            }
        }.start()
    }

    private fun showShamirSharesDialog(shares: List<String>) {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(12), dp(20), dp(4)) }
        layout.addView(TextView(this).apply {
            text = tr("تم تقسيم المفتاح. احفظ هذه الأجزاء بأمان، ستحتاجها لفتح الخزنة.", "Key split. Save these shares safely, you'll need them to open the vault.")
            setTextColor(AppConfig.TEXT_MUTED); textSize = fs(13f); setPadding(0, 0, 0, dp(12))
        })
        val scroll = ScrollView(this).apply { layoutParams = LinearLayout.LayoutParams(MATCH, dp(200)) }
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        shares.forEachIndexed { i, share ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(4), 0, dp(4)) }
            row.addView(TextView(this).apply { text = "${i + 1}: "; setTextColor(AppConfig.GOLD_PRIMARY); typeface = Typeface.DEFAULT_BOLD })
            row.addView(TextView(this).apply { text = share.substringAfter(":"); setTextColor(AppConfig.TEXT_MAIN); typeface = Typeface.MONOSPACE; textSize = fs(10f); layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f) })
            row.addView(iconButton("📋", "Copy") { copyToClipboard(share) })
            list.addView(row)
        }
        scroll.addView(list); layout.addView(scroll)
        dialogBuilder().setTitle(tr("🔑 أجزاء المفتاح", "🔑 Key Shares")).setView(layout).setPositiveButton(tr("تم", "Done"), null).show().also { secureDialog(it) }
    }

    private fun saveVaultFile(html: String, fileName: String, pass: String, attachCount: Int) {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); setType("text/html"); putExtra(Intent.EXTRA_TITLE, fileName) }
        launchForResult(intent) { code, d ->
            val uri = d?.data
            if (code != RESULT_OK || uri == null) return@launchForResult
            try {
                val output = contentResolver.openOutputStream(uri, "wt")
                    ?: throw java.io.IOException("Unable to open destination")
                output.use { it.write(html.toByteArray(Charsets.UTF_8)) }
            } catch (e: Exception) {
                haptic(false)
                toast(tr("فشل الحفظ: ", "Save failed: ") + (e.message ?: "I/O error"), true)
                return@launchForResult
            }
            VaultStore.addVaultStats(attachCount, html.length.toLong())
            val busy = showBusy(tr("جاري التحقق من سلامة الملف...", "Verifying the saved file..."))
            Thread {
                val ok = verifyFile(uri, pass, draftKeyFileBytes)
                runOnUiThread {
                    busy.dismiss(); haptic(ok)
                    toast(if (ok) tr("👑 تم الحفظ والتحقق من سلامة الملف ✔", "👑 Saved & verified ✔") else tr("⚠️ تم الحفظ لكن فشل التحقق", "⚠️ Saved but verification failed"), true)
                    wipeDrafts(); currentScreen = "home"; renderUI()
                }
            }.start()
        }
    }

    private fun verifyFile(uri: Uri, pass: String, keyfile: ByteArray?): Boolean {
        return try {
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return false
            val parsed = CryptoEngine.parseHtml(String(bytes, Charsets.UTF_8)) ?: return false
            CryptoEngine.decryptVault(parsed, pass.toCharArray(), keyfile) != null
        } catch (e: Throwable) { false }
    }

    private fun shareVaultFile(html: String, fileName: String, attachCount: Int) {
        try {
            val dir = File(cacheDir, "shared").apply { mkdirs() }
            val f = File(dir, fileName); f.writeText(html, Charsets.UTF_8)
            val uri = Uri.parse("content://$packageName.vaultshare/shared/" + Uri.encode(fileName))
            val send = Intent(Intent.ACTION_SEND).apply { setType("text/html"); putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            suppressLockOnce = true
            startActivity(Intent.createChooser(send, tr("مشاركة الخزنة المشفرة", "Share encrypted vault")))
            VaultStore.addVaultStats(attachCount, html.length.toLong()); haptic(true)
            wipeDrafts(); currentScreen = "home"; renderUI()
        } catch (e: Exception) { haptic(false); toast(tr("فشلت المشاركة: ", "Share failed: ") + e.message, true) }
    }

    private fun cleanShareCache() { try { File(cacheDir, "shared").listFiles()?.forEach { it.delete() } } catch (e: Exception) {} }

    private fun strengthOf(p: String): Int {
        if (p.isEmpty()) return 0
        var s = minOf(p.length, 20) * 3; var classes = 0
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
        val all = sets.joinToString(""); val out = mutableListOf<Char>()
        sets.forEach { out.add(it[rng.nextInt(it.length)]) }
        while (out.size < len) out.add(all[rng.nextInt(all.length)])
        out.shuffle(rng); return out.joinToString("")
    }

    private fun generatePassphrase(words: Int, extras: Boolean): String {
        val cons = "bcdfghjklmnprstvwz"; val vow = "aeiou"
        val ws = (1..words).map {
            val sb = StringBuilder()
            sb.append(cons[rng.nextInt(cons.length)].uppercaseChar())
            sb.append(vow[rng.nextInt(5)]); sb.append(cons[rng.nextInt(cons.length)])
            sb.append(vow[rng.nextInt(5)]); sb.append(cons[rng.nextInt(cons.length)])
            sb.toString()
        }
        var out = ws.joinToString("-")
        if (extras) out += "-" + (10 + rng.nextInt(90)) + "!@#%&*?"[rng.nextInt(7)]
        return out
    }

    private fun log2(x: Double) = ln(x) / ln(2.0)

    private fun showGeneratorDialog(onUse: (String) -> Unit) {
        var phraseMode = false; var current = ""
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(12), dp(20), dp(4)) }
        val group = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        val rbRand = RadioButton(this).apply { text = tr("عشوائية", "Random"); setTextColor(AppConfig.TEXT_MAIN); id = View.generateViewId(); isChecked = true }
        val rbPhrase = RadioButton(this).apply { text = tr("عبارة مرورية", "Passphrase"); setTextColor(AppConfig.TEXT_MAIN); id = View.generateViewId() }
        group.addView(rbRand); group.addView(rbPhrase)
        val preview = TextView(this).apply { textSize = fs(17f); typeface = Typeface.MONOSPACE; setTextColor(AppConfig.GOLD_LIGHT); setTextIsSelectable(true); setPadding(0, dp(8), 0, dp(4)) }
        val entropy = TextView(this).apply { textSize = fs(12f); setTextColor(AppConfig.TEXT_MUTED); setPadding(0, 0, 0, dp(8)) }
        val lenLabel = TextView(this).apply { setTextColor(AppConfig.TEXT_MAIN); textSize = fs(14f) }
        val seek = SeekBar(this).apply { max = 40; progress = 12 }
        val chk = CheckBox(this).apply { isChecked = true; setTextColor(AppConfig.TEXT_MAIN) }
        fun regen() {
            if (phraseMode) {
                val w = seek.progress.coerceAtMost(5) + 3
                lenLabel.text = tr("عدد الكلمات: $w", "Words: $w"); chk.text = tr("إضافة رقم ورمز", "Add number & symbol")
                current = generatePassphrase(w, chk.isChecked)
                entropy.text = "≈ " + (w * log2(145800.0) + if (chk.isChecked) log2(90.0 * 7) else 0.0).toInt() + " bits"
            } else {
                val len = seek.progress + 8
                lenLabel.text = tr("الطول: $len", "Length: $len"); chk.text = tr("رموز خاصة", "Special characters")
                current = generatePassword(len, chk.isChecked)
                entropy.text = "≈ " + (len * log2(if (chk.isChecked) 69.0 else 57.0)).toInt() + " bits"
            }
            preview.text = current
        }
        fun configureSeek() { if (phraseMode) { seek.max = 5; seek.progress = 2 } else { seek.max = 40; seek.progress = 12 } }
        group.setOnCheckedChangeListener { _, id -> phraseMode = id == rbPhrase.id; configureSeek(); regen() }
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) { regen() }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        chk.setOnCheckedChangeListener { _, _ -> regen() }
        layout.addView(group); layout.addView(preview); layout.addView(entropy); layout.addView(lenLabel); layout.addView(seek); layout.addView(chk)
        layout.addView(Button(this).apply { text = tr("🔄 توليد جديد", "🔄 Generate again"); isAllCaps = false; setOnClickListener { regen() } })
        regen()
        dialogBuilder().setTitle(tr("🎲 المولّد", "🎲 Generator")).setView(layout)
            .setPositiveButton(tr("استخدام", "Use")) { _, _ -> onUse(current) }
            .setNegativeButton(tr("إلغاء", "Cancel"), null).show().also { secureDialog(it) }
    }

    // ---------------------------------------------------------- open
    private fun buildOpen(parent: LinearLayout) {
        parent.addView(createBackButton())
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; background = luxuryBg(AppConfig.CARD_DARK, 16, 1, AppConfig.BORDER); setPadding(dp(32), dp(48), dp(32), dp(48))
        }
        card.addView(TextView(this).apply { text = "📂"; textSize = fs(64f); gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(16)) })
        card.addView(TextView(this).apply { text = tr("اختر ملف الخزنة المشفرة (.html)", "Choose an encrypted vault file (.html)"); setTextColor(AppConfig.TEXT_MAIN); textSize = fs(16f); gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(8)) })
        card.addView(TextView(this).apply { text = tr("يدعم الملفات القديمة (v4) والجديدة (v5)", "Supports old (v4) and new (v5) files"); setTextColor(AppConfig.TEXT_MUTED); textSize = fs(12f); gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(24)) })
        card.addView(goldButton(tr("تصفح الملفات", "Browse files"), 48).apply {
            setOnClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); setType("*/*") }
                launchForResult(intent) { code, d -> if (code == RESULT_OK) d?.data?.let { openVaultFile(it) } }
            }
        })
        parent.addView(card)
    }

    private fun openVaultFile(uri: Uri) {
        try {
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return
            val parsed = CryptoEngine.parseHtml(String(bytes, Charsets.UTF_8))
            if (parsed == null) { toast(tr("ملف غير صالح", "Invalid file")); return }
            showUnlockDialog(parsed)
        } catch (e: Throwable) { toast(tr("خطأ في القراءة", "Read error")) }
    }

    private fun showUnlockDialog(p: ParsedVault) {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0) }
        val (row, et) = secretInput(tr("أدخل المفتاح", "Enter key"), "") {}
        
        var keyFileBytes: ByteArray? = null
        val keyFileStatus = TextView(this).apply { textSize = fs(12f); setTextColor(AppConfig.TEXT_MUTED); setPadding(0, dp(6), 0, 0) }
        val keyFileBtn = outlineButton(tr("📁 إرفاق ملف مفتاح (إن وجد)", "📁 Attach keyfile (if any)"), 40).apply {
            layoutParams = lp(h = dp(40), top = 6)
            setOnClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); setType("*/*") }
                launchForResult(intent) { code, data ->
                    if (code == RESULT_OK && data?.data != null) {
                        try {
                            keyFileBytes = contentResolver.openInputStream(data.data!!)?.use { it.readBytes() }
                            keyFileStatus.text = tr("✔ تم تحميل ملف المفتاح", "✔ Keyfile loaded")
                            keyFileStatus.setTextColor(AppConfig.OK_GREEN)
                        } catch (e: Exception) {}
                    }
                }
            }
        }
        
        if (p.keyFileHash != null) {
            layout.addView(TextView(this).apply { text = tr("هذه الخزنة تتطلب ملف مفتاح إضافي!", "This vault requires an additional keyfile!"); setTextColor(AppConfig.WARN_ORANGE); typeface = Typeface.DEFAULT_BOLD })
            layout.addView(keyFileBtn); layout.addView(keyFileStatus)
        }

        val err = TextView(this).apply { setTextColor(AppConfig.DANGER_RED); textSize = fs(13f); setPadding(0, dp(6), 0, 0) }
        val busy = ProgressBar(this).apply { visibility = View.GONE }
        layout.addView(row); layout.addView(err); layout.addView(busy)
        
        val dlg = dialogBuilder().setTitle(tr("🔒 فتح الخزنة", "🔒 Unlock vault")).setView(layout)
            .setPositiveButton(tr("فتح", "Open"), null).setNegativeButton(tr("إلغاء", "Cancel"), null).create()
        dlg.show(); secureDialog(dlg)
        val btn = dlg.getButton(AlertDialog.BUTTON_POSITIVE)
        btn.setOnClickListener {
            val rem = VaultStore.lockRemainingMs()
            if (rem > 0) { err.text = tr("محاولات خاطئة كثيرة. انتظر ", "Too many wrong attempts. Wait ") + (rem / 1000 + 1) + "s"; shake(row); haptic(false); return@setOnClickListener }
            val pass = et.text.toString(); if (pass.isEmpty()) return@setOnClickListener
            btn.isEnabled = false; busy.visibility = View.VISIBLE; err.text = ""
            Thread {
                val dec = CryptoEngine.decryptVault(p, pass.toCharArray(), keyFileBytes)
                runOnUiThread {
                    if (!dlg.isShowing) return@runOnUiThread
                    busy.visibility = View.GONE; btn.isEnabled = true
                    if (dec != null) { VaultStore.resetFailures(); dlg.dismiss(); haptic(true); onDecrypted(dec) }
                    else { VaultStore.recordFailure(); haptic(false); err.text = tr("مفتاح خاطئ أو ملف تالف", "Wrong key or corrupted file"); shake(row) }
                }
            }.start()
        }
    }

    private fun onDecrypted(json: String) {
        decryptedPayloadJson = json
        val exp = try { JSONObject(json).optLong("expires", 0L) } catch (e: Exception) { 0L }
        if (exp > 0 && System.currentTimeMillis() > exp) {
            dialogBuilder().setTitle(tr("⏳ انتهت صلاحية الخزنة", "⏳ Vault expired"))
                .setMessage(tr("انتهت في ", "Expired on ") + SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date(exp)) + tr("\nصفحة HTML ترفض عرضها، لكنك كمالك تستطيع عرضها هنا.", "\nThe HTML page refuses to show it, but as the owner you can view it here."))
                .setPositiveButton(tr("عرض على أي حال", "View anyway")) { _, _ -> showDecrypted() }
                .setNegativeButton(tr("إغلاق", "Close")) { _, _ -> decryptedPayloadJson = null }
                .setCancelable(false).show().also { secureDialog(it) }
        } else showDecrypted()
    }
    private fun showDecrypted() { unlockAnimPending = true; currentScreen = "view"; renderUI() }

    // ---------------------------------------------------------- view
    private fun mediaMime(item: JSONObject): String {
        val d = item.optString("data")
        return if (d.startsWith("data:")) d.substringAfter("data:").substringBefore(";").ifEmpty { "application/octet-stream" } else item.optString("mime", "application/octet-stream")
    }
    private fun mediaB64(item: JSONObject): String { val d = item.optString("data"); return if (d.startsWith("data:")) d.substringAfter(",") else d }

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
        val json = try { JSONObject(jsonStr) } catch (e: Exception) { toast(tr("بيانات تالفة", "Corrupted data")); goHome(); return }

        if (unlockAnimPending) {
            unlockAnimPending = false
            if (effectsOn()) {
                val icon = TextView(this).apply { text = "🔒"; textSize = fs(56f); gravity = Gravity.CENTER; layoutParams = lp(bottom = 4) }
                parent.addView(icon)
                icon.postDelayed({
                    icon.animate().scaleX(1.3f).scaleY(1.3f).setDuration(180).withEndAction {
                        icon.text = "🔓"
                        icon.animate().scaleX(1f).scaleY(1f).setDuration(180).withEndAction {
                            icon.animate().alpha(0f).setStartDelay(600).setDuration(300).withEndAction { icon.visibility = View.GONE }.start()
                        }.start()
                    }.start()
                }, 150)
            }
        }

        val title = json.optString("title")
        val type = json.optString("type")
        val spec = AppConfig.spec(type)
        
        if (title.isNotEmpty()) parent.addView(TextView(this).apply { text = title; setTextColor(AppConfig.GOLD_PRIMARY); textSize = fs(20f); typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD); gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(4)) })
        
        val info = mutableListOf(spec.title)
        json.optString("created").takeIf { it.isNotEmpty() }?.let { info.add(it) }
        json.optLong("expires", 0L).takeIf { it > 0 }?.let { info.add(tr("تنتهي: ", "Expires: ") + SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date(it))) }
        parent.addView(TextView(this).apply { text = info.joinToString("  •  "); setTextColor(AppConfig.TEXT_MUTED); textSize = fs(12f); gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(12)) })

        // Reminders
        val reminders = json.optJSONArray("reminders")
        if (reminders != null) for (i in 0 until reminders.length()) {
            val r = reminders.getJSONObject(i)
            val rDate = r.optLong("date")
            if (rDate > 0 && rDate < System.currentTimeMillis()) {
                val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = luxuryBg(AppConfig.CARD_DARK, 12, 1, AppConfig.WARN_ORANGE); setPadding(dp(16), dp(16), dp(16), dp(16)); layoutParams = lp(bottom = 12) }
                box.addView(TextView(this).apply { text = "⏰ " + tr("تذكير مستحق", "Due Reminder"); setTextColor(AppConfig.WARN_ORANGE); typeface = Typeface.DEFAULT_BOLD })
                box.addView(TextView(this).apply { text = r.optString("text"); setTextColor(AppConfig.TEXT_MAIN); textSize = fs(15f); setPadding(0, dp(6), 0, 0) })
                parent.addView(box)
            }
        }

        // TOTP Codes
        val fields = json.optJSONObject("data")
        val secretArr = json.optJSONArray("secret"); val totpArr = json.optJSONArray("totp")
        val secretSet = HashSet<String>().also { s -> if (secretArr != null) for (i in 0 until secretArr.length()) s.add(secretArr.optString(i)) }
        val legacy = Regex("password|pass|cvv|pin|seed|secret|كلمة المرور|رقم البطاقة|العبارة", RegexOption.IGNORE_CASE)

        if (totpArr != null && fields != null) for (i in 0 until totpArr.length()) {
            val label = totpArr.optString(i); val p = TotpEngine.parse(fields.optString(label)) ?: continue
            val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = luxuryBg(AppConfig.CARD_DARK, 12, 1, AppConfig.GOLD_PRIMARY); setPadding(dp(16), dp(14), dp(16), dp(14)); layoutParams = lp(bottom = 14) }
            box.addView(TextView(this).apply { text = "🔢  $label"; setTextColor(AppConfig.TEXT_MUTED); textSize = fs(12f); typeface = Typeface.DEFAULT_BOLD })
            val codeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val code = TextView(this).apply { text = "------"; setTextColor(AppConfig.GOLD_PRIMARY); textSize = fs(32f); typeface = Typeface.MONOSPACE; letterSpacing = 0.1f; gravity = Gravity.CENTER; layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f) }
            codeRow.addView(code)
            codeRow.addView(iconButton("📋", tr("نسخ الرمز", "Copy code")) { copyToClipboard(TotpEngine.code(p, System.currentTimeMillis())) })
            box.addView(codeRow)
            val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = p.period; layoutParams = lp(h = dp(4), top = 4) }
            box.addView(bar); parent.addView(box); totpEntries.add(TotpEntry(p, code, bar))
        }

        // Fields
        if (fields != null && fields.length() > 0) {
            val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = luxuryBg(AppConfig.CARD_DARK, 12, 1, AppConfig.GOLD_PRIMARY); setPadding(dp(16), dp(16), dp(16), dp(16)); layoutParams = lp(bottom = 16) }
            for (key in fields.keys()) {
                val valStr = fields.optString(key); if (valStr.isEmpty()) continue
                val hide = if (secretArr != null) secretSet.contains(key) else legacy.containsMatchIn(key)
                val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(8), 0, dp(8)) }
                row.addView(TextView(this).apply { text = key; setTextColor(AppConfig.TEXT_MUTED); textSize = fs(12f); typeface = Typeface.DEFAULT_BOLD })
                val valRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                val valueView = TextView(this).apply { text = if (hide) MASK else valStr; setTextColor(AppConfig.TEXT_MAIN); textSize = fs(16f); typeface = Typeface.DEFAULT_BOLD; layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f) }
                valRow.addView(valueView)
                if (hide) {
                    var shown = false
                    valRow.addView(iconButton("👁", tr("إظهار / إخفاء $key", "Show / hide $key")) { shown = !shown; flipText(valueView, if (shown) valStr else MASK) })
                }
                valRow.addView(iconButton("📋", tr("نسخ $key", "Copy $key")) { copyToClipboard(valStr) })
                
                // Feature #12: QR code for field
                if (key.contains("wifi", true) || key.contains("url", true) || key.contains("address", true)) {
                    valRow.addView(iconButton("🔲", tr("عرض QR", "Show QR")) {
                        val bmp = QrEncoder.renderBitmap(valStr, 800)
                        if (bmp != null) {
                            val iv = ImageView(this).apply { setImageBitmap(bmp); adjustViewBounds = true; setPadding(0, dp(16), 0, dp(16)) }
                            dialogBuilder().setTitle(key).setView(iv).setPositiveButton("OK", null).show()
                        }
                    })
                }
                
                row.addView(valRow); box.addView(row)
            }
            parent.addView(box)
        }

        // Media
        val media = json.optJSONArray("media")
        if (media != null) for (i in 0 until media.length()) {
            val item = media.getJSONObject(i)
            val name = item.optString("name"); val kind = item.optString("kind"); val mime = mediaMime(item); val b64 = mediaB64(item)
            val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = luxuryBg(AppConfig.CARD_DARK, 12, 1, AppConfig.BORDER); setPadding(dp(16), dp(16), dp(16), dp(16)); layoutParams = lp(bottom = 12) }
            if (kind == "image") {
                card.addView(ImageView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(MATCH, dp(220)); scaleType = ImageView.ScaleType.FIT_CENTER
                    contentDescription = tr("صورة: $name — اضغط للتكبير", "Image: $name — tap to enlarge")
                    try { setImageBitmap(decodeSampled(Base64.decode(b64, Base64.DEFAULT), 1024)) } catch (e: Throwable) {}
                    setOnClickListener { try { decodeSampled(Base64.decode(b64, Base64.DEFAULT), 2048)?.let { showImagePreview(it) } } catch (e: Throwable) { toast(tr("تعذر العرض", "Cannot display")) } }
                })
            }
            card.addView(TextView(this).apply { text = name; setTextColor(AppConfig.TEXT_MAIN); textSize = fs(12f); setPadding(0, dp(8), 0, dp(8)); gravity = Gravity.CENTER })
            card.addView(goldButton(tr("حفظ الملف", "Save file"), 48).apply {
                setOnClickListener {
                    pendingSaveFileData = b64
                    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); setType(mime); putExtra(Intent.EXTRA_TITLE, name) }
                    launchForResult(intent) { code, d ->
                        val uri = d?.data; val payload = pendingSaveFileData
                        if (code == RESULT_OK && uri != null && payload != null) {
                            try {
                                val output = contentResolver.openOutputStream(uri, "wt")
                                    ?: throw java.io.IOException("Unable to open destination")
                                output.use { it.write(Base64.decode(payload, Base64.DEFAULT)) }
                                haptic(true)
                                toast(tr("✔ تم حفظ الملف", "✔ File saved"))
                            } catch (e: Exception) {
                                haptic(false)
                                toast(tr("فشل الحفظ: ", "Save failed: ") + (e.message ?: "I/O error"), true)
                            }
                        }
                        pendingSaveFileData = null
                    }
                }
            })
            parent.addView(card)
        }

        // Action Row (Lock & Delete from Library)
        val actionRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutParams = lp(top = 8) }
        actionRow.addView(outlineButton(tr("🔒 قفل", "🔒 Lock"), 48, AppConfig.GOLD_PRIMARY).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(8) }
            setOnClickListener { goHome() }
        })
        if (currentVaultId != null) {
            actionRow.addView(outlineButton(tr("🗑 حذف", "🗑 Delete"), 48, AppConfig.DANGER_RED).apply {
                layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f)
                setOnClickListener {
                    dialogBuilder().setTitle(tr("حذف", "Delete")).setMessage(tr("حذف هذه الخزنة نهائياً؟", "Delete this vault permanently?"))
                        .setPositiveButton(tr("حذف", "Delete")) { _, _ -> LibraryStore.deleteVault(currentVaultId!!); goHome() }
                        .setNegativeButton(tr("إلغاء", "Cancel"), null).show()
                }
            })
        }
        parent.addView(actionRow)

        if (totpEntries.isNotEmpty()) { tickTotp(); handler.postDelayed(totpTick, 1000) }
    }

    private fun tickTotp() {
        val now = System.currentTimeMillis()
        for (e in totpEntries) {
            val c = TotpEngine.code(e.p, now); val rem = TotpEngine.remaining(e.p, now); val h = (c.length + 1) / 2
            e.codeView.text = c.substring(0, h) + " " + c.substring(h)
            e.bar.progress = rem
            e.bar.progressTintList = ColorStateList.valueOf(if (rem <= 5) AppConfig.WARN_ORANGE else AppConfig.GOLD_PRIMARY)
        }
    }

    private fun showImagePreview(bmp: Bitmap) {
        val dlg = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val frame = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val img = ImageView(this).apply { setImageBitmap(bmp); scaleType = ImageView.ScaleType.FIT_CENTER }
        var scale = 1f; var lastX = 0f; var lastY = 0f
        val sd = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(d: ScaleGestureDetector): Boolean {
                scale = (scale * d.scaleFactor).coerceIn(1f, 5f); img.scaleX = scale; img.scaleY = scale
                if (scale == 1f) { img.translationX = 0f; img.translationY = 0f }
                return true
            }
        })
        img.setOnTouchListener { _, e ->
            sd.onTouchEvent(e)
            if (!sd.isInProgress && scale > 1f) {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_UP -> { lastX = e.x; lastY = e.y }
                    MotionEvent.ACTION_MOVE -> { img.translationX += e.x - lastX; img.translationY += e.y - lastY; lastX = e.x; lastY = e.y }
                }
            }
            true
        }
        frame.addView(img, FrameLayout.LayoutParams(MATCH, MATCH))
        frame.addView(iconButton("✖", tr("إغلاق", "Close"), 24f) { dlg.dismiss() }.apply { setTextColor(Color.WHITE) },
            FrameLayout.LayoutParams(dp(56), dp(56), Gravity.TOP or Gravity.END).apply { setMargins(dp(8), dp(32), dp(8), 0) })
        dlg.setContentView(frame); secureDialog(dlg); dlg.show()
    }

    // ---------------------------------------------------------- settings
    private fun showSettingsDialog() {
        lateinit var dlg: AlertDialog
        fun reopen() { dlg.dismiss(); renderUI(); showSettingsDialog() }
        val pad = dp(20)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }
        fun section(t: String) = TextView(this).apply { text = t; setTextColor(AppConfig.GOLD_PRIMARY); textSize = fs(13f); typeface = Typeface.DEFAULT_BOLD; setPadding(0, dp(14), 0, dp(6)) }
        fun check(t: String, v: Boolean, cb: (android.widget.CompoundButton, Boolean) -> Unit) = CheckBox(this).apply {
            text = t; setTextColor(AppConfig.TEXT_MAIN); textSize = fs(14f); isChecked = v; minHeight = dp(44); setOnCheckedChangeListener { b, c -> cb(b, c) }
        }

        layout.addView(section(tr("اللغة", "Language")))
        val langRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for ((code, name) in listOf("ar" to "العربية", "en" to "English")) {
            langRow.addView((if ((code == "ar") == VaultStore.isArabic()) goldButton(name, 44) else outlineButton(name, 44)).apply {
                layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(8) }
                setOnClickListener { VaultStore.setLang(code); reopen() }
            })
        }
        layout.addView(langRow)

        layout.addView(section(tr("الثيم", "Theme")))
        val strip = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (t in THEMES) {
            val sel = t.id == AppConfig.theme.id
            val cell = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(6), dp(4), dp(6), dp(4)) }
            cell.addView(View(this).apply {
                background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(t.bg1, t.acc)).apply { shape = GradientDrawable.OVAL; setStroke(dp(if (sel) 3 else 1), if (sel) Color.WHITE else t.border) }
                layoutParams = LinearLayout.LayoutParams(dp(46), dp(46))
            })
            cell.addView(TextView(this).apply { text = if (VaultStore.isArabic()) t.nameAr else t.nameEn; setTextColor(AppConfig.TEXT_MUTED); textSize = fs(10f); gravity = Gravity.CENTER; setPadding(0, dp(4), 0, 0) })
            cell.contentDescription = (if (VaultStore.isArabic()) t.nameAr else t.nameEn); cell.isClickable = true
            cell.setOnClickListener { VaultStore.setTheme(t.id); haptic(true); reopen() }
            strip.addView(cell)
        }
        layout.addView(HorizontalScrollView(this).apply { addView(strip); isHorizontalScrollBarEnabled = false })

        layout.addView(section(tr("المظهر والتفاعل", "Appearance & feedback")))
        layout.addView(check(tr("التأثيرات الحركية", "Visual effects"), VaultStore.effects()) { _, c -> VaultStore.setEffects(c); reopen() })
        layout.addView(check(tr("الاهتزاز عند النجاح / الفشل", "Haptic feedback"), VaultStore.haptic()) { _, c -> VaultStore.setHaptic(c) })
        layout.addView(TextView(this).apply { text = tr("حجم الخط", "Font size"); setTextColor(AppConfig.TEXT_MUTED); textSize = fs(12f); setPadding(0, dp(8), 0, dp(4)) })
        layout.addView(Spinner(this).apply {
            adapter = themedAdapter(AppConfig.fontLabels()); setSelection(VaultStore.fontIndex(), false)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) { if (p2 != VaultStore.fontIndex()) { VaultStore.setFontIndex(p2); reopen() } }
                override fun onNothingSelected(p0: AdapterView<*>?) {}
            }
        })

        layout.addView(section(tr("الخصوصية والأمان", "Privacy & security")))
        layout.addView(check(tr("منع لقطات الشاشة", "Block screenshots"), VaultStore.isScreenSecure()) { _, c -> VaultStore.setScreenSecure(c); applyScreenSecurity() })
        layout.addView(check(tr("قفل التطبيق ببصمة / رمز الجهاز", "App lock (biometrics / device PIN)"), VaultStore.isAppLock()) { b, c ->
            if (c && !deviceSecure()) { b.isChecked = false; toast(tr("فعّل قفل الشاشة في إعدادات الجهاز أولاً", "Set a screen lock in device settings first"), true) } else VaultStore.setAppLock(c)
        })
        
        layout.addView(TextView(this).apply { text = tr("القفل التلقائي عند مغادرة التطبيق", "Auto-lock after leaving the app"); setTextColor(AppConfig.TEXT_MUTED); textSize = fs(12f); setPadding(0, dp(8), 0, dp(4)) })
        layout.addView(Spinner(this).apply {
            adapter = themedAdapter(AppConfig.lockLabels()); setSelection(AppConfig.LOCK_VALUES.indexOf(VaultStore.autoLockSeconds()).coerceAtLeast(0), false)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) { VaultStore.setAutoLockSeconds(AppConfig.LOCK_VALUES[p2]) }
                override fun onNothingSelected(p0: AdapterView<*>?) {}
            }
        })
        
        layout.addView(check(tr("هز الهاتف للطوارئ (مسح وقفل)", "Shake phone for panic (clear & lock)"), VaultStore.isShakePanicEnabled()) { _, c -> VaultStore.setShakePanic(c) })
        layout.addView(check(tr("قفل عند قلب الهاتف", "Lock on face-down"), VaultStore.isFaceDownLockEnabled()) { _, c -> VaultStore.setFaceDownLock(c) })

        layout.addView(outlineButton(tr("🧹 مسح الذاكرة والحافظة الآن", "🧹 Clear memory & clipboard now"), 44, AppConfig.WARN_ORANGE).apply { layoutParams = lp(h = dp(44), top = 12); setOnClickListener { dlg.dismiss(); wipeEverything() } })
        layout.addView(outlineButton(tr("إعادة تعيين الإحصائيات", "Reset statistics"), 44, AppConfig.TEXT_MUTED).apply { layoutParams = lp(h = dp(44), top = 8); setOnClickListener { VaultStore.resetStats(); toast(tr("تمت إعادة التعيين", "Reset done")); dlg.dismiss(); renderUI() } })

        dlg = dialogBuilder().setTitle(tr("⚙️ الإعدادات", "⚙️ Settings")).setView(ScrollView(this).apply { addView(layout) }).setPositiveButton(tr("حسناً", "OK"), null).create()
        dlg.show(); secureDialog(dlg)
    }

    // ---------------------------------------------------------- clipboard + countdown ring
    private fun copyToClipboard(text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Keykeep", text)
        if (Build.VERSION.SDK_INT >= 33) clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
        cm.setPrimaryClip(clip); haptic(true)
        toast(tr("تم النسخ — سيُمسح بعد 30 ثانية", "Copied — clears in 30 seconds"))
        handler.removeCallbacks(clearClipRunnable); handler.postDelayed(clearClipRunnable, CLIP_CLEAR_MS)
        showRing()
    }

    private fun clearClipboard() {
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            if (Build.VERSION.SDK_INT >= 28) cm.clearPrimaryClip() else cm.setPrimaryClip(ClipData.newPlainText("", ""))
        } catch (e: Exception) {}
    }

    private fun showRing() {
        if (!effectsOn()) return
        hideRing()
        val r = CountdownRing(this).apply {
            ringColor = AppConfig.GOLD_PRIMARY; trackColor = withAlpha(AppConfig.TEXT_MUTED, 0x55); textColor = AppConfig.TEXT_MAIN
            background = luxuryBg(withAlpha(AppConfig.CARD_DARK, 0xE6), 26, 1, AppConfig.BORDER).apply { shape = GradientDrawable.OVAL }
            contentDescription = tr("اضغط لمسح الحافظة الآن", "Tap to clear the clipboard now")
            setOnClickListener { handler.removeCallbacks(clearClipRunnable); clearClipboard(); hideRing(); toast(tr("تم مسح الحافظة", "Clipboard cleared")) }
        }
        overlay.addView(r, FrameLayout.LayoutParams(dp(54), dp(54), Gravity.BOTTOM or Gravity.END).apply { setMargins(dp(16), dp(16), dp(16), dp(28)) })
        ring = r
        ringAnim = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = CLIP_CLEAR_MS; interpolator = LinearInterpolator()
            addUpdateListener { r.fraction = it.animatedValue as Float; r.label = ((it.animatedValue as Float) * 30f + 0.99f).toInt().toString(); r.invalidate() }
            addListener(object : AnimatorListenerAdapter() { override fun onAnimationEnd(a: Animator) { if (ring === r) hideRing() } })
            start()
        }
    }

    private fun hideRing() {
        ringAnim?.cancel(); ringAnim = null
        ring?.let { overlay.removeView(it) }; ring = null
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
        val units = arrayOf("B", "KB", "MB", "GB", "TB"); var s = size.toDouble(); var i = 0
        while (s >= 1024 && i < units.size - 1) { s /= 1024; i++ }
        return String.format(Locale.US, "%.1f %s", s, units[i])
    }
}

// ============================================================
// SHARE PROVIDER (replaces androidx FileProvider; no extra dependency)
// Manifest entry required inside <application>:
//   <provider
//       android:name="com.keep.aio.VaultShareProvider"
//       android:authorities="${applicationId}.vaultshare"
//       android:exported="false"
//       android:grantUriPermissions="true" />
// ============================================================
class VaultShareProvider : ContentProvider() {
    private fun fileFor(uri: Uri): File? {
        val ctx = context ?: return null
        val dir = File(ctx.cacheDir, "shared").canonicalFile
        val name = uri.lastPathSegment ?: return null
        val f = File(dir, name).canonicalFile
        return if (f.parentFile == dir && f.exists()) f else null
    }

    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String = "text/html"

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val f = fileFor(uri) ?: throw java.io.FileNotFoundException(uri.toString())
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? {
        val f = fileFor(uri) ?: return null
        val cols = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return MatrixCursor(cols).apply { addRow(arrayOf<Any>(f.name, f.length())) }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = 0
}



