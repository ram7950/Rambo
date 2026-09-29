package com.rambo.carvoice

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(40, 40, 40, 40)
            setBackgroundColor(Color.BLACK)
        }

        val title = TextView(this).apply {
            text = "RAMBO"
            textSize = 42f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        val status = TextView(this).apply {
            text = "Offline Car Voice Assistant"
            textSize = 18f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        }

        val button = Button(this).apply {
            text = "🎙️  TALK TO RAMBO"
            textSize = 20f
            setOnClickListener {
                status.text = "RAMBO is listening..."
            }
        }

        layout.addView(title)
        layout.addView(status)

        layout.addView(
            button,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                120
            ).apply {
                topMargin = 60
            }
        )

        setContentView(layout)
    }
}
