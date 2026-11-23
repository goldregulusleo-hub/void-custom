# RuneScape Custom — Fase 9B-2

## Custom XP Policy configurable por habilidad y origen

Fecha: 23 de noviembre de 2026  
Repositorio: `C:\Juegos\Codigo\void-custom`  
Motor: Void 2.12.0  
Cliente objetivo: RuneScape revisión 634

## 1. Resumen ejecutivo

Se implementó `Custom XP Policy` sobre el hook neutral de la Fase 9B-1, sin rediseñarlo y sin modificar call sites de producción. La política permite combinar:

1. Un master switch global.
2. Un switch independiente para cada origen explícito.
3. Un factor por origen.
4. Un factor por cada una de las 25 habilidades reales de Void.

Toda la configuración empaquetada inicia OFF. Las llamadas actuales de dos argumentos siguen llegando como `XpOrigin.UNKNOWN`; el hook las mantiene en identidad antes de consultar la política. Por ello, las actividades normales del personaje no reciben multiplicadores custom todavía.

La configuración se analiza una sola vez durante el descubrimiento de scripts. El resultado es un snapshot inmutable basado en arrays, con consulta O(1), sin acceso a archivos, Koin, parsing ni logging en cada award.

## 2. Veredicto

**GO.**

Los criterios de 9B-2 se consideran cumplidos:

- Defaults completos OFF.
- Master, categorías y factores independientes.
- `UNKNOWN` siempre identidad.
- Validación estricta `0.01..100.0`.
- Las 25 skills admitidas con nombre canónico, incluida `constitution`.
- Registro único y limpieza mediante `Script.clear()`.
- Composición comprobada con Bonus XP y `world.experienceRate`.
- Skills bloqueadas y Assistance sin doble multiplicación.
- Sin cambios de persistencia ni experiencia histórica.
- Formato correcto y suites completas sin fallos.

## 3. Baseline y protección Git

| Elemento | Valor confirmado |
|---|---|
| Rama | `feature/custom-xp-policy` |
| Commit inicial real | `fea9cc6c0174d3c424eb9d86353badab8325816c` |
| Mensaje inicial | `docs(custom-xp): preserve architecture and hook reports` |
| Merge de 9B-1 | `06761683e4803eb4514fda020cb289d07996bbf3` |
| Referencia estable | `stable/2.12.0` |
| Estado inicial | Limpio |
| Commit de entrega | `ad3535f661225a701c8887c26f39ec853b06a1de` |

El prompt indicaba el corto `fea9c6c0`; el repositorio contenía y verificó `fea9cc6c0174d3c424eb9d86353badab8325816c`. Se utilizó el estado real sin modificar la historia.

No se modificaron tags, remotos, protecciones ni ramas estables. No se realizó push, merge o PR.

## 4. Archivos modificados y creados

### Producción

| Ruta | Tipo | Responsabilidad |
|---|---|---|
| `game/src/main/kotlin/content/custom/xp/CustomXpConfig.kt` | Nuevo | Validación, defaults, warnings limitados y snapshot inmutable. |
| `game/src/main/kotlin/content/custom/xp/CustomXpPolicy.kt` | Nuevo | Implementación de `ExperienceModifier` y cálculo por origen/skill. |
| `game/src/main/kotlin/content/custom/xp/CustomXpScript.kt` | Nuevo | Registro único de la política durante descubrimiento de scripts. |
| `game/src/main/resources/game.properties` | Modificado | Defaults operativos OFF y tabla de las 25 skills. |
| `engine/src/main/kotlin/world/gregs/voidps/engine/data/Settings.kt` | Modificado | Vista read-only de nombres para detectar claves custom desconocidas sin releer archivos. |

### Pruebas

| Ruta | Cobertura principal |
|---|---|
| `engine/src/test/kotlin/world/gregs/voidps/engine/data/SettingsTest.kt` | Verifica la nueva vista de nombres. |
| `game/src/test/kotlin/content/custom/xp/CustomXpConfigTest.kt` | Defaults, 25 skills, seis categorías, inválidos, unknown keys, precedencia y snapshot sin hot reload. |
| `game/src/test/kotlin/content/custom/xp/CustomXpPolicyTest.kt` | Identidad, categorías, factores menores/mayores y overflow. |
| `game/src/test/kotlin/content/custom/xp/CustomXpIntegrationTest.kt` | Hook, truncamiento, Bonus XP, world rate, eventos, bloqueo e histórico. |
| `game/src/test/kotlin/content/custom/xp/CustomXpScriptTest.kt` | Registro único y lifecycle. |
| `game/src/test/kotlin/content/custom/xp/CustomXpWorldTest.kt` | Descubrimiento real de scripts y default OFF en mundo. |

