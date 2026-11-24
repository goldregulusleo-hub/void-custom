# RuneScape Custom — Fase 9B-3A

## Piloto de clasificación de experiencia `TRAINING`

Fecha: 23 de noviembre de 2026
Repositorio: `C:\Juegos\Codigo\void-custom`
Motor: Void 2.12.0
Cliente objetivo: RuneScape revisión 634

## 1. Resumen ejecutivo

Se completó el primer piloto de clasificación real por origen sobre el contrato central de experiencia de las fases 9B-1 y 9B-2. El cambio clasifica exclusivamente como `XpOrigin.TRAINING` las recompensas normales de:

- Woodcutting, en `Woodcutting.chopDown`.
- Fishing, en `Fishing.fish`.

No se amplió la clasificación a recompensas de quests, eventos, familiares, obstáculos, combate, Slayer ni otras habilidades. Las fórmulas oficiales, probabilidades de éxito, tiempos, consumos, inventario, agotamiento de recursos, Bonus XP, `world.experienceRate`, eventos y persistencia permanecen en su flujo existente.

El cambio de producción se limita a importar `XpOrigin` y pasar `XpOrigin.TRAINING` a dos llamadas ya existentes de `Player.exp`. No se modificó el núcleo del motor, la política custom, la configuración, TOML, GROML, persistencia ni datos del cliente.

## 2. Veredicto

**GO.**

El piloto satisface el alcance de 9B-3A:

- Sólo dos call sites de producción fueron clasificados.
- Los defaults OFF conservan exactamente la XP oficial.
- Los factores de origen y habilidad se componen una sola vez.
- Las rutas fallidas no otorgan XP ni recursos.
- La obtención y consumo de recursos no cambia.
- `UNKNOWN` y los call sites no migrados conservan identidad.
- Bonus XP y tasa mundial siguen componiéndose después de la política custom.
- El truncamiento original a décimas se conserva.
- El award exitoso emite un solo evento de experiencia.
- No hay cambios de esquema o serialización.
- Formato, pruebas focalizadas y suites completas finalizaron sin fallos.

## 3. Baseline y protección Git

| Elemento | Valor confirmado |
|---|---|
| Rama de trabajo | `feature/xp-origin-training-pilot` |
| Commit inicial | `9aff342414cc9593022ed369d608747ddaaba297` |
| Mensaje inicial | `Merge pull request #5 from goldregulusleo-hub/feature/custom-xp-policy` |
| Política previa | `ad3535f661225a701c8887c26f39ec853b06a1de` — `feat(game): add configurable custom XP policy` |
| Referencia del motor | Void 2.12.0 |
| Estado inicial | Limpio |
| Commit de entrega | Commit local único descendiente del baseline, con asunto `feat(game): classify woodcutting and fishing XP as training` |

El hash del commit de entrega se informa externamente junto con el cierre de la fase; no se incrusta en el propio archivo versionado porque el contenido participa en el cálculo de ese mismo hash.

No se modificaron ramas estables, tags, remotos o protecciones. No se realizó push, merge, rebase, tag ni PR.

## 4. Alcance auditado

### 4.1 Contrato central ya existente

| Ruta | Símbolo | Evidencia relevante |
|---|---|---|
| `engine/src/main/kotlin/world/gregs/voidps/engine/entity/character/player/skill/exp/XpOrigin.kt` | `enum class XpOrigin` | Declara `TRAINING`, `COMBAT`, `QUEST`, `LAMP`, `EVENT`, `ASSISTANCE` y `UNKNOWN`. |
| `engine/src/main/kotlin/world/gregs/voidps/engine/entity/character/player/skill/exp/Experience.kt` | `Experience.add(skill, experience, origin)` | Omite la política para `UNKNOWN`; para origen explícito llama a `ExperienceModifiers.modify`, después aplica décimas, multiplicador del jugador y `world.experienceRate`, y finalmente trunca con `toInt()`. |
| `engine/src/main/kotlin/world/gregs/voidps/engine/entity/character/player/skill/exp/Experience.kt` | `Player.exp(skill, experience, origin)` | Sobrecarga/extensión utilizada por el piloto. La versión de dos argumentos sigue resolviendo a `UNKNOWN`. |
| `engine/src/main/kotlin/world/gregs/voidps/engine/entity/character/player/skill/exp/ExperienceModifier.kt` | `ExperienceModifier`, `ExperienceModifiers` | Contrato y registro de modificadores ya implementados en 9B-1. |
| `game/src/main/kotlin/content/custom/xp/CustomXpConfig.kt` | `CustomXpConfig`, `CustomXpSnapshot.factor` | Master switch, switch/factor por origen, factor por habilidad, validación `0.01..100.0` e identidad segura. |
| `game/src/main/kotlin/content/custom/xp/CustomXpPolicy.kt` | `CustomXpPolicy.modify` | Multiplica la base por el factor compuesto y revierte a la base si el resultado no es positivo o finito. |
| `game/src/main/resources/game.properties` | `custom.xp.*` | Defaults OFF; `training.factor`, `woodcutting` y `fishing` permanecen en `1.0`. |

