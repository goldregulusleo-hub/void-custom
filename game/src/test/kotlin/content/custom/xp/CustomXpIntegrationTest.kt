package content.custom.xp

import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import world.gregs.voidps.engine.data.Settings
import world.gregs.voidps.engine.entity.character.player.Player
import world.gregs.voidps.engine.entity.character.player.skill.Skill
import world.gregs.voidps.engine.entity.character.player.skill.Skills
import world.gregs.voidps.engine.entity.character.player.skill.exp.Experience
import world.gregs.voidps.engine.entity.character.player.skill.exp.ExperienceModifiers
import world.gregs.voidps.engine.entity.character.player.skill.exp.XpOrigin
import kotlin.test.assertEquals

internal class CustomXpIntegrationTest {
    private lateinit var experience: Experience
    private lateinit var player: Player

    @BeforeEach
    fun setup() {
        Settings.clear()
        ExperienceModifiers.close()
        player = mockk(relaxed = true)
        experience = Experience()
        experience.player = player
    }

    @AfterEach
    fun teardown() {
        ExperienceModifiers.close()
        Settings.clear()
    }

    @Test
    fun `Custom factor composes with bonus and world rate before original truncation`() {
        register(
            "custom.xp.enabled" to "true",
            "custom.xp.source.training.enabled" to "true",
            "custom.xp.source.training.factor" to "1.5",
            "custom.xp.skill.attack" to "1.25",
            "world.experienceRate" to "2.0",
        )
        experience.multiplier = 2.7

        experience.add(Skill.Attack, 1.23, XpOrigin.TRAINING)

        assertEquals(124, experience.direct(Skill.Attack))
        assertEquals(12.4, experience.get(Skill.Attack))
    }

    @Test
    fun `Old and unknown awards stay identity while custom policy is on`() {
        register(
            "custom.xp.enabled" to "true",
            "custom.xp.source.training.enabled" to "true",
            "custom.xp.source.training.factor" to "2.0",
            "custom.xp.skill.attack" to "3.0",
        )

        experience.add(Skill.Attack, 10.0)
        experience.add(Skill.Attack, 10.0, XpOrigin.UNKNOWN)

        assertEquals(20.0, experience.get(Skill.Attack))
    }

    @Test
    fun `Custom award emits one experience event`() {
        register(
            "custom.xp.enabled" to "true",
            "custom.xp.source.combat.enabled" to "true",
            "custom.xp.source.combat.factor" to "2.0",
        )
        mockkObject(Skills)
        try {
            experience.add(Skill.Attack, 10.0, XpOrigin.COMBAT)

            verify(exactly = 1) { Skills.exp(player, Skill.Attack, 0, 200) }
        } finally {
            unmockkObject(Skills)
        }
    }

    @Test
    fun `Blocked assistance award skips custom policy`() {
        register(
            "custom.xp.enabled" to "true",
            "custom.xp.source.assistance.enabled" to "true",
            "custom.xp.source.assistance.factor" to "2.0",
            "custom.xp.skill.magic" to "3.0",
        )
        mockkObject(Skills)
        try {
            experience.addBlock(Skill.Magic)
            experience.add(Skill.Magic, 10.0, XpOrigin.ASSISTANCE)

            verify(exactly = 1) { Skills.blocked(player, Skill.Magic, 100) }
            assertEquals(0.0, experience.get(Skill.Magic))
        } finally {
            unmockkObject(Skills)
        }
    }

    @Test
    fun `Policy only affects new explicit awards`() {
        experience.set(Skill.Woodcutting, 100.0)
        register(
            "custom.xp.enabled" to "true",
            "custom.xp.source.training.enabled" to "true",
            "custom.xp.source.training.factor" to "2.0",
        )

        experience.add(Skill.Woodcutting, 10.0, XpOrigin.TRAINING)

        assertEquals(120.0, experience.get(Skill.Woodcutting))
    }

    private fun register(vararg values: Pair<String, String>) {
        Settings.load(mapOf(*values))
        val snapshot = CustomXpConfig(Settings) {}.load()
        ExperienceModifiers.register("custom_xp_policy", CustomXpPolicy(snapshot))
    }
}
