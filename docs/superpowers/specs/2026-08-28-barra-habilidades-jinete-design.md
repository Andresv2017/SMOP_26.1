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
| `client/rider/RiderAbilityHud.java` *(nuevo)* | Cliente: dibuja la barra, una mitad por habilidad |
| `client/hellhippo/HellHippoRiderHud.java` *(se borra)* | Absorbido por `RiderAbilityHud` |
| `entity/hellhippo/HellHippoEntity.java` | Declarar sus habilidades; bajar el cooldown melee |
| `assets/smop/textures/gui/rider_ability_bar.png` *(nuevo)* | Marco + relleno |
| `assets/smop/textures/entity/hell_hippo/hh_testbar.png` *(se borra)* | Placeholder |
| `tools/build-ability-bar.py` *(nuevo)* | Parte el arte de una fila en el sheet de dos e imprime las constantes |
| `tools/art/rider_ability_bar_source.png` *(nuevo)* | El arte tal como se autorea: una sola barra encendida |

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

El campo `mount` también se va: quien envía es `RiderAbilities.sync`, que ya recibe la montura, así que una habilidad no necesita saber de quién es. Con él cae `controllerOf(Mob)`, que solo servía para alimentar el `tick(rider)` de la boss bar y queda muerta.

Firma nueva del constructor:

```java
public RiderAbility(String id, int cooldownTicks, int tintARGB)
```

El parámetro `title` desaparece: era el nombre que mostraba la boss bar, y el HUD nuevo no dibuja texto.

**`tryUse()`** conserva la guarda de `isReady()` y fija `remaining = cooldownTicks`. No envía nada: una habilidad no conoce a sus hermanas, y el paquete lleva el estado de todas — quien reenvía es la montura, tras ver que `tryUse()` devolvió `true` (§3).

**`tick()`** solo decrementa, y pierde el parámetro `rider`: solo existía para decidir a quién enseñarle la boss bar. Toda la gestión de `watcher` — añadir y quitar jugadores — desaparece con ella.

**`save`/`load`** no cambian. El NBT (`FearCooldown`, `AttackCooldown`) se mantiene, así que los hipopótamos ya guardados cargan sin migración.

## 2. Contrato — `RiderControllable`

```java
default List<RiderAbility> riderAbilities() {
    return List.of();
}
```

El orden de la lista es el orden en pantalla, de izquierda a derecha: el índice 0 se queda con la mitad izquierda de la barra y el 1 con la derecha. `HellHippoEntity` devuelve `List.of(mountedAttack, fearPulse)` — el ataque a la izquierda, porque se recarga en un segundo y es el que se mira a cada golpe.

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
- **`tick()`**, en `ClientTickEvent.Post`: si el jugador va montado justo en `mountId`, decrementa cada `remaining` con suelo en 0. Si no, cuenta ticks sin pareja y limpia el estado al pasar de `UNMATCHED_GRACE_TICKS` (40).

Esa comprobación del vehículo es la que hace innecesario el antiguo `hide()`: desmontarse, morir, cambiar de mundo o desconectar dejan de coincidir, y el estado se descarta.

**Por qué la cortesía de 40 ticks y no un descarte inmediato.** `ServerLevel` tickea las entidades antes de la fase de seguimiento que emite `ClientboundSetPassengersPacket`. Al entrar al mundo ya montado, el estado de habilidades puede por tanto salir un tick **por delante** de la noticia de que vas montado: el cliente recibiría el paquete con `getVehicle()` todavía a nulo. Con descarte inmediato eso tira el estado y no hay un segundo envío, así que las barras saldrían llenas el resto de la sesión. La ventana lo absorbe sin añadir tráfico.

- **`entries()`**, lo que lee el HUD, devuelve vacío salvo que el jugador vaya montado en `mountId` justo en ese momento. Se comprueba al pintar y no se confía en una bandera del tick anterior: durante la ventana de cortesía el estado existe pero no debe dibujarse.

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

**El sheet va en escala de grises.** El HUD multiplica cada fila por un color, y el arte es rojo puro `(R, 0, 0)`: multiplicarlo por morado sigue dando rojo, solo que más oscuro. En gris, el tinte `FRAME_TINT = 0xFFFF0000` devuelve el arte original píxel a píxel, y el relleno acepta cualquier color.

**Constantes medidas.** `BAR_WIDTH = 190`, `BAR_HEIGHT = 26`, sheet `256 × 64` (potencia de dos, con la fila de relleno en `v = 26`). Canal interior: `INNER_Y = 11`, `INNER_HEIGHT = 3`, partido por el hueco de la calavera (`x 92..97`) en dos mitades exactas — `LEFT_X = 5` y `RIGHT_X = 98`, ambas de `HALF_WIDTH = 87`. Las imprime `tools/build-ability-bar.py`; no se estiman a ojo. Si se retoca el arte, se vuelve a correr el script y se copian de su salida.

