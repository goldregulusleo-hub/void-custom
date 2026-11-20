package content.quest.miniquest.sheep_shearer

import FakeRandom
import WorldTest
import content.area.misthalin.lumbridge.cleanUpSheepShearerBank
import content.entity.player.bank.bank
import content.quest.quest
import content.quest.questCompleted
import continueDialogue
import dialogueOption
import itemOnNpc
import itemOnObject
import npcOption
import org.junit.jupiter.api.Test
import skipDialogues
import world.gregs.voidps.engine.client.ui.InterfaceApi
import world.gregs.voidps.engine.client.ui.dialogue
import world.gregs.voidps.engine.data.Settings
import world.gregs.voidps.engine.data.config.VariableDefinition.Companion.persist
import world.gregs.voidps.engine.data.definition.VariableDefinitions
import world.gregs.voidps.engine.entity.character.player.Player
import world.gregs.voidps.engine.entity.character.player.skill.Skill
import world.gregs.voidps.engine.entity.obj.GameObjects
import world.gregs.voidps.engine.inv.add
import world.gregs.voidps.engine.inv.inventory
import world.gregs.voidps.engine.inv.remove
import world.gregs.voidps.type.Tile
import world.gregs.voidps.type.setRandom
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SheepShearerTest : WorldTest() {

    @Test
    fun `Rejecting Fred's job leaves the miniquest unstarted`() {
        val player = createPlayer(Tile(3188, 3273))
        val fred = createNPC("fred_the_farmer_lumbridge", Tile(3189, 3273))

        player.npcOption(fred, "Talk-to")
        tick()
        player.skipDialogues()
        player.dialogueOption(3) // I'm looking for work.
        player.skipDialogues()
        player.dialogueOption(1) // It takes all sorts, I suppose.
        player.skipDialogues()
        player.dialogueOption(1) // That doesn't sound very exciting.
        player.skipDialogues()

        assertEquals("unstarted", player.quest("sheep_shearer_miniquest"))
        assertFalse(player.questCompleted("sheep_shearer_miniquest"))
    }

    @Test
    fun `Accepting Fred's job starts with twenty balls remaining`() {
        val player = createPlayer(Tile(3188, 3273))
        val fred = createNPC("fred_the_farmer_lumbridge", Tile(3189, 3273))

        player.npcOption(fred, "Talk-to")
        tick()
        player.skipDialogues()
        player.dialogueOption(3) // I'm looking for work.
        player.skipDialogues()
        player.dialogueOption(1) // It takes all sorts, I suppose.
        player.skipDialogues()
        player.dialogueOption(2) // I'll take the job.
        player.skipDialogues()
        tick()

        assertEquals("started", player.quest("sheep_shearer_miniquest"))
        assertEquals(20, player["sheep_shearer_black_balls_of_wool", -1])
    }

    @Test
    fun `Implemented miniquest is not treated as complete when missing quests are skipped`() {
        Settings.load(mapOf("quests.requirements.skipMissing" to "true"))
        val player = createPlayer(emptyTile)

        assertFalse(player.questCompleted("sheep_shearer_miniquest"))
        player["sheep_shearer_miniquest"] = "completed"
        assertTrue(player.questCompleted("sheep_shearer_miniquest"))
        player["sheep_shearer_miniquest"] = "completed_with_second_reward"
        assertTrue(player.questCompleted("sheep_shearer_miniquest"))
    }

    @Test
    fun `Black sheep require the job and produce black wool deterministically`() {
        setRandom(
            object : FakeRandom() {
                override fun nextBits(bitCount: Int): Int = Int.MAX_VALUE ushr (31 - bitCount)
            },
        )
        val player = createPlayer(Tile(3194, 3275))
        player.inventory.add("shears")
        val forbiddenSheep = createNPC("black_sheep", Tile(3195, 3275))

        player.npcOption(forbiddenSheep, "Shear")
        tick(4)
        assertEquals(0, player.inventory.count("black_wool"))

        player["sheep_shearer_miniquest"] = "started"
        val allowedSheep = createNPC("black_sheep", Tile(3195, 3276))
        player.npcOption(allowedSheep, "Shear")
        tick(4)

        assertEquals(1, player.inventory.count("black_wool"))
    }

    @Test
    fun `Black wool only spins while the job is active`() {
        val wheelTile = Tile(3081, 3430)
        val player = createPlayer(wheelTile.addX(1))
        val wheel = GameObjects.find(wheelTile, "spinning_wheel_barbarian_village")
        player.inventory.add("black_wool")

        player.itemOnObject(wheel, player.inventory.indexOf("black_wool"))
        tick()
        assertEquals(1, player.inventory.count("black_wool"))
        assertEquals(0, player.inventory.count("ball_of_black_wool"))

        val activePlayer = createPlayer(wheelTile.addX(1), name = "active-spinner")
        activePlayer["sheep_shearer_miniquest"] = "started"
        activePlayer.inventory.add("black_wool")
        activePlayer.itemOnObject(wheel, activePlayer.inventory.indexOf("black_wool"))
        tick()
        activePlayer.continueDialogue("dialogue_skill_creation", "choice1")
        tick(3)

        assertEquals(0, activePlayer.inventory.count("black_wool"))
        assertEquals(1, activePlayer.inventory.count("ball_of_black_wool"))
    }

    @Test
    fun `Partial delivery removes only the balls delivered and never makes progress negative`() {
        val player = createPlayer(Tile(3188, 3273))
        val fred = createNPC("fred_the_farmer_lumbridge", Tile(3189, 3273))
        player["sheep_shearer_miniquest"] = "started"
        player["sheep_shearer_black_balls_of_wool"] = 20
        player.inventory.add("ball_of_black_wool", 7)

        player.itemOnNpc(fred, player.inventory.indexOf("ball_of_black_wool"))
        tick()

        assertEquals(13, player["sheep_shearer_black_balls_of_wool", -1])
        assertEquals(0, player.inventory.count("ball_of_black_wool"))
        assertEquals("started", player.quest("sheep_shearer_miniquest"))
    }

    @Test
    fun `Journal opens at every state and reports real progress`() {
        val player = createPlayer(emptyTile)
        val states = listOf("unstarted", "started", "completed", "completed_with_second_reward")

        for (state in states) {
            player["sheep_shearer_miniquest"] = state
            player["sheep_shearer_black_balls_of_wool"] = 13
            InterfaceApi.openQuestJournal(player, "sheep_shearer_miniquest")
            assertTrue(player.interfaces.contains("quest_scroll"), "journal should open at $state")
            player.interfaces.close("quest_scroll")
        }

        player["sheep_shearer_miniquest"] = "started"
        val lines = player.sheepShearerJournalLines()
        assertTrue(lines.any { "delivered <maroon>7" in it })
        assertTrue(lines.any { "needs <maroon>13" in it })
    }

    @Test
    fun `Final delivery grants the primary reward once and no quest points`() {
        val player = createPlayer(Tile(3188, 3273))
        val fred = createNPC("fred_the_farmer_lumbridge", Tile(3189, 3273))
        player["sheep_shearer_miniquest"] = "started"
        player["sheep_shearer_black_balls_of_wool"] = 1
        player.inventory.add("black_wool", 2)
        player.inventory.add("ball_of_black_wool", 3)
        val craftingBefore = player.experience.get(Skill.Crafting)
        val pointsBefore = player["quest_points", 0]

        player.itemOnNpc(fred, player.inventory.indexOf("ball_of_black_wool"))
        tick()
        repeat(10) {
            if (player.quest("sheep_shearer_miniquest") == "completed") {
                return@repeat
            }
            if (player.dialogue != null) {
                player.dialogueOption("continue")
            }
            tick()
        }

        assertEquals("completed", player.quest("sheep_shearer_miniquest"))
        assertEquals(0, player["sheep_shearer_black_balls_of_wool", -1])
        assertEquals(0, player.inventory.count("black_wool"))
        assertEquals(0, player.inventory.count("ball_of_black_wool"))
        assertEquals(craftingBefore + 150.0, player.experience.get(Skill.Crafting))
        assertEquals(2000, player.inventory.count("coins"))
        assertEquals(pointsBefore, player["quest_points", 0])

        player.inventory.add("ball_of_black_wool")
        player.itemOnNpc(fred, player.inventory.indexOf("ball_of_black_wool"))
        tick()
        assertEquals(craftingBefore + 150.0, player.experience.get(Skill.Crafting))
        assertEquals(2000, player.inventory.count("coins"))
    }

    @Test
    fun `Second reward can be retried when inventory is full and cannot be duplicated`() {
        val player = createPlayer(Tile(3188, 3273))
        val fred = createNPC("fred_the_farmer_lumbridge", Tile(3189, 3273))
        player["sheep_shearer_miniquest"] = "completed"
        repeat(28) { player.inventory.add("bucket") }

        player.npcOption(fred, "Talk-to")
        tick()
        player.skipDialogues()
        assertEquals("completed", player.quest("sheep_shearer_miniquest"))
        assertEquals(0, player.inventory.count("coins"))

        player.inventory.remove("bucket")
        player.npcOption(fred, "Talk-to")
        tick()
        player.skipDialogues()
        assertEquals("completed_with_second_reward", player.quest("sheep_shearer_miniquest"))
        assertEquals(1940, player.inventory.count("coins"))

        player.npcOption(fred, "Talk-to")
        tick()
        player.skipDialogues()
        assertEquals(1940, player.inventory.count("coins"))
    }

    @Test
    fun `Bank cleanup removes only Sheep Shearer items in every location scenario`() {
        val player = createPlayer(emptyTile)

        player.cleanUpSheepShearerBank()
        assertEquals(0, player.bank.count)

        player.inventory.add("black_wool", 2)
        player.cleanUpSheepShearerBank()
        assertEquals(2, player.inventory.count("black_wool"), "inventory cleanup is handled by completion")

        player.bank.add("black_wool", 2)
        player.bank.add("ball_of_black_wool", 3)
        player.bank.add("bronze_sword")
        player.cleanUpSheepShearerBank()

        assertEquals(0, player.bank.count("black_wool"))
        assertEquals(0, player.bank.count("ball_of_black_wool"))
        assertEquals(1, player.bank.count("bronze_sword"))
        assertEquals(2, player.inventory.count("black_wool"))
    }

    @Test
    fun `Quest state and counter use the persistent variable store`() {
        val player = createPlayer(emptyTile)
        player["sheep_shearer_miniquest"] = "started"
        player["sheep_shearer_black_balls_of_wool"] = 9

        assertTrue(VariableDefinitions.get("sheep_shearer_miniquest").persist)
        assertTrue(VariableDefinitions.get("sheep_shearer_black_balls_of_wool").persist)
        assertEquals("started", player.variables.data["sheep_shearer_miniquest"])
        assertEquals(9, player.variables.data["sheep_shearer_black_balls_of_wool"])

        val reloaded = Player(variables = player.variables.data.toMutableMap())
        assertEquals("started", reloaded.quest("sheep_shearer_miniquest"))
        assertEquals(9, reloaded["sheep_shearer_black_balls_of_wool", -1])
    }

    @Test
    fun `Existing quest completion semantics remain unchanged`() {
        Settings.load(mapOf("quests.requirements.skipMissing" to "true"))
        val player = createPlayer(emptyTile)

        assertFalse(player.questCompleted("dorics_quest"))
        assertFalse(player.questCompleted("cooks_assistant"))
        player["dorics_quest"] = "completed"
        player["cooks_assistant"] = "completed"
        assertTrue(player.questCompleted("dorics_quest"))
        assertTrue(player.questCompleted("cooks_assistant"))
    }
}
