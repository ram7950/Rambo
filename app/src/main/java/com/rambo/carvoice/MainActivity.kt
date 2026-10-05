package com.rambo.carvoice

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.TextView

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val text = TextView(this)

        text.text = "RAMBO\n\nSTARTED SUCCESSFULLY"
        text.textSize = 24f
        text.setTextColor(Color.WHITE)
        text.setBackgroundColor(Color.BLACK)
        text.gravity = Gravity.CENTER
        text.setPadding(30, 30, 30, 30)

        setContentView(text)
    }
}
