package com.example.navassist

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

/**
 * Bottom-sheet chat with a cloud AI. Opens over whatever app you are in, supports typing and
 * voice (speech-to-text in, text-to-speech out).
 */
class AiChatActivity : Activity(), TextToSpeech.OnInitListener {

    private val history = ArrayList<ChatMsg>()
    private val ui = Handler(Looper.getMainLooper())
    private lateinit var col: LinearLayout
    private lateinit var scrollV: ScrollView
    private lateinit var input: EditText
    private lateinit var micBtn: ImageView
    private lateinit var sendBtn: ImageView
    private lateinit var speakBtn: ImageView
    private lateinit var empty: LinearLayout
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var speakReplies = true
    private var recognizer: SpeechRecognizer? = null
    private var listening = false
    private var voiceTurn = false
    private var busy = false

    private fun dp(v: Int) = Ui.dp(this, v)
    private val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT
    private val MATCH = LinearLayout.LayoutParams.MATCH_PARENT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        val cfg = AiConfig.load(this)

        val root = FrameLayout(this)
        root.setOnClickListener { finish() }

        val r = dp(26).toFloat()
        val sheet = LinearLayout(this)
        sheet.orientation = LinearLayout.VERTICAL
        sheet.background = GradientDrawable().apply {
            setColor(0xF2111827.toInt())
            cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            setStroke(dp(1), Ui.BORDER)
        }
        sheet.setPadding(dp(14), dp(10), dp(14), dp(12))
        sheet.isClickable = true

        // grab handle
        val handle = View(this)
        handle.background = Ui.shape(0xFF374151.toInt(), dp(3).toFloat())
        val hl = LinearLayout.LayoutParams(dp(38), dp(4))
        hl.gravity = Gravity.CENTER_HORIZONTAL
        hl.bottomMargin = dp(8)
        sheet.addView(handle, hl)

        // header
        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        val logo = ImageView(this)
        logo.setImageResource(R.drawable.ic_sparkle)
        logo.setPadding(dp(8), dp(8), dp(8), dp(8))
        logo.background = Ui.shape(Ui.VIOLET, dp(12).toFloat())
        header.addView(logo, LinearLayout.LayoutParams(dp(36), dp(36)))
        val titles = LinearLayout(this)
        titles.orientation = LinearLayout.VERTICAL
        titles.setPadding(dp(10), 0, 0, 0)
        titles.addView(Ui.text(this, "AI Assistant", 16f, Ui.TEXT, weight = 600))
        titles.addView(Ui.text(this, if (cfg.ready) "${cfg.provider.label} · ${cfg.model}" else "Not set up yet", 11.5f, Ui.MUTED))
        header.addView(titles, LinearLayout.LayoutParams(0, WRAP, 1f))
        speakBtn = iconButton(R.drawable.ic_volume_up) {
            speakReplies = !speakReplies
            updateSpeakIcon()
            if (!speakReplies) tts?.stop()
        }
        header.addView(speakBtn, LinearLayout.LayoutParams(dp(40), dp(40)))
        header.addView(iconButton(R.drawable.ic_settings) {
            startActivity(Intent(this, MainActivity::class.java).putExtra("open_ai", true))
            finish()
        }, LinearLayout.LayoutParams(dp(40), dp(40)))
        header.addView(iconButton(R.drawable.ic_close) { finish() }, LinearLayout.LayoutParams(dp(40), dp(40)))
        sheet.addView(header)

        // conversation
        scrollV = ScrollView(this)
        scrollV.isVerticalScrollBarEnabled = false
        col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(0, dp(4), 0, dp(8))
        scrollV.addView(col)
        sheet.addView(scrollV, LinearLayout.LayoutParams(MATCH, 0, 1f))

        empty = buildEmptyState(cfg)
        col.addView(empty)

