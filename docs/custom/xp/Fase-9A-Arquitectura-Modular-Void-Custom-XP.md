# Fase 9A — Auditoría y diseño de arquitectura modular de Void Custom

## XP por habilidad como primer componente

**Fecha de auditoría:** 2026-11-23  
**Repositorio:** `C:\Juegos\Codigo\void-custom`  
**Rama inspeccionada:** `stable/2.12.0`  
**HEAD y tag custom:** `8eab928e435d8657443cb26d71e43b33aee67314` (`custom-v0.3.0`)  
**Tag oficial de comparación:** `2.12.0`, `ba3331d99af2f15a1d36ce1f84303acf940a4e75`  
**Cliente:** revisión 634  
**Modalidad:** auditoría y diseño; no se implementó ni ejecutó código.

---

## 1. Resumen ejecutivo y recomendación arquitectónica

### Veredicto

**GO condicionado** para construir una plataforma pequeña de extensiones y usar los multiplicadores de XP por habilidad como primer componente.

La condición es aceptar un punto de integración mínimo en el núcleo de XP. Void 2.12.0 no ofrece un hook previo, mutable o cancelable para transformar la cantidad antes de concederla. El listener `Skills.experience` se ejecuta después de actualizar la experiencia y no sirve como interceptor correcto. Tampoco es posible decorar `Experience` mediante Koin sin cambiar el núcleo, porque `Player` y `PlayerSave` construyen `Experience` directamente.

La recomendación es:

1. Mantener la política, validación, configuración y pruebas funcionales en un paquete **propuesto** bajo `game/src/main/kotlin/content/custom/`, porque el generador de scripts sólo escanea `game/src/main/kotlin/content`.
2. Añadir un contrato genérico y estrecho de modificación de XP en `engine`, con implementación identidad por defecto y registro controlado durante la carga de scripts.
3. Hacer una única llamada al contrato desde `Experience.add`, antes de la conversión final a décimas de XP y sin cambiar el camino existente cuando el componente está apagado.
4. No reutilizar ni sobrescribir `Experience.multiplier`: Bonus XP Weekend ya es dueño de ese multiplicador por jugador.
5. No crear un módulo Gradle nuevo. El módulo `game` y su descubrimiento de `Script` son suficientes.

### Conclusión sobre “cero cambios upstream”

**No es viable con corrección completa para XP por habilidad.** Un script puro puede observar la XP después de concedida, pero no puede transformar la concesión original. Compensar después agregando o retirando XP produciría eventos repetidos, niveles transitorios, auditorías incorrectas, problemas con XP bloqueada/asistencia y diferencias de redondeo.

El menor acoplamiento razonable y verificable es un hook genérico en `Experience.add`. Toda la lógica custom puede quedar fuera de las clases oficiales; aun así, el archivo central seguirá siendo un punto de conflicto potencial al integrar tags futuros. No se promete conflicto cero.

### Decisión funcional imprescindible antes de implementar

El contrato actual `Player.exp(skill, amount)` no incluye el origen de la XP. Por tanto, una política central por habilidad afectará, de forma uniforme, toda XP nueva que pase por `Experience.add`: entrenamiento, combate, familiar, Slayer, misiones, lámparas, libros, eventos y asistencia. Si el propietario quiere excluir alguna categoría, se necesitará una fase posterior que añada contexto de origen y migre los call sites correspondientes.

---

## 2. Inventario del código inspeccionado

### 2.1 Descubrimiento de contenido y ciclo de vida

| Evidencia confirmada | Ruta y símbolo | Consecuencia arquitectónica |
|---|---|---|
| `Script` compone las APIs de eventos, variables, inventario, diálogos, timers y recarga de settings. | `engine/src/main/kotlin/world/gregs/voidps/engine/Script.kt:31`, `Script`; `:72`, `Script.interfaces`; `:101`, `checkLoading` | Los handlers se registran mientras carga el contenido. No se pueden añadir arbitrariamente después del arranque. |
| Las clases `Script` se instancian desde `scripts.txt`; sus parámetros de constructor se resuelven con Koin. | `game/src/main/kotlin/ContentLoader.kt:19`, `load`; `:57`, `loadScript` | Es posible una extensión custom autocargada y también inyectar dependencias registradas, si realmente se necesitan. |
| `ScriptMetadataTask` detecta clases concretas que implementan `Script`. | `build-logic/src/main/kotlin/ScriptMetadataTask.kt:16`, `ScriptMetadataTask`; `:104`, `resolveScripts` | El descubrimiento se genera en build, no mediante escaneo de classpath en runtime. |
| El task sólo recibe `src/main/kotlin/content`. El preload también usa el paquete `content`. | `game/build.gradle.kts:65`, `packageName = "content"`; `:70-73`, `scriptMetadata`/`inputDirectory` | Una ruta propuesta `game/src/main/kotlin/custom/...` no se detectaría. Debe usarse `game/src/main/kotlin/content/custom/...` o cambiar Gradle; se recomienda lo primero. |
| Koin carga módulos de engine, game y definiciones antes del contenido. | `game/src/main/kotlin/Main.kt:135-156`, `startKoin`, `loadKoinModules`, `ContentLoader().load()` | Un servicio custom podría registrarse en `gameModule`, pero para este caso no justifica más acoplamiento si un objeto de configuración puro basta. |
| Los módulos actuales registran servicios y loaders. | `game/src/main/kotlin/GameModules.kt:22`, `gameModule`; `engine/src/main/kotlin/world/gregs/voidps/engine/EngineModules.kt:32`, `engineModule` | Koin existe, pero `Experience` no es un bean de Koin. |

