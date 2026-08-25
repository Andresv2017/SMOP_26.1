# Spawn de todos los mobs: procedimiento y medición

El peso de una entrada no dice nada por sí mismo. Hay que leerlo en tres capas y en este orden:

1. **¿La categoría llega a tirar?** Si su cap está lleno, todo lo demás es decorativo.
2. **¿Qué fracción del pool es nuestra?** El peso se lee contra los otros de la misma categoría en ese
   bioma, no en abstracto.
3. **¿La regla acepta el suelo de ese bioma?** Aquí es donde mueren las entradas en silencio.

Este documento es el procedimiento y **el resultado de haberlo pasado a los seis**, en un mundo recién
generado, el 24 de agosto de 2026.

---

## El comando

```
/smop debug spawn state
/smop debug spawn sim <tangoftero|salmon|kriftognathus|hell_hippo|nirasmosaurus|gt> [pasadas]
/smop debug spawn watch [segundos]
```

- **`state`** — dónde estás: bioma, contadores de cada categoría contra su cap, el pool ya con los
  biome modifiers aplicados, censo de mobs SMOP cargados (separando los que cuentan contra el cap de
  los exentos) y la columna de agua. Lista también MONSTER.
- **`sim <mob>`** — reproduce el bucle de `NaturalSpawner` sin spawnear nada y dice **en qué puerta**
  murió cada intento. El único que distingue "el bioma me rechaza" de "la categoría nunca tiene turno".
- **`watch`** — escucha la tubería real, mira todo el namespace `smop` y saca una fila por mob. La
  columna que importa es **cuántos `finalized` vinieron de `CHUNK_GENERATION` y cuántos del ciclo
  periódico**: son dos fuentes distintas y confundirlas es el error fácil.

## Preparación

Mundo **nuevo**, `/time set day` y `doDaylightCycle false` (cinco de los seis exigen luz > 8), y
aléjate **más de 24 bloques** del punto donde esperas el spawn: el spawner descarta toda posición a
menos de 24 de un jugador.

## La ronda

1. `state` en el bioma → apunta el contador de la categoría contra el cap.
2. `sim <mob> 30` → el histograma de puertas.
3. `watch 300` y vete a generar terreno nuevo. Luego lee el log.
4. Repite el paso 1 **en cuatro sitios a miles de bloques**. Un punto no dice nada.

---

# El resultado

## El hallazgo que ordena todo lo demás

**CREATURE está permanentemente llena y no es recuperable.** Medido en cinco biomas de un mundo recién
generado, cap 10 en todos:

| Bioma | CREATURE | MONSTER | WATER_CREATURE | WATER_AMBIENT |
|---|---|---|---|---|
| `plains` | **128 / 10** | 70 / 70 | 7 / 5 | 0 / 20 |
| `river` | **98 / 10** | 73 / 70 | 6 / 5 | 6 / 20 |
| `jungle` | **83 / 10** | 70 / 70 | 5 / 5 | 0 / 20 |
| `sparse_jungle` | **153 / 10** | 70 / 70 | 0 / 5 | 26 / 20 |
| `swamp` (sim) | **155 / 10** | — | — | — |

Y el `sim`, cuatro veces, con tres mobs distintos y en cuatro sitios distintos:

```
--- where each of the 8670 attempts died ---
  category cap full (global)      8670  100.00%
```

100,00%. Ni un solo intento de GT, hipopótamo o Kriftognathus llegó nunca a la siguiente puerta.

**La consecuencia práctica:** los cuatro mobs de tierra (Tangoftero, Kriftognathus, Hell Hippo, Grand
Tyrant) **solo existen porque los pone la generación de chunks**. El ciclo periódico no produce ni uno.
En terreno ya explorado no aparecerá jamás ninguno nuevo. El `watch` de 300 s lo dice sin ambigüedad:

| Mob | placementCheck | positionCheck | finalized | de chunkgen | del ciclo |
|---|---|---|---|---|---|
| `gt` | 34 → 26 | 26 → 26 | 26 | **26** | **0** |
| `kriftognathus` | 9 → 9 | 9 → 9 | 9 | **9** | **0** |
| `salmon` | 36 → 30 | 26 → 6 | 6 | 0 | **6** |
| `tangoftero` | 4 → 4 | 4 → 4 | 4 | **4** | **0** |
| `nirasmosaurus` | 18 → 16 | 15 → 2 | 2 | 0 | **2** |
| `hell_hippo` | 9 → **1** | 1 → 1 | 1 | **1** | **0** |

