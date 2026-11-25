package content.custom.xp

import WorldTest
import content.entity.combat.damageDealers
import content.entity.death.NPCDeath
import content.entity.player.combat.CombatExperience
import content.skill.slayer.slayerMaster
import content.skill.slayer.slayerPoints
import content.skill.slayer.slayerStreak
import content.skill.slayer.slayerTask
import content.skill.slayer.slayerTaskRemaining
import interfaceOption
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import world.gregs.voidps.engine.client.ui.open
import world.gregs.voidps.engine.data.Settings
import world.gregs.voidps.engine.entity.Despawn
import world.gregs.voidps.engine.entity.character.Character
import world.gregs.voidps.engine.entity.character.Death
import world.gregs.voidps.engine.entity.character.mode.combat.CombatApi
import world.gregs.voidps.engine.entity.character.mode.combat.CombatAttack
import world.gregs.voidps.engine.entity.character.player.Player
import world.gregs.voidps.engine.entity.character.player.skill.Skill
import world.gregs.voidps.engine.entity.character.player.skill.Skills
import world.gregs.voidps.engine.entity.character.player.skill.exp.ExperienceModifiers
import world.gregs.voidps.engine.entity.character.player.skill.exp.XpOrigin
import world.gregs.voidps.engine.entity.character.player.skill.exp.exp
import world.gregs.voidps.engine.entity.item.Item
import kotlin.test.assertEquals

internal class CombatXpOriginTest : WorldTest() {

    @BeforeEach
    fun resetPolicy() {
        Settings.load(mapOf("world.experienceRate" to "1.0"))
        configure()
    }

    @AfterEach
    fun restorePolicy() {
        Settings.load(mapOf("world.experienceRate" to "1.0"))
        configure()
    }

    @Test
    fun `Master off preserves official combat experience`() {
        configure(
            "custom.xp.enabled" to "false",
            "custom.xp.source.combat.enabled" to "true",
            "custom.xp.source.combat.factor" to "2.0",
            "custom.xp.skill.attack" to "1.5",
        )
        val player = createPlayer(emptyTile)

        grant(player, Skill.Attack, 25.0)

        assertEquals(250, player.experience.direct(Skill.Attack))
    }

    @Test
    fun `Combat off preserves official combat experience`() {
        configure(
            "custom.xp.enabled" to "true",
            "custom.xp.source.combat.enabled" to "false",
            "custom.xp.source.combat.factor" to "2.0",
            "custom.xp.skill.attack" to "1.5",
        )
        val player = createPlayer(emptyTile)

        grant(player, Skill.Attack, 25.0)

        assertEquals(250, player.experience.direct(Skill.Attack))
    }

    @Test
    fun `Controlled melee applies independent factors to all combat skills`() {
        configureCombat()
        val player = createPlayer(emptyTile)
        player["attack_style"] = "controlled"
        val constitution = player.experience.direct(Skill.Constitution)

        attack(player, damage = 75, type = "melee")

        assertEquals(300, player.experience.direct(Skill.Attack))
        assertEquals(400, player.experience.direct(Skill.Strength))
        assertEquals(200, player.experience.direct(Skill.Defence))
        assertEquals(200, player.experience.direct(Skill.Constitution) - constitution)
    }

    @Test
    fun `Ranged combat applies ranged and Constitution factors once`() {
        configureCombat()
        val player = createPlayer(emptyTile)
        val constitution = player.experience.direct(Skill.Constitution)

        attack(player, damage = 50, type = "range")

        assertEquals(800, player.experience.direct(Skill.Ranged))
        assertEquals(133, player.experience.direct(Skill.Constitution) - constitution)
    }

    @Test
    fun `Offensive magic applies spell and damage experience as combat`() {
        configureCombat()
        val player = createPlayer(emptyTile)
        val constitution = player.experience.direct(Skill.Constitution)

        attack(player, damage = 75, type = "magic", spell = "wind_strike")

        assertEquals(820, player.experience.direct(Skill.Magic))
        assertEquals(200, player.experience.direct(Skill.Constitution) - constitution)
    }