No se modificaron `Experience.kt`, `ExperienceModifier.kt`, `XpOrigin.kt`, `Player`, `PlayerSave`, quests, Slayer, NPC, drops, TOML ni el cliente.

## 5. Arquitectura implementada

```text
Arranque del servidor
    -> Settings.load()
       -> ./game.properties externo si existe
       -> si no, /game.properties empaquetado
       -> variables de entorno reemplazan claves exactas
    -> ContentLoader descubre CustomXpScript
    -> CustomXpConfig valida Settings una vez
    -> CustomXpSnapshot copia arrays inmutables
    -> CustomXpPolicy se registra como "custom_xp_policy"

Award futuro con origen explícito
    -> Player.exp(skill, base, origin)
    -> Experience.add(skill, base, origin)
       -> skill bloqueada: camino original, sin policy
       -> UNKNOWN: identidad en el hook 9B-1
       -> ExperienceModifiers
          -> CustomXpPolicy
             -> snapshot.factor(skill, origin), O(1)
       -> base * customFactor
       -> * 10
       -> * Experience.multiplier (Bonus XP)
       -> * world.experienceRate
       -> truncamiento original
       -> un único set / Skills.exp / persistencia
```

### Separación de responsabilidades

`CustomXpConfig` conoce nombres de properties, validación y warnings. `CustomXpSnapshot` conserva solamente booleanos y doubles ya validados. `CustomXpPolicy` no conoce archivos ni strings de configuración. `CustomXpScript` sólo registra la política, siguiendo el patrón de scripts de Void.

La única ampliación del engine es `Settings.names()`. Era necesaria para rechazar `custom.xp.skill.hitpoints`, skills inexistentes y errores tipográficos sin duplicar el parser de properties. No contiene lógica custom y no afecta las lecturas existentes.

## 6. Contratos Kotlin definitivos

### Carga

```kotlin
class CustomXpConfig(
    private val settings: Settings = Settings,
    private val warning: (String) -> Unit = { ... },
) {
    fun load(): CustomXpSnapshot
}
```

El callback de warning permite comprobar la validación sin acoplar tests al backend de logging. En producción usa `InlineLogger` únicamente durante la carga.

### Snapshot

```kotlin
class CustomXpSnapshot internal constructor(...) {
    val enabled: Boolean
    fun sourceEnabled(origin: XpOrigin): Boolean
    fun sourceFactor(origin: XpOrigin): Double
    fun skillFactor(skill: Skill): Double
    fun factor(skill: Skill, origin: XpOrigin): Double
}
```

Los arrays recibidos se copian y permanecen privados. Ningún consumidor puede modificar el snapshot después de publicarlo.

### Política

```kotlin
class CustomXpPolicy(
    private val snapshot: CustomXpSnapshot,
) : ExperienceModifier
```

La política devuelve la base cuando el factor es identidad o si una multiplicación extrema no produce un double finito y positivo. El fallback adicional de 9B-1 permanece intacto.

### Registro

```kotlin
class CustomXpScript : Script {
    init {
        ExperienceModifiers.register(
            "custom_xp_policy",
            CustomXpPolicy(CustomXpConfig().load()),
        )
    }
}
```

El identificador estable impide duplicados. `ExperienceModifiers` ya forma parte de `Script.interfaces`; por tanto, `Script.clear()` elimina el registro antes de una nueva carga.

## 7. Tabla completa de configuración

### Master y orígenes

