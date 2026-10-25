package com.keep.aio

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
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
    
    // Native extraction state
    private var decryptedPayloadJson: String? = null
    private var pendingSaveFileData: String? = null
    private var pendingSaveFileMime: String? = null

    // ROYAL PALETTE
    private val BG_OBSIDIAN = Color.parseColor("#0B0C10")
    private val CARD_DARK = Color.parseColor("#1F2833")
    private val GOLD_PRIMARY = Color.parseColor("#D4AF37")
    private val GOLD_LIGHT = Color.parseColor("#F3E5AB")
    private val TEXT_WHITE = Color.parseColor("#FFFFFF")
    private val TEXT_MUTED = Color.parseColor("#8A8F9C")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        renderRoyalUI()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun luxuryBg(color: Int, radius: Int, strokeWidth: Int = 1, strokeColor: Int = GOLD_PRIMARY): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            if (strokeWidth > 0) setStroke(dp(strokeWidth), strokeColor)
        }
    }

    private fun applyGoldGradient(view: View) {
        val gradient = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(GOLD_LIGHT, GOLD_PRIMARY, Color.parseColor("#B8860B"))
        ).apply {
            cornerRadius = dp(12).toFloat()
        }
        view.background = gradient
    }

    private fun renderRoyalUI() {
        val root = FrameLayout(this).apply {
            setBackgroundColor(BG_OBSIDIAN)
        }

        val scrollView = ScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(60), dp(24), dp(24))
        }
        scrollView.addView(container)
        root.addView(scrollView)

        val crown = TextView(this).apply {
            text = "👑"
            textSize = 36f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(8))
        }

        val title = TextView(this).apply {
            text = "KEYKEEP"
            textSize = 32f
            setTextColor(GOLD_PRIMARY)
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            letterSpacing = 0.2f
            gravity = Gravity.CENTER
        }

        val subtitle = TextView(this).apply {
            text = "ROYAL VAULT"
            textSize = 12f
            setTextColor(TEXT_MUTED)
            letterSpacing = 0.3f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(40))
        }

        container.addView(crown)
        container.addView(title)
        container.addView(subtitle)

        val contentArea = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        container.addView(contentArea)

        when (currentScreen) {
            "home" -> buildHome(contentArea)
            "create" -> buildCreate(contentArea)
            "open" -> buildOpen(contentArea)
            "view" -> buildView(contentArea)
        }

        setContentView(root)
    }

    private fun buildHome(parent: LinearLayout) {
        val btnCreate = Button(this).apply {
            text = "إنشاء خزنة ملكية"
            setTextColor(Color.BLACK)
            textSize = 16f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            applyGoldGradient(this)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setOnClickListener { 
                mediaItems.clear()
                currentScreen = "create"
                renderRoyalUI() 
            }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.bottomMargin = dp(16) }
        }
        parent.addView(btnCreate)

        val btnOpen = Button(this).apply {
            text = "فتح خزنة"
            setTextColor(GOLD_PRIMARY)
            textSize = 16f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            background = luxuryBg(Color.TRANSPARENT, 12, 1, GOLD_PRIMARY)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setOnClickListener { currentScreen = "open"; renderRoyalUI() }
        }
        parent.addView(btnOpen)
    }

    private fun buildCreate(parent: LinearLayout) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = luxuryBg(CARD_DARK, 16, 1, Color.parseColor("#333A45"))
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }

        card.addView(createLuxuryLabel("الرسالة السرية"))
        val msgInput = createLuxuryInput("اكتب رسالتك هنا...", true)
        card.addView(msgInput)

        card.addView(createLuxuryLabel("مفتاح التشفير"))
        val passInput = createLuxuryInput("كلمة مرور قوية", false)
        passInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        card.addView(passInput)

        card.addView(createLuxuryLabel("تأكيد المفتاح"))
        val passConfirmInput = createLuxuryInput("أعد كتابة كلمة المرور", false)
        passConfirmInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        card.addView(passConfirmInput)

        val btnAddMedia = Button(this).apply {
            text = "➕ إرفاق مستندات/صور (متعدد)"
            setTextColor(GOLD_PRIMARY)
            background = luxuryBg(Color.TRANSPARENT, 8, 1, GOLD_PRIMARY)
            setOnClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true) // Batch selection
                }
                startActivityForResult(intent, 2001)
            }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(10) }
        }
        card.addView(btnAddMedia)

        val mediaStatus = TextView(this).apply {
            text = if (mediaItems.isEmpty()) "لا توجد مرفقات" else "تم إرفاق ${mediaItems.size} عنصر"
            setTextColor(TEXT_MUTED)
            textSize = 12f
            setPadding(0, dp(8), 0, dp(16))
            gravity = Gravity.CENTER
        }
        card.addView(mediaStatus)

        val btnGenerate = Button(this).apply {
            text = "🔒 ختم وتشفير الخزنة"
            setTextColor(Color.BLACK)
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            applyGoldGradient(this)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setOnClickListener {
                val msg = msgInput.text.toString()
                val pass = passInput.text.toString()
                val confirm = passConfirmInput.text.toString()

                if (pass.isEmpty() || pass != confirm) {
                    Toast.makeText(this@MainActivity, "المفاتيح غير متطابقة", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                // Prevent huge HTML files that might crash mobile browsers
                val totalSize = mediaItems.sumOf { it.optString("data").length }
                if (totalSize > 50 * 1024 * 1024) {
                    Toast.makeText(this@MainActivity, "حجم المرفقات كبير جداً (الحد الأقصى ~35MB)", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }

                try {
                    pendingSaveHtml = generateVaultHtml(msg, pass, mediaItems)
                    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "text/html"
                        putExtra(Intent.EXTRA_TITLE, "RoyalVault_${UUID.randomUUID().toString().take(4)}.html")
                    }
                    startActivityForResult(intent, 1001)
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "خطأ في التشفير: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
        card.addView(btnGenerate)

        parent.addView(card)

        val btnBack = Button(this).apply {
            text = "رجوع"
            setTextColor(TEXT_MUTED)
            background = null
            setOnClickListener { currentScreen = "home"; renderRoyalUI() }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(16) }
        }
        parent.addView(btnBack)
    }

    private fun buildOpen(parent: LinearLayout) {
        val btnPick = Button(this).apply {
            text = "📂 استيراد ملف الخزنة"
            setTextColor(Color.BLACK)
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            applyGoldGradient(this)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setOnClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }
                startActivityForResult(intent, 1002)
            }
        }
        parent.addView(btnPick)

        val btnBack = Button(this).apply {
            text = "رجوع"
            setTextColor(TEXT_MUTED)
            background = null
            setOnClickListener { currentScreen = "home"; renderRoyalUI() }
            setPadding(0, dp(20), 0, 0)
        }
        parent.addView(btnBack)
    }

    private fun buildView(parent: LinearLayout) {
        val jsonStr = decryptedPayloadJson ?: run {
            currentScreen = "home"
            renderRoyalUI()
            return
        }

        try {
            val json = JSONObject(jsonStr)
            val msg = json.optString("message", "")
            
            if (msg.isNotEmpty()) {
                val msgBox = TextView(this).apply {
                    text = msg
                    setTextColor(TEXT_WHITE)
                    textSize = 16f
                    setLineSpacing(0f, 1.3f)
                    setPadding(dp(16), dp(16), dp(16), dp(16))
                    background = luxuryBg(CARD_DARK, 12, 1, GOLD_PRIMARY)
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.bottomMargin = dp(20) }
                }
                parent.addView(msgBox)
            }
            
            val media = json.optJSONArray("media")
            if (media != null && media.length() > 0) {
                for (i in 0 until media.length()) {
                    val item = media.getJSONObject(i)
                    val kind = item.optString("kind")
                    val data = item.optString("data")
                    val name = item.optString("name", "File")
                    
                    val card = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        background = luxuryBg(CARD_DARK, 12, 1, Color.parseColor("#333A45"))
                        setPadding(dp(16), dp(16), dp(16), dp(16))
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.bottomMargin = dp(16) }
                    }
                    
                    if (kind == "image") {
                        val imgView = ImageView(this).apply {
                            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(250))
                            scaleType = ImageView.ScaleType.FIT_CENTER
                            try {
                                val b64 = data.substringAfter(",")
                                val bytes = Base64.getDecoder().decode(b64)
                                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                setImageBitmap(bmp)
                            } catch (e: Exception) {}
                        }
                        card.addView(imgView)
                    } else {
                        val icon = TextView(this).apply {
                            text = "📎 $name"
                            setTextColor(TEXT_WHITE)
                            textSize = 14f
                            setPadding(0, dp(8), 0, dp(8))
                        }
                        card.addView(icon)
                    }
                    
                    val btnSave = Button(this).apply {
                        text = "حفظ العنصر"
                        setTextColor(Color.BLACK)
                        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
                        applyGoldGradient(this)
                        setOnClickListener {
                            val mime = if (data.startsWith("data:")) data.substringAfter("data:").substringBefore(";") else "application/octet-stream"
                            pendingSaveFileData = if (data.contains(",")) data.substringAfter(",") else data
                            pendingSaveFileMime = mime
                            
                            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = mime
                                putExtra(Intent.EXTRA_TITLE, name)
                            }
                            startActivityForResult(intent, 3001)
                        }
                    }
                    card.addView(btnSave)
                    parent.addView(card)
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "خطأ في قراءة المحتوى", Toast.LENGTH_SHORT).show()
        }
        
        val btnBack = Button(this).apply {
            text = "إغلاق الخزنة"
            setTextColor(TEXT_MUTED)
            background = null
            setOnClickListener { 
                decryptedPayloadJson = null
                currentScreen = "home"
                renderRoyalUI() 
            }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(16) }
        }
        parent.addView(btnBack)
    }

    private fun createLuxuryLabel(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(GOLD_PRIMARY)
            textSize = 13f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            setPadding(0, dp(12), 0, dp(6))
        }
    }

    private fun createLuxuryInput(hint: String, multiLine: Boolean): EditText {
        return EditText(this).apply {
            this.hint = hint
            setTextColor(TEXT_WHITE)
            setHintTextColor(Color.parseColor("#555555"))
            background = luxuryBg(Color.parseColor("#151A20"), 8, 1, Color.parseColor("#333A45"))
            setPadding(dp(14), dp(14), dp(14), dp(14))
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

        when (requestCode) {
            2001 -> handleMediaSelectionIntent(data)
            1001 -> data?.data?.let { handleSaveHtml(it) }
            1002 -> data?.data?.let { handleOpenVault(it) }
            3001 -> handleNativeFileSave(data?.data)
        }
    }

    private fun handleMediaSelectionIntent(data: Intent?) {
        if (data == null) return
        val uris = mutableListOf<Uri>()
        if (data.clipData != null) {
            val clipData = data.clipData!!
            for (i in 0 until clipData.itemCount) {
                uris.add(clipData.getItemAt(i).uri)
            }
        } else if (data.data != null) {
            uris.add(data.data!!)
        }
        handleMediaSelection(uris)
    }

    private fun handleMediaSelection(uris: List<Uri>) {
        var addedCount = 0
        for (uri in uris) {
            try {
                val mime = contentResolver.getType(uri) ?: "application/octet-stream"
                val name = getFileName(uri)
                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: continue
                
                if (bytes.size > 20 * 1024 * 1024) { // 20MB limit per file
                    Toast.makeText(this, "الملف $name يتجاوز 20MB", Toast.LENGTH_SHORT).show()
                    continue
                }
                
                val b64 = Base64.getEncoder().encodeToString(bytes)
                val kind = if (mime.startsWith("image/")) "image" else "file"
                
                val item = JSONObject().apply {
                    put("kind", kind)
                    put("data", "data:$mime;base64,$b64")
                    put("name", name)
                }
                mediaItems.add(item)
                addedCount++
            } catch (e: OutOfMemoryError) {
                Toast.makeText(this, "الملف كبير جداً، تم تخطيه", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                // Ignore
            }
        }
        if (addedCount > 0) {
            Toast.makeText(this, "تم إضافة $addedCount عنصر بنجاح", Toast.LENGTH_SHORT).show()
        }
        renderRoyalUI()
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
            } finally {
                cursor?.close()
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) result = result?.substring(cut + 1)
        }
        return result ?: "file_${System.currentTimeMillis()}"
    }

    private fun handleSaveHtml(uri: Uri) {
        try {
            contentResolver.openOutputStream(uri)?.use { out ->
                out.write(pendingSaveHtml.toByteArray(Charsets.UTF_8))
            }
            Toast.makeText(this, "👑 تم ختم وحفظ الخزنة بنجاح", Toast.LENGTH_LONG).show()
            currentScreen = "home"
            renderRoyalUI()
        } catch (e: Exception) {
            Toast.makeText(this, "فشل الحفظ: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun handleNativeFileSave(uri: Uri?) {
        if (uri == null) return
        val data = pendingSaveFileData
        if (data == null) {
            Toast.makeText(this, "لا توجد بيانات للحفظ", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val bytes = Base64.getDecoder().decode(data)
            contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            Toast.makeText(this, "✔ تم حفظ الملف بنجاح", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "فشل حفظ الملف: ${e.message}", Toast.LENGTH_SHORT).show()
        } finally {
            pendingSaveFileData = null
            pendingSaveFileMime = null
        }
    }

    private fun handleOpenVault(uri: Uri) {
        try {
            val html = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.readText()
            if (html.isNullOrEmpty()) {
                Toast.makeText(this, "الملف فارغ أو غير قابل للقراءة", Toast.LENGTH_SHORT).show()
                return
            }

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

            promptRoyalPassword { pass ->
                Toast.makeText(this, "جاري فك التشفير...", Toast.LENGTH_SHORT).show()
                Thread {
                    try {
                        val decryptedJson = decryptVault(salt, iv, cipher, hmac, vaultId, iterations, pass)
                        runOnUiThread {
                            if (decryptedJson != null) {
                                decryptedPayloadJson = decryptedJson
                                currentScreen = "view"
                                renderRoyalUI()
                            } else {
                                Toast.makeText(this, "المفتاح خاطئ أو الملف تالف", Toast.LENGTH_LONG).show()
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

    private fun promptRoyalPassword(onPass: (String) -> Unit) {
        val input = EditText(this).apply {
            hint = "أدخل مفتاح الخزنة"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setTextColor(TEXT_WHITE)
            setHintTextColor(Color.GRAY)
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        val dialog = AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("👑 فتح الخزنة الملكية")
            .setView(input)
            .setPositiveButton("فتح") { _, _ -> onPass(input.text.toString()) }
            .setNegativeButton("إلغاء", null)
            .create()
        
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(GOLD_PRIMARY)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.GRAY)
        }
        dialog.show()
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

        return getRoyalHtmlTemplate(saltB64, ivB64, cipherB64, hmacB64, vaultId, iterations)
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

    // ---------------------------------------------------------
    // ROYAL HTML TEMPLATE (With Blob Downloader Fix)
    // ---------------------------------------------------------
    private fun getRoyalHtmlTemplate(salt: String, iv: String, cipher: String, hmac: String, vaultId: String, iterations: Int): String {
        return """<!DOCTYPE html>
<html lang="ar" dir="rtl">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Royal Vault</title>
<style>
  body { background: #0B0C10; color: #FFFFFF; font-family: 'Georgia', serif; display: flex; align-items: center; justify-content: center; min-height: 100vh; margin: 0; padding: 20px; }
  .box { background: #1F2833; border: 1px solid #D4AF37; border-radius: 16px; padding: 32px; width: 100%; max-width: 400px; box-shadow: 0 10px 40px rgba(212,175,55,0.2); }
  h1 { color: #D4AF37; text-align: center; margin-top: 0; font-size: 28px; letter-spacing: 2px; }
  input { width: 100%; padding: 14px; margin-bottom: 16px; border-radius: 8px; border: 1px solid #333A45; background: #151A20; color: #fff; box-sizing: border-box; outline: none; font-family: sans-serif; }
  input:focus { border-color: #D4AF37; }
  button { width: 100%; padding: 14px; border-radius: 8px; border: none; background: linear-gradient(90deg, #F3E5AB, #D4AF37, #B8860B); color: #000; font-weight: bold; cursor: pointer; transition: 0.2s; font-family: sans-serif; margin-bottom: 10px; }
  button:hover { opacity: 0.9; }
  .content { display: none; margin-top: 20px; text-align: left; white-space: pre-wrap; line-height: 1.6; font-family: sans-serif; }
  .media { width: 100%; border-radius: 8px; margin-top: 10px; border: 1px solid #333A45; }
  .err { color: #ff7a7a; font-size: 12px; text-align: center; margin-top: 10px; min-height: 16px; font-family: sans-serif; }
</style>
</head>
<body>
<div class="box">
  <h1>👑 ROYAL VAULT</h1>
  <div id="loginBox">
    <input type="password" id="pass" placeholder="أدخل مفتاح الخزنة" autocomplete="off">
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

function downloadBase64File(dataUrl, filename) {
    try {
        var arr = dataUrl.split(','), mime = arr[0].match(/:(.*?);/)[1],
            bstr = atob(arr[1]), n = bstr.length, u8arr = new Uint8Array(n);
        while(n--){
            u8arr[n] = bstr.charCodeAt(n);
        }
        var blob = new Blob([u8arr], {type:mime});
        var url = window.URL.createObjectURL(blob);
        var a = document.createElement('a');
        a.style.display = 'none';
        a.href = url;
        a.download = filename;
        document.body.appendChild(a);
        a.click();
        window.URL.revokeObjectURL(url);
        document.body.removeChild(a);
    } catch(e) {
        alert("فشل التحميل: " + e.message);
    }
}

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
          
          const btn = document.createElement('button');
          btn.textContent = 'حفظ الصورة';
          btn.onclick = () => downloadBase64File(item.data, item.name);
          document.getElementById('resultBox').appendChild(btn);
        } else {
          const btn = document.createElement('button');
          btn.textContent = 'تحميل ' + item.name;
          btn.onclick = () => downloadBase64File(item.data, item.name);
          document.getElementById('resultBox').appendChild(btn);
        }
      });
    }
  } catch (e) {
    document.getElementById('err').textContent = 'مفتاح خاطئ أو ملف تالف';
  }
}
</script>
</body>
</html>"""
    }
}