        // input bar
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.background = Ui.shape(Ui.BG, dp(26).toFloat(), Ui.BORDER, dp(1))
        bar.setPadding(dp(16), dp(4), dp(6), dp(4))
        input = EditText(this)
        input.hint = "Message, or tap the mic"
        input.setHintTextColor(Ui.MUTED)
        input.setTextColor(Ui.TEXT)
        input.textSize = 15f
        input.typeface = Ui.font(this, 400)
        input.background = null
        input.maxLines = 4
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        input.imeOptions = EditorInfo.IME_ACTION_SEND
        input.setPadding(0, dp(10), dp(8), dp(10))
        input.setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_SEND) send(input.text.toString(), false)
            true
        }
        bar.addView(input, LinearLayout.LayoutParams(0, WRAP, 1f))
        micBtn = circleButton(R.drawable.ic_mic, Ui.VIOLET) { toggleListening() }
        sendBtn = circleButton(R.drawable.ic_send, Ui.CYAN) { send(input.text.toString(), false) }
        sendBtn.visibility = View.GONE
        bar.addView(micBtn, LinearLayout.LayoutParams(dp(42), dp(42)))
        bar.addView(sendBtn, LinearLayout.LayoutParams(dp(42), dp(42)))
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val has = !s.isNullOrBlank()
                sendBtn.visibility = if (has) View.VISIBLE else View.GONE
                micBtn.visibility = if (has) View.GONE else View.VISIBLE
            }
        })
        val barLp = LinearLayout.LayoutParams(MATCH, WRAP)
        barLp.topMargin = dp(6)
        sheet.addView(bar, barLp)

        val sh = (resources.displayMetrics.heightPixels * 0.82f).toInt()
        root.addView(sheet, FrameLayout.LayoutParams(MATCH, sh, Gravity.BOTTOM))
        setContentView(root)
    }

    // ------------------------------------------------------------------ UI pieces

    private fun iconButton(icon: Int, onClick: () -> Unit): ImageView {
        val b = ImageView(this)
        b.setImageResource(icon)
        b.setPadding(dp(10), dp(10), dp(10), dp(10))
        b.alpha = 0.75f
        b.setOnClickListener { onClick() }
        return b
    }

    private fun circleButton(icon: Int, color: Int, onClick: () -> Unit): ImageView {
        val b = ImageView(this)
        b.setImageResource(icon)
        b.setPadding(dp(11), dp(11), dp(11), dp(11))
        b.background = Ui.oval(color)
        b.setOnClickListener { onClick() }
        return b
    }

    private fun updateSpeakIcon() {
        speakBtn.setImageResource(if (speakReplies) R.drawable.ic_volume_up else R.drawable.ic_volume_off)
    }

    private fun buildEmptyState(cfg: AiConfig): LinearLayout {
        val e = LinearLayout(this)
        e.orientation = LinearLayout.VERTICAL
        e.gravity = Gravity.CENTER_HORIZONTAL
        e.setPadding(dp(8), dp(28), dp(8), dp(8))
        val hello = Ui.text(this, if (cfg.ready) "How can I help?" else "Let’s set up your AI", 20f, Ui.TEXT, weight = 600)
        hello.gravity = Gravity.CENTER
        e.addView(hello)
        val sub = Ui.text(
            this,
            if (cfg.ready) "Type a message or tap the mic and just talk."
            else "Add an API key from OpenAI, Anthropic, Google Gemini or any OpenAI-compatible service. Your key is encrypted on this phone.",
            13.5f, Ui.MUTED
        )
        sub.gravity = Gravity.CENTER
        sub.setPadding(dp(8), dp(8), dp(8), dp(16))
        e.addView(sub)
        if (cfg.ready) {
            for (s in listOf("What can you help me with?", "Tell me a fun fact", "Explain how Wi-Fi works")) {
                e.addView(chip(s) { send(s, false) })
            }
        } else {
            e.addView(chip("Open AI settings") {
                startActivity(Intent(this, MainActivity::class.java).putExtra("open_ai", true))
                finish()
            })
        }
        return e
    }

    private fun chip(text: String, onClick: () -> Unit): View {
        val t = Ui.text(this, text, 14f, Ui.TEXT, weight = 500)
        t.gravity = Gravity.CENTER
        t.setPadding(dp(16), dp(11), dp(16), dp(11))
        t.background = Ui.ripple(Ui.shape(Ui.BG, dp(18).toFloat(), Ui.BORDER, dp(1)), dp(18).toFloat())
        t.setOnClickListener { onClick() }
        val lp = LinearLayout.LayoutParams(WRAP, WRAP)
        lp.topMargin = dp(8)
        t.layoutParams = lp
        return t
    }

    private fun bubble(text: String, user: Boolean): TextView {
        val t = Ui.text(this, text, 15f, Ui.TEXT)
        t.setTextIsSelectable(true)
        t.setLineSpacing(0f, 1.15f)
        t.setPadding(dp(14), dp(10), dp(14), dp(10))
        t.maxWidth = (resources.displayMetrics.widthPixels * 0.78f).toInt()
        t.background = if (user) Ui.shape(Ui.VIOLET, dp(18).toFloat())
        else Ui.shape(Ui.BG, dp(18).toFloat(), Ui.BORDER, dp(1))
        val lp = LinearLayout.LayoutParams(WRAP, WRAP)
        lp.gravity = if (user) Gravity.END else Gravity.START
        lp.topMargin = dp(8)
        col.addView(t, lp)
        scrollV.post { scrollV.fullScroll(View.FOCUS_DOWN) }
        return t
    }

    // ------------------------------------------------------------------ chatting

    private fun send(raw: String, viaVoice: Boolean) {
        val text = raw.trim()
        if (text.isEmpty() || busy) return
        val cfg = AiConfig.load(this)
        empty.visibility = View.GONE
        if (!cfg.ready) {
            bubble(text, true)
            bubble("I need an API key first. Tap the ⚙ icon above to choose a provider and paste your key.", false)
            input.setText("")
            return
        }
        voiceTurn = viaVoice
        busy = true
        history.add(ChatMsg("user", text))
        bubble(text, true)
        input.setText("")
        val thinking = bubble("Thinking…", false)
        val msgs = history.takeLast(20).toMutableList()
        while (msgs.isNotEmpty() && msgs[0].role != "user") msgs.removeAt(0)
        Thread {
            val res = AiClient.chat(cfg, msgs)
            ui.post {
                busy = false
                if (isFinishing || isDestroyed) return@post
                thinking.text = res.text
                if (res.ok) {
                    history.add(ChatMsg("assistant", res.text))
                    if (speakReplies && voiceTurn) speak(res.text)
                } else {
                    history.removeAt(history.size - 1)
                }
                scrollV.post { scrollV.fullScroll(View.FOCUS_DOWN) }
            }
        }.start()
    }

    // ------------------------------------------------------------------ voice out

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val loc = Locale.forLanguageTag(Prefs.voiceLang(this))
            val r = tts?.setLanguage(loc)
            ttsReady = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
            if (!ttsReady) {
                tts?.setLanguage(Locale.getDefault())
                ttsReady = true
            }
        }
    }

    private fun speak(text: String) {
        if (ttsReady) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "reply")
    }

    // ------------------------------------------------------------------ voice in

    private fun toggleListening() {
        if (listening) stopListening() else startListening()
    }

    private fun startListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 7)
            return
        }
        val lang = Prefs.voiceLang(this)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            try {
                startActivityForResult(intent, 8)
            } catch (e: Exception) {
                Toast.makeText(this, "Voice input is not available on this phone", Toast.LENGTH_LONG).show()
            }
            return
        }
        tts?.stop()
        val rec = recognizer ?: SpeechRecognizer.createSpeechRecognizer(this).also {
            it.setRecognitionListener(listener)
            recognizer = it
        }
        rec.startListening(intent)
        setListening(true)
    }

    private fun stopListening() {
        recognizer?.stopListening()
        setListening(false)
    }

    private fun setListening(on: Boolean) {
        listening = on
        micBtn.background = Ui.oval(if (on) Ui.RED else Ui.VIOLET)
        input.hint = if (on) "Listening…" else "Message, or tap the mic"
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onPartialResults(partialResults: Bundle?) {
            val t = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (!t.isNullOrBlank()) input.hint = t
        }

        override fun onResults(results: Bundle?) {
            setListening(false)
            val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (!t.isNullOrBlank()) send(t, true)
        }

        override fun onError(error: Int) {
            setListening(false)
            val msg = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Didn’t catch that. Tap the mic and try again."
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Voice recognition needs an internet connection."
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is needed."
                else -> "Voice input error ($error)."
            }
            Toast.makeText(this@AiChatActivity, msg, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (code == 7 && results.isNotEmpty() && results[0] == PackageManager.PERMISSION_GRANTED) startListening()
    }

    @Deprecated("Used only when no in-app speech service exists")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 8 && resultCode == RESULT_OK) {
            val t = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!t.isNullOrBlank()) send(t, true)
        }
    }

    override fun onDestroy() {
        recognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
