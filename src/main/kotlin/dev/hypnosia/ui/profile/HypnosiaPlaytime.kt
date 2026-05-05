package dev.hypnosia.ui.profile

import dev.hypnosia.license.HypnosiaPaths
import net.minecraft.client.MinecraftClient
import java.nio.file.Files
import java.time.LocalDate
import java.time.YearMonth
import java.util.Properties
import kotlin.io.path.exists

object HypnosiaPlaytime {
    private const val FILE_NAME = "playtime.properties"
    private const val SAVE_INTERVAL_TICKS = 20 * 20

    private val lock = Any()
    private val dailySeconds = mutableMapOf<LocalDate, Long>()

    private var loaded = false
    private var launchRecorded = false
    private var totalSeconds = 0L
    private var ticksThisSecond = 0
    private var ticksSinceSave = 0
    private var opensDate: LocalDate = LocalDate.now()
    private var opensToday = 0

    data class Snapshot(
        val totalSeconds: Long,
        val opensToday: Int,
        val streakDays: Int,
        val month: YearMonth,
        val dailySeconds: Map<LocalDate, Long>,
    )

    fun recordLaunch() {
        synchronized(lock) {
            loadIfNeeded()
            if (launchRecorded) {
                return
            }

            val today = LocalDate.now()
            if (opensDate != today) {
                opensDate = today
                opensToday = 0
            }
            opensToday += 1
            launchRecorded = true
            saveLocked()
        }
    }

    fun tick(client: MinecraftClient) {
        synchronized(lock) {
            loadIfNeeded()
            if (client.player == null || client.world == null) {
                return
            }

            ticksThisSecond += 1
            ticksSinceSave += 1
            if (ticksThisSecond >= 20) {
                ticksThisSecond = 0
                val today = LocalDate.now()
                totalSeconds += 1
                dailySeconds[today] = (dailySeconds[today] ?: 0L) + 1L
            }

            if (ticksSinceSave >= SAVE_INTERVAL_TICKS) {
                ticksSinceSave = 0
                saveLocked()
            }
        }
    }

    fun snapshot(): Snapshot {
        synchronized(lock) {
            loadIfNeeded()
            val today = LocalDate.now()
            if (opensDate != today) {
                opensDate = today
                opensToday = 0
            }
            return Snapshot(
                totalSeconds = totalSeconds,
                opensToday = opensToday,
                streakDays = streakDays(today),
                month = YearMonth.from(today),
                dailySeconds = dailySeconds.toMap(),
            )
        }
    }

    private fun streakDays(today: LocalDate): Int {
        var cursor = today
        var streak = 0
        while ((dailySeconds[cursor] ?: 0L) > 0L) {
            streak += 1
            cursor = cursor.minusDays(1)
        }
        return streak
    }

    private fun loadIfNeeded() {
        if (loaded) {
            return
        }

        loaded = true
        val file = HypnosiaPaths.rootFile(FILE_NAME)
        if (!file.exists()) {
            return
        }

        runCatching {
            val properties = Properties()
            Files.newInputStream(file).use(properties::load)
            totalSeconds = properties.getProperty("total.seconds")?.toLongOrNull() ?: 0L
            opensDate = properties.getProperty("opens.date")?.let(LocalDate::parse) ?: LocalDate.now()
            opensToday = properties.getProperty("opens.today")?.toIntOrNull() ?: 0
            properties.stringPropertyNames()
                .asSequence()
                .filter { it.startsWith("day.") && it.endsWith(".seconds") }
                .forEach { key ->
                    val dateText = key.removePrefix("day.").removeSuffix(".seconds")
                    val seconds = properties.getProperty(key)?.toLongOrNull() ?: 0L
                    runCatching { LocalDate.parse(dateText) }.getOrNull()?.let { date ->
                        if (seconds > 0L) {
                            dailySeconds[date] = seconds
                        }
                    }
                }
        }
    }

    private fun saveLocked() {
        val file = HypnosiaPaths.rootFile(FILE_NAME)
        val properties = Properties()
        properties.setProperty("total.seconds", totalSeconds.toString())
        properties.setProperty("opens.date", opensDate.toString())
        properties.setProperty("opens.today", opensToday.toString())
        dailySeconds.entries
            .sortedBy { it.key }
            .takeLast(420)
            .forEach { (date, seconds) ->
                properties.setProperty("day.$date.seconds", seconds.toString())
            }
        Files.newOutputStream(file).use { output ->
            properties.store(output, "Hypnosia local playtime")
        }
    }
}
