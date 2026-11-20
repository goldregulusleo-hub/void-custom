package content.quest.miniquest.sheep_shearer

import content.quest.quest
import content.quest.questJournal
import world.gregs.voidps.engine.Script
import world.gregs.voidps.engine.entity.character.player.Player

class SheepShearer : Script {
    init {
        questJournalOpen("sheep_shearer_miniquest") {
            questJournal("Sheep Shearer", sheepShearerJournalLines())
        }
    }
}

fun Player.sheepShearerJournalLines(): List<String> = when (quest("sheep_shearer_miniquest")) {
    "started" -> {
        val remaining = get("sheep_shearer_black_balls_of_wool", 20).coerceIn(0, 20)
        val delivered = 20 - remaining
        buildList {
            add("<str>I have spoken to Fred the Farmer.")
            add("")
            add("<navy>I need to shear <maroon>black sheep<navy>, then spin the wool into")
            add("<maroon>balls of black wool<navy> for <maroon>Fred the Farmer<navy>.")
            add("")
            add("<navy>I have delivered <maroon>$delivered<navy> of the <maroon>20 balls of black wool<navy>.")
            if (remaining == 0) {
                add("<navy>I should speak to <maroon>Fred the Farmer<navy> to collect my reward.")
            } else {
                add("<navy>Fred still needs <maroon>$remaining balls of black wool<navy>.")
            }
        }
    }
    "completed", "completed_with_second_reward" -> listOf(
        "<str>I spoke to Fred the Farmer.",
        "",
        "<str>I sheared black sheep and spun the wool into balls.",
        "",
        "<str>I delivered 20 balls of black wool to Fred.",
        "<str>Fred rewarded me for my work.",
        "",
        "<red>QUEST COMPLETE!",
    )
    else -> listOf(
        "<navy>I can start this miniquest by speaking to <maroon>Fred the Farmer<navy>.",
        "<navy>He lives at his farm <maroon>north-west of Lumbridge<navy>.",
        "",
        "<navy>There are no requirements for this miniquest.",
    )
}
