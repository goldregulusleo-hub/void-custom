# RuneScape Custom — Fase 9C-1

## QA funcional reportado del piloto TRAINING

Fecha de documentación: 24 de noviembre de 2026

## 1. Naturaleza de la evidencia

Este documento conserva resultados de QA manual proporcionados por el propietario del proyecto después de la Fase 9B-3A. Son **pruebas manuales reportadas por el propietario**; Codex no ejecutó el servidor, el cliente ni estas acciones de juego y no inspeccionó archivos de personajes.

La fecha exacta de la ejecución manual no fue suministrada. El registro se versiona durante la Fase 9B-3B para preservar la evidencia antes de continuar con otras categorías de experiencia.

## 2. Entorno reportado

- Servidor de desarrollo separado del servidor principal protegido.
- Personaje destinado a pruebas.
- Cliente RuneScape revisión 634.
- Configuración operativa en un `game.properties` local ignorado por Git.
- `world.experienceRate = 1.0`.
- Bonus XP Weekend desactivado.
- No se versionaron la configuración local, datos personales o archivos del personaje.

## 3. Configuración ON utilizada

| Capa | Factor |
|---|---:|
| Origen `TRAINING` | 2× |
| Woodcutting | 3× |
| Fishing | 4× |
| Tasa mundial | 1× |

Factores efectivos esperados:

- Woodcutting: `2 × 3 = 6×`.
- Fishing: `2 × 4 = 8×`.

## 4. Resultados reportados

| Escenario | Acción observada | Resultado reportado | Evaluación |
|---|---|---|---|
| Woodcutting OFF | Cortar 2 Yew logs | `2 × 175 = 350 XP` | Coincide con XP oficial. |
| Fishing OFF | Pescar 2 Sharks | `2 × 110 = 220 XP` | Coincide con XP oficial. |
| Woodcutting ON | Cortar 1 Yew log | `175 × 6 = 1.050 XP` | Coincide con TRAINING 2× y Woodcutting 3×. |
| Fishing ON | Pescar 1 Shark | `110 × 8 = 880 XP` | Coincide con TRAINING 2× y Fishing 4×. |
| Reversión OFF | Volver a desactivar Custom XP | Woodcutting y Fishing volvieron a conceder la XP original | Confirma reversibilidad operativa reportada. |

## 5. Conclusión del QA reportado

La evidencia manual es coherente con la composición implementada en 9B-2 y con la clasificación `TRAINING` del piloto 9B-3A:

```text
XP efectiva = XP base × factor de origen × factor de habilidad
```

Con los switches desactivados, el comportamiento volvió a identidad. Con los switches activados, cada factor se aplicó una sola vez en los ejemplos reportados.

Este registro complementa, pero no sustituye, las pruebas automatizadas y suites completas documentadas en `docs/custom/xp/Fase-9B-3A-Training-Pilot.md`.
