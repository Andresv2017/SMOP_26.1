# Verificación del Tangoftero (Fase 3)

## Arranque

```bash
./gradlew runClient
```

Crea un mundo **creativo, superplano, paz desactivada** (necesitas hostiles para las pruebas de no-muertos).

Preparación:

```
/gamerule doDaylightCycle false
/give @s smop:tangoftero_spawn_egg 16
/give @s smop:tangoftero_egg 8
/give @s minecraft:rabbit 16
/give @s minecraft:chicken 16
/give @s minecraft:rotten_flesh 16
```

---

## 1. Render y variantes

| Qué hacer | Qué debe pasar |
|---|---|
| Suelta 8–10 con el huevo de spawn | Modelo adulto correcto, sin texturas rosas. Deberían salir **colores distintos** (9 variantes aleatorias) |
| Mírale la cabeza y muévete alrededor | El cuello te sigue con la mirada (capa aditiva `lookAt`, tope 30°) |

⚠️ Si un mob sale con textura de "modelo faltante", el nombre de variante y el fichero no coinciden — el renderer compone la ruta desde el nombre del enum en minúsculas.

## 2. Locomoción — es donde se ve si el blending funciona

| Qué hacer | Qué debe pasar |
|---|---|
| Déjalo quieto | Clip `idle` |
| Déjalo vagar | `walk`, con transición suave desde idle |
| Métele un zombi cerca (que lo persiga) | `sprint` — se activa con `isAggressive()` |
| Métele en agua | `swim` |

⚠️ **Lo más probable que falle aquí:** que idle y walk **parpadeen** alternándose cada pocos ticks. Significaría que el hold-timer de `isMoving()` (6 ticks) es demasiado corto o que el umbral de velocidad está mal. Es exactamente el problema que el flag sincronizado existe para evitar, así que si parpadea, avísame.

## 3. Ataque — recién reestructurado, sin probar

```
/deluxelib debug hitboxes
```

Suelta un zombi al lado de un Tangoftero salvaje. Con el debug activo verás el **arco del mordisco** dibujado con partículas.

| Qué comprobar |
|---|
| El clip `attack` se reproduce entero, una vez por mordisco |
| El daño cae **cuando las mandíbulas se cierran**, no antes ni después (frames 6–8 de 17) |
| Un zombi que retrocede durante el windup **esquiva** el golpe |
| Los Tangofteros **no se muerden entre ellos** (hay filtro) |
| La locomoción se detiene durante el mordisco (no camina mordiendo) |

⚠️ **Los números del arco son estimaciones mías, nunca ajustados.** `sector(2.0F, 70.0F)` con anchor `(0.6, 0.0, 0.5)`. Si el arco queda flotando en el sitio equivocado o no alcanza, dime qué ves con el debug y lo ajusto — es literalmente para lo que sirve ese comando.

## 4. Sueño — el sistema reescrito, sin probar en runtime

```
/time set night
```

Aléjate un poco y **no le pegues**. El Tangoftero **no se despierta por jugadores** (implementa `ISleepAwareness` devolviendo `false`), así que puedes quedarte mirando.

Secuencia esperada:

1. ~5 s tranquilo y sin objetivo → clip `preparing_sleep` (1 s)
2. → clip `sleep` en bucle, **y el mob deja de moverse del todo**
3. `/time set day` → clip `awakening` (1 s) → vuelve a la normalidad

Despertar por amenaza: con uno dormido, `/summon minecraft:zombie ~ ~ ~` a menos de 4 bloques → debe pasar a `awakening`.

Despertar por daño: pégale mientras duerme → `awakening`.

⚠️ **Comprobación clave:** un mob dormido **no debe pasear**. Si camina dormido, el `SleepGoal` no está preemptando la locomoción y hay que revisar prioridades.

⚠️ Tras despertarlo, debe tardar ~5 s en volver a intentar dormirse (ventana de calma). Si se duerme instantáneamente, el contador de interrupción no se está reiniciando.

## 5. Domesticación, cría y huevos

