package content.custom.xp

import world.gregs.voidps.engine.entity.character.player.Player
import world.gregs.voidps.engine.entity.character.player.skill.Skill
import world.gregs.voidps.engine.entity.character.player.skill.exp.ExperienceModifier
import world.gregs.voidps.engine.entity.character.player.skill.exp.XpOrigin

class CustomXpPolicy(private val snapshot: CustomXpSnapshot) : ExperienceModifier {
    override fun modify(player: Player, skill: Skill, origin: XpOrigin, base: Double): Double {
        val factor = snapshot.factor(skill, origin)
        if (factor == CustomXpConfig.IDENTITY_FACTOR) {
            return base
        }
        val modified = base * factor
        return if (modified.isFinite() && modified > 0.0) modified else base
    }
}
