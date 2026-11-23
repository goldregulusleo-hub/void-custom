# Fase 9A-2 — Arquitectura de XP configurable por habilidad y origen

**Fecha:** 2026-11-23  
**Repositorio:** `C:\Juegos\Codigo\void-custom`  
**Rama:** `stable/2.12.0`  
**Baseline custom:** `custom-v0.3.0` / `8eab928e435d8657443cb26d71e43b33aee67314`  
**Upstream comparado:** Void `2.12.0` / `ba3331d99af2f15a1d36ce1f84303acf940a4e75`  
**Cliente:** revisión 634  
**Modalidad:** auditoría y diseño. Ninguna propuesta está implementada.

---

## 1. Resumen ejecutivo y dictamen GO/NO-GO

### Dictamen

**GO condicionado**, dividido en tres cambios revisables antes de QA:

1. Un hook identidad central y compatible con las llamadas actuales.
2. Política custom y configuración, apagadas por defecto.
3. Clasificación incremental de call sites; toda ruta todavía no clasificada usa `UNKNOWN` y no recibe multiplicación custom.

Se confirmaron los hallazgos de 9A en el código real:

- `Player.exp` delega directamente en `Experience.add`: `engine/src/main/kotlin/world/gregs/voidps/engine/entity/character/player/skill/exp/Experience.kt:89`.
- `Experience.add` es el punto central de concesión normal: `Experience.kt:45-56`.
- `Skills.experience` es una notificación posterior, no mutable ni cancelable: `engine/src/main/kotlin/world/gregs/voidps/engine/entity/character/player/skill/Skills.kt:33,52`.
- `Experience` se construye directamente en `Player` y `PlayerSave`, no mediante Koin: `Player.kt:43`, `PlayerSave.kt:46`.

Por ello, **cero cambios en engine no es viable** si el origen debe influir antes del único `set`. El punto mínimo inevitable es añadir un overload con origen y llamar a un modificador identidad dentro de `Experience.add`. La política, los flags y el snapshot deben quedar en la ruta propuesta `game/src/main/kotlin/content/custom/xp/`.

### Resultado de cobertura estática

- 312 coincidencias léxicas de `exp(` en producción.
- 4 no eran awards: `kotlin.math.exp`, declaración/dispatch `Skills.exp` y declaración del helper `Player.exp`.
- **308 call sites de award** en **182 archivos**.
- Los “182 imports” de 9A eran 182 archivos que importan el helper, no 182 concesiones.
- **303 call sites clasificados con alta confianza**.
- **5 call sites `UNKNOWN`** por ambigüedad real.
- **7 call sites de `Experience.set`** excluidos expresamente.
- No se halló ningún bypass de producción que invoque `Experience.add` directamente fuera de `Player.exp`.

La cobertura es completa para llamadas Kotlin estáticas visibles a `exp`/`Experience.add` en `engine/src/main/kotlin` y `game/src/main/kotlin`. No equivale a asegurar que todo contenido futuro o todas las definiciones data-driven estén clasificadas; para eso se propone una prueba de cobertura.

---

## 2. Evidencia Git pre/post

### Precondiciones verificadas

| Comprobación | Resultado |
|---|---|
| Rama | `stable/2.12.0` |
| `HEAD` | `8eab928e435d8657443cb26d71e43b33aee67314` |
| `custom-v0.3.0^{}` | `8eab928e435d8657443cb26d71e43b33aee67314` |
| Estado antes de auditar | Limpio |
| Informe 9A previo | Presente en `build/reports/Fase-9A-Arquitectura-Modular-Void-Custom-XP.md` |
| Informe 9A-2 | Ignorado por `.gitignore:49`, regla `/build/` |

### Postcondición

Después de crear este informe se volvió a verificar rama, HEAD, archivos rastreados y staging. El árbol Git permaneció limpio; este Markdown está ignorado y no rastreado.

---

## 3. Arquitectura real de XP y Settings

### 3.1 Flujo actual confirmado

```text
call site de contenido
    -> Player.exp(skill, baseXp)
    -> Experience.add(skill, baseXp)
         descarta baseXp <= 0
         actual = baseXp * 10
                  * Experience.multiplier
                  * Settings["world.experienceRate"]
         si bloqueada -> Skills.blocked(player, skill, actual.toInt())
         si no -> set(skill, current + actual.toInt())
    -> Experience.set
    -> Skills.exp(from, to), notificación posterior
    -> nivel, cliente, xp_counter, lifetime_xp, Bonus XP y logs
    -> PlayerSave persiste el IntArray final
```

Evidencias:

- `Experience.multiplier`: `Experience.kt:21`.
- `set`: `Experience.kt:27-37`.
- `add` y fórmula: `Experience.kt:45-56`, en especial `:49`.
- XP bloqueada: `Experience.kt:51`, `Skills.kt:38,58`.
- máximo interno: `Experience.kt:71`, `2_000_000_000` décimas.
- eventos posteriores: `game/src/main/kotlin/content/entity/player/stat/Experience.kt:24,30` y `.../dialogue/type/LevelUp.kt:44`.
- carga/guardado: `PlayerSave.kt:46,278,483`; enlace a jugador/niveles: `AccountManager.kt:81-82`.

`Experience.set` no es una ganancia: fija un total. Los siete usos de producción excluidos son:

| Ruta | Línea | Uso |
|---|---:|---|
| `game/src/main/kotlin/content/quest/QuestCommands.kt` | 110 | Preparación administrativa de quest. |
| `game/src/main/kotlin/content/bot/behaviour/condition/Condition.kt` | 389 | Ajuste de nivel de bot. |
| `game/src/main/kotlin/content/bot/BotCommands.kt` | 466 | Comando de bot. |
| `game/src/main/kotlin/content/entity/player/command/SkillCommands.kt` | 63,75,118 | Comandos/reset de skill. |
| `game/src/main/kotlin/content/area/misthalin/tutorial_island/TutorialRestrictions.kt` | 33 | Cap/restauración de tutorial. |

También quedan fuera el write interno `Experience.kt:34`, la lectura de save `PlayerSave.kt:278` y la copia al save `PlayerSave.kt:483`.

