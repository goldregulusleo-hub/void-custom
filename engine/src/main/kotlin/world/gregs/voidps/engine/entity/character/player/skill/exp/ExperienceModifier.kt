package world.gregs.voidps.engine.entity.character.player.skill.exp

import world.gregs.voidps.engine.entity.character.player.Player
import world.gregs.voidps.engine.entity.character.player.skill.Skill

fun interface ExperienceModifier {
    fun modify(player: Player, skill: Skill, origin: XpOrigin, base: Double): Double
}

object ExperienceModifiers : AutoCloseable {
    private val registered = linkedMapOf<String, ExperienceModifier>()

    @Volatile
    private var modifiers: List<ExperienceModifier> = emptyList()

    @Synchronized
    fun register(id: String, modifier: ExperienceModifier) {
        require(id.isNotBlank()) { "Experience modifier id cannot be blank." }
        check(id !in registered) { "Experience modifier '$id' is already registered." }
        registered[id] = modifier
        modifiers = registered.values.toList()
    }

    @Synchronized
    fun unregister(id: String): Boolean {
        val removed = registered.remove(id) != null
        if (removed) {
            modifiers = registered.values.toList()
        }
        return removed
    }

    fun modify(player: Player, skill: Skill, origin: XpOrigin, base: Double): Double {
        var result = base
        for (modifier in modifiers) {
            val modified = try {
                modifier.modify(player, skill, origin, result)
            } catch (_: Exception) {
                return base
            }
            if (!modified.isFinite() || modified <= 0.0) {
                return base
            }
            result = modified
        }
        return result
    }

    @Synchronized
    override fun close() {
        registered.clear()
        modifiers = emptyList()
    }
}
