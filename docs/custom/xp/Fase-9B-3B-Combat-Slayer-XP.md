# RuneScape Custom — Fase 9B-3B

## Clasificación de XP de combate, Constitution y Slayer

Fecha: 24 de noviembre de 2026
Repositorio: `C:\Juegos\Codigo\void-custom`
Motor: Void 2.12.0
Cliente objetivo: RuneScape revisión 634

## 1. Resumen ejecutivo

Se clasificaron como `XpOrigin.COMBAT` las cuatro fronteras de experiencia identificadas por la arquitectura 9A-2:

1. `CombatExperience.grant`, que centraliza Attack, Strength, Defence, Constitution, Ranged, Magic ofensiva y ataques de familiares.
2. `NPCDeath.slay`, para Slayer al derrotar un NPC elegible de la tarea activa.
3. `DesertSlayerBosses`, para la recompensa Slayer de los cuatro bosses especiales del dungeon de Pollnivneach.
4. `Ghast`, para los 30 puntos de Prayer ligados específicamente a derrotar un Ghast.

No se modificó ninguna fórmula de XP, daño, precisión, defensa, velocidad, elegibilidad Slayer, puntos Slayer, drop, NPC, TOML, configuración o persistencia. Cada cambio de producción importa `XpOrigin` y añade el argumento `XpOrigin.COMBAT` a una llamada existente de `exp`.

La compra de XP Slayer con puntos continúa usando la llamada de dos argumentos y, por tanto, `UNKNOWN`. La magia utilitaria, Assistance y todas las categorías ajenas a combate permanecen sin cambios.

## 2. Veredicto

**GO.**

Los criterios de 9B-3B están cumplidos:

- Las fuentes de combate auditadas usan `COMBAT` sin alterar la XP base.
- Master OFF y COMBAT OFF conservan identidad.
- Los factores se aplican una sola vez cuando ambos switches están ON.
- El reparto melee, controlado, Ranged, Magic y Constitution permanece correcto.
- Los NPC sin `combat_exp` continúan sin conceder XP.
- Slayer conserva elegibilidad, decremento, finalización y puntos.
- La última muerte de una tarea sólo completa y recompensa una vez.
- Los familiares conceden la XP al propietario sin duplicación.
- La compra de XP Slayer con puntos no fue reclasificada.
- Assistance y skills bloqueadas conservan el hook previo.
- Woodcutting, Fishing y Sheep Shearer no presentan regresiones.
- Formato, pruebas focalizadas y suites completas pasaron sin fallos.
- Los cambios de producción son cuatro modificaciones locales y auditables.

## 3. Baseline y estado Git inicial

| Elemento | Valor confirmado |
|---|---|
| Rama | `feature/xp-origin-combat-slayer` |
| Commit baseline | `32151cba91ed20ebcc6c9225bfbf2170a9b8526b` |
| Mensaje baseline | `Merge pull request #6 from goldregulusleo-hub/feature/xp-origin-training-pilot` |
| Commit del piloto 9B-3A | `55b4f2d08c56cbb9b87ddb1347124781060a234f` |
| Rama estable protegida | `stable/2.12.0` |
| Estado inicial | Limpio |
| Commit de entrega | Commit local descendiente del baseline con asunto `feat(game): classify combat and slayer XP origins` |

El hash del commit de entrega se informa en el cierre externo. No se incrusta dentro del documento versionado porque este archivo forma parte del contenido que determina ese mismo hash.

No se creó otra rama. No se modificaron stable, tags, remotos o versiones. No se realizó push, merge o PR.

## 4. Rutas y métodos auditados

### 4.1 Contrato central existente

| Ruta | Símbolo | Resultado de auditoría |
|---|---|---|
| `engine/src/main/kotlin/world/gregs/voidps/engine/entity/character/player/skill/exp/Experience.kt` | `Experience.add` | La política custom sólo se consulta para origen explícito; luego se aplican décimas, Bonus XP, tasa mundial y truncamiento. No se modificó. |
| `engine/src/main/kotlin/world/gregs/voidps/engine/entity/character/player/skill/exp/XpOrigin.kt` | `XpOrigin.COMBAT` | Origen ya existente; no se rediseñó. |
| `game/src/main/kotlin/content/custom/xp/CustomXpPolicy.kt` | `CustomXpPolicy.modify` | Reutilizado sin cambios. |
| `game/src/main/kotlin/content/custom/xp/CustomXpConfig.kt` | `CustomXpSnapshot.factor` | Reutiliza master, switch COMBAT, factor de origen y factor por skill. |
| `game/src/main/resources/game.properties` | `custom.xp.*` | Permanece OFF por defecto y no fue modificado. |

