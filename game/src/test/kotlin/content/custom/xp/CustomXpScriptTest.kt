package content.custom.xp

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import world.gregs.voidps.engine.Script
import world.gregs.voidps.engine.data.Settings
import world.gregs.voidps.engine.entity.character.player.Player
import world.gregs.voidps.engine.entity.character.player.skill.Skill
import world.gregs.voidps.engine.entity.character.player.skill.exp.ExperienceModifiers
import world.gregs.voidps.engine.entity.character.player.skill.exp.XpOrigin
import kotlin.test.assertEquals

internal class CustomXpScriptTest {

    @BeforeEach
    fun setup() {
        Settings.clear()
        ExperienceModifiers.close()
    }

    @AfterEach
    fun teardown() {
        Script.clear()
        Settings.clear()
    }

    @Test
    fun `Script registers configured policy once and lifecycle allows reload`() {
        Settings.load(
            mapOf(
                "custom.xp.enabled" to "true",
                "custom.xp.source.training.enabled" to "true",
                "custom.xp.source.training.factor" to "2.0",
                "custom.xp.skill.woodcutting" to "1.5",
            ),
        )

        CustomXpScript()
        assertEquals(30.0, award())
        assertThrows<IllegalStateException> { CustomXpScript() }

        Script.clear()
        CustomXpScript()
        assertEquals(30.0, award())
    }

    private fun award(): Double {
        val player = Player()
        player.experience.player = player
        player.experience.add(Skill.Woodcutting, 10.0, XpOrigin.TRAINING)
        return player.experience.get(Skill.Woodcutting)
    }
}