Los dos acuáticos son los únicos con caudal continuo, y es exactamente lo que predijo la medición del
Nirasmosaurus: sus categorías rotan porque sus ocupantes despawnean.

⚠️ **MONSTER no es la salida que parecía.** Salió **70/70 y 73/70, llena también**, en todas las
muestras. La idea de mover el GT allí queda descartada por medición, no por opinión: cambiaría un cap
saturado por otro cap saturado, perdiendo `canSpawnFarFromPlayer` por el camino.

## 1. Tangoftero — llanura, peso 10 ✅

Funciona. Confirmado en juego. Pool de llanura medido, total 61: Tangoftero 16%, GT 8%, oveja 20%.
Los 4 que salieron en el `watch` fueron de generación de chunk, en parejas (2-4 por grupo, como pide
la entrada). Como es un `Animal`, `removeWhenFarAway` devuelve `false` y **no despawnea**: los que
pone la generación se quedan.

## 2. Kriftognathus — 7 biomas, peso 8, mínimo 2 ✅

**El arreglo de la regla funciona.** `checkKriftoSpawnRules` delegaba en `Animal.checkAnimalSpawnRules`,
que exige el tag `#minecraft:animals_spawnable_on` — y ese tag contiene un bloque, `grass_block`. Los
tres badlands (terracota, arena roja, tierra basta) y el grove (nieve) no podían producir uno jamás.
Ahora la regla acepta `#animals_spawnable_on`, `#terracotta`, `#sand`, `#dirt`, bloque de nieve y capa
de nieve, con la luz > 8 intacta; la nieve polvo se queda fuera porque lo que se pose encima se hunde.

Medido después del arreglo: **9 de 9 `placementCheck` pasados**, con spawns registrados en badlands
(`-389,70,-3855`, `-261,95,-3927`) y en selva dispersa (`-541,75,441`). Antes de tocarlo, en badlands
no había ni un intento que sobreviviera a la regla.

**Cambio de grupo: mínimo 1 → 2.** Los grupos salían muy repartidos — tres juntos en `-541,75,441`,
pero uno solo en `-389`, `-191`, `-161` y `-451`. La entrada pasa a 2-3.

⚠️ Dos es un mínimo sobre lo que se **pide**, no sobre lo que aterriza: el bucle de grupo descarta al
miembro cuyo propio `positionCheck` falle, así que puede seguir saliendo uno suelto de vez en cuando.
Si quieres una garantía dura hace falta un acompañante en `finalizeSpawn`, como el ternero del
hipopótamo.

Cuotas del pool, medidas: selva 8% (total 99, el loro se lleva el 40%), selva dispersa 12% (total 66).

## 3. Hell Hippo — savana y pantano, peso 4 ✅ con barro

**Corrijo lo que dije:** la entrada del manglar **no** es imposible. Lo viste generado allí, y el
`placementCheck` confirma que pasa — sobre los parches de hierba que el bioma tiene entre el barro,
que es justo lo que observaste. Lo que sí es cierto es que está muy limitado: **1 de 9 intentos
pasaron la puerta**, la peor tasa de los seis, y los 8 fallos eran barro y agua bajo el punto.

Así que la nota del código ("el suelo de barro lo hace más raro allí") era correcta y mi lectura del
tag se pasó de frenada.

**Ahora pisa el barro.** `checkHellHippoSpawnRules` deja de delegar y acepta `#animals_spawnable_on`,
`minecraft:mud` y `minecraft:muddy_mangrove_roots`, con la luz > 8 intacta. El manglar es donde más
sentido tiene un animal anfibio de este tamaño — es la razón por la que está en la lista — y sembrarlo
allí sin que pueda pisar el suelo del sitio no era sembrarlo.

**Qué comprobar:** `sim hell_hippo 30` dentro de un manglar. La tasa de `placementCheck` debería subir
muy por encima de 1 de 9. Ojo con la cuota: el manglar comparte los mismos 4 de peso que la savana, así
que si de golpe se llena de hipopótamos, lo que se toca es el peso.

## 4. Salmón — ríos, peso 5 ✅

Los tres cambios están confirmados en juego:

