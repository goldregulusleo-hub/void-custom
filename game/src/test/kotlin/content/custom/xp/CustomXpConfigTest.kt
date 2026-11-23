package content.custom.xp

import org.junit.jupiter.api.Test
import world.gregs.voidps.engine.data.Settings
import world.gregs.voidps.engine.entity.character.player.skill.Skill
import world.gregs.voidps.engine.entity.character.player.skill.exp.XpOrigin
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class CustomXpConfigTest {

    @Test
    fun `Empty configuration is safely disabled`() {
        val (snapshot, warnings) = load(emptyMap())

        assertFalse(snapshot.enabled)
        for (origin in XpOrigin.entries) {
            assertFalse(snapshot.sourceEnabled(origin))
            assertEquals(1.0, snapshot.sourceFactor(origin))
        }
        for (skill in Skill.entries) {
            assertEquals(1.0, snapshot.skillFactor(skill))
        }
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun `All twenty five skills use their canonical keys`() {
        val values = mutableMapOf(
            "custom.xp.enabled" to "true",
            "custom.xp.source.training.enabled" to "true",
        )
        Skill.entries.forEachIndexed { index, skill ->
            values["custom.xp.skill.${skill.name.lowercase()}"] = ((index + 1) / 10.0).toString()
        }

        val (snapshot, warnings) = load(values)

        assertEquals(25, Skill.count)
        Skill.entries.forEachIndexed { index, skill ->
            assertEquals((index + 1) / 10.0, snapshot.skillFactor(skill), skill.name)
        }
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun `All supported sources can be configured independently`() {
        val origins = XpOrigin.entries.filter { it != XpOrigin.UNKNOWN }
        val values = mutableMapOf("custom.xp.enabled" to "true")
        origins.forEachIndexed { index, origin ->
            val source = origin.name.lowercase()
            values["custom.xp.source.$source.enabled"] = "true"
            values["custom.xp.source.$source.factor"] = (index + 1.5).toString()
        }

        val (snapshot, warnings) = load(values)

        origins.forEachIndexed { index, origin ->
            assertTrue(snapshot.sourceEnabled(origin), origin.name)
            assertEquals(index + 1.5, snapshot.sourceFactor(origin), origin.name)
        }
        assertFalse(snapshot.sourceEnabled(XpOrigin.UNKNOWN))
        assertEquals(1.0, snapshot.sourceFactor(XpOrigin.UNKNOWN))
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun `Invalid flags default to off`() {
        val (snapshot, warnings) = load(
            mapOf(
                "custom.xp.enabled" to "TRUE",
                "custom.xp.source.combat.enabled" to "yes",
            ),
        )

        assertFalse(snapshot.enabled)
        assertFalse(snapshot.sourceEnabled(XpOrigin.COMBAT))
        assertEquals(2, warnings.size)
    }

    @Test
    fun `Invalid factors default to identity with one warning each`() {
        val invalid = listOf("NaN", "Infinity", "-Infinity", "0", "-1", "0.009", "100.001", "text")

        for (value in invalid) {
            val (snapshot, warnings) = load(mapOf("custom.xp.source.event.factor" to value))

            assertEquals(1.0, snapshot.sourceFactor(XpOrigin.EVENT), value)
            assertEquals(1, warnings.size, value)
        }
    }

    @Test
    fun `Boundary factors are accepted`() {
        val (snapshot, warnings) = load(
            mapOf(
                "custom.xp.source.quest.factor" to "0.01",
                "custom.xp.skill.slayer" to "100.0",
            ),
        )

        assertEquals(0.01, snapshot.sourceFactor(XpOrigin.QUEST))
        assertEquals(100.0, snapshot.skillFactor(Skill.Slayer))
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun `Unknown custom settings are warned and ignored`() {
        val (snapshot, warnings) = load(
            mapOf(
                "custom.xp.skill.hitpoints" to "2.0",
                "custom.xp.skill.sailing" to "3.0",
                "custom.xp.source.unknown.enabled" to "true",
            ),
        )

        assertEquals(1.0, snapshot.skillFactor(Skill.Constitution))
        assertFalse(snapshot.sourceEnabled(XpOrigin.UNKNOWN))
        assertEquals(3, warnings.size)
        assertTrue(warnings.all { it.startsWith("Unknown custom XP setting") })
    }

    @Test
    fun `Later operational settings override packaged defaults`() {
        val settings = Settings()
        settings.load(CustomXpConfigTest::class.java.getResourceAsStream("/game.properties")!!)
        settings.load(
            mapOf(
                "custom.xp.enabled" to "true",
                "custom.xp.source.training.enabled" to "true",
                "custom.xp.source.training.factor" to "2.0",
                "custom.xp.skill.woodcutting" to "1.5",
            ),
        )
        val warnings = mutableListOf<String>()

        val snapshot = CustomXpConfig(settings, warnings::add).load()

        assertTrue(snapshot.enabled)
        assertTrue(snapshot.sourceEnabled(XpOrigin.TRAINING))
        assertEquals(2.0, snapshot.sourceFactor(XpOrigin.TRAINING))
        assertEquals(1.5, snapshot.skillFactor(Skill.Woodcutting))
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun `Snapshot does not hot reload when settings change`() {
        val settings = Settings()
        settings.load(
            mapOf(
                "custom.xp.enabled" to "true",
                "custom.xp.source.training.enabled" to "true",
                "custom.xp.source.training.factor" to "2.0",
            ),
        )
        val snapshot = CustomXpConfig(settings) {}.load()

        settings.load(mapOf("custom.xp.source.training.factor" to "3.0"))

        assertEquals(2.0, snapshot.sourceFactor(XpOrigin.TRAINING))
        assertEquals(3.0, CustomXpConfig(settings) {}.load().sourceFactor(XpOrigin.TRAINING))
    }

    private fun load(values: Map<String, String>): Pair<CustomXpSnapshot, List<String>> {
        val settings = Settings()
        settings.load(values)
        val warnings = mutableListOf<String>()
        val snapshot = CustomXpConfig(settings, warnings::add).load()
        return Pair(snapshot, warnings)
    }
}