### 2.2 Settings, properties y TOML

| Evidencia confirmada | Ruta y símbolo | Lectura técnica |
|---|---|---|
| `Settings.load()` busca `./game.properties`; si no existe usa el recurso `/game.properties`. | `engine/src/main/kotlin/world/gregs/voidps/engine/data/Settings.kt:83,92`, `PROPERTY_FILE_NAME`, `load(fileName)` | La configuración operativa simple recomendada es el `game.properties` externo del servidor. |
| Hay getters tipados y defaults (`Boolean`, `Double`, etc.). | `Settings.kt:35-53`, `getOrNull`, operadores `get`, `bool`, `double` | Las nuevas claves pueden ser opcionales; el flag ausente puede significar OFF y el multiplicador ausente 1.0. |
| `Double.toDoubleOrNull()` acepta valores que después deben validarse semánticamente. | `Settings.kt:45,53` | Se debe rechazar explícitamente `NaN`, infinito, cero, negativos y valores fuera del rango aprobado. |
| En arranque, variables de entorno se copian después de cargar el properties. | `game/src/main/kotlin/Main.kt:128-131`, `settings()` | En arranque, una variable de entorno con el nombre exacto de la clave tiene precedencia sobre el archivo. |
| Existe un comando de recarga que llama `Settings.load()` y luego `SettingsReload.now()`. | `game/src/main/kotlin/content/entity/player/command/ServerCommands.kt:85-87` | Hay recarga real para handlers que se registren en `settingsReload`; no es automática para cualquier objeto. |
| Los `CachedSetting` se refrescan cuando `Settings` carga propiedades. | `engine/src/main/kotlin/world/gregs/voidps/engine/data/CachedSetting.kt:18` | Es útil para hot paths, pero la validación avanzada requiere un snapshot custom. |
| `Settings.load()` agrega propiedades a la instancia existente y no hace `clear()` primero. La recarga del comando tampoco reaplica `System.getenv()`. | `Settings.kt:17-32`; `ServerCommands.kt:86`; `Main.kt:129-130` | Cambiar una clave existente puede recargarse; borrar una clave puede dejar el valor previo, y la precedencia de entorno del arranque no se reconstruye. El reinicio es el procedimiento autoritativo. |
| Los TOML se descubren bajo `storage.data` y se entregan a un grafo de loaders. | `engine/src/main/kotlin/world/gregs/voidps/engine/data/ConfigFiles.kt:14,27,57`; `.../load/Loader.kt:9`; `.../load/LoaderGraph.kt:22,75,82` | TOML es apropiado para definiciones masivas; no aporta ventaja para una docena de tasas simples y exigiría un loader/registro adicional. |

**Conclusión de configuración:** usar properties para XP custom. En una primera implementación, el contrato operativo soportado debe ser **editar y reiniciar**. Se puede diseñar una recarga atómica mediante `settingsReload`, pero no debe prometerse hasta probar también eliminación de claves, entorno y jugadores ya conectados.

### 2.3 Flujo de XP, niveles, cliente y persistencia