| Clave | Default | Valores válidos | Efecto |
|---|---:|---|---|
| `custom.xp.enabled` | `false` | `true`, `false` | Master global. Ausente o inválido equivale a OFF. |
| `custom.xp.source.training.enabled` | `false` | `true`, `false` | Habilita TRAINING. |
| `custom.xp.source.training.factor` | `1.0` | `0.01..100.0` | Factor TRAINING. |
| `custom.xp.source.combat.enabled` | `false` | `true`, `false` | Habilita COMBAT. |
| `custom.xp.source.combat.factor` | `1.0` | `0.01..100.0` | Factor COMBAT. |
| `custom.xp.source.quest.enabled` | `false` | `true`, `false` | Habilita QUEST. |
| `custom.xp.source.quest.factor` | `1.0` | `0.01..100.0` | Factor QUEST. |
| `custom.xp.source.lamp.enabled` | `false` | `true`, `false` | Habilita LAMP. |
| `custom.xp.source.lamp.factor` | `1.0` | `0.01..100.0` | Factor LAMP. |
| `custom.xp.source.event.enabled` | `false` | `true`, `false` | Habilita EVENT. |
| `custom.xp.source.event.factor` | `1.0` | `0.01..100.0` | Factor EVENT. |
| `custom.xp.source.assistance.enabled` | `false` | `true`, `false` | Habilita ASSISTANCE explícito futuro. |
| `custom.xp.source.assistance.factor` | `1.0` | `0.01..100.0` | Factor ASSISTANCE. |

No existe configuración para `UNKNOWN`: siempre es identidad. Una clave `custom.xp.source.unknown.*` genera warning y se ignora.

### Habilidades

Todas las claves son opcionales, tienen default `1.0` y aceptan doubles finitos dentro de `0.01..100.0`.

| Skill Void | Clave |
|---|---|
| Attack | `custom.xp.skill.attack` |
| Defence | `custom.xp.skill.defence` |
| Strength | `custom.xp.skill.strength` |
| Constitution | `custom.xp.skill.constitution` |
| Ranged | `custom.xp.skill.ranged` |
| Prayer | `custom.xp.skill.prayer` |
| Magic | `custom.xp.skill.magic` |
| Cooking | `custom.xp.skill.cooking` |
| Woodcutting | `custom.xp.skill.woodcutting` |
| Fletching | `custom.xp.skill.fletching` |
| Fishing | `custom.xp.skill.fishing` |
| Firemaking | `custom.xp.skill.firemaking` |
| Crafting | `custom.xp.skill.crafting` |
| Smithing | `custom.xp.skill.smithing` |
| Mining | `custom.xp.skill.mining` |
| Herblore | `custom.xp.skill.herblore` |
| Agility | `custom.xp.skill.agility` |
| Thieving | `custom.xp.skill.thieving` |
| Slayer | `custom.xp.skill.slayer` |
| Farming | `custom.xp.skill.farming` |
| Runecrafting | `custom.xp.skill.runecrafting` |
| Hunter | `custom.xp.skill.hunter` |
| Construction | `custom.xp.skill.construction` |
| Summoning | `custom.xp.skill.summoning` |
| Dungeoneering | `custom.xp.skill.dungeoneering` |

`hitpoints` no es alias de `constitution` en esta versión; se advierte y se ignora.

## 8. Precedencia, defaults y validación

### Precedencia operativa

1. Si existe `./game.properties` en el directorio de trabajo, `Settings.load()` usa ese archivo externo.
2. Si no existe, usa el `/game.properties` incluido en el jar.
3. `Main.settings()` ejecuta después `properties.putAll(System.getenv())`; una variable de entorno cuyo nombre coincida exactamente reemplaza el valor cargado.
4. `CustomXpScript` construye el snapshot después de esa carga.

Un archivo externo no necesita declarar todas las claves. La política aplica sus propios defaults seguros para las ausentes.

### Resolución

```text
master ausente, false o inválido          -> 1.0
origin == UNKNOWN                         -> 1.0
source enabled ausente, false o inválido  -> 1.0
en otro caso                              -> sourceFactor * skillFactor
```

### Validación

Se rechazan y sustituyen por `1.0`:

- `NaN`.
- `Infinity` y `-Infinity`.
- Texto no numérico.
- Cero o negativos.
- Valores menores que `0.01`.
- Valores mayores que `100.0`.

Los límites `0.01` y `100.0` son inclusivos. Cada clave inválida o desconocida emite como máximo un warning durante cada construcción del snapshot. No se emiten warnings por award.

El contrato v1 es **editar y reiniciar**. Cambiar `Settings` en memoria no modifica un snapshot ya creado; la prueba correspondiente demuestra que sólo una nueva carga produce otro snapshot.

## 9. Ejemplos de cálculo

