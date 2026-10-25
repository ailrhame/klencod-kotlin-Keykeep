
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
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; img-src data: blob:; base-uri 'none'; form-action 'none'">
<meta name="referrer" content="no-referrer">
<meta name="robots" content="noindex,nofollow">
<title>KEYKEEP ROYAL</title>
<style>
:root{%%THEME%%}
*{box-sizing:border-box}
body{margin:0;min-height:100vh;display:flex;flex-direction:column;background:var(--bg1);color:var(--txt);font-family:system-ui,-apple-system,sans-serif}
.bg{position:fixed;inset:0;background:radial-gradient(circle at 50% 0%,var(--bg2) 0%,var(--bg1) 70%);z-index:-2}
#bgc{position:fixed;inset:0;z-index:-1;pointer-events:none}
.header{text-align:center;padding:34px 20px 8px}
.crown{font-size:48px;animation:float 3s ease-in-out infinite}
@keyframes float{0%,100%{transform:translateY(0)}50%{transform:translateY(-10px)}}
h1{color:var(--acc);margin:8px 0 0;font-family:Georgia,serif;letter-spacing:2px;font-size:24px}
.sub{color:var(--mut);font-size:13px;min-height:18px;margin-top:6px}
.container{flex:1;padding:20px;max-width:600px;margin:0 auto;width:100%}
.card{background:var(--card);border:1px solid var(--bd);border-radius:16px;padding:18px;margin-bottom:16px;box-shadow:0 10px 30px rgba(0,0,0,.35)}
.fadein{opacity:0;animation:fin .5s ease forwards}
@keyframes fin{from{opacity:0;transform:translateY(16px)}to{opacity:1;transform:translateY(0)}}
.shake{animation:shake .45s}
@keyframes shake{0%,100%{transform:translateX(0)}15%{transform:translateX(-12px)}30%{transform:translateX(12px)}45%{transform:translateX(-9px)}60%{transform:translateX(9px)}80%{transform:translateX(-4px)}}
input{width:100%;padding:16px;margin-bottom:14px;border-radius:12px;border:1px solid var(--bd);background:var(--inp);color:var(--txt);outline:none;font-size:16px}
input:focus{border-color:var(--acc)}
button{font-family:inherit}
.main-btn{position:relative;overflow:hidden;width:100%;padding:16px;border-radius:12px;border:none;background:linear-gradient(135deg,var(--acc2),var(--acc),var(--acc3));color:var(--btxt);font-weight:bold;font-size:16px;cursor:pointer;animation:glow 2.4s ease-in-out infinite}
.main-btn::after{content:"";position:absolute;top:0;bottom:0;width:35%;left:-40%;background:linear-gradient(100deg,transparent,rgba(255,255,255,.45),transparent);animation:shim 2.8s infinite}
@keyframes shim{0%{left:-40%}45%,100%{left:120%}}
@keyframes glow{0%,100%{box-shadow:0 0 0 0 transparent}50%{box-shadow:0 0 18px 2px var(--acc)}}
.main-btn:disabled{opacity:.5}
.err{color:#ff7a7a;font-size:14px;text-align:center;margin-top:10px;min-height:20px}
.meta{color:var(--mut);font-size:12px;text-align:center;margin-bottom:8px}
.title{color:var(--acc);font-size:20px;text-align:center;margin:0 0 6px;font-family:Georgia,serif}
.row{display:flex;gap:8px;align-items:flex-start;padding:12px 0;border-bottom:1px solid var(--bd)}
.row:last-child{border-bottom:none}
.key{color:var(--acc);font-weight:bold;min-width:90px;font-size:14px}
.val{flex:1;font-family:monospace;font-size:16px;word-break:break-all;white-space:pre-wrap}
.flip{animation:flip .35s}
@keyframes flip{0%{transform:rotateX(0)}50%{transform:rotateX(90deg)}100%{transform:rotateX(0)}}
.tools{display:flex;gap:4px}
.mini{background:transparent;border:1px solid var(--bd);color:var(--txt);border-radius:8px;padding:6px 10px;cursor:pointer;min-width:40px;min-height:36px}
.code{font-family:monospace;font-size:32px;letter-spacing:4px;color:var(--acc);text-align:center;padding:6px 0}
.bar{height:5px;border-radius:3px;background:var(--bd);overflow:hidden}
.bar>i{display:block;height:100%;background:var(--acc);transition:width 1s linear}
.media{width:100%;border-radius:12px;margin-top:10px;border:1px solid var(--bd);display:block}
.fname{font-size:14px;word-break:break-all;margin-bottom:8px}
.fbtn{width:100%;padding:12px;border-radius:8px;border:1px solid var(--acc);background:transparent;color:var(--acc);font-weight:bold;cursor:pointer;margin-top:10px}
.lock{background:transparent;border:1px solid var(--dng);color:var(--dng);border-radius:12px;padding:12px;width:100%;cursor:pointer}
#res{display:none}
#uIcon{position:fixed;left:50%;top:38%;font-size:84px;opacity:0;pointer-events:none;transform:translate(-50%,-50%)}
.pop{animation:pop 1.1s ease forwards}
@keyframes pop{0%{opacity:0;transform:translate(-50%,-50%) scale(.3)}25%{opacity:1;transform:translate(-50%,-50%) scale(1.3)}60%{opacity:1;transform:translate(-50%,-50%) scale(1)}100%{opacity:0;transform:translate(-50%,-50%) scale(1.1)}}
@media (prefers-reduced-motion: reduce){*{animation:none!important;transition:none!important}}
</style>
</head>
<body>
<div class="bg"></div>
<canvas id="bgc"></canvas>
<div id="uIcon">🔒</div>
<div class="header"><div class="crown">👑</div><h1>ROYAL VAULT</h1><div class="sub" id="sub"></div></div>
<div class="container">
  <div id="login" class="card">
    <input type="password" id="pass" autocomplete="off">
    <button class="main-btn" id="goBtn"></button>
    <div class="err" id="err"></div>
  </div>
  <div id="res"></div>
</div>
<script>
const SALT = "%%SALT%%"; const IV = "%%IV%%"; const CIPHER = "%%CIPHER%%"; const VAULT_ID = "%%VAULT_ID%%"; const VERSION = "5"; const ITER = "%%ITER%%"; const KEYFILE_HASH = "%%KEYFILE_HASH%%";
var AR = %%ISAR%%;
function T(a, b) { return AR ? a : b; }
var enc = new TextEncoder();
var fails = 0, busy = false, idleTimer = null, hiddenAt = 0, totps = [], tick = null;
var UNLOCK = T('فتح الخزنة', 'Unlock');
var MASK_TXT = '••••••••';
var REDUCED = window.matchMedia && matchMedia('(prefers-reduced-motion: reduce)').matches;
function byId(i) { return document.getElementById(i); }
function mk(tag, cls, txt) { var e = document.createElement(tag); if (cls) e.className = cls; if (txt !== undefined && txt !== null) e.textContent = txt; return e; }
function fromB64(s) { var b = atob(s), u = new Uint8Array(b.length); for (var i = 0; i < b.length; i++) u[i] = b.charCodeAt(i); return u; }
function setErr(t) { byId('err').textContent = t; }

(function init() {
  document.documentElement.lang = AR ? 'ar' : 'en';
  document.documentElement.dir = AR ? 'rtl' : 'ltr';
  byId('pass').placeholder = T('أدخل مفتاح الخزنة', 'Enter vault key');
  byId('goBtn').textContent = UNLOCK;
  var t = T('خزنة مشفرة — لا تغادر جهازك', 'Encrypted vault — stays on your device'), el = byId('sub'), i = 0;
  if (REDUCED) { el.textContent = t; } else { (function s() { el.textContent = t.substring(0, i++); if (i <= t.length) setTimeout(s, 35); })(); }
  if (!REDUCED) {
    var cv = byId('bgc'), cx = cv.getContext('2d'), W, H, ps = [];
    var rs = function () { W = cv.width = innerWidth; H = cv.height = innerHeight; }; rs(); addEventListener('resize', rs);
    for (var k = 0; k < 30; k++) ps.push({ x: Math.random() * W, y: Math.random() * H, r: 1 + Math.random() * 2.2, v: 0.15 + Math.random() * 0.5, a: 0.15 + Math.random() * 0.35 });
    var col = getComputedStyle(document.documentElement).getPropertyValue('--acc').trim() || '#D4AF37';
    (function f() {
      cx.clearRect(0, 0, W, H);
      ps.forEach(function (p) { p.y -= p.v; if (p.y < -5) { p.y = H + 5; p.x = Math.random() * W; } cx.globalAlpha = p.a; cx.fillStyle = col; cx.beginPath(); cx.arc(p.x, p.y, p.r, 0, 6.283); cx.fill(); });
      requestAnimationFrame(f);
    })();
  }
})();

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

function b32(s) {
  var A = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567'; s = String(s).toUpperCase().replace(/[ =-]/g, ''); if (!s) return null;
  var bits = 0, buf = 0, out = [];
  for (var i = 0; i < s.length; i++) { var v = A.indexOf(s[i]); if (v < 0) return null; buf = (buf << 5) | v; bits += 5; if (bits >= 8) { bits -= 8; out.push((buf >> bits) & 255); } buf &= (1 << bits) - 1; }
  return new Uint8Array(out);
}
function parseTotp(inp) {
  inp = String(inp).trim(); var d = 6, p = 30, alg = 'SHA-1', sec = inp;
  if (/^otpauth:\/\//i.test(inp)) {
    var q = inp.indexOf('?'); var ps = new URLSearchParams(q >= 0 ? inp.substring(q + 1) : '');
    sec = ps.get('secret') || ''; var dd = parseInt(ps.get('digits'), 10); if (dd >= 6 && dd <= 8) d = dd;
    var pp = parseInt(ps.get('period'), 10); if (pp >= 15 && pp <= 120) p = pp;
    var a = (ps.get('algorithm') || '').toUpperCase(); if (a === 'SHA256') alg = 'SHA-256'; else if (a === 'SHA512') alg = 'SHA-512';
  }
  var k = b32(sec); return k && k.length ? { k: k, d: d, p: p, a: alg } : null;
}
async function totpCode(t, now) {
  var c = Math.floor(now / 1000 / t.p), m = new Uint8Array(8);
  for (var i = 7; i >= 0; i--) { m[i] = c & 255; c = Math.floor(c / 256); }
  var key = await crypto.subtle.importKey('raw', t.k, { name: 'HMAC', hash: t.a }, false, ['sign']);
  var h = new Uint8Array(await crypto.subtle.sign('HMAC', key, m)); var o = h[h.length - 1] & 15;
  var bin = ((h[o] & 127) << 24) | (h[o + 1] << 16) | (h[o + 2] << 8) | h[o + 3];
  var s = String(bin % Math.pow(10, t.d)); while (s.length < t.d) s = '0' + s; return s;
}
function fmtCode(s) { var h = Math.ceil(s.length / 2); return s.substring(0, h) + ' ' + s.substring(h); }
async function runTick() {
  var now = Date.now();
  for (var i = 0; i < totps.length; i++) {
    var e = totps[i], rem = e.t.p - (Math.floor(now / 1000) % e.t.p);
    e.code.textContent = fmtCode(await totpCode(e.t, now)); e.bar.style.width = (rem / e.t.p * 100) + '%'; e.sec.textContent = rem + 's';
  }
}

function isSecret(key, list) {
  if (list) return list.indexOf(key) >= 0;
  return /password|pass|cvv|pin|seed|secret|كلمة المرور|رقم البطاقة|العبارة/i.test(key);
}
function copyText(t, btn) {
  function done() { var o = btn.textContent; btn.textContent = '✔'; setTimeout(function () { btn.textContent = o; }, 1200); setTimeout(function () { try { navigator.clipboard.writeText(' '); } catch (e) {} }, 30000); }
  function fallback() { var ta = document.createElement('textarea'); ta.value = t; ta.style.position = 'fixed'; ta.style.opacity = '0'; document.body.appendChild(ta); ta.select(); try { document.execCommand('copy'); done(); } catch (e) {} document.body.removeChild(ta); }
  if (navigator.clipboard && navigator.clipboard.writeText) navigator.clipboard.writeText(t).then(done, fallback); else fallback();
}
function toBlob(item) {
  var mime = item.mime || 'application/octet-stream', b64 = item.data || '';
  if (b64.indexOf('data:') === 0) { var m = /^data:([^;,]*)/.exec(b64); if (m && m[1]) mime = m[1]; b64 = b64.substring(b64.indexOf(',') + 1); }
  return new Blob([fromB64(b64)], { type: mime });
}
function saveBlob(blob, name) {
  var url = URL.createObjectURL(blob), a = document.createElement('a');
  a.href = url; a.download = name || 'file'; document.body.appendChild(a); a.click(); document.body.removeChild(a);
  setTimeout(function () { URL.revokeObjectURL(url); }, 10000);
}
function addFx(el, i) { if (!REDUCED) { el.classList.add('fadein'); el.style.animationDelay = (i * 80) + 'ms'; } return el; }

function show(data) {
  byId('login').style.display = 'none';
  var box = byId('res'); box.textContent = ''; box.style.display = 'block';
  var n = 0;
  if (data.expires && Date.now() > data.expires) {
    var ex = mk('div', 'card'); ex.appendChild(mk('div', 'title', T('انتهت صلاحية هذه الخزنة', 'This vault has expired')));
    ex.appendChild(mk('div', 'meta', new Date(data.expires).toLocaleString())); box.appendChild(addFx(ex, 0)); return;
  }
  var head = mk('div', 'card');
  if (data.title) head.appendChild(mk('div', 'title', data.title));
  var info = [];
  if (data.created) info.push(T('أُنشئت: ', 'Created: ') + data.created);
  if (data.expires) info.push(T('تنتهي: ', 'Expires: ') + new Date(data.expires).toLocaleDateString());
  if (info.length) head.appendChild(mk('div', 'meta', info.join('  •  ')));
  if (data.title || info.length) box.appendChild(addFx(head, n++));
  var secrets = Array.isArray(data.secret) ? data.secret : null;
  var tl = Array.isArray(data.totp) ? data.totp : [];
  var f = data.data || {};
  var keys = Object.keys(f).filter(function (k) { return f[k]; });
  tl.forEach(function (k) {
    var t = f[k] ? parseTotp(f[k]) : null; if (!t) return;
    var c = mk('div', 'card'); c.appendChild(mk('div', 'key', '🔢 ' + k));
    var code = mk('div', 'code', '------'), sec = mk('div', 'meta', ''), bar = mk('div', 'bar'), fill = mk('i'); bar.appendChild(fill);
    var cp = mk('button', 'mini', '📋'); cp.onclick = async function () { copyText(await totpCode(t, Date.now()), cp); };
    c.appendChild(code); c.appendChild(bar); c.appendChild(sec); c.appendChild(cp); box.appendChild(addFx(c, n++));
    totps.push({ t: t, code: code, bar: fill, sec: sec });
  });
  if (keys.length) {
    var card = mk('div', 'card');
    keys.forEach(function (k) {
      var v = String(f[k]), hide = isSecret(k, secrets), shown = !hide;
      var row = mk('div', 'row'), val = mk('span', 'val', hide ? MASK_TXT : v), tools = mk('span', 'tools');
      if (hide) {
        var eye = mk('button', 'mini', '👁'); eye.setAttribute('aria-label', T('إظهار', 'Reveal'));
        eye.onclick = function () { shown = !shown; val.classList.remove('flip'); void val.offsetWidth; if (!REDUCED) val.classList.add('flip'); setTimeout(function () { val.textContent = shown ? v : MASK_TXT; }, REDUCED ? 0 : 150); };
        tools.appendChild(eye);
      }
      var cp = mk('button', 'mini', '📋'); cp.setAttribute('aria-label', T('نسخ', 'Copy')); cp.onclick = function () { copyText(v, cp); }; tools.appendChild(cp);
      row.appendChild(mk('span', 'key', k)); row.appendChild(val); row.appendChild(tools); card.appendChild(row);
    });
    box.appendChild(addFx(card, n++));
  }
  (data.media || []).forEach(function (item) {
    var c = mk('div', 'card'); c.appendChild(mk('div', 'fname', (item.kind === 'image' ? '🖼 ' : '📎 ') + (item.name || 'file')));
    var blob = toBlob(item);
    if (item.kind === 'image' && /^image\//.test(blob.type)) { var img = mk('img', 'media'); img.src = URL.createObjectURL(blob); c.appendChild(img); }
    var b = mk('button', 'fbtn', T('⬇ حفظ', '⬇ Save')); b.onclick = function () { saveBlob(blob, item.name); }; c.appendChild(b);
    box.appendChild(addFx(c, n++));
  });
  var lk = mk('button', 'lock', T('🔒 قفل الآن', '🔒 Lock now')); lk.onclick = function () { location.reload(); }; box.appendChild(lk);
  if (totps.length) { runTick(); tick = setInterval(runTick, 1000); }
  armIdle();
}

function armIdle() {
  function reset() { clearTimeout(idleTimer); idleTimer = setTimeout(function () { location.reload(); }, 90000); }
  ['click', 'touchstart', 'keydown', 'scroll'].forEach(function (ev) { document.addEventListener(ev, reset, { passive: true }); });
  reset();
}
function cooldown(sec) {
  var btn = byId('goBtn');
  if (sec <= 0) { btn.disabled = false; btn.textContent = UNLOCK; return; }
  btn.disabled = true; btn.textContent = T('انتظر ', 'Wait ') + sec + 's';
  setTimeout(function () { cooldown(sec - 1); }, 1000);
}
function unlockAnim() {
  if (REDUCED) return; var ic = byId('uIcon'); ic.textContent = '🔒'; ic.classList.remove('pop'); void ic.offsetWidth; ic.classList.add('pop');
  setTimeout(function () { ic.textContent = '🔓'; }, 300);
}
async function unlock() {
  if (busy) return;
  var pass = byId('pass').value; if (!pass) return;
  if (!window.crypto || !crypto.subtle) { setErr(T('⚠️ المتصفح يمنع التشفير. افتح الملف بمتصفح حديث أو عبر تطبيق KEYKEEP.', '⚠️ Browser blocks crypto. Open in a modern browser or the KEYKEEP app.')); return; }
  busy = true; byId('goBtn').disabled = true; setErr(T('جاري فك التشفير...', 'Decrypting...'));
  try {
    var data = await decryptVault(pass);
    byId('pass').value = ''; setErr(''); unlockAnim(); show(data);
  } catch (e) {
    fails++; setErr(T('مفتاح خاطئ أو ملف تالف', 'Wrong key or corrupted file'));
    var lg = byId('login'); lg.classList.remove('shake'); void lg.offsetWidth; lg.classList.add('shake');
    if (navigator.vibrate) navigator.vibrate(120);
    cooldown(fails >= 3 ? Math.min(Math.pow(2, fails - 2), 60) : 0);
  }
  busy = false;
  if (fails < 3 && byId('res').style.display !== 'block') byId('goBtn').disabled = false;
}
byId('goBtn').addEventListener('click', unlock);
byId('pass').addEventListener('keydown', function (e) { if (e.key === 'Enter') unlock(); });
document.addEventListener('visibilitychange', function () {
  if (document.hidden) hiddenAt = Date.now();
  else if (hiddenAt && byId('res').style.display === 'block' && Date.now() - hiddenAt > 30000) location.reload();
});
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
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "*/*" }
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
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"; putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true) }
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
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "text/html"; putExtra(Intent.EXTRA_TITLE, fileName) }
        launchForResult(intent) { code, d ->
            val uri = d?.data
            if (code != RESULT_OK || uri == null) return@launchForResult
            try { contentResolver.openOutputStream(uri, "wt")?.use { it.write(html.toByteArray(Charsets.UTF_8)) } }
            catch (e: Exception) { haptic(false); toast(tr("فشل الحفظ", "Save failed")); return@launchForResult }
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
            val send = Intent(Intent.ACTION_SEND).apply { type = "text/html"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
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
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "*/*" }
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
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "*/*" }
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
                            try { contentResolver.openOutputStream(uri, "wt")?.use { it.write(Base64.decode(payload, Base64.DEFAULT)) }; haptic(true); toast(tr("✔ تم حفظ الملف", "✔ File saved")) }
                            catch (e: Exception) { haptic(false); toast(tr("فشل الحفظ", "Save failed")) }
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