### 3.2 Modificadores existentes y Assistance

- `world.experienceRate=1.0`: `game/src/main/resources/game.properties:92`.
- Bonus XP Weekend usa `Experience.multiplier`: `game/src/main/kotlin/content/entity/player/stat/BonusExperience.kt:24,35`; flag en `game.properties:296`.
- Bonus de NPC/combat se incorpora a la base antes de `player.exp`: `game/src/main/kotlin/content/entity/player/combat/CombatExperience.kt:93`.
- Assistance recibe XP ya procesada por el tramo bloqueado y vuelve a llamar `player.exp`: `game/src/main/kotlin/content/social/assist/RequestAssist.kt:81`.

Ese último flujo ya puede componer dos tramos de tasa oficial/Bonus XP. La Fase 9B no debe “corregirlo” incidentalmente porque rompería equivalencia OFF. Para evitar una **tercera semántica custom o doble factor custom**, se recomienda:

1. Si la skill está bloqueada, el primer tramo conserva exactamente la fórmula oficial y **no aplica factor custom**.
2. `RequestAssist` reotorga con origen explícito `ASSISTANCE`.
3. El factor custom se aplica una sola vez, al jugador que finalmente recibe la XP, y sólo si el flag `ASSISTANCE` está ON.

### 3.3 Settings confirmado

- `Settings.load()` usa `./game.properties` y cae al recurso interno: `Settings.kt:83,92`.
- Getters tipados/defaults: `Settings.kt:35-53`.
- En arranque, el entorno se copia después del archivo: `Main.kt:129-130`.
- Existe recarga administrativa: `ServerCommands.kt:86-87`.
- `Settings.load()` agrega sobre `Properties` existentes; no limpia claves eliminadas. La recarga tampoco reaplica el entorno del arranque.

Por tanto, v1 debe documentar **editar el `game.properties` externo y reiniciar**. No se promete hot reload.

---

## 4. Inventario trazable y métricas de fuentes XP

### 4.1 Método de auditoría

Se buscaron llamadas de producción a `exp(` con receptor explícito o implícito, imports del helper, llamadas directas a `experience.add`, `experience.set` y writes al array. Cada coincidencia fue contrastada con su clase/método o helper común. No se asignó origen únicamente por nombre de carpeta.

### 4.2 Métricas finales

| Origen propuesto | Call sites | Confianza | Migración |
|---|---:|---|---|
| `TRAINING` | 219 | Alta | Explícita o mediante helper de skill probado. |
| `COMBAT` | 4 | Alta | Un cambio en `CombatExperience.grant` cubre combate normal/familiar; tres rutas directas. |
| `QUEST` | 45 | Alta | Recompensa fija/culminación, no cualquier acción situada en un quest. |
| `LAMP` | 9 | Alta | Consumible/effigy/book o reward selector equivalente. |
| `EVENT` | 25 | Alta bajo la taxonomía propuesta | Actividad, minijuego, reward periódico/canjeable. |
| `ASSISTANCE` | 1 | Alta | Reaward en `RequestAssist`. |
| `UNKNOWN` | 5 | Ambigua por diseño | No custom hasta decisión/más contexto. |
| **Total** | **308** | **303 alta / 5 ambigua** | — |

### 4.3 Inventario por familia y camino

| Origen | Skill/familia y clase/método | Evidencia | Camino a `add` | Riesgo/prioridad |
|---|---|---|---|---|
| TRAINING | Skills rutinarias: Agility 53, Construction 12, Cooking 3, Crafting 6, Farming 7, Firemaking 1, Fishing 1, Fletching 4, Herblore 1, Hunter 12, Magic 34, Mining 1, Prayer 4, Runecrafting 3, Smithing 7, Summoning 6, Thieving 3, Woodcutting 1. | Rutas `game/src/main/kotlin/content/skill/...`; ejemplos: `Woodcutting.kt:95`, `Fishing.kt:138`, `Mining.kt:125`, `Cooking.kt:143`. | llamada directa `exp` -> helper -> `Experience.add` | P1 por volumen; migrar por subpaquete y prueba de cobertura. |
| TRAINING | Magic no combat: teleports, alchemy, lunar/utilidad. | `engine/.../Teleport.kt:172`; `content/skill/magic/book/...` (34 calls) | directo | P1; no confundir teletransporte con COMBAT. |
| TRAINING | Prayer por huesos/ofrendas y utilidades. | `BoneBurying.kt:33`, `BoneOffering.kt:40`, `Ectofuntus.kt:54`, `SpiritShieldBlessing.kt:39` | directo | P1. |
| TRAINING | Agility/acciones de área y quest-specific que pagan por cada acción, no como recompensa final. | `EaglesEyries.kt:37,49,60`, `LegendsQuest.kt:66`, shortcuts listados en el apéndice. | directo | P2; regla semántica debe quedar documentada. |
| COMBAT | Attack, Strength, Defence, Constitution, Ranged, Magic; estilo controlado; familiar. `CombatExperience.grant`. | `game/src/main/kotlin/content/entity/player/combat/CombatExperience.kt:93` | todos los handlers de esa clase -> `grant` -> `player.exp` | P0: un solo cambio cubre el fan-out probado. |
| COMBAT | XP Slayer por muerte válida. | `game/src/main/kotlin/content/entity/death/NPCDeath.kt:217` | muerte -> `player.exp(Slayer, ...)` | P0; probar último kill/task. |
| COMBAT | Slayer bosses y Ghast Prayer ligada a kill. | `DesertSlayerBosses.kt:18`, `Ghast.kt:81` | directo | P1. |
| QUEST | Recompensas finales/fijas, incluida Sheep Shearer. | `FredTheFarmer.kt:312`; 45 calls detalladas abajo/apéndice. | directo | P1; flag QUEST OFF conserva reward oficial. |
| LAMP | Antique lamps, random-event lamp/book, effigies y selectores equivalentes. | `AntiqueLamp.kt:16`; `AncientEffigies.kt:40,53,88`; `RandomEventGift.kt:74`; `SurpriseExam.kt:78` | directo tras selección de skill | P1; textos muestran base. |
| EVENT | Evil Tree, Shooting Star, minijuegos, Penguin, Tears semanal, Wise Old Man, Slayer points. | `EvilTree.kt:382,548,580,665`; `Larry.kt:124`; `Juna.kt:344`; `SlayerRewards.kt:37` | directo | P1; flags OFF por default. |
| ASSISTANCE | Reaward al asistente. | `RequestAssist.kt:81` | bloqueada -> handler -> `player.exp` | P0 por riesgo de doble factor/cap. |
| UNKNOWN | Dungeoneering híbrido. | `DungeonComplete.kt:317`, `ResourceDungeons.kt:50`, `DaemonheimRewards.kt:59,112` | directo | P1: decidir TRAINING vs EVENT; mientras tanto identidad. |
| UNKNOWN | Helper data-driven de recetas. | `ItemOnItems.kt:137`; definitions `*.recipes.toml` por `game.properties:657` | receta -> `replaceItems` -> `player.exp` | P0: una misma ruta sirve skills, Sorceress's Garden y una receta de quest; requiere origen en definición/contexto. |

