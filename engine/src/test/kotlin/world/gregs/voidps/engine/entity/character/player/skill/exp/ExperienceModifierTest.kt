package world.gregs.voidps.engine.entity.character.player.skill.exp

import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import world.gregs.voidps.engine.Script
import world.gregs.voidps.engine.entity.character.player.Player
import world.gregs.voidps.engine.entity.character.player.skill.Skill
import world.gregs.voidps.engine.entity.character.player.skill.Skills

internal class ExperienceModifierTest {
    private lateinit var experience: Experience
    private lateinit var player: Player

    @BeforeEach
    fun setup() {
        ExperienceModifiers.close()
        player = mockk(relaxed = true)
        experience = Experience()
        experience.player = player
    }

    @AfterEach
    fun teardown() {
        ExperienceModifiers.close()
    }

    @Test
    fun `Existing and explicit unknown overloads remain identity`() {
        var called = false
        ExperienceModifiers.register("unexpected") { _, _, _, base ->
            called = true
            base * 2.0
        }

        experience.add(Skill.Attack, 10.0)
        experience.add(Skill.Attack, 10.0, XpOrigin.UNKNOWN)

        assertEquals(20.0, experience.get(Skill.Attack))
        assertFalse(called)
    }

    @Test
    fun `Every explicit origin is identity without a modifier`() {
        XpOrigin.entries.forEach { origin ->
            experience.add(Skill.Attack, 1.25, origin)
        }

        assertEquals(8.4, experience.get(Skill.Attack))
    }

    @Test
    fun `Explicit origin supplies award context to modifier`() {
        var receivedPlayer: Player? = null
        var receivedSkill: Skill? = null
        var receivedOrigin: XpOrigin? = null
        var receivedBase = 0.0
        ExperienceModifiers.register("double") { awardPlayer, skill, origin, base ->
            receivedPlayer = awardPlayer
            receivedSkill = skill
            receivedOrigin = origin
            receivedBase = base
            base * 2.0
        }

        experience.add(Skill.Strength, 12.5, XpOrigin.COMBAT)

        assertEquals(player, receivedPlayer)
        assertEquals(Skill.Strength, receivedSkill)
        assertEquals(XpOrigin.COMBAT, receivedOrigin)
        assertEquals(12.5, receivedBase)
        assertEquals(25.0, experience.get(Skill.Strength))
    }

    @Test
    fun `Modifiers run in deterministic registration order`() {
        ExperienceModifiers.register("add") { _, _, _, base -> base + 1.0 }
        ExperienceModifiers.register("multiply") { _, _, _, base -> base * 2.0 }

        experience.add(Skill.Attack, 10.0, XpOrigin.TRAINING)

        assertEquals(22.0, experience.get(Skill.Attack))
    }

    @Test
    fun `Duplicate modifier ids are rejected and can be registered after clear`() {
        ExperienceModifiers.register("custom") { _, _, _, base -> base }

        assertThrows<IllegalStateException> {
            ExperienceModifiers.register("custom") { _, _, _, base -> base }
        }

        ExperienceModifiers.close()
        ExperienceModifiers.register("custom") { _, _, _, base -> base }
    }

    @Test
    fun `Modifier can be unregistered`() {
        ExperienceModifiers.register("double") { _, _, _, base -> base * 2.0 }

        assertTrue(ExperienceModifiers.unregister("double"))
        assertFalse(ExperienceModifiers.unregister("double"))
        experience.add(Skill.Attack, 10.0, XpOrigin.TRAINING)

        assertEquals(10.0, experience.get(Skill.Attack))
    }

    @Test
    fun `Recoverable modifier failure falls back to original award`() {
        ExperienceModifiers.register("double") { _, _, _, base -> base * 2.0 }
        ExperienceModifiers.register("failure") { _, _, _, _ -> throw IllegalStateException("failure") }

        experience.add(Skill.Attack, 10.0, XpOrigin.QUEST)

        assertEquals(10.0, experience.get(Skill.Attack))
    }

    @Test
    fun `Invalid modifier results fall back to original award`() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, 0.0, -1.0).forEachIndexed { index, invalid ->
            ExperienceModifiers.close()
            ExperienceModifiers.register("invalid-$index") { _, _, _, _ -> invalid }

            experience.add(Skill.Attack, 10.0, XpOrigin.EVENT)
        }

        assertEquals(40.0, experience.get(Skill.Attack))
    }

    @Test
    fun `Severe modifier errors are not hidden`() {
        ExperienceModifiers.register("error") { _, _, _, _ -> throw AssertionError("severe") }

        assertThrows<AssertionError> {
            experience.add(Skill.Attack, 10.0, XpOrigin.LAMP)
        }
        assertEquals(0.0, experience.get(Skill.Attack))
    }

    @Test
    fun `Blocked awards preserve original formula and skip modifiers`() {
        var called = false
        ExperienceModifiers.register("double") { _, _, _, base ->
            called = true
            base * 2.0
        }
        mockkObject(Skills)
        try {
            experience.addBlock(Skill.Attack)
            experience.add(Skill.Attack, 10.0, XpOrigin.ASSISTANCE)

            verify(exactly = 1) { Skills.blocked(player, Skill.Attack, 100) }
            assertFalse(called)
            assertEquals(0.0, experience.get(Skill.Attack))
        } finally {
            unmockkObject(Skills)
        }
    }

    @Test
    fun `One award emits one experience event`() {
        mockkObject(Skills)
        try {
            experience.add(Skill.Attack, 10.0, XpOrigin.COMBAT)

            verify(exactly = 1) { Skills.exp(player, Skill.Attack, 0, 100) }
        } finally {
            unmockkObject(Skills)
        }
    }

    @Test
    fun `Script lifecycle clears registered modifiers`() {
        ExperienceModifiers.register("double") { _, _, _, base -> base * 2.0 }

        Script.clear()
        experience.add(Skill.Attack, 10.0, XpOrigin.TRAINING)

        assertEquals(10.0, experience.get(Skill.Attack))
    }

    @Test
    fun `Original and explicit JVM descriptors coexist`() {
        val addDescriptors = Experience::class.java.declaredMethods.filter { it.name == "add" }.map { it.parameterTypes.toList() }
        val extensionClass = Class.forName("world.gregs.voidps.engine.entity.character.player.skill.exp.ExperienceKt")
        val expDescriptors = extensionClass.declaredMethods.filter { it.name == "exp" }.map { it.parameterTypes.toList() }

        assertTrue(addDescriptors.contains(listOf(Skill::class.java, Double::class.javaPrimitiveType)))
        assertTrue(addDescriptors.contains(listOf(Skill::class.java, Double::class.javaPrimitiveType, XpOrigin::class.java)))
        assertTrue(expDescriptors.contains(listOf(Player::class.java, Skill::class.java, Double::class.javaPrimitiveType)))
        assertTrue(expDescriptors.contains(listOf(Player::class.java, Skill::class.java, Double::class.javaPrimitiveType, XpOrigin::class.java)))
    }
}