    @Test
    fun `Zero damage attack grants no combat experience`() {
        configureCombat()
        val player = createPlayer(emptyTile)
        val constitution = player.experience.direct(Skill.Constitution)

        attack(player, damage = 0, type = "melee")

        assertEquals(0, player.experience.direct(Skill.Attack))
        assertEquals(constitution, player.experience.direct(Skill.Constitution))
    }

    @Test
    fun `Combat composes once with bonus and world rate`() {
        configureCombat()
        Settings.load(mapOf("world.experienceRate" to "2.0"))
        val player = createPlayer(emptyTile)
        player.experience.multiplier = 2.0

        grant(player, Skill.Attack, 25.0)

        assertEquals(3_000, player.experience.direct(Skill.Attack))
    }

    @Test
    fun `Decimal combat factors preserve original truncation`() {
        configure(
            "custom.xp.enabled" to "true",
            "custom.xp.source.combat.enabled" to "true",
            "custom.xp.source.combat.factor" to "1.5",
            "custom.xp.skill.attack" to "0.33",
        )
        val player = createPlayer(emptyTile)

        grant(player, Skill.Attack, 25.0)

        assertEquals(123, player.experience.direct(Skill.Attack))
        assertEquals(12.3, player.experience.get(Skill.Attack))
    }

    @Test
    fun `Non combat origins and unknown remain identity`() {
        configureCombat()
        val player = createPlayer(emptyTile)

        player.exp(Skill.Attack, 10.0, XpOrigin.TRAINING)
        player.exp(Skill.Attack, 10.0, XpOrigin.QUEST)
        player.exp(Skill.Attack, 10.0, XpOrigin.LAMP)
        player.exp(Skill.Attack, 10.0, XpOrigin.EVENT)
        player.exp(Skill.Attack, 10.0, XpOrigin.UNKNOWN)

        assertEquals(500, player.experience.direct(Skill.Attack))
    }

    @Test
    fun `Eligible combat award emits one experience event`() {
        configureCombat()
        val player = createPlayer(emptyTile)
        mockkObject(Skills)
        try {
            grant(player, Skill.Attack, 25.0)

            verify(exactly = 1) { Skills.exp(player, Skill.Attack, 0, 750) }
        } finally {
            unmockkObject(Skills)
        }
    }

    @Test
    fun `Target disabling combat experience remains ineligible`() {
        configureCombat()
        val player = createPlayer(emptyTile)
        val pheasant = createNPC("pheasant_1_tail", emptyTile.addY(1))

        combatExperience().grant(player, pheasant, Skill.Attack, 25.0)

        assertEquals(0, player.experience.direct(Skill.Attack))
    }

    @Test
    fun `Eligible Slayer kill applies combat and skill factors`() {
        configureCombat()
        val player = createPlayer(emptyTile)
        player.slayerTask = "lesser_demons"
        player.slayerTaskRemaining = 2

        npcDeath().slay(player, createNPC("lesser_demon", emptyTile.addY(1)))

        assertEquals(4_740, player.experience.direct(Skill.Slayer))
        assertEquals(1, player.slayerTaskRemaining)
    }

    @Test
    fun `Ineligible Slayer kill grants no experience or progress`() {
        configureCombat()
        val player = createPlayer(emptyTile)
        player.slayerTask = "greater_demons"
        player.slayerTaskRemaining = 2

        npcDeath().slay(player, createNPC("lesser_demon", emptyTile.addY(1)))

        assertEquals(0, player.experience.direct(Skill.Slayer))
        assertEquals(2, player.slayerTaskRemaining)
    }

    @Test
    fun `Last Slayer kill completes once without changing points`() {
        configureCombat()
        val player = createPlayer(emptyTile)
        player.slayerMaster = "mazchna"
        player.slayerTask = "lesser_demons"
        player.slayerTaskRemaining = 1
        val npc = createNPC("lesser_demon", emptyTile.addY(1))
        val death = npcDeath()

        death.slay(player, npc)
        death.slay(player, npc)

        assertEquals(4_740, player.experience.direct(Skill.Slayer))
        assertEquals("nothing", player.slayerTask)
        assertEquals(0, player.slayerTaskRemaining)
        assertEquals(1, player.slayerStreak)
        assertEquals(1, player.slayerPoints)
        assertEquals(1, player["slayer_tasks_completed", 0])
    }

