package com.rambo.carvoice.command

data class ParsedCommand(
    val command: Command,
    val entity: String? = null,
    val confidence: Float = 0f,
    val rawText: String = ""
)
