# RuneScape Custom — Fase 9B-1

## Implementación del contrato central de experiencia por origen

Fecha de ejecución: 23 de noviembre de 2026  
Repositorio: `C:\Juegos\Codigo\void-custom`  
Motor: Void 2.12.0  
Cliente objetivo: RuneScape revisión 634

## 1. Resumen ejecutivo y veredicto

**Veredicto: GO.**

Se implementó una infraestructura mínima y central para asociar una categoría de origen a cada concesión de experiencia. La fase conserva las firmas JVM existentes, añade overloads explícitos, incorpora un contrato pequeño de modificadores y conecta ese contrato una sola vez en `Experience.add`.

No existe todavía ninguna política personalizada ni multiplicador registrado por el juego. En consecuencia, todas las concesiones mantienen el comportamiento de Void Custom v0.3.0. Las llamadas antiguas se clasifican como `UNKNOWN` y se mantienen como identidad incluso si en una prueba se registra un modificador. La rama de skills bloqueadas —base del flujo de Assistance— omite por completo el registro y conserva la fórmula original.

La aceptación se sustenta en:

- 13 pruebas nuevas del contrato y del punto central.
- 83 pruebas focalizadas de `engine`, sin fallos.
- 19 pruebas focalizadas de `game` para Bonus XP, Assistance y level-up, sin fallos.
- Suite completa de `engine`: 1,443 pruebas, 0 fallos, 0 errores y 3 omitidas preexistentes.
- Suite completa de `game`: 4,693 pruebas, 0 fallos, 0 errores y 1 omitida preexistente.
- Verificación explícita de los descriptores JVM antiguos y nuevos.
- Verificación de persistencia mediante las pruebas de snapshots, cuentas y almacenamiento temporal.

No se modificaron call sites de contenido, misiones, Slayer, TOML, configuración, persistencia ni el servidor principal.

## 2. Rama y commit inicial

| Elemento | Valor confirmado |
|---|---|
| Rama estable de origen | `stable/2.12.0` |
| Tag de referencia | `custom-v0.3.0` |
| Commit inicial | `8eab928e435d8657443cb26d71e43b33aee67314` |
| Rama de trabajo | `feature/xp-origin-hook` |
| Estado inicial | Árbol limpio |
| Commit de entrega | `457c8a1af00ab7cfa36de0c1121c9ff05b1e102f` |

La rama se creó directamente desde el commit inicial confirmado. No se modificaron tags, ramas estables ni remotos.

## 3. Arquitectura implementada

El flujo definitivo de 9B-1 es:

```text
Call site existente
  -> Player.exp(skill, amount)
  -> Experience.add(skill, amount)
  -> XpOrigin.UNKNOWN
  -> identidad obligatoria
  -> fórmula original de Void
  -> Experience.set
  -> Skills.exp / eventos y actualización existentes

Call site futuro clasificado
  -> Player.exp(skill, amount, origin)
  -> Experience.add(skill, amount, origin)
  -> si la skill está bloqueada: fórmula original, sin modificador
  -> si origin == UNKNOWN: identidad obligatoria
  -> si origin es explícito: cadena determinista de modificadores
  -> fórmula original de Void
  -> Experience.set
  -> Skills.exp / eventos y actualización existentes
```

El diseño mantiene separadas tres responsabilidades:

1. `XpOrigin` expresa intención de dominio sin contener política.
2. `ExperienceModifier` define el punto de extensión neutral.
3. `ExperienceModifiers` administra registro, orden, fallback y lifecycle.

No se creó un módulo Gradle ni un framework genérico de plugins. Tampoco se consulta Koin, se procesa configuración o se genera logging por cada award.

## 4. Archivos creados y modificados

### Archivos creados

| Ruta | Propósito |
|---|---|
| `engine/src/main/kotlin/world/gregs/voidps/engine/entity/character/player/skill/exp/XpOrigin.kt` | Enum de orígenes admitidos. |
| `engine/src/main/kotlin/world/gregs/voidps/engine/entity/character/player/skill/exp/ExperienceModifier.kt` | Contrato funcional y registro determinista. |
| `engine/src/test/kotlin/world/gregs/voidps/engine/entity/character/player/skill/exp/ExperienceModifierTest.kt` | Pruebas del contrato, compatibilidad, lifecycle y seguridad. |
| `build/reports/Fase-9B-1-Implementacion-Hook-XP.md` | Este informe; permanece ignorado por Git bajo `build/`. |

### Archivos upstream modificados