### 4.2 Combate normal y familiares

Ruta: `game/src/main/kotlin/content/entity/player/combat/CombatExperience.kt`.

`CombatExperience` recibe `CombatAttack` sólo cuando el daño es positivo. Sus fórmulas existentes permanecen intactas:

| Tipo/estilo | XP base antes de bonus del objetivo |
|---|---|
| Cannon | Ranged `damage / 5.0`; sin Constitution |
| Magic ofensiva | XP base del spell + `damage / 5.0` |
| Magic defensiva | Magic base + `damage / 7.5`; Defence `damage / 10.0` |
| Melee accurate | Attack `damage / 2.5` |
| Melee aggressive | Strength `damage / 2.5` |
| Melee controlled | Attack, Strength y Defence, cada una `damage / 7.5` |
| Melee defensive | Defence `damage / 2.5` |
| Dragonfire de jugador | Magic `damage / 7.5`; Defence `damage / 10.0` |
| Ranged normal | Ranged `damage / 2.5` |
| Ranged long range | Ranged y Defence, cada una `damage / 5.0` |
| Constitution | `damage / 7.5`, salvo el cannon |

Todos los caminos anteriores convergen en `CombatExperience.grant`. Allí se conserva:

- El rechazo de NPC con `combat_exp = false`.
- El multiplicador `calcBonus(target)` derivado de niveles/defensas del NPC o combat level del jugador.
- La misma cantidad final entregada a `Player.exp`.

El único cambio es transmitir `XpOrigin.COMBAT` en esa entrega central.

Los ataques de familiares `*_familiar*` ya reutilizaban `grant`: resuelven `ownerIndex`, distribuyen XP según tipo/estilo del propietario y añaden Constitution. Por ello una sola modificación central clasifica también estas concesiones sin tocar Summoning crafting, scrolls o utilidades.

### 4.3 Slayer por tarea

Ruta: `game/src/main/kotlin/content/entity/death/NPCDeath.kt`, método `slay`.

Se confirmó el orden original:

1. La tarea no puede ser `nothing`.
2. La categoría del NPC debe coincidir con la tarea.
3. `slayer_xp` debe ser distinto de cero.
4. Se concede XP Slayer.
5. Se decrementa el contador.
6. Si llega a cero se actualizan streak, puntos, contador y `slayer_target`.

Sólo el paso 4 transmite ahora `XpOrigin.COMBAT`. No se modificaron categorías, datos recuperados, correcciones del fork, cálculo de puntos ni mensajes.

La atribución previa de una muerte de familiar a su propietario ocurre en `NPCDeath` antes de llamar a `slay`; permanece intacta.

### 4.4 Bosses Slayer especiales

Ruta: `game/src/main/kotlin/content/area/kharidian_desert/pollnivneach/dungeon/DesertSlayerBosses.kt`.

Los NPC `monstrous_cave_crawler`, `turoth_mightiest`, `basilisk_boss` y `kurask_overlord` conceden 1.000 XP Slayer al killer cuando se dispara su `npcDespawn`, además de marcar `killed_<id>`, devolver al jugador y mostrar el diálogo. La cantidad y los efectos laterales se conservan; sólo la XP se etiqueta como COMBAT.

### 4.5 Prayer por Ghast

Ruta: `game/src/main/kotlin/content/area/morytania/mort_myre_swamp/Ghast.kt`.

Los 30 XP de Prayer sólo se conceden en el handler `npcDeath("ghast")` al killer jugador. La arquitectura 9A-2 distinguió esta recompensa por kill de la Prayer rutinaria por huesos/ofrendas. Se clasifica como COMBAT y se preserva el progreso de Nature Spirit.

## 5. Call sites clasificados

| Ruta | Símbolo/call site | Cambio |
|---|---|---|
| `game/src/main/kotlin/content/entity/player/combat/CombatExperience.kt` | `CombatExperience.grant` | `player.exp(skill, experience * calcBonus(target), XpOrigin.COMBAT)` |
| `game/src/main/kotlin/content/entity/death/NPCDeath.kt` | `NPCDeath.slay` | `player.exp(Skill.Slayer, slayerExp, XpOrigin.COMBAT)` |
| `game/src/main/kotlin/content/area/kharidian_desert/pollnivneach/dungeon/DesertSlayerBosses.kt` | handler `npcDespawn` | `killer.exp(Skill.Slayer, 1000.0, XpOrigin.COMBAT)` |
| `game/src/main/kotlin/content/area/morytania/mort_myre_swamp/Ghast.kt` | handler `npcDeath("ghast")` | `killer.exp(Skill.Prayer, 30.0, XpOrigin.COMBAT)` |

