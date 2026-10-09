package com.rambo.carvoice.command

import java.util.Locale

class CommandEngine {

    fun parse(input: String): List<ParsedCommand> {
        val raw = input.trim()
        if (raw.isBlank()) {
            return listOf(ParsedCommand(Command.UNKNOWN, confidence = 0f, rawText = raw))
        }

        val normalized = normalize(raw)
        return splitCommands(normalized).map { parseSingle(it, raw) }
    }

    fun debugParse(input: String): String =
        parse(input).mapIndexed { index, item ->
            "${index + 1}. ${item.command} -> ${item.entity ?: "-"} (${item.confidence})"
        }.joinToString("\n")

    private fun normalize(input: String): String =
        input.lowercase(Locale.ROOT)
            .replace(Regex("[,;.!?]+"), " and ")
            .replace(
                Regex("\\b(please|could you|can you|would you|hey rambo|rambo)\\b"),
                " "
            )
            .replace(Regex("\\s+"), " ")
            .trim()

    private val commandStart = (
        "open\\b|launch\\b|start\\b|go\\s+(?:to\\s+)?(?:home|back)\\b|" +
        "home\\s+screen\\b|home\\b|back\\b|" +
        "what\\s+(?:is\\s+)?(?:the\\s+)?time\\b|current\\s+time\\b|" +
        "tell\\s+me\\s+(?:the\\s+)?time\\b|time\\b|" +
        "what\\s+(?:is\\s+)?(?:the\\s+)?date\\b|today'?s\\s+date\\b|" +
        "tell\\s+me\\s+(?:today'?s\\s+)?date\\b|date\\b|" +
        "set\\s+(?:the\\s+)?volume\\b|(?:the\\s+)?volume\\b|" +
        "(?:increase|decrease|raise|lower)\\s+(?:the\\s+)?volume\\b|" +
        "mute\\b|silence\\b|unmute\\b|sound\\s+on\\b|" +
        "hello\\b|hi\\b|hey\\b|how\\s+are\\s+you\\b|" +
        "what\\s+is\\s+your\\s+name\\b|who\\s+are\\s+you\\b"
    )

    private fun splitCommands(input: String): List<String> {
        val starts = Regex(
            "\\b(?:open|launch|start|go\\s+(?:to\\s+)?(?:home|back)|" +
            "home(?:\\s+screen)?|back|what\\s+(?:is\\s+)?(?:the\\s+)?time|" +
            "current\\s+time|tell\\s+me\\s+(?:the\\s+)?time|time|" +
            "what\\s+(?:is\\s+)?(?:the\\s+)?date|today'?s\\s+date|" +
            "tell\\s+me\\s+(?:today'?s\\s+)?date|date|" +
            "set\\s+(?:the\\s+)?volume|volume|" +
            "(?:increase|decrease|raise|lower)\\s+(?:the\\s+)?volume|" +
            "mute|silence|unmute|sound\\s+on|hello|hi|hey|" +
            "how\\s+are\\s+you|what\\s+is\\s+your\\s+name|who\\s+are\\s+you)\\b"
        )
        val separator = Regex("\\s+(?:and\\s+then|then|after\\s+that|also|and)\\s+")
        val result = mutableListOf<String>()
        var remaining = input.trim()

        while (remaining.isNotEmpty()) {
            val match = separator.findAll(remaining).firstOrNull { candidate ->
                val nextStart = candidate.range.last + 1
                starts.containsMatchIn(remaining.substring(nextStart))
            }

            if (match == null) {
                result += remaining.trim()
                break
            }

            val left = remaining.substring(0, match.range.first).trim()
            if (left.isNotEmpty()) result += left

            remaining = remaining.substring(match.range.last + 1).trim()
        }

        return result.ifEmpty { listOf(input) }
    }

    private fun parsed(
        command: Command,
        rawText: String,
        entity: String? = null,
        confidence: Float = 0.95f
    ) = ParsedCommand(command, entity, confidence, rawText)

