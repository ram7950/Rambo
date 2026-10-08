package com.rambo.carvoice

import android.Manifest
import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.rambo.carvoice.command.Command
import com.rambo.carvoice.command.CommandEngine
import com.rambo.carvoice.voice.VoskSpeechEngine
import java.util.Locale


class MainActivity : Activity() {

    private val commandEngine = CommandEngine()

    private lateinit var voskSpeechEngine: VoskSpeechEngine
    private var textToSpeech: TextToSpeech? = null
    private var ttsReady = false
    private var greetingPending = false

    private lateinit var avatar: TextView
    private lateinit var status: TextView
    private lateinit var result: TextView
    private lateinit var button: Button

    private val micRequestCode = 1001

    private var avatarAnimator: ObjectAnimator? = null

    // RAMBO background / steering interaction state
    private var bootGreetingMode = false
    private var steeringListeningRequested = false
    private var ramboInitialized = false
    private var overlayPermissionRequested = false

    // Floating RAMBO overlay
    private var overlayView: TextView? = null
    private var overlayWindowManager: WindowManager? = null

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        if (intent?.action == "com.rambo.carvoice.STEERING_MIC") {
            setIntent(intent)
            steeringListeningRequested = true
            bootGreetingMode = false
            beginSteeringListening()
            return
        }