| Evidencia confirmada | Ruta y símbolo | Consecuencia |
|---|---|---|
| El único helper `Player.exp` delega en `Experience.add`. | `engine/src/main/kotlin/world/gregs/voidps/engine/entity/character/player/skill/exp/Experience.kt:89` | Punto de entrada compartido por contenido normal. |
| `Experience.add` ignora XP `<= 0`, calcula décimas, aplica `multiplier` y `world.experienceRate`, trata bloqueos y finalmente llama `set`. | `Experience.kt:45-56` | Es el punto mínimo y correcto para un multiplicador adicional. |
| La tasa global existente es `world.experienceRate=1.0`. | `game/src/main/resources/game.properties:92`; `Experience.kt:49` | La tasa custom debe componerse, no sustituir esta tasa. |
| `Experience.set` actualiza sólo si el total queda entre 0 y `MAXIMUM_EXPERIENCE`; luego notifica y audita. | `Experience.kt:27-37`; `:69`, `MAXIMUM_EXPERIENCE` | Debe preservarse la semántica actual, incluido el comportamiento al exceder el máximo. |
| El listener `Skills.experience` recibe `from`/`to` después del cambio. | `engine/src/main/kotlin/world/gregs/voidps/engine/entity/character/player/skill/Skills.kt:33,52` | Es observador posterior, no interceptor. |
| Los listeners actualizan contador, XP vitalicia y cliente. | `game/src/main/kotlin/content/entity/player/stat/Experience.kt:24-32` | Si se ajustara XP después del evento se duplicarían efectos visibles/persistidos. |
| `LevelUp` calcula transiciones a partir de `from`/`to`. | `game/src/main/kotlin/content/entity/player/dialogue/type/LevelUp.kt:41-49` | La cantidad final debe estar decidida antes del único `set`. |
| `Player` construye `Experience()` directamente. | `engine/src/main/kotlin/world/gregs/voidps/engine/entity/character/player/Player.kt:38,43` | Un decorador Koin no puede reemplazarlo con la arquitectura actual. |
| La carga crea `Experience` desde el array persistido y `AccountManager` enlaza jugador/niveles. | `engine/src/main/kotlin/world/gregs/voidps/engine/data/PlayerSave.kt:21,46`; `AccountManager.kt:81-82` | No se requiere migrar el formato de guardado. |
| El guardado copia el array interno de XP; el multiplicador temporal no se persiste. | `PlayerSave.kt:483`; `Experience.kt:18-21` | Sólo la XP final concedida queda persistida. Apagar el feature no revierte XP histórica. |
| La revisión usa `Skill.Constitution`, no `Hitpoints`; hay 25 skills y Dungeoneering llega a 120. | `engine/src/main/kotlin/world/gregs/voidps/engine/entity/character/player/skill/Skill.kt:3-29,31-40` | La clave canónica propuesta es `constitution`; no se debe inventar `hitpoints` como alias funcional sin decisión expresa. |

### 2.4 Modificadores y fuentes de XP existentes

**Confirmado en código:**

- Tasa mundial: `world.experienceRate`, aplicada en `Experience.add`.
- Bonus XP Weekend: `game/src/main/kotlin/content/entity/player/stat/BonusExperience.kt:16-45`. Al aparecer el jugador asigna `experience.multiplier`, lo reduce por tiempo y contabiliza la parte bonus después de la concesión. El flag existente es `events.bonusExperience.enabled=false` en `game.properties:296`.
- Bonus de objetivo de combate: `game/src/main/kotlin/content/entity/player/combat/CombatExperience.kt`, funciones `grant` y `calcBonus`; modifica la cantidad base antes de llamar `player.exp`.
- Combate controlado reparte llamadas separadas entre Attack, Strength y Defence. Constitución se concede aparte.
- Familiares de combate conceden XP al propietario mediante el mismo `grant`.
- Asistencia bloquea la XP del solicitante y concede al asistente mediante `player.exp` en `game/src/main/kotlin/content/social/assist/RequestAssist.kt`.
- Slayer concede mediante `player.exp(Skill.Slayer, slayerExp)` en `game/src/main/kotlin/content/entity/death/NPCDeath.kt:217`.
- Lámparas/libros/effigies llaman el mismo `exp`: por ejemplo `game/src/main/kotlin/content/achievement/AntiqueLamp.kt`, `content/activity/ancient_effigies/AncientEffigies.kt` y `content/activity/event/random/RandomEventGift.kt`.
- Recompensas de misiones también llaman `exp`; por ejemplo Sheep Shearer en `game/src/main/kotlin/content/area/misthalin/lumbridge/FredTheFarmer.kt:312`.

Se localizaron **182 imports** del helper `world.gregs.voidps.engine.entity.character.player.skill.exp.exp` en producción, además de llamadas con receptor explícito. No se encontró otro helper de concesión normal. Los usos de `experience.set` localizados corresponden a comandos, bots, restricciones/tutorial y utilidades de quest; deben quedar fuera del multiplicador por defecto porque fijan un total, no conceden una ganancia ordinaria.

### 2.5 Customizaciones actuales y acoplamiento

La comparación `2.12.0..HEAD` muestra 27 rutas distintas en el diff normal; ignorando dos diferencias sólo de fin de línea quedan 25. Las áreas funcionales relevantes son:

- **Sheep Shearer:** script nuevo `game/src/main/kotlin/content/quest/miniquest/sheep_shearer/SheepShearer.kt`, cambios en `FredTheFarmer.kt` y `SpinningWheel.kt`, más `SheepShearerTest.kt`. El acoplamiento es medio porque dos archivos de contenido upstream fueron modificados.
- **Slayer:** guardia de compatibilidad de revisión 634 en `game/src/main/kotlin/content/skill/slayer/Slayer.kt`, cambios en tablas de maestros y `slayer.tables.toml`, definiciones NPC/área relacionadas y pruebas `SlayerDataCoverageTest.kt`/`SlayerTaskTest.kt`. El acoplamiento es medio-alto por atravesar código y datos upstream.

