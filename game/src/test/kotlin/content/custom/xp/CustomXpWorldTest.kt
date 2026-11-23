package content.custom.xp

import WorldTest
import org.junit.jupiter.api.Test
import world.gregs.voidps.engine.entity.character.player.skill.Skill
import world.gregs.voidps.engine.entity.character.player.skill.exp.XpOrigin
import kotlin.test.assertEquals

internal class CustomXpWorldTest : WorldTest() {

    @Test
    fun `Content discovery registers one default off policy`() {
        assertEquals(1, scripts.filterIsInstance<CustomXpScript>().size)
        val player = createPlayer()

        player.experience.add(Skill.Attack, 1.25, XpOrigin.TRAINING)

        assertEquals(12, player.experience.direct(Skill.Attack))
    }
}
