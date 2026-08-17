# Port del Nirasmosaurus de 1.20.1 a 26.1

## Dónde encaja

Quinta criatura del mod, después de Tangoftero, Salmón, Kriftognathus y Hell Hippo. Es un reptil
marino: la pieza que faltaba para que la plantilla lea como fauna relicta completa —pterosaurio,
entelodonte, reptil marino y tirano— y la única que aún no tiene código pese a tener **las siete
texturas ya hechas**, incluidas las variantes con silla y una `sup_dude`.

Se porta antes que el GT porque reaprovecha casi todo lo que el Hell Hippo dejó montado (montura,
silla, inventario, navegación anfibia) mientras que el GT no reaprovecha casi nada.

## Qué se porta y qué no

El 1.20.1 es la **fuente de la que se lee el diseño**, no la fuente de la que se copia el código.

- **Se porta:** el comportamiento. Qué hace el animal, cuándo, y con qué se siente. Y los
  **keyframes**, que son arte autorado, no código — se traen tal cual, podando solo los canales cuyo
  hueso no exista en el rig de 26.1.
- **No se porta:** la implementación. Los tres controladores propios, el enum de claves de animación,
  las ~150 líneas de `AnimationState` malabareados a mano, el conteo de intentos de tameo. Todo eso
  tiene ya equivalente en DeluxeLib o en las bases del mod, y donde no lo tiene se escribe de nuevo.
- **Se retunea, no se copia:** los números. Duraciones, distancias y frames de daño del legacy son
  **punto de partida a verificar contra los clips reales**, no valores a trasladar. En el Hell Hippo
  adiviné duraciones de clip mal varias veces hasta que las leí del `withLength`; aquí se leen desde
  el principio.

## El tamaño real

Medirlo en líneas del legacy no dice nada, porque esas líneas no se van a escribir. Medido en lo que
sí hay que hacer:

| | Hell Hippo | Nirasmosaurus |
|---|---|---|
| Comportamientos distintos | ~15 | **~11** |
| Clips de animación (datos a portar) | 31 | **59** |
| Mecánicas sin soporte en librería | 0 | **3** (agarre, sacudida, giro) |

**Por comportamientos es más pequeño que el Hell Hippo**, no cinco veces mayor. Lo que sube el coste
es otra cosa: el doble de datos de animación, y tres mecánicas que no existen en ninguna parte y hay
que diseñar desde cero. Ese es el riesgo real, y está concentrado en una sola fase (1d).

## La decisión que define el port: de qué hereda

El legacy extiende un `WaterEntity` propio que no vamos a traer. En 26.1 hay dos candidatos y
**ninguno encaja del todo**:

- **`SMOPWaterAnimal`** (base del salmón) trae travesía acuática ya resuelta, pero también
  `shouldFlopOnLand()` — el coleteo del pez varado. Un reptil marino no colea en tierra: **camina**.
- **`GenderedSMOPAnimal`** no trae nada de agua, pero es de lo que cuelga el Hell Hippo, que acaba de
  resolver lo anfibio: `AmphibiousPathNavigation`, `canBreatheUnderwater`, `getFluidJumpThreshold` al
  máximo y un `travel()` que da propulsión real en vez del 0.02 fijo de vanilla.

**Decisión: `GenderedSMOPAnimal`**, con el kit anfibio del Hell Hippo y tomando de `SMOPWaterAnimal`
solo la mitad de travesía acuática. El hippo es un animal terrestre que entra al agua; el Niras es lo
inverso, un animal acuático que sale a tierra. La misma maquinaria sirve para los dos con los
umbrales invertidos, y ninguno de los dos quiere el coleteo.

Que sea `Gendered` no es opcional: hay `niras_male` y `niras_female` con sus variantes de silla.

## Hitboxes multiparte: se aplaza, y a propósito

El legacy usa `PartEntity` en el Niras y en el GT. **DeluxeLib no tiene nada de esto** —verificado—
así que es trabajo nuevo de librería, y cuando se haga va **en DeluxeLib, no en SMOP**, porque la
comparten dos mobs.

Se aplaza a la fase del GT por dos razones. Una, es la pieza de más riesgo de lo que queda y no debe
bloquear un mob que por lo demás es abordable. Dos, la necesidad es asimétrica: en un jefe, que
golpear la cola no cuente como golpear la cabeza es parte del combate; en una montura larga es sobre
todo cosmético. El Niras sale con **un solo AABB**, con la consecuencia asumida de que la caja será
más generosa de lo que el modelo sugiere.

## Qué sustituye a qué

Esta tabla es el port. La columna izquierda se lee para entender la intención; se implementa la
derecha.