    @Test
    fun `Offensive familiar grants combat experience to its owner once`() {
        configureCombat()
        val owner = createPlayer(emptyTile)
        owner["attack_style"] = "accurate"
        val constitution = owner.experience.direct(Skill.Constitution)
        val familiar = createNPC("spirit_wolf_familiar", emptyTile.addX(1))
        familiar.ownerIndex = owner.index

        CombatApi.attack(familiar, combatAttack(createPlayer(emptyTile.addY(1)), 75, "melee"))

        assertEquals(900, owner.experience.direct(Skill.Attack))
        assertEquals(200, owner.experience.direct(Skill.Constitution) - constitution)
    }

    @Test
    fun `Desert Slayer boss reward is classified as combat`() {
        configureCombat()
        val player = createPlayer(emptyTile)
        val boss = createNPC("monstrous_cave_crawler", emptyTile.addY(1))
        boss.damageDealers[player] = 1

        Despawn.npc(boss)

        assertEquals(60_000, player.experience.direct(Skill.Slayer))
        assertEquals(true, player["killed_monstrous_cave_crawler", false])
    }

    @Test
    fun `Ghast Prayer reward is classified as combat`() {
        configureCombat()
        val player = createPlayer(emptyTile)
        val ghast = createNPC("ghast", emptyTile.addY(1))
        ghast.damageDealers[player] = 1

        Death.killed(ghast)

        assertEquals(1_200, player.experience.direct(Skill.Prayer))
    }

    @Test
    fun `Slayer experience bought with points is not combat`() {
        configureCombat()
        val player = createPlayer(emptyTile)
        player.slayerPoints = 400
        player.open("slayer_rewards")

        player.interfaceOption("slayer_rewards", "buy_xp_icon", "Buy XP")

        assertEquals(100_000, player.experience.direct(Skill.Slayer))
        assertEquals(0, player.slayerPoints)
    }

    private fun configureCombat() {
        configure(
            "custom.xp.enabled" to "true",
            "custom.xp.source.combat.enabled" to "true",
            "custom.xp.source.combat.factor" to "2.0",
            "custom.xp.skill.attack" to "1.5",
            "custom.xp.skill.strength" to "2.0",
            "custom.xp.skill.defence" to "1.0",
            "custom.xp.skill.constitution" to "1.0",
            "custom.xp.skill.ranged" to "2.0",
            "custom.xp.skill.magic" to "2.0",
            "custom.xp.skill.prayer" to "2.0",
            "custom.xp.skill.slayer" to "3.0",
        )
    }

    private fun configure(vararg values: Pair<String, String>) {
        val customSettings = Settings()
        customSettings.load(mapOf(*values))
        ExperienceModifiers.unregister("custom_xp_policy")
        ExperienceModifiers.register("custom_xp_policy", CustomXpPolicy(CustomXpConfig(customSettings) {}.load()))
    }

    private fun grant(player: Player, skill: Skill, experience: Double) {
        combatExperience().grant(player, createPlayer(emptyTile.addY(1)), skill, experience)
    }

    private fun attack(player: Player, damage: Int, type: String, spell: String = "") {
        CombatApi.attack(player, combatAttack(createPlayer(emptyTile.addY(1)), damage, type, spell))
    }

    private fun combatAttack(target: Character, damage: Int, type: String, spell: String = "") = CombatAttack(
        target = target,
        damage = damage,
        type = type,
        weapon = Item.EMPTY,
        spell = spell,
        special = false,
        delay = 0,
    )

    private fun combatExperience(): CombatExperience = scripts.filterIsInstance<CombatExperience>().first()

    private fun npcDeath(): NPCDeath = scripts.filterIsInstance<NPCDeath>().first()
}