No se propone mover, desactivar ni reescribir esas funciones. Tampoco pueden convertirse retroactivamente en reversibles mediante un flag sin diseñar y probar una ruta de reversión para sus cambios de datos y comportamiento.

### 2.6 Capacidades de pruebas

- `ExperienceTableTest` ya cubre tasa global, multiplicador Bonus, bloqueos, notificación y helper `Player.exp`: `engine/src/test/kotlin/.../ExperienceTableTest.kt:12,45,56,114,141`.
- `BonusExperienceWorldTest` cubre ON, descenso temporal y OFF/reset: `game/src/test/kotlin/content/entity/player/stat/BonusExperienceWorldTest.kt:14,24,38,50`.
- `SettingsReloadTest` confirma el despacho de handlers: `engine/src/test/kotlin/world/gregs/voidps/engine/data/SettingsReloadTest.kt:7,21`.
- `WorldTest` inicia Koin, módulos, definiciones y `ContentLoader`, y crea jugadores aislados: `game/src/test/kotlin/WorldTest.kt:63,104,158-187`.
- Las pruebas usan rutas temporales y desactivan storage real. La futura implementación debe mantener esa separación y nunca apuntar a `data/saves`.

---

## 3. Diagrama lógico textual del flujo de XP

### 3.1 Flujo actual confirmado

```text
Fuente de XP
  (skill, combate, familiar, Slayer, quest, lámpara, evento, asistencia)
        |
        v
Player.exp(skill, baseXp)
        |
        v
Experience.add(skill, baseXp)
  1. descarta baseXp <= 0
  2. actual = baseXp * 10
              * Experience.multiplier       [Bonus XP por jugador]
              * world.experienceRate         [tasa global]
  3. si skill bloqueada -> Skills.blocked
  4. si no -> Experience.set(current + actual.toInt())
        |
        +--> AuditLog
        +--> Skills.experience(from, to), posterior
                +--> nivel máximo/level-up
                +--> cliente skillLevel
                +--> xp_counter y lifetime_xp
                +--> contador de Bonus XP
                +--> logs/eventos aleatorios/bots
        |
        v
PlayerSave copia el IntArray de XP al guardar
```

### 3.2 Flujo propuesto con feature OFF

```text
Experience.add
  -> hook genérico retorna exactamente baseXp (identidad)
  -> continúa la expresión y el único set actuales
  -> resultado observable equivalente a Void 2.12.0
```

La equivalencia OFF exige que no haya una segunda concesión, que no se cambie el orden aritmético del camino original y que el registro del componente tenga identidad segura aunque falte o falle su configuración.

### 3.3 Flujo propuesto con feature ON

```text
baseXp
  -> Custom XP obtiene factor O(1) para la skill
  -> adjustedBase = baseXp * customSkillFactor
  -> * 10
  -> * Experience.multiplier (Bonus XP existente)
  -> * world.experienceRate
  -> conversión única a Int
  -> bloqueos / set / eventos / cliente / persistencia existentes
```

El custom no debe escribir `Experience.multiplier`. Ese campo permanece reservado al Bonus XP existente.

---

## 4. Comparación de arquitecturas

| Alternativa | Viabilidad real | Archivos/zonas | Acoplamiento y upgrades | Testabilidad y rendimiento | Desactivación | Decisión |
|---|---|---|---|---|---|---|
| 1. Sólo paquete `custom` con scripts existentes | Parcial: el script se descubre si está bajo `content/custom`, pero no hay evento previo mutable. | Sólo archivos nuevos bajo `game/src/main/kotlin/content/custom`. | Muy bajo, pero no resuelve el requisito correctamente. | El ajuste posterior generaría dos eventos, doble notificación, redondeos y estados transitorios. | Flag en script. | **NO GO** para XP. Sí es válida para futuras funciones basadas en eventos ya suficientes. |
| 2. Decorador/servicio DI o interceptor con APIs actuales | No como reemplazo de `Experience`: se construye directamente en `Player`/`PlayerSave`; el evento existente es posterior. | Para forzarlo habría que cambiar constructores, persistencia y módulos o introducir un hook. | Alto si se cambia toda la construcción; innecesario. | Koin por cada award sería coste evitable. Un snapshot inmutable O(1) es mejor. | Dependería del servicio. | **NO GO tal como existe**. Puede usarse DI sólo para servicios auxiliares después de crear el hook. |
| 3. Adaptador central mínimo + política custom | Sí. Un hook identidad antes del cálculo final es suficiente. | Cambio pequeño en `Experience.add`; contrato/registro genérico en engine; implementación y config en `content/custom/xp`. | Conflicto localizado en el archivo central al actualizar upstream; el resto queda aislado. | Unit tests del hook + WorldTest; consulta O(1) sin parsing por award. | Flag OFF devuelve identidad; reinicio autoritativo. | **GO recomendado**. |