### 4.2 Woodcutting

Ruta de producción: `game/src/main/kotlin/content/skill/woodcutting/Woodcutting.kt`.

Símbolos confirmados:

- `Woodcutting.chopDown` valida árbol, nivel, espacio o banking de Evil Tree, hacha y ciclo de acción.
- `Woodcutting.success` conserva el cálculo de probabilidad existente.
- En la rama exitosa, `val xp = log.int("xp") / 10.0` conserva la conversión de datos a XP real.
- `tryDropNest`, `addLog` y `deplete` se ejecutan en el mismo orden posterior al award.
- El único cambio de comportamiento intencional es `player.exp(Skill.Woodcutting, xp, XpOrigin.TRAINING)`.

Dato de prueba confirmado en `data/skill/woodcutting/log.tables.toml`, fila `[.logs]`:

- `xp = 250`, que el código divide entre diez: 25.0 XP oficial.
- `deplete_rate = 1000`, suficiente para comprobar que el árbol normal pasa a `tree_stump` después del corte exitoso bajo el random de pruebas.

### 4.3 Fishing

Ruta de producción: `game/src/main/kotlin/content/skill/fishing/Fishing.kt`.

Símbolos confirmados:

- `Fishing.fish` conserva validaciones de inventario, spot móvil, nivel, aparejo, carnada y tiempos.
- La fila seleccionada conserva `val experience = row.int("xp")` y su comprobación de nivel/probabilidad.
- La carnada se consume antes del award, como en el flujo original.
- `addCatch` se ejecuta después del award en el mismo orden.
- El único cambio de comportamiento intencional es `player.exp(Skill.Fishing, experience.toDouble(), XpOrigin.TRAINING)`.

Dato de prueba confirmado en `data/skill/fishing/fishing.tables.toml`, fila `[.raw_trout]`:

- `level = 20`.
- `xp = 50`.
- `bait = "feather"`.

### 4.4 Correspondencia con el diseño 9A-2

El manifiesto de `docs/custom/xp/Fase-9A-2-Arquitectura-XP-Por-Origen.md` ya clasificaba los call sites originales de Fishing y Woodcutting como entrenamiento normal. El piloto materializa únicamente esas dos entradas y no reinterpreta rutas ambiguas.

## 5. Cambios implementados

### 5.1 Producción

| Ruta | Cambio |
|---|---|
| `game/src/main/kotlin/content/skill/woodcutting/Woodcutting.kt` | Importa `XpOrigin` y añade `XpOrigin.TRAINING` al único award normal del script. |
| `game/src/main/kotlin/content/skill/fishing/Fishing.kt` | Importa `XpOrigin` y añade `XpOrigin.TRAINING` al único award normal del script. |

No se alteraron fórmulas, constantes, TOML, probabilidades, requisitos, animaciones, temporización, consumo, drops o lógica de agotamiento.

### 5.2 Pruebas

| Ruta | Cambio |
|---|---|
| `game/src/test/kotlin/content/custom/xp/TrainingXpPilotTest.kt` | Nueva suite de integración del piloto con 13 casos. |

La suite usa las interacciones reales de mundo de las dos habilidades: un objeto `tree` con `bronze_hatchet`, y el NPC `fishing_spot_lure_bait_lumbridge` con `fly_fishing_rod` y `feather`. No simula la concesión principal del piloto mediante una llamada directa, salvo los casos deliberados que demuestran identidad de `UNKNOWN` y call sites no clasificados.

### 5.3 Documentación

| Ruta | Seguimiento Git |
|---|---|
| `docs/custom/xp/Fase-9B-3A-Training-Pilot.md` | Versionada. |
| `build/reports/Fase-9B-3A-Training-Pilot.md` | Copia operativa ignorada por la regla `.gitignore:49` (`/build/`). |