### 4.4 Recompensas QUEST clasificadas

Además de Sheep Shearer, las 45 concesiones QUEST quedan trazadas en:

- `ZavisticRarve.kt:747,748`; `Kaqemeex.kt:168`; `MageOfZamorak.kt:209`; `GhostsAhoy.kt:334`.
- `Grish.kt:472,473,474`; `MyrequeStranger.kt:46-50`; `DrFenkenstrain.kt:312`; `FillimanTarlock.kt:590-592`.
- `KingLathas.kt:84`; `SquireAsrol.kt:200`; `Edmond.kt:252`; `Doric.kt:151`; `CaptainBraindeath.kt:627-629`.
- `Trufitus.kt:313`; `ClayGolem.kt:219,220`; `WizardMizgog.kt:171`; `Drezel.kt:642`; `Osman.kt:116`.
- `Xenia.kt:461-465`; `FredTheFarmer.kt:312`; `LumbridgeChurch.kt:144`; `Gudrun.kt:498`; `Dororan.kt:32,91,120,142`.
- `Cook.kt:125`; `Juna.kt:245`; `Gertrude.kt:84`.

### 4.5 LAMP, EVENT, COMBAT y ASSISTANCE clasificados

**LAMP (9):**

- `NaturalHistorianRellekka.kt:48`, quest Gunnar's Ground `AntiqueLamp.kt:23`, achievement `AntiqueLamp.kt:16`.
- `SurpriseExam.kt:78`, `RandomEventGift.kt:74`, `AncientEffigies.kt:40,53,88`.
- `StrongholdOfPlayerSafetyRewards.kt:36`.

**EVENT (25):**

- Vinesweeper: `VinesweeperRewards.kt:94`, `Vinesweeper.kt:196`.
- Sorceress's Garden: `SorceressGarden.kt:85-88,130`.
- Mage Training Arena: `TelekineticTheatre.kt:84,214`, `EnchantingChamber.kt:89`, `CreatureGraveyard.kt:83`, `AlchemistsPlayground.kt:104,130`.
- Evil Tree: `EvilTree.kt:382,548,580,665`; Shooting Star: `ShootingStar.kt:64`, `ShootingStarHandler.kt:37`.
- `SlayerRewards.kt:37`, `Jim.kt:84`, `Larry.kt:124`, `Juna.kt:344`, `WiseOldMan.kt:119`, `OldMansMessage.kt:166`.

**COMBAT (4):** `CombatExperience.kt:93`, `NPCDeath.kt:217`, `DesertSlayerBosses.kt:18`, `Ghast.kt:81`.  
**ASSISTANCE (1):** `RequestAssist.kt:81`.

---

## 5. Casos híbridos y política `UNKNOWN`

| Caso | Opciones razonables | Recomendación inicial |
|---|---|---|
| Magic de combate vs teletransporte/utilidad | COMBAT para daño; TRAINING para casting utilitario. | Clasificar en el helper combat y en cada spell no combat. |
| Prayer por huesos vs Prayer por kill de Ghast | TRAINING vs COMBAT. | Mantener distinción por efecto real. |
| Summoning crafting/scroll/utilidad vs familiar que ataca | TRAINING vs COMBAT. | Familiar ofensivo ya pasa por `CombatExperience.grant`; utilidades siguen TRAINING. |
| Slayer XP por kill vs compra de XP | COMBAT vs EVENT. | `NPCDeath` COMBAT; `SlayerRewards` EVENT. El factor de skill Slayer se compone en ambos. |
| Dungeoneering completion/discovery/token exchange | Puede entenderse como TRAINING o EVENT. | `UNKNOWN` hasta decisión del propietario. |
| Mage Training Arena y otros minijuegos | EVENT aunque entrenen una skill. | EVENT en la taxonomía propuesta; flag OFF por default reduce riesgo. |
| Tears of Guthix | Reward semanal EVENT; reward de completar quest QUEST. | Separación por líneas `Juna.kt:344` y `:245`. |
| Penguin reward que abre selector de skill | LAMP por UI o EVENT por causa. | EVENT: se clasifica por causa del award, no por widget usado. |
| Quest-specific action XP | QUEST o TRAINING. | Si paga por la acción repetible/skill, TRAINING (`EaglesEyries`, `LegendsQuest`); si es reward de progreso/completar, QUEST. |
| `ItemOnItems` | Las definiciones incluyen skills, minijuego y quest. | `UNKNOWN` hasta que `ItemOnItemDefinition` o el llamador proporcione origen explícito. |
| Assistance | Origen original o ASSISTANCE. | Categoría independiente y una sola aplicación custom al receptor final. |

Regla segura: `UNKNOWN` nunca recibe factor custom, aunque aparezca una clave de configuración. Una nueva llamada que use el overload antiguo queda funcionalmente equivalente a Void y visible como deuda de migración.

---

## 6. Comparación de contratos Kotlin y recomendación

Todos los tipos y firmas de esta sección son **propuestas; no existen en el repositorio actual**.

### Alternativa A — Origen explícito opcional