### Arquitectura objetivo propuesta

Los siguientes nombres son **propuestas; no existen hoy**:

```text
engine/
  .../skill/exp/ExperienceModifier.kt
      contrato estrecho + registro ordenado + implementación identidad
  .../skill/exp/Experience.kt
      una llamada central al contrato
  .../Script.kt
      integración de ciclo de vida/clear del registro, si es necesaria

game/
  src/main/kotlin/content/custom/xp/
      CustomExperienceRates.kt       Script de registro/ciclo de vida
      CustomExperienceConfig.kt      parseo, validación y snapshot inmutable
  src/test/kotlin/content/custom/xp/
      CustomExperienceConfigTest.kt
      CustomExperienceRatesWorldTest.kt
```

Contrato conceptual propuesto:

- Entrada: jugador, `Skill` y XP base positiva.
- Salida: XP base ajustada, todavía como `Double` y antes de cualquier conversión a décimas.
- Default: identidad.
- Registro: durante carga de `Script`, con identificador estable para impedir duplicados.
- Orden: determinista. Para Fase XP debe haber un solo modificador custom; no crear una cadena genérica innecesaria.
- Ciclo de vida: el registro se limpia con `Script.clear` para no contaminar pruebas o recargas del mundo.
- Fallo: excepción o resultado no finito del modificador debe degradar a identidad, emitir diagnóstico limitado y nunca perder la XP base.

Esta separación deja en upstream únicamente el punto de extensión genérico. La configuración y la política por skill pertenecen al fork custom.

---

## 5. Configuración, feature flags, recarga y seguridad

### 5.1 Formato recomendado

Estas claves son **propuestas y todavía no son funcionales**:

```properties
# Custom XP: OFF por defecto
custom.features.xp.enabled=false
custom.features.xp.defaultMultiplier=1.0

# Overrides opcionales, usando el nombre canónico de Skill en minúsculas
custom.features.xp.skill.attack=1.0
custom.features.xp.skill.strength=1.0
custom.features.xp.skill.defence=1.0
custom.features.xp.skill.constitution=1.0
custom.features.xp.skill.slayer=1.0
custom.features.xp.skill.woodcutting=1.0
custom.features.xp.skill.fishing=1.0
custom.features.xp.skill.runecrafting=1.0
```

No se recomienda TOML para esta primera función: `Settings` ya soporta properties y defaults; un TOML nuevo necesitaría loader, registro, reload y más superficie de upgrade.

### 5.2 Precedencia propuesta

1. Si `custom.features.xp.enabled` falta, es inválido o es `false`: factor custom `1.0`.
2. Si está ON y existe un override válido para la skill: usarlo.
3. Si no existe override: usar `custom.features.xp.defaultMultiplier` si es válido.
4. Si el default falta o es inválido: usar `1.0`.
5. Después componer con `Experience.multiplier` y `world.experienceRate` existentes.

No se propone que `custom.features.xp.defaultMultiplier` reemplace `world.experienceRate`; son capas distintas.

### 5.3 Validación y fallback

Recomendación sujeta a aprobación de producto:

- Aceptar sólo números finitos y estrictamente mayores que cero.
- Rango inicial recomendado: `0.01..100.0`. El límite exacto es decisión del propietario.
- Rechazar `NaN`, `Infinity`, `-Infinity`, cero, negativos y valores fuera de rango.
- Flag inválido = OFF.
- Override inválido = ignorar override y caer al default validado; si éste también falla, `1.0`.
- Construir un snapshot inmutable de 25 factores, indexado por `Skill.ordinal`, y publicarlo de forma atómica.
- Registrar una sola advertencia por clave al cargar/recargar, nunca una advertencia por award.
- No incluir nombres de jugadores ni datos de cuenta en logs de configuración.

### 5.4 Recarga

Void tiene una recarga real de settings, pero con limitaciones comprobadas. Para la primera entrega:

- **Contrato soportado:** cambiar `game.properties` externo y reiniciar el servidor.
- **Posible mejora controlada:** handler `settingsReload` que construya y sustituya atómicamente el snapshot.
- Antes de declarar hot reload soportado hay que probar: edición de valores, eliminación de claves, precedencia de entorno, fallo parcial y jugadores conectados.
- El rollback funcional seguro es `enabled=false` + reinicio. No borra XP ya concedida.

---

## 6. Diseño detallado de XP por habilidad

### 6.1 Semántica propuesta