- **Pool 50/50**, leído en un río: `minecraft:salmon w5 1-5 50%` · `smop:salmon w5 2-5 50%`.
- **Banda de Y**: todos los spawns registrados cayeron en Y 55-61, ninguno en acuífero profundo.
- **Caudal real**: 6 spawns en 300 s, todos del ciclo periódico. WATER_AMBIENT nunca dio FULL (0/20,
  6/20) salvo en un punto puntual de selva dispersa.

Dato útil de la línea `column:` — en un río solo el **14,7%** de las tiradas de Y pueden caer en agua,
y en la selva el 7,1%. Explica por qué los peces parecen escasos sin que nada esté roto.

## 5. Nirasmosaurus — océano cálido y playa, peso 8 ✅

Sigue como se midió: 2 spawns naturales en el `watch`, ambos del ciclo periódico. WATER_CREATURE sale
lleno (5/5, 6/5, 7/5) pero **rotando** — en una muestra estaba a 0/5. Esa rotación es exactamente la
diferencia con CREATURE que justificó moverlo aquí.

## 6. Grand Tyrant — llanura y desierto, peso 5 ✅ con persistencia

Spawnea, y bien: **26 en 300 s** de generar terreno nuevo. Todos de `CHUNK_GENERATION`, ninguno del
ciclo — como todo lo CREATURE.

**El problema era que desaparecía.** `CortexMonster` extiende `PathfinderMob`, donde
`Mob#removeWhenFarAway` devuelve `true` por defecto, así que un GT salvaje despawnea al alejarte. Y
como la categoría está saturada al 100%, **no vuelve nunca**: el que se va, se fue para siempre. Por
eso era el único mob que notabas desaparecer — los otros tres de tierra son `Animal`, y ahí
`removeWhenFarAway` devuelve `false`.

**Arreglado con `requiresCustomPersistence()` → `true`**, que hace las dos mitades: `Mob#checkDespawn`
se salta al mob que lo declara, y `NaturalSpawner#createState` lo deja fuera del censo de la
categoría. O sea que el GT se queda **sin gastar** un presupuesto que ya va diez veces pasado.

**Y uno por sitio — al segundo intento.** La comprobación "¿hay otro cerca?" no puede ir en la regla
de spawn, que es donde debería: **`WorldGenRegion#getEntities` devuelve lista vacía siempre**, y como
todos los GT nacen en generación de chunks, habría pasado siempre sin haber visto nada. Así que el
animal nace **debiendo** la comprobación, la deuda se guarda en disco con él y se paga en el primer
tick después de que su chunk cargue.

Contra qué se paga es lo que hubo que corregir. Preguntarle al nivel por GT cercanos parece lo
correcto y mide mal: **el nivel solo conoce las entidades cargadas**, y dos que se generaron a
trescientos bloques no coinciden cargados jamás. Medido así: **3 en una llanura y 3 en un desierto**,
ninguno capaz de ver a los otros.

Ahora se paga contra `GTLandmarks`, una lista de sitios ocupados en el save data del nivel: se
consulta en el hilo del servidor, sobrevive a que el chunk se descargue y le da igual si el vecino
está en memoria. Si hay una marca a menos de **320 bloques**, el recién llegado se descarta; si no,
la pone y se queda.

Las marcas no se liberan nunca, ni al morir el animal. No hay nada que llene el hueco — el ciclo
periódico no ha producido un solo GT en toda la medición — así que liberar solo serviría para que la
siguiente pasada de generación colase otro donde acabas de matar al primero.

⚠️ **320 es el mando.** Sale uno por cada cuadrado de 640 bloques, que es más ancho que la mayoría de
los parches de bioma: uno por bioma y algunos biomas sin ninguno. Si quedan demasiado escasos, se baja
el radio. **El peso ya no es la palanca**: la marca decide el resultado y el peso solo decide a
cuántos candidatos se rechaza.

⚠️ Esto solo aplica a los GT que nazcan a partir de ahora. Los que ya estén en un mundo guardado no
tienen marca ni deuda, así que la prueba hay que hacerla en mundo nuevo.

---

## Qué apuntar de cada sesión

- El contador de la categoría contra el cap, en los cuatro sitios, con el bioma de cada uno.
- El porcentaje de intentos que murió en cada puerta, del `sim`.
- Cuántos `finalized` dio el `watch` y **cuántos de ellos fueron `CHUNK_GENERATION`**.
