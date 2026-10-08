package com.rambo.carvoice.command

import java.util.Locale

class CommandEngine {

    fun parse(input: String): List<ParsedCommand> {

        val raw = input.trim()

        if (raw.isBlank()) {
            return listOf(
                ParsedCommand(
                    command = Command.UNKNOWN,
                    confidence = 0f,
                    rawText = raw
                )
            )
        }

        val normalized = normalize(raw)
        val parts = splitCommands(normalized)

        return parts.map { parseSingle(it, raw) }
    }

    fun debugParse(input: String): String {

        val parsed = parse(input)

        return parsed.mapIndexed { index, item ->
            "${index + 1}. ${item.command} -> ${item.entity ?: "-"} (${item.confidence})"
        }.joinToString("\n")
    }

    private fun normalize(input: String): String {

        return input
            .lowercase(Locale.getDefault())
            .replace(Regex("[,;]+"), " ")
            .replace(
                Regex("\\b(please|could you|can you|would you|hey rambo|rambo)\\b"),
                " "
            )
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun splitCommands(input: String): List<String> {

        val result = input
            .split(
                Regex(
                    "\\s+(?:and then|then|after that|also|and)\\s+"
                )
            )
            .map { it.trim() }
            .filter { it.isNotBlank() }

        return if (result.isEmpty()) {
            listOf(input)
        } else {
            result
        }
    }

    private fun parseSingle(
        text: String,
        rawText: String
    ): ParsedCommand {

        val value = text.trim()

        if (
            value.contains("open chrome") ||
            value.contains("launch chrome") ||
            value.contains("start chrome") ||
            value == "chrome"
        ) {
            return ParsedCommand(
                command = Command.OPEN_APP,
                entity = "Chrome",
                confidence = 0.98f,
                rawText = rawText
            )
        }

        if (
            value.contains("open youtube") ||
            value.contains("launch youtube") ||
            value.contains("start youtube") ||
            value == "youtube"
        ) {
            return ParsedCommand(
                command = Command.OPEN_APP,
                entity = "YouTube",
                confidence = 0.98f,
                rawText = rawText
            )
        }

        if (
            value == "go home" ||
            value.contains("go to home") ||
            value.contains("home screen") ||
            value == "home"
        ) {
            return ParsedCommand(
                command = Command.GO_HOME,
                confidence = 0.98f,
                rawText = rawText
            )
        }

        if (
            value == "go back" ||
            value.contains("go back") ||
            value == "back"
        ) {
            return ParsedCommand(
                command = Command.GO_BACK,
                confidence = 0.98f,
                rawText = rawText
            )
        }

        if (
            value.contains("what time") ||
            value.contains("current time") ||
            value.contains("tell me the time")
        ) {
            return ParsedCommand(
                command = Command.GET_TIME,
                confidence = 0.96f,
                rawText = rawText
            )
        }

        if (
            value.contains("hello") ||
            value.contains("hi") ||
            value.contains("hey")
        ) {
            return ParsedCommand(
                command = Command.GREETING,
                confidence = 0.95f,
                rawText = rawText
            )
        }

        return ParsedCommand(
            command = Command.UNKNOWN,
            confidence = 0.10f,
            rawText = rawText
        )
    }
}