- Afectar sólo XP futura que entra por `Experience.add`/`Player.exp`.
- No recalcular ni migrar XP histórica.
- No leer ni editar archivos de personajes.
- No multiplicar `Experience.set`, carga desde `PlayerSave`, comandos de nivel, restauraciones o caps de tutorial.
- Aplicar un solo factor custom por skill y una sola vez por award.
- Resolver la skill por el enum real. Canonicalizar con nombres del enum en minúsculas; `constitution` es correcto para esta revisión.
- Mantener el límite y la conducta actual de `Experience.set` en `0..2_000_000_000` décimas internas.

### 6.2 Orden, redondeo y límites

Orden recomendado:

1. Validación actual `baseXp > 0`.
2. Factor custom por skill sobre la XP base.
3. Conversión conceptual a décimas (`* 10`).
4. `Experience.multiplier` existente.
5. `world.experienceRate` existente.
6. Una sola conversión `toInt()` en el lugar equivalente al actual.
7. Flujo de bloqueos o `set` existente.

No se debe redondear cada multiplicador por separado. La conversión final debe conservar el truncamiento actual. Si el total supera el máximo, Void actualmente no aplica ese `set`; esta fase no debe cambiar esa semántica incidental sin una decisión separada.

### 6.3 Cobertura por fuente y decisión recomendada

| Fuente | Flujo actual | Política inicial recomendada | Decisión pendiente |
|---|---|---|---|
| Skilling normal | `Player.exp` | Aplicar factor de skill | Aprobar. |
| Combate y Constitución | `CombatExperience.grant` -> `Player.exp` | Aplicar por skill después del bonus del NPC incluido en la base | Aprobar. |
| Estilo controlado | Tres awards separados | Cada skill usa su factor | Aprobar. |
| Familiar | Award al propietario | Aplicar factor del propietario/skill | Aprobar. |
| Slayer | `Player.exp(Slayer, ...)` | Aplicar factor Slayer | Aprobar; afecta la progresión custom actual. |
| Asistencia | XP redirigida al asistente | Aplicar factor del asistente | Aprobar; el cap se calcula alrededor de cantidades que merecen prueba específica. |
| Quest rewards | Mismo `exp` | **Aplicar en v1** para mantener un único contrato | Confirmar, especialmente Sheep Shearer. |
| Lámparas, libros y effigies | Mismo `exp` | **Aplicar en v1** | Confirmar; los textos muestran XP base y podrían no coincidir con el total efectivo. |
| Eventos/actividades | Mismo `exp` | **Aplicar en v1** | Confirmar. |
| `Experience.set` y carga | No pasa por `add` | No aplicar | Recomendado y no negociable para evitar corrupción/migración. |

La recomendación de aplicar a todas las fuentes de `add` mantiene coherencia con `world.experienceRate` y Bonus XP actuales. Excluir fuentes sin contexto provocaría heurísticas frágiles. Si se requiere distinción, la siguiente evolución debe introducir un tipo de origen explícito y migrar call sites gradualmente.

### 6.4 Observabilidad mínima

- Al arranque: feature OFF/ON, default efectivo, número de overrides válidos y versión del esquema de configuración.
- En configuración inválida: clave y razón, sin valor sensible ni jugador.
- En fallback por excepción: contador agregado/rate-limited; conceder XP original.
- No loguear cada award. `AuditLog.event(player, "exp", skill, experience)` ya existe y registra la cantidad base; cambiar su significado es otra decisión.

---

## 7. Patrón reutilizable para otros componentes custom

### Límites de propiedad

**Upstream/engine:** contratos mínimos, neutrales y con default seguro; nunca reglas específicas del servidor custom.  
**Custom/game:** feature flags, configuración, política, telemetría de la función y pruebas de comportamiento.  
**Datos custom:** sólo cuando la función realmente sea data-driven y tenga estrategia de activación/reversión comprobada.

### Reglas propuestas

1. Una función por paquete `content.custom.<dominio>` y una suite propia.
2. Un punto de integración por dominio; no un “event bus universal”.
3. Default OFF para toda función nueva con impacto jugable.
4. Camino OFF equivalente y cubierto por prueba.
5. No reutilizar estado propiedad de otra función; ejemplo: XP custom no usa el campo de Bonus XP.
6. No añadir módulo Gradle mientras `game` y `content` sean suficientes.
7. No usar reflexión adicional, parcheo binario ni copias completas de clases upstream.
8. Cada PR debe declarar archivos upstream tocados y un procedimiento de rollback.
9. Bots, PvE/mazmorra, drops y UI deben tener hooks propios si los existentes no alcanzan; no deben depender del hook de XP.
10. Un feature flag no deshace por sí solo TOML ya cargado, estados persistidos, quests o cambios estructurales. Toda función que modifique datos necesita diseño explícito de reversión o aceptación de reinicio/reload.

### Guía de upgrade upstream