| Ruta | Cambio mínimo | Justificación |
|---|---|---|
| `engine/src/main/kotlin/world/gregs/voidps/engine/entity/character/player/skill/exp/Experience.kt` | Overloads y una llamada al registro en el camino no bloqueado. | Es el único punto real común de concesión de XP. Conservar aquí el hook evita modificar 308 call sites y evita duplicar eventos. |
| `engine/src/main/kotlin/world/gregs/voidps/engine/Script.kt` | Una importación y `ExperienceModifiers` en `Script.interfaces`. | `Script.clear()` ya es el lifecycle central que limpia APIs estáticas; integrarlo evita fugas y duplicados entre cargas y pruebas. |

No se modificó `Experience.set`, la clase `Player`, `PlayerSave`, definiciones TOML, contenido de quests ni configuración.

## 5. Contratos Kotlin definitivos

### Origen

```kotlin
enum class XpOrigin {
    TRAINING,
    COMBAT,
    QUEST,
    LAMP,
    EVENT,
    ASSISTANCE,
    UNKNOWN,
}
```

### Modificador

```kotlin
fun interface ExperienceModifier {
    fun modify(
        player: Player,
        skill: Skill,
        origin: XpOrigin,
        base: Double,
    ): Double
}
```

`fun interface` es el equivalente Kotlin de una interfaz funcional de un solo método, similar al uso de un delegado o una interfaz SAM en C#. Permite registrar una lambda sin crear una clase explícita.

### Overloads compatibles

```kotlin
fun Experience.add(skill: Skill, experience: Double)
fun Experience.add(skill: Skill, experience: Double, origin: XpOrigin)

fun Player.exp(skill: Skill, experience: Double)
fun Player.exp(skill: Skill, experience: Double, origin: XpOrigin)
```

No se sustituyó la firma antigua por un parámetro con valor por defecto. En JVM, los parámetros forman parte del descriptor del método; conservar overloads reales protege el código Kotlin/Java ya compilado contra el descriptor anterior.

## 6. Registro y seguridad

`ExperienceModifiers` utiliza un `LinkedHashMap` para conservar el orden de registro y publica una lista snapshot mediante `@Volatile`. Las modificaciones del registro están sincronizadas; el camino de cada award sólo lee la snapshot y no crea listas, no resuelve dependencias y no procesa archivos.

Reglas implementadas:

- El identificador del modificador no puede estar vacío.
- Un identificador duplicado produce `IllegalStateException`.
- La ejecución sigue el orden de registro.
- `unregister` permite retirar de forma controlada un registro.
- `close` borra tanto el mapa como la snapshot.
- `Script.clear()` invoca `close`, igual que para las demás APIs estáticas.
- Sin registros, el resultado es exactamente `base`.
- `UNKNOWN` no consulta el registro y siempre es identidad.
- Skills bloqueadas no consultan el registro.
- Si un modificador lanza `Exception`, se restaura la XP base original del award completo.
- Si devuelve `NaN`, infinito, cero o un valor negativo, se restaura la XP base original.
- No se captura `Throwable`; errores graves de JVM o del motor, como `AssertionError`, continúan propagándose.

El fallback vuelve a la base original, no al resultado parcial de modificadores anteriores. Así se evita conceder XP parcialmente transformada cuando una cadena falla.

## 7. Punto de integración y equivalencia identidad

La integración está únicamente en el overload nuevo de `Experience.add`:

1. XP menor o igual que cero conserva el retorno temprano original.
2. Si la skill está bloqueada, se calcula directamente `experience * 10 * multiplier * world.experienceRate` y se llama a `Skills.blocked`, igual que antes.
3. Para awards no bloqueados, `UNKNOWN` usa directamente `experience`.
4. Un origen explícito consulta el registro; con el registro vacío recibe el mismo `experience`.
5. La fórmula posterior conserva el orden original: base, `* 10`, `* Experience.multiplier`, `* world.experienceRate` y `toInt()` en el mismo punto.
6. `AuditLog`, `Experience.set`, el máximo de XP y `Skills.exp` continúan en el mismo camino y se ejecutan una sola vez.

Evidencias concretas:

- Los siete valores de `XpOrigin` conceden la misma XP sin registros.
- Siete awards de `1.25` producen `8.4` XP total: cada award mantiene el truncamiento original de `12.5` a `12` unidades internas.
- Las llamadas antiguas y `UNKNOWN` permanecen en identidad aun cuando se registra deliberadamente un modificador que duplicaría XP.
- El test de evento verifica exactamente una llamada a `Skills.exp` por award.
- La rama bloqueada verifica exactamente `Skills.blocked(player, skill, 100)` para 10 XP y confirma que el modificador no fue invocado.
- Los tests existentes de tasa `10x`, `Experience.multiplier`, máximo, cero, negativos y notificación siguen pasando.
- Bonus XP Weekend y Request Assist pasan tanto en la batería focalizada como dentro de la suite completa de juego.

## 8. Pruebas ejecutadas y resultados

### 8.1 Motor focalizado

Comando exacto:

```powershell
.\gradlew.bat :engine:test --tests "world.gregs.voidps.engine.entity.character.player.skill.exp.ExperienceModifierTest" --tests "world.gregs.voidps.engine.entity.character.player.skill.ExperienceTableTest" --tests "world.gregs.voidps.engine.data.PlayerSaveTest" --tests "world.gregs.voidps.engine.data.AccountStorageTest" --tests "world.gregs.voidps.engine.data.AccountManagerTest" --tests "world.gregs.voidps.engine.entity.character.player.skill.LevelsTest"
```

Resultado: `BUILD SUCCESSFUL` en 48 s; 83 pruebas ejecutadas, 0 fallos. Incluye 13 pruebas nuevas del hook.

### 8.2 Regresiones focalizadas de juego

Comando exacto:

```powershell
.\gradlew.bat :game:test --tests "content.entity.player.stat.BonusExperienceTest" --tests "content.entity.player.stat.BonusExperienceWorldTest" --tests "content.social.assist.RequestAssistTest" --tests "content.entity.player.dialogue.type.LevelUpTest" --tests "content.entity.player.dialogue.type.LevelUpInteractTest"
```

Resultado: `BUILD SUCCESSFUL` en 2 min 30 s; 19 pruebas ejecutadas, 0 fallos.

### 8.3 Suite completa de engine

Comando exacto:

```powershell
.\gradlew.bat :engine:test
```

Resultado: `BUILD SUCCESSFUL` en 1 min 25 s.

| Suites | Pruebas | Fallos | Errores | Omitidas |
|---:|---:|---:|---:|---:|
| 210 | 1,443 | 0 | 0 | 3 |

Las tres omitidas pertenecen a `GameLoopTest`: `Start game loop`, `Cancel job early` y `Exception in stage`. Ya están marcadas como skipped por la suite; no son fallos introducidos por esta fase.

### 8.4 Suite completa de game

Comando exacto:

```powershell
.\gradlew.bat :game:test
```

Resultado: `BUILD SUCCESSFUL` en 3 min 47 s.

| Suites | Pruebas | Fallos | Errores | Omitidas |
|---:|---:|---:|---:|---:|
| 521 | 4,693 | 0 | 0 | 1 |

La prueba omitida es `SpiritTreePatchTest > Rake farming patch`, ya marcada como skipped. No pertenece al área modificada.

### 8.5 Advertencias observadas

Se observaron advertencias no bloqueantes y ajenas al hook:

- `-Xjvm-default` está deprecado.
- `-Xcontext-parameters` y `-Xinline-classes` son redundantes para la versión actual de Kotlin.
- Gradle informa features deprecadas con impacto futuro en Gradle 10.
- `FarmingPatchPick.kt:126` contiene un cast no comprobado preexistente.
- La JVM advierte sobre class sharing al añadir bootstrap classpath en pruebas.

No hubo warnings nuevos emitidos por los archivos de 9B-1.

## 9. Cobertura de criterios de aceptación

| Criterio | Evidencia | Estado |
|---|---|---|
| Compila correctamente | Suites focalizadas y completas compilaron. | Cumplido |
| Origen explícito | Enum y overloads probados. | Cumplido |
| Firmas antiguas compatibles | Reflexión verifica ambos descriptores JVM. | Cumplido |
| Identidad sin política | Todos los orígenes y llamadas antiguas probados. | Cumplido |
| Enteros, decimales y truncamiento | Tests existentes más award `1.25`. | Cumplido |
| Cero y negativos | `ExperienceTableTest` completo. | Cumplido |
| Tasa global y Bonus XP | Tests engine y Bonus XP de game. | Cumplido |
| Skills bloqueadas y Assistance | Test unitario del bypass más `RequestAssistTest`. | Cumplido |
| Máximo de XP | `ExperienceTableTest`. | Cumplido |
| Evento único y sin level-up duplicado | `Skills.exp` exactamente una vez y suites `LevelUp*`. | Cumplido |
| Registro y limpieza | Duplicados, orden, unregister, close y `Script.clear`. | Cumplido |
| PlayerSave compatible | Ningún campo persistido nuevo; pruebas de save/account/storage pasan. | Cumplido |
| Fallback seguro | Exception y resultados inválidos vuelven a base; Error se propaga. | Cumplido |
| Servidor principal intacto | No se accedió ni modificó su directorio. | Cumplido |
| Diferencia localizada | Dos archivos nuevos de producción y dos upstream modificados mínimamente. | Cumplido |