| Qué hacer | Qué debe pasar |
|---|---|
| Dale **conejo** a uno salvaje | 1 de cada 3 lo doma (partículas de corazón / humo) |
| Dale **pollo** a dos adultos | Se emparejan — **no nace cría directa** |
| Espera tras el emparejamiento | Uno pone un bloque `tangoftero_egg` en el suelo |
| Espera ~45 s sobre el huevo | Pasa por 3 fases visuales de agrietado y eclosiona |
| Coloca varios huevos en el mismo bloque | Se acumulan hasta 4 (como huevos de tortuga) |

✅ **Riesgo de crash del bebé: resuelto antes de la prueba.** Los clips del bebé animaban `arms`, `epiglotis` y `tail_tip`, huesos que su propio modelo no tiene — herencia del export de Blockbench. En 1.20.1 se ignoraban en silencio; en 26.1 el bake lanza `Cannot animate tail_tip, which does not exist in model` y tira el cliente. Canales eliminados de los 12 clips y verificado con `tools/check-bones.sh`. Aun así, **la eclosión sigue siendo la prueba que más quiero que hagas**: es lo único que ejerce el modelo bebé de verdad.

Alternativa rápida para probar el bebé sin criar:
```
/summon smop:tangoftero ~ ~ ~ {Age:-24000}
```

## 6. Nido y bandada

| Qué hacer | Qué debe pasar |
|---|---|
| Pon un huevo cerca de adultos salvajes | Se quedan rondándolo (radio 3) |
| **Rompe** el huevo delante de ellos | **Huyen** del sitio (reacción `FLEE`) |
| Pega a uno salvaje con otros cerca | Los demás te toman como objetivo (`AssistFlockGoal`, radio 10) |
| Prueba lo mismo con uno **domado** | **No** debe asistir — los domados no entran en peleas de bandada |

## 7. Rugido antiundead

Requiere: **domado**, **adulto**, y 30 s desde el último rugido.

1. Rodéate de 3–4 zombis
2. Dale **carne podrida** al Tangoftero domado
3. ~0,75 s después: clip `bite` (mordisco de comer) y se cura
4. ~0,75 s más: **rugido** (4 s)
5. 2 s después de empezar el rugido: los zombis en 10 bloques **salen huyendo**

⚠️ **Comprobación específica:** durante los 4 s de rugido el mob debe quedarse **clavado en el sitio** y no reproducir idle por debajo. Esto era un fallo heredado de 1.20.1 que corregí; si lo ves pasear o balancearse mientras ruge, la corrección no funcionó.

## 8. Órdenes (sit / follow / wander)

Con uno **domado** y la **mano vacía**, shift + clic derecho repetidamente:

```
wandering → staying → following → wandering → ...
```

Cada cambio muestra un mensaje en la barra de acción. En `staying` no se mueve; en `following` te sigue; en `wandering` te ignora y deambula.

⚠️ Con comida en la mano gana la alimentación, no la orden. Mano vacía.

## 9. Muerte y loot

| Qué comprobar |
|---|
| Al morir reproduce el clip `death` (1,5 s) y el cadáver **permanece** hasta que termina, en vez de desaparecer a los 20 ticks de vanilla |
| **No** hace el giro de 90° de vanilla al morir |
| El cadáver **no** sigue mirándote con la cabeza (additivo bloqueado) |
| Dropea 1–2 `tango_leg` y 0–2 `tango_feather` |

---

## Resumen de riesgo

| Zona | Riesgo | Por qué |
|---|---|---|
| Arco del mordisco | 🟡 Medio | Números sin ajustar |
| Sueño | 🟡 Medio | Sistema reescrito, sin probar en runtime |
| Blending idle↔walk | 🟡 Medio | El hold-timer podría necesitar ajuste |
| Eclosión / bebé | 🟡 Medio | El crash por huesos ya está corregido, pero el modelo bebé no se ha renderizado nunca |
| Render / variantes | 🟢 Bajo | Ya verificado: el cliente carga sin errores |

## Ya verificado desde aquí

- Compila.
- El servidor dedicado arranca con cero ERROR/WARN: registros, datapack y tag `smop:egg_blocks` correctos.
- El cliente arranca hasta el menú con cero ERROR/WARN: **todos** los modelos, blockstates y texturas cargan.
- Los 12 estados del blockstate del huevo existen y apuntan a modelos que existen.
- Las 9 texturas de variante + la del bebé están donde el renderer las busca.