1. Comparar el nuevo tag contra los puntos de integración (`Experience.add`, `Script`, metadata y settings).
2. Confirmar que `Player.exp` sigue delegando en un único punto y buscar nuevos writes directos a experiencia.
3. Confirmar el orden de Bonus XP, tasa mundial, bloqueos, `set` y listeners.
4. Ejecutar primero pruebas de contrato OFF, después composición ON y finalmente `WorldTest` completo.
5. Revisar cambios upstream en `Skill`, persistencia y cliente antes de resolver conflictos.
6. Mantener el hook en un commit/PR separado de la política custom para que el conflicto sea pequeño y auditable.

---

## 8. Plan de pruebas, compatibilidad y mantenimiento

### 8.1 Pruebas unitarias del contrato central

- Sin modificador registrado: identidad exacta.
- Componente registrado pero flag OFF: mismos enteros internos que Void 2.12.0 para valores enteros y decimales.
- XP cero/negativa sigue ignorada.
- Skill bloqueada conserva la cantidad efectiva correcta para Assistance.
- Máximo de XP conserva conducta actual.
- Excepción o resultado inválido del modificador cae a identidad.
- Registro/clear no duplica modificadores entre tests.

### 8.2 Pruebas unitarias de configuración

- Flag ausente, false, true e inválido.
- Default ausente/válido/inválido.
- Override por skill y fallback.
- Todas las 25 skills; `constitution` y `runecrafting` de forma explícita.
- Rechazo de NaN, infinitos, cero, negativos y extremos.
- Snapshot inmutable y sustitución atómica.

### 8.3 Pruebas `WorldTest`

- OFF: Attack, Constitution y una skill no combat equivalen exactamente al baseline.
- ON: override de Attack, Strength sin override y fallback global.
- Composición: `custom factor * BonusExperience.multiplier * world.experienceRate`, sin doble aplicación.
- Fuentes representativas: Woodcutting/crafting, combate controlado, familiar, Slayer, Sheep Shearer, lámpara y asistencia.
- Cliente/listeners: un único evento `Skills.experience`, un único level-up y contador/lifetime correctos.
- Persistencia aislada: guardar/recargar en directorio temporal y verificar sólo el total final; no persistir snapshot ni multiplicador.
- Recarga, si se implementa: cambios válidos, inválidos y rollback con jugador conectado.
- Descubrimiento: `ContentLoader` incluye el script custom generado.

### 8.4 Compatibilidad

La prueba de compatibilidad crítica en cada tag es la equivalencia OFF. También debe existir una comprobación estática o de test que falle si:

- `Experience.add` cambia de firma o desaparece;
- aparece otro camino de concesión normal que no usa el hook;
- Bonus XP cambia de propietario/orden;
- cambia el enum `Skill` o su ordinalidad;
- `PlayerSave` cambia el formato de XP.

No se ejecutaron estas pruebas en Fase 9A por restricción expresa; aquí sólo se define el plan.

---

## 9. Matriz de riesgos y limitaciones

| Riesgo | Prob. | Impacto | Prioridad | Mitigación |
|---|---:|---:|---:|---|
| Conflicto en `Experience.add` al integrar un tag | Media | Alta | P1 | Hook de una llamada, PR separado, prueba OFF y revisión manual del orden. |
| Doble multiplicación con Bonus XP o world rate | Media | Alta | P1 | Factor custom separado; prueba de composición con valores no triviales. |
| Aplicar tasas a quests/lámparas sin intención | Alta si no se decide | Alta | P1 | Aprobación de política antes de implementar; no inferir origen. |
| Assistance calcula cap/transferencia con cantidad inesperada | Media | Alta | P1 | Tests de XP bloqueada, cap diario y actor que recibe el factor. |
| Configuración inválida causa pérdida/exceso de XP | Media | Alta | P1 | Snapshot validado, límites, identidad como fallback, logs rate-limited. |
| Hot reload deja clave borrada o no reaplica entorno | Media | Media | P1 | Reinicio como contrato v1; pruebas antes de anunciar recarga. |
| XP ya otorgada permanece tras rollback | Cierta | Media | P1 | Comunicar rollback funcional; no intentar quitar XP histórica. |
| Lámpara muestra texto de XP base, no total efectivo | Alta con factor != 1 | Media | P2 | Decidir si el texto permanece base o se expone resultado efectivo en una fase con API adecuada. |
| Award empuja el total sobre máximo y se descarta | Baja | Media | P2 | Preservar upstream y añadir test; cualquier clamp sería cambio aparte. |
| Performance en hot path | Baja | Media | P2 | `DoubleArray`/snapshot O(1), sin parsing, mapas ni logs por award. |
| Fuga de registro entre mundos/tests | Media | Media | P2 | Integrar el clear con ciclo de vida de `Script` y test de recarga. |
| Nuevos caminos de XP upstream eluden el hook | Media | Media | P2 | Búsqueda de `experience.set`, writes al array y helper `exp` en cada upgrade. |
| Feature flags se usen como falsa reversión de datos/quests | Media | Alta | P2 | Regla arquitectónica: flags sólo revierten caminos diseñados para ello. |
| Cambiar ordinal de `Skill` afecta snapshot indexado | Baja | Alta | P2 | Construir snapshot desde `Skill.entries` y test de cobertura completa. |

