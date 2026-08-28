# Barra de habilidades del jinete — diseño

**Objetivo:** sustituir las boss bars vanilla que marcan la recarga de las habilidades del Hell Hippo por una barra propia dibujada sobre la hotbar, y dejar el componente lo bastante genérico para que cualquier montura futura declare sus habilidades y obtenga sus barras sin tocar el HUD. De paso, bajar la recarga del ataque melee montado.

**Stack:** NeoForge 26.1, Java 21, DeluxeLib 1.0.0 (`NetworkCreator`, `AbstractNetworkPacket`).

## El problema

`RiderAbility` mezcla dos responsabilidades: la lógica de recarga (servidor) y su presentación, que hoy es un `ServerBossEvent`. Eso obliga a que la única forma de ver un cooldown sea una barra de jefe en la parte alta de la pantalla — dos de ellas cuando Fear y Charge recargan a la vez, con el aspecto de un combate contra el Wither.

`HellHippoRiderHud` ya existe pero es un tanteo: pinta `hh_testbar.png` (un rectángulo rojo plano de 256×64) siempre que el jugador va montado, sin leer ningún progreso.

## Restricciones globales

- **Este proyecto no tiene framework de tests.** No hay `src/test`. La verificación es `./gradlew compileJava` en verde más la comprobación en juego que cierra el trabajo. No inventes un framework ni añadas dependencias de test.
- **`RiderControllable` lo puede implementar cualquier montura.** Todo lo que se le añada entra con el comportamiento de hoy por defecto: hoy solo lo implementa `HellHippoEntity`, y así debe seguir siendo válido para una montura sin habilidades.
- **El cliente nunca es la fuente de verdad del cooldown.** Descuenta en local para que la barra vaya suave, pero el servidor decide si una habilidad se puede usar; `tryUse()` sigue siendo la única puerta.
- Comentarios en el estilo de la casa: explican **por qué**, no qué.
- Commits frecuentes, uno por tarea.

## Estructura de ficheros

| Fichero | Responsabilidad |
|---|---|
| `entity/rider/RiderAbility.java` | Solo recarga: `tryUse()`, `tick()`, save/load. Pierde la boss bar |
| `entity/rider/RiderAbilities.java` *(nuevo)* | Un único helper `sync(mount, rider)` que arma y envía el paquete |
| `entity/RiderControllable.java` | Nuevo `riderAbilities()` con default vacío |
| `network/packet/RiderAbilityStateClientPacket.java` *(nuevo)* | Servidor→jinete: estado completo de las habilidades de su montura |
| `network/SMOPNetwork.java` | Registrar el paquete nuevo |
| `client/rider/RiderAbilityTracker.java` *(nuevo)* | Cliente: guarda el último estado y lo descuenta por tick |
| `client/rider/RiderAbilityHud.java` *(nuevo)* | Cliente: dibuja una barra por habilidad sobre la hotbar |
| `client/hellhippo/HellHippoRiderHud.java` *(se borra)* | Absorbido por `RiderAbilityHud` |
| `entity/hellhippo/HellHippoEntity.java` | Declarar sus habilidades; bajar el cooldown melee |
| `assets/smop/textures/gui/rider_ability_bar.png` *(nuevo)* | Marco + relleno |
| `assets/smop/textures/entity/hell_hippo/hh_testbar.png` *(se borra)* | Placeholder |

---

## 1. Servidor — `RiderAbility`

Se queda con la recarga y nada más.

**Fuera:** `ServerBossEvent bar`, `BossEvent.BossBarColor color`, el campo `watcher`, el método `hide()` y el import de `ServerBossEvent`.

**Dentro:**

| Campo | Tipo | Para qué |
|---|---|---|
| `id` | `String` | Identidad estable en el paquete (`"fear"`, `"charge"`). No es clave de traducción; el HUD no escribe texto |
| `tintARGB` | `int` | Color con el que el HUD multiplica el relleno. Recoge la semántica que tenían las boss bars: morado para Fear, rojo para Charge |
| `cooldownTicks` | `int` | Ya existía |
| `remaining` | `int` | Ya existía |
| `mount` | `Mob` | Ya existía; ahora sirve para saber a qué entidad pertenece el estado que se envía |

Firma nueva del constructor:

```java
public RiderAbility(Mob mount, String id, int cooldownTicks, int tintARGB)
```

El parámetro `title` desaparece: era el nombre que mostraba la boss bar, y el HUD nuevo no dibuja texto.