| Escenario | Cálculo | Resultado |
|---|---|---:|
| Master OFF, base 10 | identidad | 10 XP |
| TRAINING OFF, Attack 3x | identidad | 10 XP |
| TRAINING 2x, Attack 1.5x, base 10 | `10 * 2 * 1.5` | 30 XP |
| TRAINING 0.5x, Attack 0.5x, base 10 | `10 * 0.5 * 0.5` | 2.5 XP |
| COMBAT 2x, Slayer 1.5x, base 10 | `10 * 2 * 1.5` | 30 XP |
| UNKNOWN con cualquier configuración | identidad en hook | base original |
| Skill bloqueada, ASSISTANCE 2x y Magic 3x | policy omitida | fórmula oficial |
| Base 1.23, TRAINING 1.5, Attack 1.25, Bonus 2.7, world 2 | `trunc(1.23 * 1.5 * 1.25 * 10 * 2.7 * 2)` | 124 unidades = 12.4 XP |

El último ejemplo prueba que no se redondea entre capas y que el truncamiento permanece en el mismo punto de Void.

## 10. Matriz de pruebas

| Requisito | Evidencia |
|---|---|
| Master OFF | `CustomXpPolicyTest > Master off is identity` |
| Master ausente | `CustomXpConfigTest > Empty configuration is safely disabled` |
| Master inválido | `CustomXpConfigTest > Invalid flags default to off` |
| Categoría OFF | `CustomXpPolicyTest > Disabled source is identity` |
| Categoría ON e independiente | Tests de config y policy sobre los seis orígenes explícitos |
| UNKNOWN | Test unitario de policy, test central y protección existente de 9B-1 |
| 25 skills | Iteración sobre `Skill.entries`; confirma `Skill.count == 25` |
| Skill override | Factores distintos para las 25 keys canónicas |
| Source × skill | Casos 0.25x, 1.875x y 3x |
| Factores menores/mayores que 1 | Policy tests |
| Inválidos | Ocho clases de valor inválido, boundaries y unknown keys |
| Decimales/truncamiento | Award 1.23 termina en 12.4 XP efectiva |
| Bonus XP | `Experience.multiplier = 2.7` más `BonusExperience*Test` |
| world rate | `world.experienceRate=2.0` dentro del test central |
| Evento único | `Skills.exp` verificado exactamente una vez |
| Skills bloqueadas | `Skills.blocked` exacto y sin factor custom |
| Assistance | Caso explícito bloqueado más `RequestAssistTest` completo |
| Registro/lifecycle | Duplicado rechazado, `Script.clear`, recarga exitosa |
| Config ausente | Snapshot vacío seguro |
| Config operativa | Defaults empaquetados sobreescritos mediante Settings |
| Descubrimiento | WorldTest encuentra exactamente un `CustomXpScript` |
| Sin hot reload | Snapshot conserva valor original hasta reconstrucción |
| Histórico/save | XP anterior permanece; suites de PlayerSave/FileStorage pasan |

## 11. Comandos y resultados

### Formato aplicado

```powershell
.\gradlew.bat :engine:spotlessApply :game:spotlessApply --parallel
```

Resultado: `BUILD SUCCESSFUL` en 19 s.

### Pruebas focalizadas

```powershell
.\gradlew.bat :engine:test --tests "world.gregs.voidps.engine.data.SettingsTest" :game:test --tests "content.custom.xp.*" --tests "content.entity.player.stat.BonusExperienceTest" --tests "content.entity.player.stat.BonusExperienceWorldTest" --tests "content.social.assist.RequestAssistTest" --tests "content.entity.player.dialogue.type.LevelUpTest" --tests "content.entity.player.dialogue.type.LevelUpInteractTest"
```

Resultado: `BUILD SUCCESSFUL` en 2 min 15 s. La ejecución focalizada registrada antes del último test de snapshot cubrió 7 tests de engine y 40 de game, todos aprobados. El test añadido después quedó cubierto por la suite completa.

### Formato y suite completa de engine

```powershell
.\gradlew.bat :engine:spotlessCheck :game:spotlessCheck :engine:test
```

Resultado: `BUILD SUCCESSFUL` en 1 min 18 s.

| Módulo | Suites | Tests | Fallos | Errores | Omitidos |
|---|---:|---:|---:|---:|---:|
| engine | 210 | 1,444 | 0 | 0 | 3 |

Los tres omitidos pertenecen a `GameLoopTest` y ya están marcados como skipped: `Start game loop`, `Cancel job early` y `Exception in stage`.

### Suite completa de game

```powershell
.\gradlew.bat :game:test
```