No existen otras referencias de producción a `XpOrigin.COMBAT` al cerrar la fase.

## 6. Archivos modificados y creados

### Producción

- `game/src/main/kotlin/content/entity/player/combat/CombatExperience.kt`.
- `game/src/main/kotlin/content/entity/death/NPCDeath.kt`.
- `game/src/main/kotlin/content/area/kharidian_desert/pollnivneach/dungeon/DesertSlayerBosses.kt`.
- `game/src/main/kotlin/content/area/morytania/mort_myre_swamp/Ghast.kt`.

### Pruebas

- `game/src/test/kotlin/content/custom/xp/CombatXpOriginTest.kt`: 18 casos nuevos.

### Documentación

- `docs/custom/xp/Fase-9B-3B-Combat-Slayer-XP.md`: informe versionado.
- `build/reports/Fase-9B-3B-Combat-Slayer-XP.md`: copia operativa idéntica e ignorada por Git.
- `docs/custom/xp/Fase-9C-1-QA-Funcional-Training.md`: preservación de QA manual del propietario para 9B-3A.

No se modificaron engine, `game.properties`, TOML, GROML, persistencia, formatos de guardado, definiciones NPC, drops, cliente o servidor principal.

## 7. Justificación de las modificaciones upstream

`CombatExperience.grant` es la frontera semántica más estrecha y estable: recibe una cantidad ya calculada por cada estilo, verifica `combat_exp` y aplica el bonus del objetivo. Etiquetar aquí evita modificar docenas de handlers, incluye familiares y excluye magia utilitaria que no pasa por combate.

Las otras tres concesiones no pasan por `CombatExperience.grant`; son recompensas directas por una muerte elegible. Se modificaron localmente porque crear un nuevo wrapper o rediseñar Death/Slayer aumentaría el alcance sin aportar seguridad.

## 8. Escenarios OFF y ON

Configuración sintética principal de las pruebas:

```properties
custom.xp.enabled=true
custom.xp.source.combat.enabled=true
custom.xp.source.combat.factor=2.0
custom.xp.skill.attack=1.5
custom.xp.skill.strength=2.0
custom.xp.skill.defence=1.0
custom.xp.skill.constitution=1.0
custom.xp.skill.ranged=2.0
custom.xp.skill.magic=2.0
custom.xp.skill.prayer=2.0
custom.xp.skill.slayer=3.0
```

Factores efectivos: Attack 3×, Strength 4×, Defence 2×, Constitution 2×, Ranged 4×, Magic 4×, Prayer por Ghast 4× y Slayer 6×.

| Escenario | Resultado observado |
|---|---|
| Master OFF, COMBAT ON | 25.0 Attack base permanece 25.0 (`250` interno). |
| Master ON, COMBAT OFF | 25.0 Attack base permanece 25.0 (`250` interno). |
| Master y COMBAT ON | Se aplican factor COMBAT × factor de skill exactamente una vez. |
| Otros orígenes | TRAINING, QUEST, LAMP, EVENT y UNKNOWN permanecen identidad con sólo COMBAT activo. |
| Bonus y tasa mundial | `25 × 2 × 1.5 × 2 × 2 = 300 XP` (`3000` interno). |
| Decimales | `25 × 1.5 × 0.33 = 12.375`; se conserva truncamiento a 12.3 XP (`123`). |

## 9. Reparto de experiencia por estilo

### Controlled melee

Con 75 de daño, la base es 10 XP para Attack, Strength y Defence, más 10 Constitution. Se observaron:

- Attack: `10 × 2 × 1.5 = 30 XP` (`300`).
- Strength: `10 × 2 × 2 = 40 XP` (`400`).
- Defence: `10 × 2 × 1 = 20 XP` (`200`).
- Constitution: `10 × 2 × 1 = 20 XP` (`200`).

### Ranged

Con 50 de daño normal:

- Ranged base 20; efectiva `20 × 2 × 2 = 80 XP` (`800`).
- Constitution base `50 / 7.5`; efectiva 13.333..., truncada a `133` unidades internas.

