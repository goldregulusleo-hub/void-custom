package content.skill.slayer

import WorldTest
import org.junit.jupiter.api.Test
import world.gregs.voidps.engine.client.variable.MapValues
import world.gregs.voidps.engine.data.definition.NPCDefinitions
import world.gregs.voidps.engine.data.definition.Rows
import world.gregs.voidps.engine.data.definition.Tables
import world.gregs.voidps.engine.data.definition.VariableDefinitions
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SlayerDataCoverageTest : WorldTest() {

    private val masters = listOf("turael", "mazchna", "vannaka", "chaeldar", "sumona", "duradel", "kuradal")

    private val masterCategories: Set<String>
        get() = masters
            .flatMap { master -> Tables.get("${master}_slayer_tasks").rows.map { Rows.get(it).rowId } }
            .toSet()

    private val globalCategories: Set<String>
        get() = Tables.get("slayer_tasks").rows.map { Rows.get(it).rowId }.toSet()

    private val targetCategories: Set<String>
        get() = (VariableDefinitions.get("slayer_target")!!.values as MapValues).values.keys.filterIsInstance<String>().toSet()

    @Test
    fun `Only the documented client-incompatible master row lacks a global task row`() {
        val missing = masterCategories - globalCategories

        assertEquals(
            setOf("tormented_demon"),
            missing,
            "Master task rows missing from slayer_tasks; tormented_demon remains explicit debt because revision 634 has no slayer_target mapping",
        )
    }

    @Test
    fun `Unsupported revision 634 master rows remain explicit and cannot be mistaken for representable tasks`() {
        assertEquals(
            setOf("aviansies", "crabs", "spiritual_creatures", "tormented_demon"),
            masterCategories - targetCategories,
            "Master task rows without a revision 634 slayer_target value",
        )
    }

    @Test
    fun `Every effectively assignable category has a positive slayer xp target`() {
        val positiveTargetCategories = NPCDefinitions.definitions
            .asSequence()
            .filter { it["slayer_xp", 0.0] > 0.0 }
            .flatMap { it.get<Set<String>>("categories", emptySet()).asSequence() }
            .toSet()
        val effectivelyAssignable = masterCategories.intersect(globalCategories).intersect(targetCategories)
        val missing = effectivelyAssignable - positiveTargetCategories

        assertTrue(missing.isEmpty(), "Representable master tasks without a positive slayer_xp target: $missing")
    }

    @Test
    fun `Temporarily blocked tasks are absent from masters but retain persistence contracts`() {
        val blocked = setOf("gorak", "warped_creatures")

        assertTrue(masterCategories.intersect(blocked).isEmpty(), "Blocked categories still listed by a master")
        assertTrue(globalCategories.containsAll(blocked), "Blocked categories lost their global task rows")
        assertTrue(targetCategories.containsAll(blocked), "Blocked categories lost their revision 634 slayer_target values")
    }

    @Test
    fun `Recovered assignment rows use their spawned slayer definitions`() {
        val expected = mapOf(
            "desert_strykewyrm" to "mound_desert_strykewyrm",
            "ice_strykewyrm" to "mound_ice_strykewyrm",
            "living_rock_creatures" to "living_rock_protector",
        )

        expected.forEach { (category, npc) ->
            assertEquals(npc, Tables.npc("slayer_tasks.$category.npc"), category)
            val definition = NPCDefinitions.get(npc)
            assertEquals(true, definition.get<Set<String>>("categories", emptySet()).contains(category), "$category/$npc category")
            assertEquals(true, definition["slayer_xp", 0.0] > 0.0, "$category/$npc slayer_xp")
        }

        val transformed = mapOf(
            "desert_strykewyrm" to "mound_desert_strykewyrm",
            "ice_strykewyrm" to "mound_ice_strykewyrm",
        )
        transformed.forEach { (combatForm, mound) ->
            assertTrue(NPCDefinitions.get(combatForm)["combat_def", ""].isNotEmpty(), "$combatForm combat_def")
            assertTrue(NPCDefinitions.get(mound)["slayer_xp", 0.0] > 0.0, "$mound slayer_xp")
        }

        listOf("living_rock_protector", "living_rock_striker", "living_rock_patriarch").forEach { npc ->
            val definition = NPCDefinitions.get(npc)
            assertTrue(definition["combat_def", ""].isNotEmpty(), "$npc combat_def")
            assertTrue(definition["slayer_xp", 0.0] > 0.0, "$npc slayer_xp")
            assertTrue(definition.get<Set<String>>("categories", emptySet()).contains("living_rock_creatures"), "$npc category")
        }
    }

    @Test
    fun `Corrected bases and clones expose exact effective slayer xp`() {
        val expected = mapOf(
            "lesser_demon" to 79.0,
            "lesser_demon_2" to 79.0,
            "rock_crab" to 50.0,
            "rock_crab_1" to 50.0,
            "giant_rock_crab" to 198.0,
            "giant_rock_crab_light" to 198.0,
            "black_dragon_evil_chicken_lair" to 199.5,
            "black_dragon_evil_chicken_lair_2" to 199.5,
            "black_dragon_evil_chicken_lair_3" to 199.5,
            "mutated_bloodveld_meiyerditch_tunnels" to 178.0,
            "mutated_bloodveld_meiyerditch_tunnels_2" to 178.0,
            "dagannoth_guardian_dagannoth_caves" to 210.0,
            "icefiend_ghorrock" to 15.0,
            "red_dragon_brimhaven_dungeon" to 143.4,
        )

        expected.forEach { (npc, xp) ->
            assertEquals(xp, NPCDefinitions.get(npc)["slayer_xp", 0.0], npc)
        }
    }
}
