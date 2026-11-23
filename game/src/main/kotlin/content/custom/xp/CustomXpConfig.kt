package content.custom.xp

import com.github.michaelbull.logging.InlineLogger
import world.gregs.voidps.engine.data.Settings
import world.gregs.voidps.engine.entity.character.player.skill.Skill
import world.gregs.voidps.engine.entity.character.player.skill.exp.XpOrigin

class CustomXpConfig(
    private val settings: Settings = Settings,
    private val warning: (String) -> Unit = { message -> logger.warn { message } },
) {
    fun load(): CustomXpSnapshot {
        val allowed = mutableSetOf(MASTER_KEY)
        val sourceEnabled = BooleanArray(XpOrigin.entries.size)
        val sourceFactors = DoubleArray(XpOrigin.entries.size) { IDENTITY_FACTOR }
        val skillFactors = DoubleArray(Skill.count) { IDENTITY_FACTOR }

        for (origin in configurableOrigins) {
            val source = origin.name.lowercase()
            val enabledKey = "custom.xp.source.$source.enabled"
            val factorKey = "custom.xp.source.$source.factor"
            allowed.add(enabledKey)
            allowed.add(factorKey)
            sourceEnabled[origin.ordinal] = boolean(enabledKey)
            sourceFactors[origin.ordinal] = factor(factorKey)
        }
        for (skill in Skill.entries) {
            val key = "custom.xp.skill.${skill.name.lowercase()}"
            allowed.add(key)
            skillFactors[skill.ordinal] = factor(key)
        }
        warnUnknown(allowed)
        return CustomXpSnapshot(boolean(MASTER_KEY), sourceEnabled, sourceFactors, skillFactors)
    }

    private fun boolean(key: String): Boolean {
        val raw = settings.getOrNull(key) ?: return false
        val parsed = raw.toBooleanStrictOrNull()
        if (parsed == null) {
            warning("Invalid custom XP flag '$key=$raw'; defaulting to false.")
        }
        return parsed ?: false
    }

    private fun factor(key: String): Double {
        val raw = settings.getOrNull(key) ?: return IDENTITY_FACTOR
        val parsed = raw.toDoubleOrNull()
        if (parsed == null || !parsed.isFinite() || parsed !in MIN_FACTOR..MAX_FACTOR) {
            warning("Invalid custom XP factor '$key=$raw'; defaulting to $IDENTITY_FACTOR.")
            return IDENTITY_FACTOR
        }
        return parsed
    }

    private fun warnUnknown(allowed: Set<String>) {
        settings.names()
            .asSequence()
            .filter { it.startsWith("custom.xp.") && it !in allowed }
            .sorted()
            .forEach { key -> warning("Unknown custom XP setting '$key'; ignoring it.") }
    }

    companion object {
        const val MIN_FACTOR = 0.01
        const val MAX_FACTOR = 100.0
        const val IDENTITY_FACTOR = 1.0
        private const val MASTER_KEY = "custom.xp.enabled"
        private val configurableOrigins = XpOrigin.entries.filter { it != XpOrigin.UNKNOWN }
        private val logger = InlineLogger("CustomXpConfig")
    }
}

class CustomXpSnapshot internal constructor(
    val enabled: Boolean,
    sourceEnabled: BooleanArray,
    sourceFactors: DoubleArray,
    skillFactors: DoubleArray,
) {
    private val sourceEnabled = sourceEnabled.copyOf()
    private val sourceFactors = sourceFactors.copyOf()
    private val skillFactors = skillFactors.copyOf()

    fun sourceEnabled(origin: XpOrigin): Boolean = sourceEnabled[origin.ordinal]

    fun sourceFactor(origin: XpOrigin): Double = sourceFactors[origin.ordinal]

    fun skillFactor(skill: Skill): Double = skillFactors[skill.ordinal]

    fun factor(skill: Skill, origin: XpOrigin): Double {
        if (!enabled || origin == XpOrigin.UNKNOWN || !sourceEnabled(origin)) {
            return CustomXpConfig.IDENTITY_FACTOR
        }
        val factor = sourceFactor(origin) * skillFactor(skill)
        return if (factor.isFinite() && factor > 0.0) factor else CustomXpConfig.IDENTITY_FACTOR
    }
}