Concepto:

```kotlin
enum class XpOrigin { TRAINING, COMBAT, QUEST, LAMP, EVENT, ASSISTANCE, UNKNOWN }

fun Player.exp(skill: Skill, experience: Double) // firma existente, conserva UNKNOWN
fun Player.exp(skill: Skill, experience: Double, origin: XpOrigin)

fun Experience.add(skill: Skill, experience: Double) // firma existente
fun Experience.add(skill: Skill, experience: Double, origin: XpOrigin)
```

Ventajas: mínima ruptura, migración incremental y deuda visible. Desventaja: el programador puede olvidar el tercer argumento.

**Compatibilidad Kotlin/JVM:** no debe reemplazarse la firma existente por un parámetro con default esperando compatibilidad binaria. Las extension functions de Kotlin se compilan como métodos estáticos y cambiar el descriptor rompe binarios ya compilados. Deben conservarse overloads reales; el overload antiguo delega a `UNKNOWN`.

### Alternativa B — Wrappers tipados

Concepto:

```kotlin
fun Player.trainingXp(skill: Skill, amount: Double)
fun Player.combatXp(skill: Skill, amount: Double)
fun Player.questXp(skill: Skill, amount: Double)
```

Ventajas: intención legible y fácil de detectar estáticamente. Desventajas: renombra/modifica los mismos call sites, multiplica API y no resuelve helpers data-driven como `ItemOnItems`. Sólo desplaza el trabajo si no hay un helper común.

### Alternativa C — Híbrida, recomendada

1. Overload central explícito de A, conservando el antiguo como `UNKNOWN`.
2. Usar helpers existentes como frontera: `CombatExperience.grant` transmite COMBAT una vez.
3. Wrappers tipados sólo donde reduzcan fan-out real y tengan significado estable; no crear uno por archivo.
4. Call sites aislados pasan `XpOrigin` explícito.
5. Marcar el overload antiguo como deprecado con warning después de 9B-3, no con error mientras exista deuda intencional.

### `XpAwardContext` y modificador

Para v1 basta `XpOrigin`; evita asignar un objeto en cada award. Un contexto más rico sería razonable sólo si aparece una necesidad comprobada:

```kotlin
data class XpAwardContext(val origin: XpOrigin)

fun interface ExperienceModifier {
    fun modify(player: Player, skill: Skill, origin: XpOrigin, base: Double): Double
}
```

Se recomienda un registro genérico de modificador con identidad por defecto, identificador estable y clear integrado con `Script.clear`. No consultar Koin ni parsear properties en cada award. El registro debe impedir duplicados entre mundos/tests.

### Archivos centrales mínimos propuestos

- Nuevo, engine: `.../skill/exp/XpOrigin.kt`.
- Nuevo, engine: `.../skill/exp/ExperienceModifier.kt` o registro equivalente.
- Modificar: `.../skill/exp/Experience.kt`, overloads y una llamada central.
- Modificar sólo si es necesario para lifecycle: `engine/.../Script.kt`, integrar el clear del registro.

Política custom propuesta:

- `game/src/main/kotlin/content/custom/xp/CustomXpConfig.kt`.
- `game/src/main/kotlin/content/custom/xp/CustomXpPolicy.kt`.
- `game/src/main/kotlin/content/custom/xp/CustomXpScript.kt`.
- Tests equivalentes bajo `game/src/test/kotlin/content/custom/xp/`.

No se recomienda un nuevo módulo Gradle.

---

## 7. Properties y fórmula de resolución

Las siguientes claves son **propuestas, no existentes**:

```properties
# OFF si falta o es inválido
custom.xp.enabled=false

# Cada source también está OFF si falta o es inválido
custom.xp.source.training.enabled=false
custom.xp.source.training.factor=1.0
custom.xp.source.combat.enabled=false
custom.xp.source.combat.factor=1.0
custom.xp.source.quest.enabled=false
custom.xp.source.quest.factor=1.0
custom.xp.source.lamp.enabled=false
custom.xp.source.lamp.factor=1.0
custom.xp.source.event.enabled=false
custom.xp.source.event.factor=1.0
custom.xp.source.assistance.enabled=false
custom.xp.source.assistance.factor=1.0

# Factores por Skill; ausencia = 1.0
custom.xp.skill.attack=1.0
custom.xp.skill.strength=1.0
custom.xp.skill.defence=1.0
custom.xp.skill.constitution=1.0
custom.xp.skill.slayer=1.0
custom.xp.skill.woodcutting=1.0
custom.xp.skill.fishing=1.0
custom.xp.skill.runecrafting=1.0
```

`constitution` es el nombre canónico de revisión 634. `hitpoints` no se acepta salvo decisión explícita de alias.

### Precedencia

```text
si master != true                         -> customFactor = 1.0
si origin == UNKNOWN                      -> customFactor = 1.0
si source.<origin>.enabled != true         -> customFactor = 1.0
si no                                     -> customFactor = sourceFactor * skillFactor
```

Después:

```text
effectiveTenths = trunc(
    baseXp
    * customFactor
    * 10
    * Experience.multiplier
    * world.experienceRate
)
```

OFF debe conservar la expresión oficial sin reordenar sus operaciones. ON aplica el factor custom antes de la conversión final; no hay redondeos intermedios.

### Validación

- Sólo finitos, positivos y dentro de un rango aprobado.
- Recomendación inicial: `0.01..100.0`; el máximo es decisión de producto.
- `NaN`, infinitos, cero, negativos o fuera de rango: factor `1.0` y warning único por carga.
- Master inválido: OFF. Source inválido: OFF.
- Snapshot inmutable: boolean master, array por origin, `DoubleArray` por skill; consulta O(1).
- Publicación atómica del snapshot; sin mapas, parsing ni logging por award.
- Reinicio obligatorio en v1.

### Ejemplos comprobables

Supuestos salvo indicación: `world.experienceRate=1`, Bonus XP `1`, Attack `1.5`, Woodcutting `2.0`.

