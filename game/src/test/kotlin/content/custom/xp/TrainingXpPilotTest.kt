package content.custom.xp

import WorldTest
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import npcOption
import objectOption
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import world.gregs.voidps.engine.data.Settings
import world.gregs.voidps.engine.entity.character.player.Player
import world.gregs.voidps.engine.entity.character.player.skill.Skill
import world.gregs.voidps.engine.entity.character.player.skill.Skills
import world.gregs.voidps.engine.entity.character.player.skill.exp.ExperienceModifiers
import world.gregs.voidps.engine.entity.character.player.skill.exp.XpOrigin
import world.gregs.voidps.engine.entity.character.player.skill.exp.exp
import world.gregs.voidps.engine.entity.obj.GameObjects
import world.gregs.voidps.engine.entity.obj.ObjectLayer
import world.gregs.voidps.engine.inv.add
import world.gregs.voidps.engine.inv.inventory
import world.gregs.voidps.type.Tile
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class TrainingXpPilotTest : WorldTest() {

    @BeforeEach
    fun resetPolicy() {
        configure()
    }

    @AfterEach
    fun restorePolicy() {
        configure()
    }

    @Test
    fun `Woodcutting remains official when custom XP is off`() {
        val (player, tile) = chopTree()

        assertEquals(250, player.experience.direct(Skill.Woodcutting))
        assertEquals(1, player.inventory.count("logs"))
        assertEquals("tree_stump", GameObjects.getLayer(tile, ObjectLayer.GROUND)?.id)
    }

    @Test
    fun `Fishing remains official when custom XP is off`() {
        val player = catchTrout()

        assertEquals(500, player.experience.direct(Skill.Fishing))
        assertEquals(1, player.inventory.count("raw_trout"))
        assertEquals(99, player.inventory.count("feather"))
    }

    @Test
    fun `Woodcutting applies training and skill factors once`() {
        configure(
            "custom.xp.enabled" to "true",
            "custom.xp.source.training.enabled" to "true",
            "custom.xp.source.training.factor" to "2.0",
            "custom.xp.skill.woodcutting" to "3.0",
        )

        val (player, _) = chopTree()

        assertEquals(1_500, player.experience.direct(Skill.Woodcutting))
        assertEquals(1, player.inventory.count("logs"))
    }

    @Test
    fun `Fishing applies its independent training and skill factors once`() {
        configure(
            "custom.xp.enabled" to "true",
            "custom.xp.source.training.enabled" to "true",
            "custom.xp.source.training.factor" to "2.0",
            "custom.xp.skill.fishing" to "4.0",
        )

        val player = catchTrout()

        assertEquals(4_000, player.experience.direct(Skill.Fishing))
        assertEquals(1, player.inventory.count("raw_trout"))
        assertEquals(99, player.inventory.count("feather"))
    }

    @Test
    fun `Training off ignores configured pilot skill factors`() {
        configure(
            "custom.xp.enabled" to "true",
            "custom.xp.source.training.enabled" to "false",
            "custom.xp.source.training.factor" to "2.0",
            "custom.xp.skill.woodcutting" to "3.0",
            "custom.xp.skill.fishing" to "4.0",
        )

        val (woodcutter, _) = chopTree()
        val fisher = catchTrout()

        assertEquals(250, woodcutter.experience.direct(Skill.Woodcutting))
        assertEquals(500, fisher.experience.direct(Skill.Fishing))
    }

    @Test
    fun `Master off ignores enabled training category`() {
        configure(
            "custom.xp.enabled" to "false",
            "custom.xp.source.training.enabled" to "true",
            "custom.xp.source.training.factor" to "2.0",
            "custom.xp.skill.woodcutting" to "3.0",
            "custom.xp.skill.fishing" to "4.0",
        )

        val (woodcutter, _) = chopTree()
        val fisher = catchTrout()

        assertEquals(250, woodcutter.experience.direct(Skill.Woodcutting))
        assertEquals(500, fisher.experience.direct(Skill.Fishing))
    }

    @Test
    fun `Decimal training factors preserve original truncation`() {
        configure(
            "custom.xp.enabled" to "true",
            "custom.xp.source.training.enabled" to "true",
            "custom.xp.source.training.factor" to "1.5",
            "custom.xp.skill.woodcutting" to "0.33",
        )

        val (player, _) = chopTree()

        assertEquals(123, player.experience.direct(Skill.Woodcutting))
        assertEquals(12.3, player.experience.get(Skill.Woodcutting))
    }

    @Test
    fun `Custom training composes once with bonus and world rate`() {
        configure(
            "custom.xp.enabled" to "true",
            "custom.xp.source.training.enabled" to "true",
            "custom.xp.source.training.factor" to "2.0",
            "custom.xp.skill.woodcutting" to "3.0",
        )
        Settings.load(mapOf("world.experienceRate" to "2.0"))
        val player = createPlayer(emptyTile)
        player.experience.multiplier = 2.0

        val (woodcutter, _) = chopTree(player)

        assertEquals(6_000, woodcutter.experience.direct(Skill.Woodcutting))
    }

    @Test
    fun `Successful classified action emits one experience event`() {
        configure(
            "custom.xp.enabled" to "true",
            "custom.xp.source.training.enabled" to "true",
            "custom.xp.source.training.factor" to "2.0",
            "custom.xp.skill.woodcutting" to "3.0",
        )
        val player = createPlayer(emptyTile)
        mockkObject(Skills)
        try {
            chopTree(player)

            verify(exactly = 1) { Skills.exp(player, Skill.Woodcutting, 0, 1_500) }
        } finally {
            unmockkObject(Skills)
        }
    }

    @Test
    fun `Failed woodcutting without a hatchet gives no reward`() {
        configurePilotFactors()
        val player = createPlayer(emptyTile)
        player.levels.set(Skill.Woodcutting, 100)
        val tree = createObject("tree", emptyTile.addY(1))

        player.objectOption(tree, "Chop down")
        tick(10)

        assertEquals(0, player.experience.direct(Skill.Woodcutting))
        assertEquals(0, player.inventory.count("logs"))
        assertTrue(GameObjects.contains(tree))
    }

    @Test
    fun `Failed fishing without tackle gives no reward or bait consumption`() {
        configurePilotFactors()
        val player = createPlayer(emptyTile)
        player.levels.set(Skill.Fishing, 20)
        val fishingSpot = createNPC("fishing_spot_lure_bait_lumbridge", emptyTile.addY(1))
        player.inventory.add("feather", 100)

        player.npcOption(fishingSpot, "Lure")
        tick(10)

        assertEquals(0, player.experience.direct(Skill.Fishing))
        assertEquals(0, player.inventory.count("raw_trout"))
        assertEquals(100, player.inventory.count("feather"))
    }

    @Test
    fun `Unknown and unclassified awards remain identity`() {
        configurePilotFactors()
        val player = createPlayer(emptyTile)

        player.exp(Skill.Woodcutting, 25.0)
        player.exp(Skill.Fishing, 50.0, XpOrigin.UNKNOWN)
        player.exp(Skill.Mining, 10.0)

        assertEquals(250, player.experience.direct(Skill.Woodcutting))
        assertEquals(500, player.experience.direct(Skill.Fishing))
        assertEquals(100, player.experience.direct(Skill.Mining))
    }

    @Test
    fun `Invalid pilot factors fall back to official woodcutting XP`() {
        configure(
            "custom.xp.enabled" to "true",
            "custom.xp.source.training.enabled" to "true",
            "custom.xp.source.training.factor" to "NaN",
            "custom.xp.skill.woodcutting" to "0",
        )

        val (player, _) = chopTree()

        assertEquals(250, player.experience.direct(Skill.Woodcutting))
    }

    private fun configurePilotFactors() {
        configure(
            "custom.xp.enabled" to "true",
            "custom.xp.source.training.enabled" to "true",
            "custom.xp.source.training.factor" to "2.0",
            "custom.xp.skill.woodcutting" to "3.0",
            "custom.xp.skill.fishing" to "4.0",
        )
    }

    private fun configure(vararg values: Pair<String, String>) {
        val customSettings = Settings()
        customSettings.load(mapOf(*values))
        ExperienceModifiers.unregister("custom_xp_policy")
        ExperienceModifiers.register("custom_xp_policy", CustomXpPolicy(CustomXpConfig(customSettings) {}.load()))
    }

    private fun chopTree(player: Player = createPlayer(emptyTile)): Pair<Player, Tile> {
        player.levels.set(Skill.Woodcutting, 100)
        val tile = emptyTile.addY(1)
        val tree = createObject("tree", tile)
        player.inventory.add("bronze_hatchet")

        player.objectOption(tree, "Chop down")
        tickIf { player.inventory.count("logs") == 0 }

        return Pair(player, tile)
    }

    private fun catchTrout(): Player {
        val player = createPlayer(emptyTile)
        player.levels.set(Skill.Fishing, 20)
        val fishingSpot = createNPC("fishing_spot_lure_bait_lumbridge", emptyTile.addY(1))
        player.inventory.add("fly_fishing_rod")
        player.inventory.add("feather", 100)

        player.npcOption(fishingSpot, "Lure")
        tickIf { !player.inventory.contains("raw_trout") }

        return player
    }
}