**`tryUse()`** conserva la guarda de `isReady()` y fija `remaining = cooldownTicks`. No envía nada: una habilidad no conoce a sus hermanas, y el paquete lleva el estado de todas — quien reenvía es la montura, tras ver que `tryUse()` devolvió `true` (§3).

**`tick(Player rider)`** solo decrementa. Toda la gestión de `watcher` — añadir y quitar jugadores de la boss bar — desaparece.

**`save`/`load`** no cambian. El NBT (`FearCooldown`, `AttackCooldown`) se mantiene, así que los hipopótamos ya guardados cargan sin migración.

**`controllerOf(Mob)`** se mantiene tal cual; `HellHippoEntity.tick()` la sigue usando.

## 2. Contrato — `RiderControllable`

```java
default List<RiderAbility> riderAbilities() {
    return List.of();
}
```

El orden de la lista es el orden de apilado en pantalla, de abajo a arriba: el índice 0 es la barra pegada a la hotbar. `HellHippoEntity` devuelve `List.of(mountedAttack, fearPulse)` — el ataque abajo, porque es el que se mira a cada golpe, y Fear encima.

El default vacío es lo que hace que el cambio sea inocuo para una montura que solo quiera `onRiderAction`.

## 3. Sincronización

`RiderAbilityStateClientPacket` (S2C) lleva el **estado completo**, no un delta:

| Campo | Encoding |
|---|---|
| `mountId` | `varInt` — id de entidad de la montura |
| tamaño de la lista | `varInt` |
| por entrada: `id` | `utf` |
| por entrada: `remaining` | `varInt` |
| por entrada: `total` | `varInt` |
| por entrada: `tintARGB` | `int` |

Son dos entradas para el hipopótamo, del orden de 40 bytes, y solo viaja al jinete. Que sea estado completo y no delta elimina de raíz la clase de bugs de desincronización acumulada.

**Quién lo arma.** `RiderAbilities.sync(RiderControllable mount, ServerPlayer rider)`: recorre `mount.riderAbilities()`, construye el paquete y lo envía a ese jugador. Si la lista está vacía no envía nada. Es el único sitio que conoce el formato, así que una montura futura no reimplementa nada — llama a `sync` en los mismos dos momentos.

**Cuándo se llama, desde `HellHippoEntity`:**

1. **Al tomar el control.** `tickRiddenState()` ya corre cada tick del lado servidor y ya distingue si hay un `ServerPlayer` controlando. Se le añade memoria del último controlador (`@Nullable UUID lastRiderId`); cuando cambia a un jinete no nulo, `sync`. Esto cubre montarse con una recarga a medias, el relog y el cambio de dimensión — casos en los que el cliente arranca sin nada.
2. **Al usar una habilidad.** `releaseFearPulse()` y `strikeFromSaddle()` ya comprueban `tryUse()`; tras el `true`, `sync`.

Entre esos dos momentos no se manda nada. El cliente descuenta solo.

`remove()` deja de llamar a `fearPulse.hide()` y `mountedAttack.hide()`: no hay nada que ocultar. El HUD desaparece por sí solo en cuanto el jugador deja de ir montado (§4).

## 4. Cliente — `RiderAbilityTracker`

Estado estático de un único jinete: el jugador local solo monta una cosa a la vez.

```java
int mountId;                 // -1 = sin estado
List<Entry> entries;         // id, remaining, total, tintARGB
```

- **`accept(packet)`** reemplaza el estado entero.
- **`tick()`**, en `ClientTickEvent.Post`: si `Minecraft.getInstance().player` es nulo, o no va montado, o el id de su vehículo no coincide con `mountId`, limpia el estado y sale. Si coincide, decrementa cada `remaining` con suelo en 0.

Esa comprobación del vehículo es la que hace innecesario el antiguo `hide()`: desmontarse, morir, cambiar de mundo o desconectar dejan de coincidir, y el estado se descarta en el tick siguiente.

- **`progress(Entry)`** devuelve `1.0F` si `total <= 0`, si no `1.0F - (float) remaining / total`. **Se llena mientras se recupera**, igual que hacía la boss bar: una barra llena se lee como "lista", no como "gastada".

El tracker no interpola con el parcial del frame. A 20 tick/s y con una recarga de 20 ticks el escalón es de 1/20 del ancho interior; si en juego se ve a saltos, el ajuste es interpolar en `RiderAbilityHud` con `partialTick`, no cambiar el tracker.