| Caso | Base | Config source | Resultado custom |
|---|---:|---|---:|
| Master OFF, Attack TRAINING | 10 | cualquier factor | 10 |
| TRAINING ON 2x, Attack 1.5x | 10 | `2 * 1.5` | 30 |
| TRAINING ON 2x, Woodcutting 2x | 25 | `2 * 2` | 100 |
| COMBAT ON 1.5x, Slayer 2x | 100 | `1.5 * 2` | 300 |
| Caso anterior + Bonus 2.7 + world 2 | 100 | `1.5 * 2 * 2.7 * 2` | 1,620 |
| QUEST OFF, Sheep Shearer Crafting factor 2x | 150 | source OFF | 150 |
| QUEST ON 1.2x, Crafting 2x | 150 | `1.2 * 2` | 360 |
| LAMP OFF, lamp de 500 | 500 | source OFF | 500 |
| EVENT ON 1.5x, Mining 2x | 25 | `1.5 * 2` | 75 |
| ASSISTANCE OFF, skill 2x | 10 | source OFF | comportamiento oficial del reaward |
| ASSISTANCE ON 1.25x, skill 2x | 10 | `1.25 * 2` | 25 antes de capas oficiales del flujo |
| UNKNOWN, cualquier flag | 10 | ignorado | 10 |

### Textos, logs y acumulados

- `Player.exp` retorna `Unit`; los diálogos existentes muestran cantidades base codificadas. V1 debe mantener esos textos o aprobar una API nueva de resultado. No cambiar retorno de la firma existente.
- Cliente, `xp_counter`, `lifetime_xp`, niveles y save reciben la cantidad efectiva porque observan el único `set` final.
- `AuditLog.event(..., "exp", ..., experience)` registra hoy la base. Conservarlo para no cambiar semántica; agregar, si se aprueba, un evento custom separado con origin/factores/efectiva.
- No loguear por award en producción; usar métricas agregadas/rate-limited.
- El límite máximo y el truncamiento actual no se corrigen en esta fase.

---

## 8. Responsabilidades y puntos de integración

```text
Call site clasificado
    -> Player.exp(skill, base, origin)
    -> Experience.add(skill, base, origin)
        |
        +-- bloqueada: camino oficial, SIN custom
        |       -> Assistance reaward con origin=ASSISTANCE
        |
        +-- no bloqueada:
                -> ExperienceModifier identity/custom
                -> fórmula oficial Bonus XP + world rate
                -> un único set / evento / persistencia
```

**Engine/upstream:** enum/contexto neutral, overloads compatibles, registro identidad y punto de aplicación.  
**Custom/game:** parseo, validación, flags, snapshot y política.  
**Contenido:** sólo declarar origen; no conocer factores.  
**Datos:** no cambian en 9B-1/9B-2. Para `ItemOnItems`, una futura solución puede añadir origen validado a la definición, pero hasta entonces queda UNKNOWN.

Conceptos para C#/.NET:

- Una extension function Kotlin como `Player.exp` se parece a un método de extensión C#, pero compila a un método estático JVM.
- Los parámetros con default de Kotlin no sustituyen a overloads reales para compatibilidad binaria.
- `object` equivale aproximadamente a un singleton; Koin es el contenedor DI, pero `Experience` no se resuelve por él.
- `Script.init` registra handlers durante bootstrap; `Script.clear` es el lifecycle que debe limpiar registros estáticos en tests/mundos.

---

## 9. Matriz de pruebas y aceptación

| Prueba | Nivel/base existente | Expectativa | Severidad |
|---|---|---|---|
| Identidad sin registro | Unit, `ExperienceTableTest` | Mismos enteros, truncamiento y evento que 2.12.0. | P0 |
| Master ausente/false/inválido | Unit + WorldTest | Todo origen equivale a upstream. | P0 |
| Source OFF con skill configurada | Unit + WorldTest | QUEST/LAMP/EVENT no cambian. | P0 |
| UNKNOWN | Unit | Siempre identidad; warning/cobertura, nunca inferencia. | P0 |
| Source × skill | Unit | Fórmula y precedencia exactas para dos skills. | P0 |
| world rate + Bonus XP + custom | `ExperienceTableTest`, `BonusExperienceWorldTest` | Cada capa una vez; contador Bonus coherente. | P0 |
| Un award/un evento | MockK/WorldTest | Un `Skills.exp`, un level-up, un update cliente. | P0 |
| Bloqueo y Assistance | `RequestAssistTest` | Primer tramo sin custom; receptor usa ASSISTANCE una vez; cap preservado. | P0 |
| Combat/controlado | `CombatTest` | Attack/Strength/Defence reciben COMBAT y su skill factor; Constitución separada. | P0 |
| Familiar | WorldTest + tests familiar | Dueño recibe COMBAT una vez. | P1 |
| Slayer último kill | `SlayerTaskTest`/WorldTest | XP Slayer COMBAT, task termina una vez. | P0 |
| Sheep Shearer QUEST OFF/ON | `SheepShearerTest:176-202` | 150 base con OFF; multiplicada sólo con QUEST ON; estado/reward únicos. | P0 |
| Lamps/effigies | `AntiqueLampTest`, `AncientEffigiesTest`, `RandomEventGiftTest` | LAMP OFF conserva base; ON aplica una vez. | P1 |
| EVENT | WorldTest de Evil Tree/Shooting Star/minijuego | Sólo EVENT ON modifica. | P1 |
| `Experience.set` | Unit | Comandos/tutorial/load no pasan por custom. | P0 |
| Máximo, cero y negativo | `ExperienceTableTest` | Semántica upstream intacta. | P0 |
| Save/reload | WorldTest con temp | Persiste XP efectiva, no origin/snapshot; cero saves reales. | P0 |
| Registro/clear | ScriptTest/WorldTest | Sin duplicados entre mundos/tests. | P0 |
| Config inválida | Unit | Fallback 1.0, warning único, sin pérdida de XP. | P0 |
| Cobertura de call sites | Test estático | Detecta overload antiguo, bypass de `add`, nuevo `set` y cambios de enum. | P1 |
| Carga de scripts | WorldTest | Script custom descubierto bajo `content/custom`. | P1 |
| Rendimiento | Unit/benchmark focalizado | Consulta O(1), sin asignación/parsing/log por award. | P1 |
| Cliente rev. 634 | QA manual | XP drop, nivel y pestaña skills correctos. | P1 |