        if (intent?.action == "com.rambo.carvoice.BOOT") {
            setIntent(intent)
            bootGreetingMode = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        bootGreetingMode = intent?.action == "com.rambo.carvoice.BOOT"

        val prefs = getSharedPreferences("rambo_state", MODE_PRIVATE)
        ramboInitialized = prefs.getBoolean("initialized", false)

        steeringListeningRequested =
            intent?.action == "com.rambo.carvoice.STEERING_MIC" ||
            (!bootGreetingMode && ramboInitialized)

        buildUi()
        startIdleAnimation()
        initializeTextToSpeech()

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {

            requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO),
                micRequestCode
            )

        } else {
            setupVoskSpeechEngine()

            if (steeringListeningRequested) {
                // Subsequent RAMBO launches are treated as steering-button
                // interactions. Never greet again.
                window.decorView.postDelayed({
                    beginSteeringListening()
                }, 700)

            } else {
                // First normal/boot launch: greeting only.
                window.decorView.postDelayed({
                    speakGreeting()
                }, 900)
            }
        }
    }

    private fun buildUi() {

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(40, 40, 40, 40)
            setBackgroundColor(Color.rgb(5, 8, 12))
        }

        val title = TextView(this).apply {
            text = "RAMBO"
            textSize = 42f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        val subtitle = TextView(this).apply {
            text = "OFFLINE CAR ASSISTANT"
            textSize = 14f
            setTextColor(Color.rgb(150, 160, 170))
            gravity = Gravity.CENTER
            letterSpacing = 0.15f
        }

        avatar = TextView(this).apply {
            text = "◉"
            textSize = 110f
            setTextColor(Color.rgb(0, 220, 255))
            gravity = Gravity.CENTER
            setShadowLayer(35f, 0f, 0f, Color.rgb(0, 220, 255))
        }

        val avatarBox = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            addView(
                avatar,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    260
                )
            )
        }

        status = TextView(this).apply {
            text = "IDLE"
            textSize = 20f
            setTextColor(Color.rgb(0, 220, 255))
            gravity = Gravity.CENTER
        }

        result = TextView(this).apply {
            text = "Hi, I am RAMBO"
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(20, 25, 20, 25)
        }

        button = Button(this).apply {
            text = "🎙  TALK TO RAMBO"
            textSize = 18f

            setOnClickListener {
                startListening()
            }
        }

        root.addView(title)
        root.addView(subtitle)

        root.addView(
            avatarBox,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                280
            )
        )

        root.addView(status)

        root.addView(
            result,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                120
            )
        )

        root.addView(
            button,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                110
            ).apply {
                topMargin = 20
            }
        )

        setContentView(root)
    }

    private fun startIdleAnimation() {

        avatarAnimator?.cancel()

        avatarAnimator = ObjectAnimator.ofFloat(
            avatar,
            View.SCALE_X,
            0.92f,
            1.08f
        ).apply {
            duration = 1300
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }

        ObjectAnimator.ofFloat(
            avatar,
            View.SCALE_Y,
            0.92f,
            1.08f
        ).apply {
            duration = 1300
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun setListeningAnimation() {

        avatarAnimator?.cancel()

        avatarAnimator = ObjectAnimator.ofFloat(
            avatar,
            View.SCALE_X,
            0.85f,
            1.18f
        ).apply {
            duration = 450
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }

        ObjectAnimator.ofFloat(
            avatar,
            View.SCALE_Y,
            0.85f,
            1.18f
        ).apply {
            duration = 450
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }

        avatar.setTextColor(Color.rgb(0, 255, 140))
        avatar.setShadowLayer(45f, 0f, 0f, Color.rgb(0, 255, 140))
    }

    private fun setThinkingAnimation() {

        avatarAnimator?.cancel()

        avatarAnimator = ObjectAnimator.ofFloat(
            avatar,
            View.ROTATION,
            -8f,
            8f
        ).apply {
            duration = 700
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            start()
        }

        avatar.setTextColor(Color.rgb(255, 190, 50))
        avatar.setShadowLayer(45f, 0f, 0f, Color.rgb(255, 190, 50))
    }

    private fun resetAvatar() {

        avatarAnimator?.cancel()

        avatar.rotation = 0f
        avatar.scaleX = 1f
        avatar.scaleY = 1f

        avatar.setTextColor(Color.rgb(0, 220, 255))
        avatar.setShadowLayer(35f, 0f, 0f, Color.rgb(0, 220, 255))

        startIdleAnimation()
    }

    private fun initializeTextToSpeech() {

        textToSpeech = TextToSpeech(this) { resultCode ->

            if (resultCode == TextToSpeech.SUCCESS) {

                val result = textToSpeech?.setLanguage(Locale.US)

                ttsReady =
                    result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED

                textToSpeech?.setSpeechRate(0.92f)
                textToSpeech?.setPitch(1.0f)

                textToSpeech?.setOnUtteranceProgressListener(
                    object : UtteranceProgressListener() {

                        override fun onStart(utteranceId: String?) {

                            runOnUiThread {
                                status.text = "SPEAKING"
                                this@MainActivity.result.text = "Hi, I am RAMBO"
                            }
                        }

                        override fun onDone(utteranceId: String?) {

                            runOnUiThread {

                                if (utteranceId == "rambo_greeting") {

                                    status.text = "READY"

                                    // Greeting is initialization only.
                                    // Never automatically open the microphone.
                                    getSharedPreferences(
                                        "rambo_state",
                                        MODE_PRIVATE
                                    ).edit()
                                        .putBoolean("initialized", true)
                                        .apply()

                                    ramboInitialized = true
                                    steeringListeningRequested = false
                                }
                            }
                        }

                        override fun onError(utteranceId: String?) {

                            runOnUiThread {

                                status.text = "READY"
                            }
                        }
                    }
                )

                if (greetingPending) {
                    greetingPending = false
                    speakGreeting()
                }

            } else {

                ttsReady = false
                status.text = "TTS UNAVAILABLE"

                // Do not automatically open microphone.
                // Listening starts only from an explicit user interaction.
            }
        }
    }

    private fun beginSteeringListening() {

        steeringListeningRequested = true
        bootGreetingMode = false

        if (android.os.Build.VERSION.SDK_INT >=
            android.os.Build.VERSION_CODES.M) {

            if (!android.provider.Settings.canDrawOverlays(this)) {

                overlayPermissionRequested = true

                try {
                    val intent = Intent(
                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                } catch (_: Exception) {
                    overlayPermissionRequested = false
                }

                return
            }
        }

        overlayPermissionRequested = false
        showRamboOverlay("LISTENING")

        if (::voskSpeechEngine.isInitialized &&
            voskSpeechEngine.isReady()) {

            startListening()

            window.decorView.postDelayed({
                moveTaskToBack(true)
            }, 150)
        }
    }

    override fun onResume() {
        super.onResume()

        if (overlayPermissionRequested &&
            steeringListeningRequested) {

            if (android.os.Build.VERSION.SDK_INT <
                android.os.Build.VERSION_CODES.M ||
                android.provider.Settings.canDrawOverlays(this)) {

                overlayPermissionRequested = false

                window.decorView.postDelayed({
                    beginSteeringListening()
                }, 250)
            }
        }
    }

    private fun speakGreeting() {

        status.text = "READY"
        result.text = "Hi, I am RAMBO"

        if (!ttsReady) {

            greetingPending = true
            status.text = "STARTING VOICE"

            return
        }

        textToSpeech?.stop()

        textToSpeech?.speak(
            "Hi, I am RAMBO",
            TextToSpeech.QUEUE_FLUSH,
            null,
            "rambo_greeting"
        )
    }

    private fun setupVoskSpeechEngine() {

        if (!::voskSpeechEngine.isInitialized) {
            voskSpeechEngine = VoskSpeechEngine(
                this,
                onResult = { spokenText ->
                    runOnUiThread {
                        speechSessionActive = false

                        if (spokenText.isNotBlank()) {
                            result.text = "HEARD: $spokenText"
                            status.text = "THINKING"
                            setThinkingAnimation()

                            val parsedCommands = commandEngine.parse(spokenText)

                            result.text = parsedCommands.joinToString("\n") {
                                "PARSED: ${it.command} -> ${it.entity ?: "-"}"
                            }

                            executeParsedCommands(parsedCommands)
                        }

                        button.isEnabled = true
                    }
                },
                onStatus = { message ->
                    runOnUiThread {
                        when {
                            message == "LOADING STT" -> {
                                status.text = "LOADING STT"
                                result.text = "Preparing offline voice engine..."
                                button.isEnabled = false
                            }

                            message == "STT READY" -> {
                                status.text = "READY"
                                result.text = "RAMBO is ready"
                                button.isEnabled = true

                                if (steeringListeningRequested &&
                                    !speechSessionActive) {

                                    beginSteeringListening()
                                }
                            }

                            message == "LISTENING" -> {
                                status.text = "LISTENING"
                                result.text = "I'm listening..."
                                setListeningAnimation()
                                button.isEnabled = false
                            }

                            message.startsWith("STT ERROR") -> {
                                status.text = "STT ERROR"
                                result.text = message
                                button.isEnabled = true
                                speechSessionActive = false
                                resetAvatar()
                            }

                            message.startsWith("MIC ERROR") -> {
                                status.text = "MIC ERROR"
                                result.text = message
                                button.isEnabled = true
                                speechSessionActive = false
                                resetAvatar()
                            }
                        }
                    }
                }
            )
        }
    }

    private fun executeParsedCommands(commands: List<com.rambo.carvoice.command.ParsedCommand>) {
        for (parsed in commands) {
            when (parsed.command) {
                Command.OPEN_APP -> {
                    when (parsed.entity?.lowercase()) {
                        "chrome" -> openApp("com.android.chrome", "Chrome")
                        "youtube" -> openApp("com.google.android.youtube", "YouTube")
                        else -> speakResponse("I don't know that app yet.")
                    }
                }

                Command.GO_HOME -> {
                    val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_HOME)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(homeIntent)
                    speakResponse("Going home.")
                }

                Command.GO_BACK -> {
                    onBackPressed()
                    speakResponse("Going back.")
                }

                Command.GET_TIME -> {
                    val time = java.text.SimpleDateFormat(
                        "h:mm a",
                        Locale.getDefault()
                    ).format(java.util.Date())

                    speakResponse("The time is $time.")
                }

                Command.GREETING -> {
                    speakResponse("Hello. Main RAMBO hoon.")
                }

                Command.UNKNOWN -> {
                    speakResponse("I heard you, but I don't know that command yet.")
                }
            }
        }
    }

    private fun handleCommand(command: String) {

        val text = command.trim().lowercase(Locale.getDefault())

        when {

            // GREETING
            text.contains("hello") ||
            text.contains("hi") ||
            text.contains("hey") -> {
                speakResponse("Hello. Main RAMBO hoon.")
            }

            // HOW ARE YOU
            text.contains("how are you") ||
            text.contains("how r you") -> {
                speakResponse("I am doing great. Tell me what you need.")
            }

            // NAME
            text.contains("your name") ||
            text.contains("what is your name") -> {
                speakResponse("My name is RAMBO.")
            }

            // OPEN CHROME
            text.contains("open chrome") ||
            text.contains("launch chrome") ||
            (text.contains("chrome") && text.contains("open")) -> {
                openApp(
                    "com.android.chrome",
                    "Chrome"
                )
            }

            // OPEN YOUTUBE
            text.contains("open youtube") ||
            text.contains("launch youtube") ||
            (text.contains("youtube") && text.contains("open")) -> {
                openApp(
                    "com.google.android.youtube",
                    "YouTube"
                )
            }

            // BACK
            text == "go back" ||
            text.contains("go back") ||
            text.contains("back") -> {
                onBackPressed()
                speakResponse("Going back.")
            }

            // HOME
            text == "go home" ||
            text.contains("go to home") ||
            text.contains("home screen") -> {
                val homeIntent =
                    Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_HOME)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                startActivity(homeIntent)
                speakResponse("Going home.")
            }

            // TIME
            text.contains("what time") ||
            text.contains("current time") ||
            text.contains("tell me the time") -> {
                val time = java.text.SimpleDateFormat(
                    "h:mm a",
                    Locale.getDefault()
                ).format(java.util.Date())

                speakResponse("The time is $time.")
            }

            // UNKNOWN
            else -> {
                speakResponse(
                    "I heard you, but I don't know that command yet."
                )
            }
        }
    }

    private fun openApp(
        packageName: String,
        appName: String
    ) {
        val launchIntent =
            packageManager.getLaunchIntentForPackage(packageName)

        if (launchIntent != null) {
            startActivity(launchIntent)
            speakResponse("$appName is opening.")
        } else {
            speakResponse("$appName is not available.")
        }
    }

    private fun showRamboOverlay(state: String) {

        if (android.os.Build.VERSION.SDK_INT >=
            android.os.Build.VERSION_CODES.M) {

            if (!android.provider.Settings.canDrawOverlays(this)) {
                return
            }
        }

        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        overlayWindowManager = wm

        if (overlayView == null) {

            val view = TextView(this).apply {
                textSize = 14f
                setTextColor(Color.WHITE)
                setPadding(30, 18, 30, 18)
                gravity = Gravity.CENTER
                setBackgroundColor(Color.argb(225, 8, 12, 18))
                elevation = 20f
                alpha = 0f
            }

            overlayView = view

            val type =
                if (android.os.Build.VERSION.SDK_INT >=
                    android.os.Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    WindowManager.LayoutParams.TYPE_PHONE
                }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                android.graphics.PixelFormat.TRANSLUCENT
            )

            params.gravity =
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            params.y = 45

            try {
                wm.addView(view, params)
            } catch (_: Exception) {
                overlayView = null
                return
            }
        }

        overlayView?.text = "◉  RAMBO  •  $state"

        overlayView?.animate()
            ?.alpha(1f)
            ?.setDuration(180)
            ?.start()
    }

    private fun hideRamboOverlay() {

        val view = overlayView ?: return

        try {
            overlayWindowManager?.removeView(view)
        } catch (_: Exception) {
        }

        overlayView = null
    }

    private fun speakResponse(response: String) {

        runOnUiThread {
            status.text = "SPEAKING"
            result.text = response
        }

        if (!ttsReady || textToSpeech == null) {
            runOnUiThread {
                status.text = "IDLE"
                resetAvatar()
            }
            return
        }

        textToSpeech?.speak(
            response,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "rambo_response"
        )
    }

    private var speechSessionActive = false


private fun startListening() {

        if (speechSessionActive) {
            return
        }

        if (!voskSpeechEngine.isReady()) {
            status.text = "STT NOT READY"
            result.text = "Offline voice engine is still loading..."
            return
        }

        speechSessionActive = true

        status.text = "STARTING"
        result.text = "Listening..."

        setListeningAnimation()

        voskSpeechEngine.startListening()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {

        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (
            requestCode == micRequestCode &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {

            setupVoskSpeechEngine()

            window.decorView.postDelayed({
                speakGreeting()
            }, 700)

        } else {

            status.text = "MICROPHONE REQUIRED"
            result.text = "Please allow microphone permission"

            button.isEnabled = false
        }
    }

    override fun onDestroy() {

        avatarAnimator?.cancel()
        hideRamboOverlay()

        if (::voskSpeechEngine.isInitialized) {
            voskSpeechEngine.release()
        }

        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null

        super.onDestroy()
    }
}