El script separa las dos filas a partir del arte de una sola: la tira encendida son las filas 11–13 con canal rojo ≥ 90, menos el hueco de la calavera (`x 92..97`), que se localiza en la fila 12 — la única donde la silueta corta la tira limpia, ya que en la de abajo asoman los dientes encendidos. Lo que cae en la tira se apaga al `0.22` para la fila del marco.

**Una barra, no una pila.** El primer intento apilaba una barra por habilidad sobre la hotbar y en juego se comía la altura de la vida de la montura: dos barras de 26 px encima de una zona que vanilla ya tiene ocupada. El arte da la salida — es simétrico y su canal interior ya viene cortado en dos mitades iguales por la calavera, así que cada habilidad se queda con una y la barra sigue leyéndose como una pieza.

Con **una sola** habilidad se refleja en las dos mitades: media barra encendida se leería como una avería. De la **tercera** en adelante no caben, y una montura que quiera más necesita otro arte, no otra fila aquí.

**Posición.** Vanilla apila hacia arriba desde el borde inferior: la hotbar, la fila de salud en `guiHeight - 39`, otros 10 px si el jugador lleva armadura, 10 por cada fila de corazones de la montura y 10 más de burbujas al bucear — y el hipopótamo nada. `VANILLA_STATUS_HEIGHT = 59` cubre el peor caso de un jinete con armadura sumergido sobre una montura de una fila de corazones, que es lo que hay hoy (20 de vida ⇒ 10 corazones ⇒ una fila).

```
x = (guiWidth - BAR_WIDTH) / 2
y = guiHeight - VANILLA_STATUS_HEIGHT - GAP - CONTENT_BOTTOM
```

con `GAP = 2` y `CONTENT_BOTTOM = 20`, que es donde acaba el contenido del arte: el PNG deja transparentes las filas 0-2 y 20-25, así que anclar por el borde del fichero dejaría un hueco muerto debajo.

**Dibujado:**

1. **Marco:** `blit` de la fila 0 completa en `(x, y)`, teñido con `FRAME_TINT`.
2. **Cada mitad:** ancho `Math.round(HALF_WIDTH * progress)`; si sale 0, se omite. Crece **desde la calavera hacia su remate** — la izquierda hacia la izquierda, la derecha hacia la derecha — así que lo último que se enciende es el gancho del extremo y el instante en que la habilidad queda lista tiene un remate visible. Como las dos filas comparten encuadre, la `u` de origen es siempre la misma `x` local que el destino.

El marco va debajo y siempre entero, así que los remates de los extremos y la calavera no se cortan nunca — que es la razón de partir la textura en dos filas en vez de tener una versión vacía y otra llena.

El HUD no dibuja texto. Las habilidades se distinguen por su tinte y por su lado fijo de la barra.

## 6. Cooldown del melee montado

`MOUNTED_ATTACK_COOLDOWN_TICKS`: `60` → `20`.

La animación de mordida (`ATTACK_SECONDS = 0.7F`, 14 ticks) es el suelo real: por debajo, un ataque nuevo reiniciaría el clip a media dentellada. 20 ticks dejan seis de respiro y encadenan mordiscos casi sin pausa, frente a los ~2.3 s de espera muerta de hoy.

`FEAR_COOLDOWN_TICKS` se queda en `300`.

## 7. Verificación en juego

Con `./gradlew compileJava` en verde:

1. Montar el hipopótamo: aparece **una** barra, entera y encendida, y **ninguna boss bar arriba**. No pisa la vida del hipopótamo ni la barra de salud: con armadura puesta y bajo el agua sigue habiendo hueco.
2. Atacar: la mitad **izquierda** se apaga de golpe y vuelve a encenderse en 1 segundo, creciendo desde la calavera hacia el remate izquierdo. Encadenar mordiscos y confirmar que la animación no se corta.
3. Usar Fear: la mitad **derecha** se apaga y tarda 15 segundos. Comprobar que las dos mitades recargan a la vez sin pisarse.
4. Desmontarse en pleno cooldown: la barra desaparece. Volver a montar de inmediato: reaparece **con el tiempo que quedaba**, no llena.
5. Salir del mundo y volver a entrar montado con un cooldown corriendo: mismo resultado que el punto 4.
6. Mirar el marco durante la recarga: los remates de los extremos se ven enteros en todo momento.

## El arte

El arte se autorea como **una sola barra encendida** — marco, remates, calavera central y la tira roja segmentada — y vive en `tools/art/rider_ability_bar_source.png`. `tools/build-ability-bar.py` lo parte en el sheet de dos filas que consume el HUD.

Separarlo a mano cada vez que se retoca es donde se cuela el desalineo de un píxel que nadie ve hasta que la barra va por la mitad; por eso el paso es un script y no una capa de Photoshop. Está verificado: marco tintado de rojo más relleno al 100 % reconstruye el arte original sin una sola diferencia.