Limitaciones reales:

- No hay contexto de origen de XP.
- No hay interceptor previo existente.
- `Experience.multiplier` es único por jugador y está ocupado por Bonus XP.
- La recarga de properties no limpia claves ausentes.
- El audit log actual conserva la XP base, no necesariamente la efectiva.
- OFF detiene efectos futuros; no revierte persistencia histórica.

---

## 10. Fases futuras, criterios GO/NO-GO y decisiones de producto

### Fase 9B-1 — Contrato mínimo de XP

**Alcance:** hook genérico identidad, integración central y pruebas de contrato; sin política custom activa.  
**GO:** equivalencia OFF exacta, un único evento, Bonus XP y bloqueos sin regresión.  
**NO-GO:** el hook exige cambiar formato de `PlayerSave`, múltiples call sites o construcción completa de `Player`.  
**PR:** pequeño y separado; rollback mediante revert del hook, sin datos afectados.

### Fase 9B-2 — Configuración y política por skill

**Alcance:** paquete `content.custom.xp`, flag default OFF, snapshot validado, overrides, tests unitarios/WorldTest.  
**GO:** política de fuentes aprobada, fallback seguro y composición probada.  
**NO-GO:** se exige excluir categorías sin aceptar añadir contexto de origen.  
**PR:** sólo custom más bloque documentado en properties. Rollback: flag OFF + reinicio.

### Fase 9C — QA aislado y despliegue gradual

**Alcance:** servidor de QA, personajes sintéticos, una skill piloto, verificación de logout/restart.  
**GO:** XP esperada, persistencia correcta, sin doble evento ni regresión de Sheep/Slayer.  
**NO-GO:** discrepancia en contadores, Assistance, Bonus XP, cliente o guardado.  
**Rollback:** flag OFF + reinicio; conservar XP ya otorgada y documentarla.

### Fase opcional posterior — Contexto de origen

Sólo si producto necesita tasas distintas para entrenamiento, quest, lamp, evento, asistencia, etc. Debe introducir un origen explícito y migrar fuentes de forma incremental. No debe resolverse con stack traces, nombres de paquete o heurísticas.

### Tamaño recomendado de los prompts de implementación

Convienen **varias fases pequeñas**, no un prompt único grande:

1. Hook central + pruebas de identidad.
2. Configuración/política custom + pruebas de composición.
3. QA/rollout y, si se aprueba, recarga en caliente.

Esto separa el mayor riesgo de upgrade del comportamiento jugable y permite revisar/retroceder cada capa.

### Preguntas de producto imprescindibles

1. ¿El multiplicador debe afectar quests, Sheep Shearer, lámparas, libros, effigies y eventos, o sólo entrenamiento?
2. ¿Debe aplicarse a XP recibida mediante Assistance y al familiar?
3. ¿El texto de recompensas debe mostrar XP base o efectiva?
4. ¿Se permite reducir XP con factores entre 0 y 1?
5. ¿Cuál es el máximo operativo permitido: 10x, 100x u otro?
6. ¿Se exige hot reload o el reinicio es aceptable para v1?
7. ¿`constitution` será la única clave aceptada o se desea un alias documentado `hitpoints`?
8. ¿La tasa custom debe multiplicarse con `world.experienceRate` y Bonus XP, como se recomienda, o alguna capa debe prevalecer/excluirse?

---

## 11. Estado Git final y confirmación de integridad

### Estado verificado

- Rama: `stable/2.12.0`.
- HEAD: `8eab928e435d8657443cb26d71e43b33aee67314`.
- `custom-v0.3.0^{}`: `8eab928e435d8657443cb26d71e43b33aee67314`.
- Base oficial `2.12.0^{}`: `ba3331d99af2f15a1d36ce1f84303acf940a4e75`.
- Árbol Git: limpio antes y después de la auditoría.
- Informe ignorado por Git mediante `.gitignore:49`, regla `/build/`.

### Confirmación explícita

No se modificó código Kotlin, TOML, properties, Gradle, scripts, pruebas, ramas, commits, tags ni remotos. No se ejecutó Gradle, compilaciones, pruebas, servidor ni cliente. No se abrió ni modificó `data/saves`, `data/cache`, respaldos, cuentas ni el servidor estable externo.

La única escritura realizada fue este informe:

`C:\Juegos\Codigo\void-custom\build\reports\Fase-9A-Arquitectura-Modular-Void-Custom-XP.md`

Ninguna propuesta descrita en este documento está implementada.