### Magic ofensiva

`wind_strike` tiene 5.5 XP base en datos. Con 75 de daño se añaden 15 XP:

- Magic: `(5.5 + 15) × 2 × 2 = 82 XP` (`820`).
- Constitution: `10 × 2 = 20 XP` (`200`).

Un evento con daño cero no concedió XP. La magia utilitaria no usa `CombatExperience` y no fue reclasificada.

## 10. Slayer, Constitution y familiares

### Slayer

- Lesser demon elegible: `79 × 2 × 3 = 474 XP` (`4740`) y decremento de tarea una vez.
- Lesser demon fuera de la categoría activa: 0 XP y ningún decremento.
- Última muerte: 474 XP, task termina, streak 1, un punto Mazchna y un único incremento de tareas completadas; repetir `slay` no duplica nada.
- Boss especial: `1000 × 2 × 3 = 6000 XP` (`60000`) y flag de boss intacto.
- Compra por 400 puntos: 10.000 XP sin factor COMBAT y descuento exacto de 400 puntos.

### Constitution

Continúa siendo una concesión independiente posterior a las skills ofensivas. No se fusionó con el award principal y no se añadió al cannon, que conserva su excepción original.

### Familiares

Un `spirit_wolf_familiar` con 75 de daño melee concedió a su propietario:

- Attack: base 30, efectiva 90 XP (`900`).
- Constitution: base 10, efectiva 20 XP (`200`).

Los valores exactos prueban una sola aplicación. Además, la regresión `SlayerTaskTest` confirma que una muerte realizada por familiar acredita la tarea al propietario.

### Assistance y bloqueos

No se modificó `RequestAssist`. Las pruebas existentes `CustomXpIntegrationTest` y `RequestAssistTest` confirman que skills bloqueadas omiten la política del tramo original y Assistance conserva su comportamiento/cap sin doble factor.

## 11. Resultados de pruebas

### 11.1 Suite nueva

`CombatXpOriginTest`: 18 tests, 0 fallos, 0 errores, 0 omitidos.

Cobertura nueva:

- Master OFF y COMBAT OFF.
- Controlled melee, Ranged, Magic y Constitution.
- Cero daño.
- Bonus XP, tasa mundial, decimales y truncamiento.
- Aislamiento de otros orígenes y UNKNOWN.
- Un solo evento de XP.
- NPC con `combat_exp = false`.
- Slayer elegible, no elegible y último kill.
- Familiar ofensivo.
- Boss Slayer especial.
- Ghast Prayer.
- Compra de XP Slayer fuera de COMBAT.

### 11.2 Focalizado ampliado

Resultado: 301 tests, 0 fallos, 0 errores, 0 omitidos.

Incluyó Custom XP, Slayer, familiares, Pollnivneach, Freaky Forester, NPC despawn, Bonus XP, level-up, Assistance, Woodcutting, Fishing y Sheep Shearer.

### 11.3 Suites completas

| Control | Tests | Fallos | Errores | Omitidos | Resultado |
|---|---:|---:|---:|---:|---|
| `:game:spotlessCheck` | — | — | — | — | Aprobado |
| `:engine:test --rerun-tasks` | 1.444 | 0 | 0 | 3 | Aprobado; 16 tareas ejecutadas |
| `:game:test` | 4.746 | 0 | 0 | 1 | Aprobado |

Baseline 9B-3A: engine 1.444 y game 4.728. Engine no cambia. Game aumenta exactamente 18 por los 18 tests nuevos.

## 12. Comandos ejecutados

```powershell
.\gradlew.bat :game:spotlessApply :game:test --tests "content.custom.xp.CombatXpOriginTest"
```

```powershell
.\gradlew.bat --no-daemon :game:cleanTest :game:test `
  --tests "content.custom.xp.*" `
  --tests "content.skill.slayer.*" `
  --tests "content.skill.summoning.Familiar*" `
  --tests "content.skill.summoning.FamineCastTest" `
  --tests "content.entity.death.NPCDespawnTest" `
  --tests "content.activity.event.random.FreakyForesterTest" `
  --tests "content.area.kharidian_desert.pollnivneach.dungeon.PollnivneachDungeonTest" `
  --tests "content.entity.player.stat.BonusExperienceTest" `
  --tests "content.entity.player.stat.BonusExperienceWorldTest" `
  --tests "content.entity.player.dialogue.type.LevelUpTest" `
  --tests "content.entity.player.dialogue.type.LevelUpInteractTest" `
  --tests "content.social.assist.RequestAssistTest" `
  --tests "content.skill.woodcutting.WoodcuttingTest" `
  --tests "content.skill.fishing.FishingTest" `
  --tests "content.quest.miniquest.sheep_shearer.SheepShearerTest"
