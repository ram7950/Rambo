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
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private var speechRecognizer: SpeechRecognizer? = null

    private lateinit var avatar: TextView
    private lateinit var status: TextView
    private lateinit var result: TextView
    private lateinit var button: Button

    private val micRequestCode = 1001

    private var avatarAnimator: ObjectAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        buildUi()
        startIdleAnimation()

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {

            requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO),
                micRequestCode
            )

        } else {
            setupSpeechRecognizer()

            // RAMBO launch interaction
            window.decorView.postDelayed({
                speakGreeting()
            }, 900)
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

    private fun speakGreeting() {

        status.text = "READY"
        result.text = "Hi, I am RAMBO"

        // Testing version:
        // Greeting is shown first, then listening starts automatically.
        window.decorView.postDelayed({
            startListening()
        }, 1200)
    }

    private fun setupSpeechRecognizer() {

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status.text = "UNAVAILABLE"
            result.text = "Speech recognition unavailable"
            button.isEnabled = false
            return
        }

        speechRecognizer =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

                SpeechRecognizer.createOnDeviceSpeechRecognizer(this)

            } else {

                status.text = "ANDROID 12+ REQUIRED"
                button.isEnabled = false
                return
            }

        speechRecognizer?.setRecognitionListener(
            object : RecognitionListener {

                override fun onReadyForSpeech(params: Bundle?) {

                    status.text = "LISTENING"
                    result.text = "I'm listening..."

                    setListeningAnimation()

                    button.isEnabled = false
                }

                override fun onBeginningOfSpeech() {

                    status.text = "HEARING YOU"
                }

                override fun onRmsChanged(rmsdB: Float) {}

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {

                    status.text = "THINKING"
                    setThinkingAnimation()
                }

                override fun onError(error: Int) {

                    status.text = "IDLE"
                    result.text = "Ready"

                    button.isEnabled = true

                    resetAvatar()
                }

                override fun onResults(results: Bundle?) {

                    val matches =
                        results?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )

                    result.text =
                        if (!matches.isNullOrEmpty()) {
                            matches[0]
                        } else {
                            "Nothing recognized"
                        }

                    status.text = "IDLE"
                    button.isEnabled = true

                    resetAvatar()
                }

                override fun onPartialResults(
                    partialResults: Bundle?
                ) {

                    val matches =
                        partialResults?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )

                    if (!matches.isNullOrEmpty()) {
                        result.text = matches[0]
                    }
                }

                override fun onEvent(
                    eventType: Int,
                    params: Bundle?
                ) {}
            }
        )
    }

    private fun startListening() {

        if (speechRecognizer == null) {

            status.text = "VOICE UNAVAILABLE"
            result.text = "Speech recognizer unavailable"

            return
        }

        status.text = "STARTING"
        result.text = "Listening..."

        setListeningAnimation()

        val intent =
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {

                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )

                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE,
                    "hi-IN"
                )

                putExtra(
                    RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                    true
                )

                putExtra(
                    RecognizerIntent.EXTRA_PREFER_OFFLINE,
                    true
                )
            }

        speechRecognizer?.startListening(intent)
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

            setupSpeechRecognizer()

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

        speechRecognizer?.destroy()
        speechRecognizer = null

        super.onDestroy()
    }
}
