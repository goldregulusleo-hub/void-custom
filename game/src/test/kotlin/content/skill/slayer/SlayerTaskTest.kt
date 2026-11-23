package content.skill.slayer

import FakeRandom
import WorldTest
import content.entity.combat.damageDealers
import content.entity.death.NPCDeath
import content.entity.effect.toxin.Poison
import content.entity.effect.toxin.poison
import content.entity.effect.transform
import itemOnNpc
import npcOption
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import world.gregs.voidps.engine.entity.character.player.skill.Skill
import world.gregs.voidps.engine.inv.add
import world.gregs.voidps.engine.inv.equipment
import world.gregs.voidps.engine.inv.inventory
import world.gregs.voidps.network.login.protocol.visual.update.player.EquipSlot
import world.gregs.voidps.type.Tile
import world.gregs.voidps.type.setRandom
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SlayerTaskTest : WorldTest() {

    @TestFactory
    fun `Complete a slayer task`() = listOf(
        "birds" to "chicken",
        "goblins" to "goblin_staff_green",
        "monkeys" to "monkey_brown",
        "rats" to "giant_rat",
        "spiders" to "giant_spider",
        "bats" to "bat",
        "cows" to "cow_calf",
        "dwarves" to "dwarf",
        "minotaurs" to "minotaur",
        "scorpions" to "scorpion",
        "zombies" to "zombie",
        "bears" to "grizzly_bear",
        "ghosts" to "ghost",
        "dogs" to "jackal",
        "kalphites" to "kalphite_worker",
        "skeletons" to "skeleton_sword_shield",
        "icefiends" to "icefiend",
        "wolves" to "white_wolf",
        "crawling_hands" to "crawling_hand",
        "cave_bugs" to "cave_bug",
        "cave_crawlers" to "cave_crawler",
        "cave_slimes" to "cave_slime",
    ).map { (task, id) ->
        dynamicTest("Complete a $task slayer task") {
            setRandom(object : FakeRandom() {
                override fun nextBits(bitCount: Int): Int = bitCount
            })
            val player = createPlayer(Tile(3231, 3298))
            player.levels.set(Skill.Attack, 99)
            player.levels.set(Skill.Strength, 99)
            player.levels.set(Skill.Defence, 99)
            player.levels.set(Skill.Constitution, 990)
            player.levels.set(Skill.Slayer, 99)
            player.equipment.set(EquipSlot.Weapon.index, "abyssal_whip")
            player.equipment.set(EquipSlot.Hat.index, "earmuffs")
            player.slayerMaster = "mazchna"
            player.slayerTask = task
            player.slayerTaskRemaining = 1

            val npc = createNPC(id, player.tile.addY(1))
            player.npcOption(npc, "Attack")
            tickIf { npc.levels.get(Skill.Constitution) > 0 }
            tick(5) // Npc death

            assertEquals("mazchna", player.slayerMaster)
            assertEquals("nothing", player.slayerTask)
            assertEquals(0, player.slayerTaskRemaining)
            assertEquals(1, player.slayerStreak)
            assertEquals(1, player.slayerPoints)
            assertTrue(player.experience.get(Skill.Slayer) > 0)
        }
    }

    @TestFactory
    fun `Vampyres count towards a vampyre task`() = listOf(
        "vampire",
        "vampyre_god_wars",
        "vampyre_juvinate_3",
        "vampyre_juvenile_3",
        "vampyre_juvenile_female",
    ).map { id ->
        dynamicTest("Kill $id on a vampyre task") {
            val player = createPlayer(Tile(3231, 3298))
            player.slayerMaster = "mazchna"
            player.slayerTask = "vampyres"
            player.slayerTaskRemaining = 2
            val npc = createNPC(id, player.tile.addY(1))

            scripts.filterIsInstance<NPCDeath>().first().slay(player, npc)

            assertEquals(1, player.slayerTaskRemaining)
            assertTrue(player.experience.get(Skill.Slayer) > 0)
        }
    }

    @Test
    fun `Banshees drain stats without earmuffs`() {
        setRandom(object : FakeRandom() {
            override fun nextBits(bitCount: Int): Int = bitCount
            override fun nextInt(from: Int, until: Int): Int = until
        })
        val player = createPlayer(Tile(3231, 3298))
        player.levels.set(Skill.Attack, 99)
        player.levels.set(Skill.Strength, 99)
        player.levels.set(Skill.Defence, 99)
        player.levels.set(Skill.Prayer, 99)
        player.levels.set(Skill.Constitution, 990)
        player.levels.set(Skill.Slayer, 15)
        player.equipment.set(EquipSlot.Weapon.index, "abyssal_whip")
        player.slayerMaster = "mazchna"
        player.slayerTask = "banshees"
        player.slayerTaskRemaining = 1

        val banshee = createNPC("banshee", player.tile.addY(1))
        player.npcOption(banshee, "Attack")
        tick(5)
        player.equipment.set(EquipSlot.Hat.index, "slayer_helmet")
        tickIf { banshee.levels.get(Skill.Constitution) > 0 }
        tick(5) // Npc death

        assertEquals("mazchna", player.slayerMaster)
        assertEquals("nothing", player.slayerTask)
        assertEquals(0, player.slayerTaskRemaining)
        assertEquals(1, player.slayerStreak)
        assertEquals(1, player.slayerPoints)
        assertTrue(player.experience.get(Skill.Slayer) > 0)
        assertEquals(80, player.levels.get(Skill.Attack))
        assertEquals(80, player.levels.get(Skill.Strength))
        assertEquals(80, player.levels.get(Skill.Defence))
        assertEquals(90, player.levels.get(Skill.Prayer))
    }

    @Test
    fun `Complete a lizard slayer task`() {
        setRandom(object : FakeRandom() {
            override fun nextBits(bitCount: Int): Int = bitCount
        })
        val player = createPlayer(Tile(3231, 3298))
        player.levels.set(Skill.Attack, 99)
        player.levels.set(Skill.Strength, 99)
        player.levels.set(Skill.Defence, 99)
        player.levels.set(Skill.Constitution, 990)
        player.levels.set(Skill.Slayer, 22)
        player.inventory.add("ice_cooler")
        player.equipment.set(EquipSlot.Weapon.index, "abyssal_whip")
        player.slayerMaster = "mazchna"
        player.slayerTask = "lizards"
        player.slayerTaskRemaining = 1

        val lizard = createNPC("lizard", player.tile.addY(1))
        player.npcOption(lizard, "Attack")
        tickIf { lizard.levels.get(Skill.Constitution) > 10 }
        tick(1)
        player.itemOnNpc(lizard, 0)
        tick(7) // Npc death

        assertEquals("mazchna", player.slayerMaster)
        assertEquals("nothing", player.slayerTask)
        assertEquals(0, player.slayerTaskRemaining)
        assertEquals(1, player.slayerStreak)
        assertEquals(1, player.slayerPoints)
        assertTrue(player.experience.get(Skill.Slayer) > 0)
    }

    @Test
    fun `10 task streak gives bonus points`() {
        setRandom(object : FakeRandom() {
            override fun nextBits(bitCount: Int): Int = bitCount
        })
        val player = createPlayer(Tile(3231, 3298))
        player.levels.set(Skill.Attack, 99)
        player.levels.set(Skill.Strength, 99)
        player.levels.set(Skill.Defence, 99)
        player.levels.set(Skill.Constitution, 990)
        player.slayerMaster = "mazchna"
        player.slayerTask = "birds"
        player.slayerStreak = 29
        player.slayerTaskRemaining = 1

        val chicken = createNPC("chicken", player.tile.addY(1))
        player.npcOption(chicken, "Attack")
        tickIf { chicken.levels.get(Skill.Constitution) > 0 }
        tick(5) // Npc death

        assertEquals("mazchna", player.slayerMaster)
        assertEquals("nothing", player.slayerTask)
        assertEquals(0, player.slayerTaskRemaining)
        assertEquals(30, player.slayerStreak)
        assertEquals(5, player.slayerPoints)
    }

    @Test
    fun `50 task streak gives bonus points`() {
        setRandom(object : FakeRandom() {
            override fun nextBits(bitCount: Int): Int = bitCount
        })
        val player = createPlayer(Tile(3231, 3298))
        player.levels.set(Skill.Attack, 99)
        player.levels.set(Skill.Strength, 99)
        player.levels.set(Skill.Defence, 99)
        player.levels.set(Skill.Constitution, 990)
        player.slayerMaster = "mazchna"
        player.slayerTask = "birds"
        player.slayerStreak = 149
        player.slayerTaskRemaining = 1

        val chicken = createNPC("chicken", player.tile.addY(1))
        player.npcOption(chicken, "Attack")
        tickIf { chicken.levels.get(Skill.Constitution) > 0 }
        tick(5) // Npc death

        assertEquals("mazchna", player.slayerMaster)
        assertEquals("nothing", player.slayerTask)
        assertEquals(0, player.slayerTaskRemaining)
        assertEquals(150, player.slayerStreak)
        assertEquals(15, player.slayerPoints)
    }

    @Test
    fun `Chaeldar assigns a task without crashing on its jungle strykewyrm entry`() {
        setRandom(object : FakeRandom() {
            override fun nextInt(until: Int): Int = 0
            override fun nextBits(bitCount: Int): Int = bitCount
        })
        val player = createPlayer(Tile(3231, 3298))
        player.levels.set(Skill.Slayer, 99)

        // Chaeldar lists jungle_strykewyrm, which had no slayer_tasks entry and threw
        // "Row not found" while rolling, so the master gave no task at all.
        val (type, amount) = assignTask(player, "chaeldar")

        assertTrue(type.isNotEmpty())
        assertTrue(amount > 0)
        assertEquals("chaeldar", player.slayerMaster)
        assertEquals(type, player.slayerTask)
    }

    @TestFactory
    fun `Corrected lesser demon definitions award exactly 79 xp and decrement once`() = listOf(
        "lesser_demon",
        "lesser_demon_2",
    ).map { id ->
        dynamicTest("Slay $id") {
            assertSlayerKill(id, "lesser_demons", 79.0)
        }
    }

    @Test
    fun `Lesser demon outside its task awards no slayer xp and does not decrement`() {
        val player = createPlayer()
        player.slayerTask = "greater_demons"
        player.slayerTaskRemaining = 2
        val before = player.experience.get(Skill.Slayer)

        scripts.filterIsInstance<NPCDeath>().first().slay(player, createNPC("lesser_demon"))

        assertEquals(before, player.experience.get(Skill.Slayer))
        assertEquals(2, player.slayerTaskRemaining)
    }

    @Test
    fun `Greater demon remains 87 xp and decrements once`() {
        assertSlayerKill("greater_demon", "greater_demons", 87.0)
    }

    @TestFactory
    fun `Corrected rock crab combat forms award their paired exact xp`() = listOf(
        Triple("rock_crab", "crabs", 50.0),
        Triple("rock_crab_1", "crabs", 50.0),
        Triple("giant_rock_crab", "crabs", 198.0),
        Triple("giant_rock_crab_light", "crabs", 198.0),
    ).map { (id, task, xp) ->
        dynamicTest("Slay $id") {
            assertSlayerKill(id, task, xp)
        }
    }

    @TestFactory
    fun `Corrected historical variants award exact xp and decrement once`() = listOf(
        Triple("black_dragon_evil_chicken_lair", "black_dragons", 199.5),
        Triple("black_dragon_evil_chicken_lair_2", "black_dragons", 199.5),
        Triple("black_dragon_evil_chicken_lair_3", "black_dragons", 199.5),
        Triple("mutated_bloodveld_meiyerditch_tunnels", "bloodvelds", 178.0),
        Triple("mutated_bloodveld_meiyerditch_tunnels_2", "bloodvelds", 178.0),
        Triple("dagannoth_guardian_dagannoth_caves", "dagannoth", 210.0),
        Triple("icefiend_ghorrock", "icefiends", 15.0),
        Triple("red_dragon_brimhaven_dungeon", "red_dragons", 143.4),
    ).map { (id, task, xp) ->
        dynamicTest("Slay $id") {
            assertSlayerKill(id, task, xp)
        }
    }

    @TestFactory
    fun `Recovered transformed and living rock targets have a combat path and decrement once`() = listOf(
        Triple("mound_desert_strykewyrm", "desert_strykewyrm", 120.0),
        Triple("mound_ice_strykewyrm", "ice_strykewyrm", 300.0),
        Triple("living_rock_protector", "living_rock_creatures", 225.0),
        Triple("living_rock_striker", "living_rock_creatures", 316.0),
        Triple("living_rock_patriarch", "living_rock_creatures", 530.0),
    ).map { (id, task, xp) ->
        dynamicTest("Slay $id") {
            val npc = createNPC(id)
            if (id.startsWith("mound_")) {
                npc.transform(task)
                assertTrue(npc.transformDef["combat_def", ""].isNotEmpty(), id)
            }
            val player = createPlayer()
            player.slayerTask = task
            player.slayerTaskRemaining = 2
            val before = player.experience.get(Skill.Slayer)

            scripts.filterIsInstance<NPCDeath>().first().slay(player, npc)

            assertEquals(before + xp, player.experience.get(Skill.Slayer), id)
            assertEquals(1, player.slayerTaskRemaining, id)
        }
    }

    @Test
    fun `New assignments never select blocked or revision incompatible categories`() {
        val blocked = setOf("gorak", "warped_creatures", "aviansies", "crabs", "spiritual_creatures", "tormented_demon")
        val player = createPlayer()
        player.levels.set(Skill.Attack, 99)
        player.levels.set(Skill.Strength, 99)
        player.levels.set(Skill.Defence, 99)
        player.levels.set(Skill.Constitution, 990)
        player.levels.set(Skill.Slayer, 99)

        listOf("turael", "mazchna", "vannaka", "chaeldar", "sumona", "duradel", "kuradal").forEach { master ->
            repeat(512) { requestedRoll ->
                setRandom(object : FakeRandom() {
                    override fun nextInt(until: Int): Int = requestedRoll.coerceAtMost(until - 1)
                    override fun nextInt(from: Int, until: Int): Int = from
                })
                val (type) = assignTask(player, master)
                assertFalse(type in blocked, "$master assigned blocked task $type at roll $requestedRoll")
            }
        }
    }

    @Test
    fun `A second sequential death notification cannot complete the same task twice`() {
        val player = createPlayer()
        player.slayerMaster = "mazchna"
        player.slayerTask = "lesser_demons"
        player.slayerTaskRemaining = 1
        val npc = createNPC("lesser_demon")
        val death = scripts.filterIsInstance<NPCDeath>().first()
        val before = player.experience.get(Skill.Slayer)

        death.slay(player, npc)
        death.slay(player, npc)

        assertEquals(before + 79.0, player.experience.get(Skill.Slayer))
        assertEquals("nothing", player.slayerTask)
        assertEquals(0, player.slayerTaskRemaining)
        assertEquals(1, player.slayerStreak)
        assertEquals(1, player.slayerPoints)
        assertEquals(1, player["slayer_tasks_completed", 0])
    }

    @Test
    fun `A familiar killing a lesser demon credits its owner`() {
        val owner = createPlayer()
        owner.slayerTask = "lesser_demons"
        owner.slayerTaskRemaining = 2
        val before = owner.experience.get(Skill.Slayer)
        val familiar = createNPC("spirit_wolf_familiar", owner.tile.addX(1))
        familiar.ownerIndex = owner.index
        val npc = createNPC("lesser_demon", owner.tile.addY(1))
        npc.damageDealers[familiar] = npc.levels.get(Skill.Constitution)

        npc.levels.set(Skill.Constitution, 0)
        tick(8)

        assertEquals(before + 79.0, owner.experience.get(Skill.Slayer))
        assertEquals(1, owner.slayerTaskRemaining)
    }

    @Test
    fun `A poison kill retains the player as slayer source`() {
        val player = createPlayer()
        player.slayerTask = "lesser_demons"
        player.slayerTaskRemaining = 2
        val before = player.experience.get(Skill.Slayer)
        val npc = createNPC("lesser_demon", player.tile.addY(1))
        npc.levels.set(Skill.Constitution, 10)
        player.poison(npc, 20)

        scripts.filterIsInstance<Poison>().first().tick(npc)
        tick(8)

        assertEquals(before + 79.0, player.experience.get(Skill.Slayer))
        assertEquals(1, player.slayerTaskRemaining)
    }

    @Test
    fun `A kill attributed to another player does not credit the task holder`() {
        val taskHolder = createPlayer()
        taskHolder.slayerTask = "lesser_demons"
        taskHolder.slayerTaskRemaining = 2
        val before = taskHolder.experience.get(Skill.Slayer)
        val killer = createPlayer(taskHolder.tile.addX(1))
        val npc = createNPC("lesser_demon", taskHolder.tile.addY(1))
        npc.damageDealers[killer] = npc.levels.get(Skill.Constitution)

        npc.levels.set(Skill.Constitution, 0)
        tick(8)

        assertEquals(before, taskHolder.experience.get(Skill.Slayer))
        assertEquals(2, taskHolder.slayerTaskRemaining)
    }

    private fun assertSlayerKill(id: String, task: String, expectedXp: Double) {
        val player = createPlayer()
        player.slayerTask = task
        player.slayerTaskRemaining = 2
        val before = player.experience.get(Skill.Slayer)

        scripts.filterIsInstance<NPCDeath>().first().slay(player, createNPC(id))

        assertEquals(before + expectedXp, player.experience.get(Skill.Slayer), id)
        assertEquals(1, player.slayerTaskRemaining, id)
    }
}