    private fun parseSingle(text: String, rawText: String): ParsedCommand {
        val value = text.trim()

        val percent = Regex(
            "(?:set\\s+)?(?:the\\s+)?volume\\s+(?:to\\s+)?(\\d{1,3})\\s*(?:percent|per cent|%)"
        ).find(value) ?: Regex(
            "(\\d{1,3})\\s*(?:percent|per cent|%)\\s+volume"
        ).find(value)

        if (percent != null) {
            val amount = percent.groupValues[1].toIntOrNull()
            if (amount != null && amount in 0..100) {
                return parsed(Command.VOLUME_SET, rawText, amount.toString(), 0.99f)
            }
        }

        if (Regex("\\b(mute|silence)\\b").containsMatchIn(value) ||
            value.contains("volume off")) {
            return parsed(Command.VOLUME_MUTE, rawText)
        }

        if (Regex("\\b(unmute|restore sound|sound on)\\b").containsMatchIn(value)) {
            return parsed(Command.VOLUME_UNMUTE, rawText)
        }

        if (Regex("\\b(volume|sound)\\b").containsMatchIn(value) &&
            Regex("\\b(up|increase|raise|louder|higher)\\b").containsMatchIn(value)) {
            return parsed(Command.VOLUME_UP, rawText)
        }

        if (Regex("\\b(volume|sound)\\b").containsMatchIn(value) &&
            Regex("\\b(down|decrease|lower|quieter|reduce)\\b").containsMatchIn(value)) {
            return parsed(Command.VOLUME_DOWN, rawText)
        }

        if (value in listOf("louder", "turn it up", "turn the volume up", "volume up", "increase volume")) {
            return parsed(Command.VOLUME_UP, rawText)
        }

        if (value in listOf("quieter", "turn it down", "turn the volume down", "volume down", "decrease volume")) {
            return parsed(Command.VOLUME_DOWN, rawText)
        }

        if (value.contains("maximum volume") || value == "max volume" || value == "full volume") {
            return parsed(Command.VOLUME_SET, rawText, "100")
        }

        if (value.contains("minimum volume") || value == "min volume") {
            return parsed(Command.VOLUME_SET, rawText, "0")
        }

        if (Regex("\\b(open|launch|start)\\s+(?:google\\s+)?chrome\\b").containsMatchIn(value) ||
            value == "chrome" || value == "google chrome") {
            return parsed(Command.OPEN_APP, rawText, "Chrome", 0.98f)
        }

        if (Regex("\\b(open|launch|start)\\s+(?:the\\s+)?(?:you\\s*tube|youtube)\\b").containsMatchIn(value) ||
            value in listOf("youtube", "you tube", "you too")) {
            return parsed(Command.OPEN_APP, rawText, "YouTube", 0.98f)
        }

        if (Regex("\\b(open|launch|start)\\s+settings\\b").containsMatchIn(value) ||
            value == "settings") {
            return parsed(Command.OPEN_APP, rawText, "Settings", 0.98f)
        }

        if (value == "go home" || value == "home" ||
            value.contains("go to home") || value.contains("home screen")) {
            return parsed(Command.GO_HOME, rawText, confidence = 0.98f)
        }

        if (value == "go back" || value == "back") {
            return parsed(Command.GO_BACK, rawText, confidence = 0.98f)
        }

        if (Regex("\\b(what\\s+(?:is\\s+)?(?:the\\s+)?time|current\\s+time|tell\\s+me\\s+(?:the\\s+)?time|time)\\b")
                .containsMatchIn(value)) {
            return parsed(Command.GET_TIME, rawText, confidence = 0.96f)
        }

        if (Regex("\\b(what\\s+(?:is\\s+)?(?:the\\s+)?date|today'?s\\s+date|tell\\s+me\\s+(?:today'?s\\s+)?date|date)\\b")
                .containsMatchIn(value)) {
            return parsed(Command.GET_DATE, rawText, confidence = 0.96f)
        }

        if (value.contains("how are you")) {
            return parsed(Command.HOW_ARE_YOU, rawText)
        }

        if (value.contains("your name") || value.contains("who are you")) {
            return parsed(Command.GET_NAME, rawText)
        }

        if (Regex("^(hello|hi|hey|hello there|hey there)$").matches(value)) {
            return parsed(Command.GREETING, rawText)
        }

        return parsed(Command.UNKNOWN, rawText, confidence = 0.10f)
    }
}