## 10. Persistencia y aislamiento

No se añadió `XpOrigin` a `Player`, `Experience.experience`, variables del jugador ni snapshots. El origen sólo vive durante la llamada. La XP efectiva sigue almacenándose en el mismo `IntArray` y usa el mismo pipeline de guardado.

Las pruebas de `PlayerSaveTest`, `AccountStorageTest` y `AccountManagerTest` usaron fixtures y almacenamiento temporal de pruebas. La suite completa también ejecutó `FileStorageTest` y pruebas de snapshots sin fallos. No se accedió a personajes reales, respaldos reales ni al servidor `C:\Juegos\2011Scape\Servidor\void-2.12.0`.

## 11. Riesgos y limitaciones

1. **No hay política custom todavía.** El registro existe, pero producción no registra ningún modificador. Esto es intencional en 9B-1.
2. **Los call sites actuales siguen siendo `UNKNOWN`.** No se clasificaron las 308 concesiones auditadas. `UNKNOWN` está protegido como identidad.
3. **Assistance explícito queda para una fase posterior.** En 9B-1 se protege el tramo bloqueado sin clasificar call sites; `RequestAssistTest` demuestra que el flujo actual no cambia.
4. **Fallback silencioso.** Por requisito no se registra por award. Una fase futura deberá decidir telemetría agregada o warning único durante carga, no dentro del camino caliente.
5. **Registro global por proceso.** Es coherente con las APIs estáticas de scripts actuales; `Script.clear` y las pruebas de lifecycle mitigan fugas entre mundos/tests.
6. **No se realizó QA manual del cliente revisión 634.** La fase no cambia protocolo ni UI, y la equivalencia está cubierta automáticamente; una observación manual puede incorporarse al validar 9B-2.
7. **Pruebas omitidas preexistentes.** Cuatro casos marcados como skipped no reducen la cobertura específica del hook, pero se registran para transparencia.

## 12. Rollback

Después del commit local, el rollback recomendado es revertir ese único commit desde una rama de trabajo:

```powershell
git revert <commit-de-9B-1>
```

No se requiere migración de datos, limpieza de personajes, reversión de TOML ni cambios en el cliente. El informe bajo `build/reports` está ignorado por Git y puede conservarse como evidencia externa.

No se recomienda usar `git reset --hard`, porque no es necesario y podría eliminar trabajo no relacionado.

## 13. Estado Git final

Estado verificado después de crear el commit local:

| Elemento | Estado final |
|---|---|
| Rama | `feature/xp-origin-hook` |
| HEAD | `457c8a1af00ab7cfa36de0c1121c9ff05b1e102f` |
| Mensaje | `feat(engine): add XP origin extension hook` |
| Base inmediata de la fase | `8eab928e435d8657443cb26d71e43b33aee67314` |
| Árbol de archivos seguidos | Limpio (`git status --short` sin salida) |
| Informe | Presente bajo `build/reports`; ignorado por `.gitignore:49` (`/build/`) |
| Publicación | Sin push, PR o merge |
| Remotos y tags | Sin modificaciones |

## 14. Recomendación para Fase 9B-2

Proceder sólo tras revisión del propietario. La siguiente fase debería implementar la política concreta bajo `game/src/main/kotlin/content/custom/xp/`, mantener `UNKNOWN` en identidad y registrar exactamente una política durante bootstrap.

Antes de clasificar call sites masivamente, 9B-2 debería cubrir:

1. Configuración validada y cargada fuera del camino de cada award.
2. Precedencia exacta de master flag, origen y skill.
3. Fallback de configuración inválida con warning único de carga.
4. Composición con `world.experienceRate` y Bonus XP una sola vez.
5. Tests OFF equivalentes a esta fase y tests ON sólo para orígenes explícitos.

No se debe iniciar 9B-2 ni 9B-3 automáticamente desde esta entrega.