## 5. Cliente — `RiderAbilityHud`

Se suscribe a `RenderGuiLayerEvent.Post` y actúa solo sobre `VanillaGuiLayers.HOTBAR`, igual que hace hoy `HellHippoRiderHud`. Sale temprano si el tracker está vacío.

**Textura:** `smop:textures/gui/rider_ability_bar.png`. Es un elemento de interfaz, no una textura de entidad, así que vive bajo `textures/gui/` y no bajo `textures/entity/hell_hippo/`.

Layout del sheet, en dos filas que **comparten encuadre**:

```
v = 0        fila 0: el marco ornamentado completo, con el canal interior vacío
v = BAR_H    fila 1: solo el relleno, alineado píxel a píxel con la fila 0
                     (todo lo que no es canal interior queda transparente)
```

Que las dos filas estén alineadas es lo que permite recortar el relleno con las mismas coordenadas que el marco: no hacen falta offsets de origen distintos, solo saber dónde cae el canal.

**Constantes.** `BAR_WIDTH`, `BAR_HEIGHT`, `SHEET_WIDTH` y `SHEET_HEIGHT` salen de las dimensiones reales del PNG. `INNER_X`, `INNER_Y`, `INNER_WIDTH` e `INNER_HEIGHT` describen el rectángulo del canal interior en coordenadas locales de la barra, y se derivan midiendo en la fila 1 la caja delimitadora de los píxeles no transparentes. Esa medición es la primera tarea del plan y sus cuatro valores quedan escritos como constantes; no se estiman a ojo.

**Dibujado, por cada entrada `i` de abajo a arriba:**

```
x = (guiWidth - BAR_WIDTH) / 2
y = guiHeight - HOTBAR_HEIGHT - GAP - (i + 1) * (BAR_HEIGHT + BAR_GAP)
```

con `HOTBAR_HEIGHT = 22` y `GAP = 2` como hoy, y `BAR_GAP = 2` entre barras.

1. **Marco:** `blit` de la fila 0 completa en `(x, y)`.
2. **Relleno:** ancho `Math.round(INNER_WIDTH * progress)`; si sale 0, se omite. `blit` de la fila 1 desde `(INNER_X, BAR_HEIGHT + INNER_Y)` a `(x + INNER_X, y + INNER_Y)`, con ese ancho y alto `INNER_HEIGHT`, multiplicado por `tintARGB`.

El marco va debajo y siempre entero, así que los remates de los extremos no se cortan nunca — que es la razón de partir la textura en dos filas en vez de tener una versión vacía y otra llena.

El HUD no dibuja texto. Las habilidades se distinguen por su tinte y por su posición fija en la pila.

## 6. Cooldown del melee montado

`MOUNTED_ATTACK_COOLDOWN_TICKS`: `60` → `20`.

La animación de mordida (`ATTACK_SECONDS = 0.7F`, 14 ticks) es el suelo real: por debajo, un ataque nuevo reiniciaría el clip a media dentellada. 20 ticks dejan seis de respiro y encadenan mordiscos casi sin pausa, frente a los ~2.3 s de espera muerta de hoy.

`FEAR_COOLDOWN_TICKS` se queda en `300`.

## 7. Verificación en juego

Con `./gradlew compileJava` en verde:

1. Montar el hipopótamo: aparecen dos barras apiladas sobre la hotbar, ambas llenas, y **ninguna boss bar arriba**.
2. Atacar: la barra de abajo se vacía de golpe y se rellena en 1 segundo. Encadenar mordiscos y confirmar que la animación no se corta.
3. Usar Fear: la barra de arriba se vacía y tarda 15 segundos. Comprobar que ambas recargan a la vez sin pisarse.
4. Desmontarse en pleno cooldown: las barras desaparecen. Volver a montar de inmediato: reaparecen **con el tiempo que quedaba**, no llenas.
5. Salir del mundo y volver a entrar montado con un cooldown corriendo: mismo resultado que el punto 4.
6. Mirar el marco durante la recarga: los remates de los extremos se ven enteros en todo momento.

## Dependencia externa

El PNG lo aporta el usuario en `src/main/resources/assets/smop/textures/gui/rider_ability_bar.png`. Si llega solo con el marco, la fila de relleno se añade sobre él antes de medir las constantes de §5. El plan de implementación no puede cerrar la tarea de constantes sin ese fichero.
