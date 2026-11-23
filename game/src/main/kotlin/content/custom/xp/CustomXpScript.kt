package content.custom.xp

import world.gregs.voidps.engine.Script
import world.gregs.voidps.engine.entity.character.player.skill.exp.ExperienceModifiers

class CustomXpScript : Script {
    init {
        ExperienceModifiers.register("custom_xp_policy", CustomXpPolicy(CustomXpConfig().load()))
    }
}