## 6. Flujo funcional resultante

```text
Acción normal exitosa
    -> Woodcutting.chopDown / Fishing.fish
    -> obtiene XP base sin cambiar la fórmula
    -> Player.exp(skill, base, XpOrigin.TRAINING)
    -> Experience.add(skill, base, TRAINING)
    -> ExperienceModifiers.modify(...)
    -> CustomXpPolicy
       -> master OFF: identidad
       -> TRAINING OFF: identidad
       -> ambos ON: base × factor TRAINING × factor de skill
    -> × 10 × Bonus XP del jugador × world.experienceRate
    -> truncamiento original a Int
    -> Experience.set / Skills.exp
```

Las acciones que no alcanzan la rama de éxito no llaman a `Player.exp`. Las llamadas de dos argumentos y las llamadas explícitas con `UNKNOWN` continúan fuera de la política custom.

## 7. Escenarios y cálculos verificados

La representación interna guarda décimas de XP; por ello 25.0 XP se comprueba como `250` y 50.0 XP como `500` mediante `Experience.direct`.

| Escenario | Configuración relevante | Resultado esperado y observado |
|---|---|---|
| Defaults/master OFF | Sin configuración custom activa | Tronco: 25.0 XP (`250`); trucha: 50.0 XP (`500`). |
| TRAINING OFF | Master ON, factores presentes, categoría OFF | Identidad: `250` y `500`. |
| Woodcutting custom | TRAINING `2.0`, Woodcutting `3.0` | `25 × 2 × 3 = 150` XP (`1500`). |
| Fishing custom | TRAINING `2.0`, Fishing `4.0` | `50 × 2 × 4 = 400` XP (`4000`). |
| Decimales | TRAINING `1.5`, Woodcutting `0.33` | `25 × 1.5 × 0.33 = 12.375`; truncamiento interno: `123`, es decir 12.3 XP. |
| Bonus y mundo | Base 25, TRAINING `2`, skill `3`, Bonus `2`, mundo `2` | `25 × 2 × 3 × 2 × 2 = 600` XP (`6000`). |
| Factores inválidos | TRAINING `NaN`, Woodcutting `0` | Ambos vuelven a identidad; 25.0 XP (`250`). |
| Fallo Woodcutting | Sin hacha | 0 XP, 0 logs, árbol intacto. |
| Fallo Fishing | Sin aparejo | 0 XP, 0 truchas, 100 plumas intactas. |
| `UNKNOWN`/no migrado | Llamadas de dos argumentos, `UNKNOWN`, Mining no migrado | Identidad aun con la política TRAINING activa. |

También se verificó que el corte entrega exactamente un log y agota el árbol normal, que la pesca entrega exactamente una trucha y consume exactamente una pluma, y que un award clasificado exitoso llama una sola vez a `Skills.exp`.

## 8. Cobertura automatizada

### 8.1 Casos nuevos del piloto

`TrainingXpPilotTest` cubre:

1. Woodcutting oficial con custom XP OFF.
2. Fishing oficial con custom XP OFF.
3. Factores TRAINING + Woodcutting aplicados una vez.
4. Factores TRAINING + Fishing aplicados una vez.
5. Categoría TRAINING OFF con factores configurados.
6. Master OFF con categoría TRAINING activa.
7. Factores decimales y truncamiento original.
8. Composición con Bonus XP y tasa mundial.
9. Un solo evento de experiencia por éxito.
10. Fallo de Woodcutting sin XP ni recurso.
11. Fallo de Fishing sin XP, captura o consumo.
12. Identidad de `UNKNOWN` y de una habilidad no migrada.
13. Fallback seguro ante factores inválidos.

### 8.2 Ejecución focalizada

Comando:

```powershell
.\gradlew.bat --no-daemon :game:cleanTest :game:test `
  --tests "content.custom.xp.*" `
  --tests "content.skill.woodcutting.WoodcuttingTest" `
  --tests "content.skill.fishing.FishingTest" `
  --tests "content.entity.player.stat.BonusExperienceTest" `
  --tests "content.entity.player.stat.BonusExperienceWorldTest" `
  --tests "content.entity.player.dialogue.type.LevelUpTest" `
  --tests "content.entity.player.dialogue.type.LevelUpInteractTest" `
  --tests "content.social.assist.RequestAssistTest"
```

