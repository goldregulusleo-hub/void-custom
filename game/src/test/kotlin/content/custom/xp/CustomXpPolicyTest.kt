package content.custom.xp

import io.mockk.mockk
import org.junit.jupiter.api.Test
import world.gregs.voidps.engine.data.Settings
import world.gregs.voidps.engine.entity.character.player.Player
import world.gregs.voidps.engine.entity.character.player.skill.Skill
import world.gregs.voidps.engine.entity.character.player.skill.exp.XpOrigin
import kotlin.test.assertEquals

internal class CustomXpPolicyTest {
    private val player: Player = mockk(relaxed = true)

    @Test
    fun `Master off is identity`() {
        val policy = policy(
            "custom.xp.source.training.enabled" to "true",
            "custom.xp.source.training.factor" to "2.0",
            "custom.xp.skill.attack" to "3.0",
        )

        assertEquals(10.0, policy.modify(player, Skill.Attack, XpOrigin.TRAINING, 10.0))
    }

    @Test
    fun `Disabled source is identity`() {
        val policy = policy(
            "custom.xp.enabled" to "true",
            "custom.xp.source.training.factor" to "2.0",
            "custom.xp.skill.attack" to "3.0",
        )

        assertEquals(10.0, policy.modify(player, Skill.Attack, XpOrigin.TRAINING, 10.0))
    }

    @Test
    fun `Unknown is always identity`() {
        val policy = policy(
            "custom.xp.enabled" to "true",
            "custom.xp.skill.attack" to "3.0",
        )

        assertEquals(10.0, policy.modify(player, Skill.Attack, XpOrigin.UNKNOWN, 10.0))
    }

    @Test
    fun `Every explicit source applies its independent factor`() {
        val origins = XpOrigin.entries.filter { it != XpOrigin.UNKNOWN }
        val values = mutableListOf("custom.xp.enabled" to "true")
        origins.forEachIndexed { index, origin ->
            val source = origin.name.lowercase()
            values.add("custom.xp.source.$source.enabled" to "true")
            values.add("custom.xp.source.$source.factor" to (index + 1.0).toString())
        }
        val policy = policy(*values.toTypedArray())

        origins.forEachIndexed { index, origin ->
            assertEquals(10.0 * (index + 1.0), policy.modify(player, Skill.Attack, origin, 10.0), origin.name)
        }
    }

    @Test
    fun `Source and skill factors compose below and above identity`() {
        val lower = policy(
            "custom.xp.enabled" to "true",
            "custom.xp.source.training.enabled" to "true",
            "custom.xp.source.training.factor" to "0.5",
            "custom.xp.skill.attack" to "0.5",
        )
        val higher = policy(
            "custom.xp.enabled" to "true",
            "custom.xp.source.combat.enabled" to "true",
            "custom.xp.source.combat.factor" to "2.0",
            "custom.xp.skill.slayer" to "1.5",
        )

        assertEquals(2.5, lower.modify(player, Skill.Attack, XpOrigin.TRAINING, 10.0))
        assertEquals(30.0, higher.modify(player, Skill.Slayer, XpOrigin.COMBAT, 10.0))
    }

    @Test
    fun `Non finite composed award falls back to base`() {
        val policy = policy(
            "custom.xp.enabled" to "true",
            "custom.xp.source.event.enabled" to "true",
            "custom.xp.source.event.factor" to "100.0",
            "custom.xp.skill.mining" to "100.0",
        )

        assertEquals(Double.MAX_VALUE, policy.modify(player, Skill.Mining, XpOrigin.EVENT, Double.MAX_VALUE))
    }

    private fun policy(vararg values: Pair<String, String>): CustomXpPolicy {
        val settings = Settings()
        settings.load(mapOf(*values))
        return CustomXpPolicy(CustomXpConfig(settings) {}.load())
    }
}