```

```powershell
.\gradlew.bat --no-daemon :game:spotlessCheck :game:test
.\gradlew.bat --no-daemon :engine:test --rerun-tasks
```

Los conteos se extrajeron de `engine/build/test-results/test/TEST-*.xml` y `game/build/test-results/test/TEST-*.xml`.

Warnings no bloqueantes:

- Flags Kotlin obsoletos/redundantes (`-Xjvm-default`, `-Xcontext-parameters`, `-Xinline-classes`).
- Usos preexistentes que requieren opt-in o están deprecados, incluidos BZIP2 y `sumBy`.
- Gradle advierte incompatibilidad futura con Gradle 10 por funciones obsoletas.
- La JVM advierte sobre class-data sharing con instrumentación.

No hubo warnings nuevos atribuibles a los cuatro cambios de clasificación.

## 13. Persistencia y regresiones

- No se añadieron campos al jugador.
- No se modificó `Experience.add`, `Experience.set` o el `IntArray` persistido.
- No se cambió el formato de guardado ni se requiere migración.
- La XP histórica no se recalcula.
- No se accedió a personajes reales, saves o respaldos.
- Woodcutting y Fishing conservaron sus pruebas TRAINING.
- Sheep Shearer conservó sus 12 pruebas funcionales.
- Assistance, Bonus XP y level-up conservaron sus regresiones.
- El servidor principal `C:\Juegos\2011Scape\Servidor\void-2.12.0` no fue accedido ni iniciado.

El documento `Fase-9C-1-QA-Funcional-Training.md` identifica expresamente sus resultados como reportados por el propietario y no como pruebas ejecutadas por Codex.

## 14. Riesgos y limitaciones

| Riesgo/limitación | Evaluación y decisión |
|---|---|
| Prayer por Ghast no es Prayer rutinaria | Se clasifica COMBAT por ser reward directo de kill y por la decisión explícita de 9A-2; huesos/ofrendas siguen sin migrar. |
| Bosses usan `npcDespawn` | Se conserva la semántica existente; sólo se etiqueta la recompensa ya otorgada al killer. |
| Especiales de familiar | Quedan cubiertos cuando emiten el `CombatAttack` normal; habilidades utilitarias no se clasifican. |
| Magic híbrida | Sólo la Magic que atraviesa `CombatExperience` es COMBAT; teleports, alchemy y utilidad permanecen UNKNOWN/TRAINING pendiente. |
| Compra Slayer | Permanece UNKNOWN deliberadamente hasta la fase EVENT; se prueba que COMBAT no la afecta. |
| Cobertura manual COMBAT | No se inició servidor/cliente; la evidencia de esta fase es automatizada. |
| Factores altos | La política existente permite hasta 100; esta fase no cambia límites ni máximos. |

## 15. Rollback

El rollback funcional es pequeño:

1. Revertir el commit local de 9B-3B.
2. Las cuatro llamadas vuelven a la sobrecarga de dos argumentos (`UNKNOWN`).
3. Eliminar la suite y documentos de esta fase si se desea retirar también la evidencia.

No existe migración de datos que revertir, configuración persistente nueva ni cambio de esquema.

## 16. Estado Git final y recomendación

Antes del commit se verificó:

- Rama correcta y baseline esperado.
- `git diff --check` limpio.
- Exactamente cuatro referencias de producción a `XpOrigin.COMBAT`.
- Ningún archivo ajeno al alcance.
- Informe operativo ignorado por `/build/` y copia versionada idéntica.
- Suites y formato aprobados.

El commit se crea localmente después de congelar este informe. El árbol versionado debe quedar limpio; no se realiza push.

Para ampliar fuentes de XP se recomienda una fase separada y semántica, no una migración masiva por carpetas. El siguiente lote debería seleccionar una familia pequeña de TRAINING o QUEST con helper común y pruebas propias. Magic utilitaria, Prayer rutinaria, Slayer comprado y otros rewards híbridos deben conservar sus categorías diferenciadas.

La Fase 9B-3B se detiene aquí para revisión del propietario.