La prueba de cobertura recomendada debe mantener un manifiesto explícito de call sites pendientes `UNKNOWN`, no convertirlos automáticamente a TRAINING. Tras 9B-3 debe fallar ante una nueva llamada de dos argumentos salvo allowlist documentada.

---

## 10. Plan 9B-1, 9B-2, 9B-3 y 9C

### 9B-1 — Hook identidad

**Archivos tentativos:** `XpOrigin.kt`, contrato/registro, `Experience.kt`, quizá `Script.kt`, tests engine.  
**GO:** overload antiguo intacto, UNKNOWN identidad, equivalencia OFF, clear y bloqueo probados.  
**NO-GO:** requiere migrar persistencia/Player o cambia dos veces eventos.  
**Rollback:** revertir PR; ningún dato nuevo.  
**Prompt:** pequeño y de alta precisión, sólo motor.

### 9B-2 — Política custom OFF

**Archivos tentativos:** `content/custom/xp/*`, tests, bloque documentado en `game.properties`.  
**GO:** snapshot O(1), validación, todas las sources OFF por default, fórmula probada.  
**NO-GO:** configuración inválida puede producir cero/NaN o el policy toca saves.  
**Rollback:** `custom.xp.enabled=false` + reinicio; revert del PR si es necesario.  
**Prompt:** mediano y focalizado en config/policy.

### 9B-3 — Clasificación incremental

Orden recomendado:

1. COMBAT helper, Assistance y Sheep Shearer.
2. TRAINING de alto uso: combat no aplica; Woodcutting, Fishing, Mining, Cooking, Crafting, Agility y Magic.
3. QUEST/LAMP/EVENT.
4. Revisar cinco UNKNOWN; no forzar resolución.

**GO por lote:** cobertura aumenta, tests del dominio pasan y UNKNOWN no cambia.  
**NO-GO:** una ruta necesita inferencia por stack/package o cambia funcionalmente una misión.  
**Rollback:** revertir el lote; overload antiguo vuelve a UNKNOWN/identidad.  
**Prompt:** varios prompts repetitivos por dominio, no uno enorme de 182 archivos.

### 9C — QA seguro

- Reutilizar el repositorio actual y storage temporal/QA.
- Master OFF primero; luego una source y dos skills.
- Probar logout/reinicio, Bonus XP, world rate, Assistance, Slayer, Sheep Shearer y cliente.
- Rollback funcional: master OFF + reinicio. No quitar XP ya otorgada.

### Upgrade 2.13+

- Revisar `Experience.add`, `Player.exp`, `Skills.blocked`, `BonusExperience`, `Skill` y `PlayerSave`.
- Reejecutar inventario estático y comparar 308/182 contra el nuevo baseline.
- Resolver conflictos del hook en PR separado de migraciones de contenido.
- Medir cobertura como `calls explícitas / calls totales`; UNKNOWN se reporta aparte, nunca como cubierta.

### Riesgos principales

| Riesgo | Prioridad | Mitigación |
|---|---:|---|
| Conflicto upstream en `Experience.kt` | P0 | Una llamada central, PR aislado, test identidad. |
| Doble factor en Assistance | P0 | Suprimir custom en tramo bloqueado y aplicar ASSISTANCE al receptor. |
| Misclasificar rewards como TRAINING | P0 | UNKNOWN seguro y revisión semántica. |
| 182 archivos generan conflictos | P1 | Lotes por dominio; wrappers sólo con fan-out real. |
| ItemOnItems mezcla orígenes | P0 | UNKNOWN hasta contexto data-driven explícito. |
| Hot reload inconsistente | P1 | Reinicio v1. |
| Textos muestran base | P1 | Decisión de producto; no cambiar firma existente. |
| Rollback no revierte XP histórica | P0 | Comunicar y auditar rollout. |

---

## 11. Decisiones funcionales requeridas

1. ¿Dungeoneering completion, resource dungeon y token exchange son TRAINING, EVENT o permanecen UNKNOWN?
2. ¿Mage Training Arena y minijuegos usan EVENT, como propone este informe?
3. ¿Se aprueba que la causa domine a la UI: Penguin reward = EVENT aunque abra `skillLamp`?
4. ¿Se aprueba QUEST sólo para reward/progreso y TRAINING para acciones de skill dentro de quests?
5. ¿ASSISTANCE será categoría independiente con una sola aplicación custom al receptor final?
6. ¿Se permiten factores reductores `0.01..<1.0`?
7. ¿Máximo 100x u otro?
8. ¿Los diálogos conservan XP base o deben mostrar efectiva mediante una API nueva posterior?
9. ¿Se acepta `constitution` como única clave canónica?
10. ¿La recarga requiere reinicio en v1? Recomendación: sí.

Ninguna de estas decisiones bloquea 9B-1: UNKNOWN e identidad permiten crear el hook sin alterar gameplay.

---

## 12. Confirmación de integridad

No se modificó Kotlin, TOML, properties, Gradle, tests, scripts, `.gitignore`, ramas, commits, tags ni remotos. No se ejecutaron Gradle, builds, tests, servidor, cliente ni comandos administrativos del juego. No se leyó ni modificó `data/saves`, `data/cache`, cuentas, contraseñas, respaldos ni otra instalación.

La única escritura fue:

`C:\Juegos\Codigo\void-custom\build\reports\Fase-9A-2-Arquitectura-XP-Por-Origen.md`

El archivo está bajo `/build/`, ignorado por Git. Ninguna propuesta está implementada.

---

## Apéndice A — Manifiesto de call sites TRAINING (219)

El resto de categorías está enumerado íntegramente en 4.4 y 4.5. Este manifiesto completa la trazabilidad de TRAINING; varias líneas del mismo archivo se agrupan sólo como presentación, no como inferencia de producción.