Resultado: `BUILD SUCCESSFUL` en 4 min.

| Módulo | Suites | Tests | Fallos | Errores | Omitidos |
|---|---:|---:|---:|---:|---:|
| game | 526 | 4,715 | 0 | 0 | 1 |

El único omitido es `SpiritTreePatchTest > Rake farming patch`, preexistente y fuera del área modificada.

### Advertencias

Se mantuvieron advertencias conocidas del build:

- `-Xjvm-default` deprecado.
- `-Xcontext-parameters` y `-Xinline-classes` redundantes.
- Features de Gradle deprecadas para una futura migración a Gradle 10.
- Warning de class sharing de la JVM durante tests.

No se introdujo warning del compilador en los archivos de Custom XP.

## 12. Persistencia y experiencia histórica

No se añadió ningún campo a `Player`, `Experience`, snapshots o archivos de cuenta. `XpOrigin`, el snapshot y el factor sólo viven durante ejecución.

La política modifica únicamente el argumento de un award futuro y deja que el pipeline existente escriba el resultado. El test de integración establece primero 100 XP históricas y confirma que un award nuevo 2x termina en 120 XP, sin reescribir las 100 anteriores.

La suite completa de engine ejecutó `PlayerSaveTest`, `FileStorageTest`, `AccountStorageTest`, `AccountManagerTest` y tests de snapshot sin fallos. Todo almacenamiento fue temporal de pruebas; no se accedió a personajes reales ni respaldos.

## 13. Riesgos y limitaciones

1. **Ningún call site real está clasificado todavía.** Las llamadas normales continúan `UNKNOWN`, por lo que la configuración ON no cambia actividades normales hasta 9B-3.
2. **Sin hot reload.** Cambios de properties requieren reinicio completo, por contrato v1.
3. **Variables de entorno con nombre exacto.** La precedencia actual de Void no transforma nombres; debe usarse exactamente la clave con puntos.
4. **Composición máxima.** Dos factores individuales de 100 producen 10,000x. Es finito y permitido por el contrato aprobado; un award que desborde double vuelve a identidad.
5. **Warnings por carga.** Un reinicio con varias claves inválidas genera un warning por clave, pero nunca por award.
6. **API genérica mínima en engine.** `Settings.names()` amplía lectura, no mutación. Su propósito es evitar aceptación silenciosa de skills o propiedades desconocidas.
7. **Semántica de máximo upstream.** No se cambió el límite ni el comportamiento de rechazo de `Experience.set`; factores grandes pueden hacer que un award supere el máximo y se comporte como antes.

## 14. Rollback

Rollback operativo inmediato:

```properties
custom.xp.enabled=false
```

Después debe reiniciarse el servidor. Con master OFF, toda categoría y skill queda en identidad aunque sus factores permanezcan configurados.

Rollback de código, si fuera necesario después del commit:

```powershell
git revert <commit-fase-9b-2>
```

No se necesita migración, limpieza de saves ni cambios del cliente. No se recomienda `git reset --hard`.

## 15. Estado Git final

Estado verificado después del commit:

| Elemento | Estado final |
|---|---|
| Rama | `feature/custom-xp-policy` |
| HEAD | `ad3535f661225a701c8887c26f39ec853b06a1de` |
| Mensaje | `feat(game): add configurable custom XP policy` |
| Padre | `fea9cc6c0174d3c424eb9d86353badab8325816c` |
| Árbol seguido | Limpio (`git status --short` sin salida) |
| Informe | Presente en `build/reports`; ignorado por `.gitignore:49` (`/build/`) |
| Publicación | Sin push, PR o merge |
| Remotos/tags | Sin modificaciones |

## 16. Preparación para Fase 9B-3

9B-3 puede comenzar, tras revisión del propietario, a clasificar call sites por lotes pequeños. Recomendaciones:

1. Empezar por fronteras centralizadas de bajo fan-out, no por reemplazo masivo.
2. Mantener un inventario explícito de llamadas todavía `UNKNOWN`.
3. Probar cada lote con master OFF y ON.
4. Clasificar Assistance únicamente en el reaward correcto, nunca en el tramo bloqueado.
5. Mantener quests, combat, lamps y events en commits separados y reversibles.
6. No deprecar todavía el overload antiguo mientras haya deuda `UNKNOWN` intencional.

La Fase 9B-2 se detiene aquí; no se modifican fuentes reales de XP hasta autorización posterior.