Resultado confirmado:

| Métrica | Valor |
|---|---:|
| Tests | 63 |
| Fallos | 0 |
| Errores | 0 |
| Omitidos | 0 |
| Casos de `TrainingXpPilotTest` | 13/13 aprobados |

Una primera ejecución focalizada completó visualmente todos los casos como `PASSED`, pero Gradle terminó con `NoSuchFileException` sobre `game/build/test-results/test/binary/in-progress-results-generic.bin`. No hubo una aserción fallida y esa ejecución no reemplazó los XML anteriores. Para eliminar cualquier ambigüedad, se repitió con `--no-daemon` y `:game:cleanTest`; la repetición produjo XML nuevos y finalizó `BUILD SUCCESSFUL`. Los conteos anteriores provienen exclusivamente de esa repetición válida.

### 8.3 Formato y suites completas

Comando:

```powershell
.\gradlew.bat --no-daemon :game:spotlessCheck :engine:test :game:test
```

Resultado: `BUILD SUCCESSFUL`.

| Módulo/control | Tests | Fallos | Errores | Omitidos | Estado |
|---|---:|---:|---:|---:|---|
| `:game:spotlessCheck` | — | — | — | — | Aprobado |
| `:engine:test` | 1.444 | 0 | 0 | 3 | Aprobado; reejecutado completamente con `--rerun-tasks` |
| `:game:test` | 4.728 | 0 | 0 | 1 | Aprobado |

La base de `game` tenía 4.715 casos; los 13 casos nuevos explican el total de 4.728.

En la pasada conjunta Gradle consideró `:engine:test` actualizado. Para no depender de resultados reutilizados se ejecutó además `.\gradlew.bat --no-daemon :engine:test --rerun-tasks`; sus 16 tareas se ejecutaron y el build terminó satisfactoriamente. Los 1.444 tests y 3 omitidos de la tabla proceden de esa reejecución forzada.

Advertencias no bloqueantes observadas:

- Gradle reporta funciones obsoletas que serán incompatibles con Gradle 10; es deuda preexistente del build y no fue introducida por este piloto.
- La JVM informa que class-data sharing sólo aplica a clases del bootstrap loader durante pruebas con instrumentación.

## 9. Matriz de aceptación

| Criterio | Evidencia | Estado |
|---|---|---|
| Clasificar Woodcutting normal como TRAINING | `Woodcutting.chopDown` usa la sobrecarga de tres argumentos | Cumplido |
| Clasificar Fishing normal como TRAINING | `Fishing.fish` usa la sobrecarga de tres argumentos | Cumplido |
| Custom XP OFF preserva XP oficial | Casos base 25.0/50.0 XP | Cumplido |
| Factores distintos por skill | 3.0 Woodcutting y 4.0 Fishing bajo la misma categoría | Cumplido |
| Switch master y switch TRAINING independientes | Dos casos de identidad separados | Cumplido |
| Decimales/truncamiento | 12.375 calculado, 12.3 almacenado | Cumplido |
| Un award/evento por éxito | `verify(exactly = 1)` sobre `Skills.exp` | Cumplido |
| Fallo sin XP | Sin hacha y sin aparejo | Cumplido |
| Inventario/recursos intactos | Log/stump y trucha/pluma comprobados | Cumplido |
| `UNKNOWN` permanece identidad | Awards directos de dos argumentos y explícito UNKNOWN | Cumplido |
| Inválidos son seguros | `NaN` y `0` vuelven a 1.0 | Cumplido |
| Composición Bonus/mundo | Resultado `6000` interno | Cumplido |
| Lifecycle de política | Cobertura previa `CustomXpScriptTest` incluida en selección focalizada | Cumplido |
| Persistencia sin cambios | Ningún archivo de save/schema/serialización fue modificado | Cumplido |
| Sin regresiones | Suites focalizada, engine y game limpias | Cumplido |

## 10. Persistencia y compatibilidad

La experiencia sigue almacenándose en el mismo `IntArray` y recorriendo `Experience.set`; no se añadió un campo de origen al personaje ni se modificó el formato de guardado. En consecuencia:

- La XP histórica no se recalcula.
- Los personajes existentes no requieren migración.
- Cerrar sesión o reiniciar conserva la XP resultante mediante el mecanismo de persistencia ya existente.
- `Experience.multiplier` sigue siendo transitorio y vuelve a `1.0` al iniciar sesión, tal como documenta el propio código.
- La configuración custom se evalúa como snapshot al cargar scripts; este piloto no introduce hot reload.