- `engine/src/main/kotlin/world/gregs/voidps/engine/entity/character/player/Teleport.kt:172`
- `game/src/main/kotlin/content/area/asgarnia/dwarven_mines/living_rock_caverns/LivingRockCreatures.kt:68`
- `game/src/main/kotlin/content/area/asgarnia/entrana/Entrana.kt:42`
- `game/src/main/kotlin/content/area/asgarnia/port_sarim/PortSarim.kt:54`
- `game/src/main/kotlin/content/area/asgarnia/taverley/TaverleyDungeon.kt:56`
- `game/src/main/kotlin/content/area/fremennik_province/lighthouse/BasaltRock.kt:58,74`
- `game/src/main/kotlin/content/area/fremennik_province/rellekka/FremennikSlayerDungeon.kt:37,65`
- `game/src/main/kotlin/content/area/kandarin/ardougne/ArdougneDoors.kt:69`
- `game/src/main/kotlin/content/area/kandarin/ourania/OuraniaAltar.kt:56`
- `game/src/main/kotlin/content/area/kandarin/seers_village/ElementalWorkshop.kt:145,255`
- `game/src/main/kotlin/content/area/kandarin/yanille/YanilleAgilityDungeon.kt:79,146,212,237,275`
- `game/src/main/kotlin/content/area/karamja/BrimhavenShotcuts.kt:31,45,62,76,99`
- `game/src/main/kotlin/content/area/kharidian_desert/DesertHeat.kt:80`
- `game/src/main/kotlin/content/area/kharidian_desert/DesertPhoenix.kt:51`
- `game/src/main/kotlin/content/area/misthalin/edgeville/EdgevilleMonkeyBars.kt:39`
- `game/src/main/kotlin/content/area/misthalin/lumbridge/combat_hall/ArcheryTarget.kt:74`
- `game/src/main/kotlin/content/area/misthalin/varrock/VarrockDummy.kt:50`
- `game/src/main/kotlin/content/area/misthalin/zanaris/Zanaris.kt:56,82`
- `game/src/main/kotlin/content/area/morytania/mort_myre_swamp/grotto/Grotto.kt:28,49`
- `game/src/main/kotlin/content/area/morytania/port_phasmatys/WreckedGhostShip.kt:175`
- `game/src/main/kotlin/content/area/morytania/slayer_tower/SlayerTower.kt:66`
- `game/src/main/kotlin/content/area/troll_country/trollheim/Trollheim.kt:23,29,39,44,51,56,68,73,80,85,98,108,141,159`
- `game/src/main/kotlin/content/area/wilderness/abyss/AbyssObstacles.kt:80,117,152,182,210`
- `game/src/main/kotlin/content/area/wilderness/MagicAxeHut.kt:53`
- `game/src/main/kotlin/content/entity/obj/canoe/Canoes.kt:118`
- `game/src/main/kotlin/content/entity/player/equip/DragonfireShield.kt:166`
- `game/src/main/kotlin/content/quest/member/eagles_peak/EaglesEyries.kt:37,49,60`
- `game/src/main/kotlin/content/quest/member/legends_quest/LegendsQuest.kt:66`
- `game/src/main/kotlin/content/skill/agility/course/ApeAtoll.kt:49,91,149,200,252,309,312`
- `game/src/main/kotlin/content/skill/agility/course/BarbarianAdvanced.kt:28,42,57,69,80,97,101`
- `game/src/main/kotlin/content/skill/agility/course/BarbarianOutpost.kt:53,78,106,121,161,167`
- `game/src/main/kotlin/content/skill/agility/course/GnomeAdvanced.kt:35,53,86,99,101`
- `game/src/main/kotlin/content/skill/agility/course/GnomeStronghold.kt:27,40,51,60,68,78,90,108,110`
- `game/src/main/kotlin/content/skill/agility/course/WildernessCourse.kt:57,86,103,118,154,169,200`
- `game/src/main/kotlin/content/skill/agility/shortcut/LogBalance.kt:39,63,82,105`
- `game/src/main/kotlin/content/skill/agility/shortcut/Pipes.kt:53,67`
- `game/src/main/kotlin/content/skill/agility/shortcut/SteppingStones.kt:45,140,160,176,201`
- `game/src/main/kotlin/content/skill/agility/shortcut/Stiles.kt:68`
- `game/src/main/kotlin/content/skill/construction/FurnitureCreation.kt:262,335`
- `game/src/main/kotlin/content/skill/construction/HouseAttackStone.kt:108,109,110,112,113,114`
- `game/src/main/kotlin/content/skill/construction/HouseFurniture.kt:98`
- `game/src/main/kotlin/content/skill/construction/HouseRangingGames.kt:112`
- `game/src/main/kotlin/content/skill/construction/HouseStudy.kt:183`
- `game/src/main/kotlin/content/skill/construction/HouseWorkshop.kt:66`
- `game/src/main/kotlin/content/skill/cooking/Cooking.kt:143`
- `game/src/main/kotlin/content/skill/cooking/DairyChurn.kt:69`
- `game/src/main/kotlin/content/skill/cooking/Wine.kt:148`
- `game/src/main/kotlin/content/skill/crafting/Jewellery.kt:118`
- `game/src/main/kotlin/content/skill/crafting/MoltenGlass.kt:67`
- `game/src/main/kotlin/content/skill/crafting/Pottery.kt:85`
- `game/src/main/kotlin/content/skill/crafting/SilverCasting.kt:104`
- `game/src/main/kotlin/content/skill/crafting/SpinningWheel.kt:128`
- `game/src/main/kotlin/content/skill/crafting/Weaving.kt:88`
- `game/src/main/kotlin/content/skill/farming/CompostBin.kt:146,171`
- `game/src/main/kotlin/content/skill/farming/FarmingPatchPick.kt:72,109`
- `game/src/main/kotlin/content/skill/farming/FarmingPatchPlant.kt:106`
- `game/src/main/kotlin/content/skill/farming/FarmingPatchTreat.kt:57,134`
- `game/src/main/kotlin/content/skill/firemaking/Firemaking.kt:108`
- `game/src/main/kotlin/content/skill/fishing/Fishing.kt:138`
- `game/src/main/kotlin/content/skill/fletching/Bolts.kt:46`
- `game/src/main/kotlin/content/skill/fletching/Darts.kt:46`
- `game/src/main/kotlin/content/skill/fletching/FletchUnfinished.kt:98`
- `game/src/main/kotlin/content/skill/fletching/HeadlessArrows.kt:66`
- `game/src/main/kotlin/content/skill/herblore/HerbCleaning.kt:24`
- `game/src/main/kotlin/content/skill/hunter/BirdSnare.kt:145`
- `game/src/main/kotlin/content/skill/hunter/BoxTrap.kt:197`
- `game/src/main/kotlin/content/skill/hunter/Butterfly.kt:64,72`
- `game/src/main/kotlin/content/skill/hunter/DeadfallTrap.kt:196`
- `game/src/main/kotlin/content/skill/hunter/Falconry.kt:194`
- `game/src/main/kotlin/content/skill/hunter/Impling.kt:107,109`
- `game/src/main/kotlin/content/skill/hunter/KebbitTracking.kt:241`
- `game/src/main/kotlin/content/skill/hunter/MagicBox.kt:282`
- `game/src/main/kotlin/content/skill/hunter/NetTrap.kt:246`
- `game/src/main/kotlin/content/skill/hunter/Pitfall.kt:181`
- `game/src/main/kotlin/content/skill/magic/book/lunar/BakePie.kt:65,66`
- `game/src/main/kotlin/content/skill/magic/book/lunar/CureGroup.kt:25`
- `game/src/main/kotlin/content/skill/magic/book/lunar/CureMe.kt:28`
- `game/src/main/kotlin/content/skill/magic/book/lunar/CureOther.kt:36`
- `game/src/main/kotlin/content/skill/magic/book/lunar/CurePlant.kt:39`
- `game/src/main/kotlin/content/skill/magic/book/lunar/Dream.kt:28`
- `game/src/main/kotlin/content/skill/magic/book/lunar/EnergyTransfer.kt:52`
- `game/src/main/kotlin/content/skill/magic/book/lunar/FertileSoil.kt:45`
- `game/src/main/kotlin/content/skill/magic/book/lunar/HealGroup.kt:36`
- `game/src/main/kotlin/content/skill/magic/book/lunar/HealOther.kt:41`
- `game/src/main/kotlin/content/skill/magic/book/lunar/Humidify.kt:35`
- `game/src/main/kotlin/content/skill/magic/book/lunar/HunterKit.kt:47`
- `game/src/main/kotlin/content/skill/magic/book/lunar/MagicImbue.kt:28`
- `game/src/main/kotlin/content/skill/magic/book/lunar/MonsterExamine.kt:39`
- `game/src/main/kotlin/content/skill/magic/book/lunar/NPCContact.kt:63`
- `game/src/main/kotlin/content/skill/magic/book/lunar/PlankMake.kt:49`
- `game/src/main/kotlin/content/skill/magic/book/lunar/PotionShare.kt:89`
- `game/src/main/kotlin/content/skill/magic/book/lunar/SpellbookSwap.kt:73`
- `game/src/main/kotlin/content/skill/magic/book/lunar/StatSpy.kt:41`
- `game/src/main/kotlin/content/skill/magic/book/lunar/StringJewellery.kt:59,60`
- `game/src/main/kotlin/content/skill/magic/book/lunar/SuperglassMake.kt:62,63`
- `game/src/main/kotlin/content/skill/magic/book/lunar/Vengeance.kt:36`
- `game/src/main/kotlin/content/skill/magic/book/lunar/VengeanceOther.kt:55`
- `game/src/main/kotlin/content/skill/magic/book/modern/Alchemy.kt:74`
- `game/src/main/kotlin/content/skill/magic/book/modern/BonesTo.kt:76`
- `game/src/main/kotlin/content/skill/magic/book/modern/Charge.kt:33`
- `game/src/main/kotlin/content/skill/magic/book/modern/EnchantCrossbowBolt.kt:101`
- `game/src/main/kotlin/content/skill/magic/book/modern/EnchantJewellery.kt:74`
- `game/src/main/kotlin/content/skill/magic/book/modern/OrbCharging.kt:51`
- `game/src/main/kotlin/content/skill/magic/book/modern/TelekineticGrab.kt:57`
- `game/src/main/kotlin/content/skill/magic/book/modern/TeleportOther.kt:48`
- `game/src/main/kotlin/content/skill/mining/Mining.kt:125`
- `game/src/main/kotlin/content/skill/prayer/bone/BoneBurying.kt:33`
- `game/src/main/kotlin/content/skill/prayer/bone/BoneOffering.kt:40`
- `game/src/main/kotlin/content/skill/prayer/bone/Ectofuntus.kt:54`
- `game/src/main/kotlin/content/skill/prayer/SpiritShieldBlessing.kt:39`
- `game/src/main/kotlin/content/skill/runecrafting/Runecrafting.kt:85,127`
- `game/src/main/kotlin/content/skill/runecrafting/Tiaras.kt:44`
- `game/src/main/kotlin/content/skill/smithing/Anvil.kt:195`
- `game/src/main/kotlin/content/skill/smithing/Cannonballs.kt:68`
- `game/src/main/kotlin/content/skill/smithing/Furnace.kt:149,196`
- `game/src/main/kotlin/content/skill/smithing/SpiritShieldSigils.kt:73`
- `game/src/main/kotlin/content/skill/smithing/SuperheatItem.kt:48,49`
- `game/src/main/kotlin/content/skill/summoning/familiar/Pyrelord.kt:118`
- `game/src/main/kotlin/content/skill/summoning/Forager.kt:89`
- `game/src/main/kotlin/content/skill/summoning/Summoning.kt:305,370`
- `game/src/main/kotlin/content/skill/summoning/SummoningCrafting.kt:129,160`
- `game/src/main/kotlin/content/skill/thieving/Pickpocketing.kt:108`
- `game/src/main/kotlin/content/skill/thieving/Stalls.kt:89`
- `game/src/main/kotlin/content/skill/thieving/TrapChests.kt:92`
- `game/src/main/kotlin/content/skill/woodcutting/Woodcutting.kt:95`