| Comportamiento (legacy) | Cómo se hace en 26.1 |
|---|---|
| Cuatro ataques con `damageFrames` en un controlador propio | `HitWindow` / `AttackShape`, que ya hacen ventanas por frame con forma real y filtro de objetivo |
| Enum de claves de animación + reproducción manual | `startAction` / `isPerforming` de `SMOPAnimal` |
| Tres métodos que encienden y apagan `AnimationState` a mano | `MobAnimator` con `setPlayCondition`, una condición por clip |
| Config de señuelo + conteo de intentos | `TameProgress`, ya extraído del Krifto en la fase 2a del hippo |
| Silla, montura, inventario | `RiderControllable`, `EquipmentSlot.SADDLE`, `SimpleContainer` del Hell Hippo |
| Ciclo de sueño a mano | `SleepPhase` del mod |
| Capa de jinete propia | `RiderPassengerLayer` + `RiderPoseHandler` de DeluxeLib |
| Sonidos disparados desde el tick | `AnimSound`, soldado al frame del clip |

**Sin equivalente, se diseña de cero:** el agarre de presa, la sacudida y el giro. Si al escribirlos
quedan genéricos, van a DeluxeLib; si quedan atados a este animal, se quedan en SMOP. Esa decisión se
toma al implementarlos, no ahora.

## Fases

Ordenadas para que parar en cualquier corte deje algo publicable.

### 1a · Esqueleto

Modelo adulto y cría, renderer con textura por sexo, entidad registrada, spawn en biomas, tabla de
botín. Los 59 clips portados con los canales huérfanos podados — 26.1 **lanza excepción** si un hueso
del clip no existe en el rig, mientras 1.20.1 lo saltaba en silencio; fue el fallo más repetido del
port del hippo. Las duraciones se leen del `withLength` de cada clip, no se estiman.

Locomoción anfibia: nada y camina, con el reparto agua/tierra por condición de reproducción.

*Verificación:* nada, sale a tierra, camina, y ninguna animación revienta.

### 1b · Vida propia

Sueño, reproducción con cría, gesto de reposo vía `IdleAnimationGoal`, animación de muerte.

*Verificación:* duerme, se reproduce, la cría sigue a la madre.

### 1c · Combate básico

Los dos mordiscos simples, en agua y en tierra, por `HitWindow` sobre `AnimatableMeleeAttackGoal`.
Las ventanas de daño se derivan de los clips reales; los frames del legacy solo dicen *dónde mirar*.

*Verificación:* muerde en agua y en tierra, y el daño cae cuando cierran las fauces.

### 1d · La presa — el escaparate

**Es la fase que justifica el mob**, y la única sin red de seguridad: nada de esto existe en librería.
Tres piezas, en este orden porque las dos últimas operan sobre la primera:

1. **Agarre** — atrapa a la presa en las fauces y la lleva ahí, visible en la boca.
2. **Sacudida** — zarandea lo que tiene agarrado.
3. **Giro de la muerte** — el giro del cocodrilo, arrastrando a la presa.

*Verificación:* agarra, zarandea, gira, y suelta a la presa al morir o al recibir daño suficiente.

### 2 · Tameo y montura

Señuelo con pescado cocinado en mano: se acerca, se detiene a distancia, **el jugador tiene que
retroceder** para que coma, tres veces. `TameProgress` lleva la cuenta y la persistencia. Luego silla,
montura y la tecla de descenso, ya declarada (`key.smop.descend`).

*Verificación:* el ritual completo doma; ensillado se conduce en agua y en superficie.

### 3 · Extras

Variante `sup_dude` (una rama de textura, barata) y la lanza Niras: un ítem arrojadizo, **no el mob**.
Se puede cortar entera sin dejar hueco.

## Desviaciones deliberadas

- **Los números del señuelo se deciden jugando.** El legacy se contradice con sus propios comentarios
  —`approachHoldDistance` vale `10.0` junto a un comentario que dice "~5 bloques", y lo mismo en
  `retreatMinDistance`—, señal de que alguien tuneó los valores y no volvió a tocar el texto. Copiar
  esos números sería heredar una duda, no un diseño.
- **Sin manada**, igual que el Hell Hippo, que perdió su sistema de grupos por estar roto.
- **El daño no vive en el goal**, sino en la `HitWindow` del clip, como en los otros cuatro mobs.

## Fuera de alcance

Hitboxes multiparte (van con el GT), y reescribir keyframes: se portan tal cual.

## El corte mínimo

Si hay que parar: **1a + 1b + 1c** dan un reptil marino vivo, creíble y publicable. **1d es lo que lo
hace memorable** y debería tener prioridad sobre la fase 2, porque el mod ya tiene una montura —el
Hell Hippo— pero no tiene nada que agarre a una presa y gire con ella bajo el agua.