La persistencia específica de personajes no fue manipulada manualmente. La confianza proviene de mantener intacto el contrato y de las suites completas existentes, no de acceder a archivos de jugador.

## 11. Rutas deliberadamente fuera de alcance

La búsqueda de awards de Woodcutting/Fishing confirmó otras rutas que continúan con la sobrecarga de dos argumentos y, por tanto, `UNKNOWN`:

| Ruta | Naturaleza | Decisión |
|---|---|---|
| `game/src/main/kotlin/content/activity/evil_tree/EvilTree.kt` | XP de árbol/raíces de evento | No clasificar en un piloto de entrenamiento normal. |
| `game/src/main/kotlin/content/area/wilderness/abyss/AbyssObstacles.kt` | Recompensa por obstáculo | Requiere decisión semántica separada. |
| `game/src/main/kotlin/content/skill/summoning/Forager.kt` | XP concedida por familiar | No es pesca manual normal. |
| `game/src/main/kotlin/content/area/morytania/braindeath_island/CaptainBraindeath.kt` | Recompensa de contenido/quest | No clasificar como entrenamiento sin auditoría de quest. |
| `game/src/main/kotlin/content/area/kharidian_desert/DesertHeat.kt` | Recompensa contextual | No es Woodcutting normal. |

También permanecen fuera de alcance todos los demás call sites catalogados en 9A-2, incluyendo otras habilidades, combate, quests, lamps, eventos y Assistance.

## 12. Riesgos, incertidumbres y mitigaciones

| Riesgo/incertidumbre | Evaluación | Mitigación o decisión |
|---|---|---|
| Confundir XP relacionada con una skill con entrenamiento normal | Alto impacto semántico | El piloto sólo migra los dos scripts canónicos de recolección. |
| Doble multiplicación | Bajo tras pruebas | Se comprueban valores exactos y una sola llamada a `Skills.exp`. |
| Alterar recursos o tiempos al insertar la clasificación | Bajo | Sólo cambia el tercer argumento; pruebas verifican inventario, bait y agotamiento. |
| Truncamiento con factores decimales | Controlado | Caso 12.375 -> 12.3 confirma el comportamiento original. |
| Bonus XP/tasa mundial en orden incorrecto | Controlado | Caso integrado confirma política custom antes de ambos multiplicadores. |
| Estado global del registro en pruebas | Controlado | Cada caso registra un snapshot local y restaura una política OFF en `@BeforeEach`/`@AfterEach`. |
| Primer cierre Gradle sin binario temporal | Infraestructura, no código | Repetición limpia y aislada aprobada; sólo esa ejecución se usa como evidencia. |
| Persistencia end-to-end no reejecutada manualmente | Bajo | No hay cambios en save/schema; se conserva el flujo de `Experience.set` y pasan suites completas. |

## 13. Notas Kotlin para lectores C#/.NET

- `fun Player.exp(...)` es una función de extensión: se invoca como método de `Player`, pero no modifica la clase `Player`. Es comparable a un extension method de C#.
- Kotlin permite sobrecargas; la llamada de dos argumentos conserva `UNKNOWN`, mientras la de tres argumentos expresa el origen explícito.
- `XpOrigin` es un `enum class`, equivalente conceptual a un `enum` de C#, pero cada valor expone `ordinal` usado por los arrays de consulta O(1).
- `@BeforeEach` y `@AfterEach` de JUnit 5 cumplen una función análoga a setup/teardown de frameworks de prueba .NET.
- `toInt()` trunca, no redondea; por eso 123.75 unidades internas terminan en 123.

## 14. Integridad final y criterio de parada

Antes del commit se verificaron:

- `git diff --check` sin errores.
- Exactamente dos referencias de producción a `XpOrigin.TRAINING` dentro de los paquetes piloto.
- Ningún cambio en TOML, GROML, motor, configuración, cliente o persistencia.
- La copia bajo `build/reports` está ignorada por Git.
- La copia bajo `docs/custom/xp` es la única copia del informe destinada al seguimiento.

La fase se detiene aquí. No se clasifican más habilidades ni se inicia 9B-3B. Cualquier expansión debe partir de una nueva decisión del responsable del proyecto y de una auditoría explícita de los call sites correspondientes.
