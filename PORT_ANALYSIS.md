# SMOP — Análisis del port 1.20.1 Forge → 26.1 NeoForge

Documento de trabajo. Tres partes:
1. Inventario completo del mod 1.20.1, separado por mob / item / sistema.
2. Qué de DeluxeLib-26.1 sustituye a qué.
3. Plan de implementación por fases.

---

# PARTE 1 — Inventario del mod 1.20.1

**Tamaño:** ~47.000 líneas Java. De ésas, **~30.000 (64%) son clases `AnimationDefinition` exportadas de Blockbench** — dato clave para el plan (ver §3.1).

## 1.0 Arquitectura actual: jerarquía de entidades

```
TamableAnimal
└── BaseEntity  (468 líneas)              ← sueño, roar, ataque, huevos, sit/follow/wander, anim states
    └── GenderedEntity (61)               ← macho/hembra + canMate
        ├── FlyingEntity (338)            ← switchNavigation tierra↔aire, travel volador, anims de vuelo
        │   └── KriftognathusEntity (657)
        ├── WaterEntity (286)             ← nado, daño por sequedad, flop, anims acuáticas, puesta en agua
        │   ├── SalmonEntity (305)
        │   └── AmphibiousEntity (226)    ← doble navigator agua/tierra, anims duales sueño
        │       └── NirasmosaurusEntity (1014)
        └── GTEntity (295)
    └── TangofteroEntity (399)            ← extiende BaseEntity directamente

AbstractChestedHorse
└── Hell_HippoEntity (1461)               ← FUERA de la jerarquía anterior. Reimplementa todo a mano.
```

**Problema estructural nº1:** `Hell_HippoEntity` no comparte nada con `BaseEntity`. Duplica género, sueño, ataque, animaciones — 1461 líneas monolíticas.

**Problema estructural nº2:** el sistema de animación es `AnimationState` de vanilla, con lógica manual `start()/stop()` esparcida entre `updateBaseAnimations()`, `updateFlyingAnimations()`, `updateAquaticAnimations()`, `playOnly()`, `stopAllXExcept()`. Sin blending, sin prioridades, sin capas. Es exactamente lo que `MobAnimator` de DeluxeLib resuelve.

**Problema estructural nº3:** `System.out.println` de debug en producción (BaseEntity, FlyingEntity, Krifto, Salmon, SMOPPackets…). Eliminar en el port.

## 1.1 Sistemas transversales (compartidos entre mobs)

| Sistema | Archivos | Descripción |
|---|---|---|
| **Sueño (día/noche)** | `SleepCycleController` (211), `ISleepingEntity`, `ISleepAwareness`, `ISleepThreatEvaluator` | Ciclo preparing→sleep→awakening con duraciones por mob, interrupción por daño/proximidad/amenaza |
| **Género** | `Gendered`, `GenderedEntity` | Flag sincronizado, gate de `canMate` |
| **Huevos** | `GenericLayEggGoal`, `EggGoalRegistry`, `ProtectEggBaseGoal` (162), `ProtectNearestEggGoal`, `ProtectOwnEggGoal`, `CustomEggBorn`, tag `EGG_BLOCKS` | Poner huevo, defenderlo, reacción a rotura (FLEE/IGNORE/ATTACK) vía `ModForgeEvents.onBlockBreak` |
| **Grupo / manada** | `IGroupBehaviour`, `GroupType` (PACK/FLOCK/SCHOOL), `GroupReaction` (DEFENSIVE/EVASIVE/NEUTRAL), `IHasLeader`, `GroupUtil`, `FollowGroupLeaderGoal`, `AssistFlockGoal` | Solo lo usan Hell_Hippo y Tangoftero |
| **Multi-hitbox** | `HitboxEntity` (PartEntity) | GT (7 partes) y Niras (2 partes, solo adulto) |
| **Doma por señuelo** | `PreAggroTameGoal` (627), `NirasLureTameConfig`, `AnimKey` | Solo Niras. Es el goal más grande del mod |
| **Variantes** | `RandomVariantCapable`, `TangofteroVariant` | Solo Tangoftero |
| **Navegación custom** | `NewFlyingPathNavigation`, `NewGroundPathNavigation`, `NewWaterMoveControl`, `VerticalSwimmingMoveControl`, `GTMoveControl` | |
| **Montura / pose de jinete** | `ICustomPlayerRidePose`, `RenderUtil`, `HumanoidModelMixin`, `LivingEntityRendererMixin`, `PlayerPoseEvent`, `ModelRotationEvent`, `ForgeClientEvents` | Hell_Hippo y Niras |
| **Cámara** | `CameraUtil`, `ShakeCameraPacket` | |
| **Red** | `SMOPPackets` (SimpleChannel), `Packet`, 4 paquetes | |

## 1.2 Mob por mob

### A) `Hell_HippoEntity` — Hipopótamo del Infierno
**El más complejo del mod.** 1461 líneas + 3294 de animaciones.

- **Base:** `AbstractChestedHorse implements MenuProvider, ItemSteerable, Saddleable, IGroupBehaviour, IHasLeader`
- **Tamaño:** 2.5×2.5 · **Salud:** ver `createAttributes()` · **Categoría:** CREATURE
- **Sistemas propios:**
  - **Confianza (trust):** `DATA_TRUSTING` + `trustingPlayerUUID`. Se gana con `Items.BEEF`. Requisito para ensillar.
  - **Intimidación / miedo:** `DATA_INTIMIDATING`, `performFearEffect()`, efecto `ModEffects.FEAR`, `FearEffect`, boss-bar de cooldown (`ServerBossEvent`, 15 s), tecla **G**.
  - **Montura:** `ItemBasedSteering`, `positionRider`, `tickRidden`, `getRiddenInput/Speed`, `getDismountLocationForPassenger`, salto cancelado.
  - **Ataque montado:** `performMountedAttack()`, boss-bar de cooldown (40 t), tecla **R**.
  - **Inventario:** `HellHippoInventory`, `createMenu`, `openInventoryFor`, tecla **V**.
  - **Armadura:** `hellhippo_armor` (`HorseArmorItem`), `updateArmorBonus()` con `AttributeModifier` UUID fijo, `containerChanged`.
  - **Sueño ambiental:** ciclo día/noche propio (no usa `SleepCycleController`), `sleepingDueToEnvironment`, cooldown.
  - **Agua:** `isSeaweed()` (algas al sumergirse), sacudida al salir (`isWet`/`isShaking`/`shakeTicks`), `HellHippoLeaveWaterShakeGoal`.
  - **Género + cría** (`Items.CARROT`), **grupo** (líder).
- **Goals propios:** `Hell_HippoAttackGoal`, `HellHippoDefendOwnerGoal`, `HellHippoHurtByTargetGoal`, `HellHippoTemptGoal`, `HellHippoWaterStrollGoal`, `HellHippoLeaveWaterShakeGoal`, `HippoTargetPlayerGoal`, `HippoTargetPreyGoal`
- **Cliente:** `Hell_HippoModel` (202), `Baby_Hell_HippoModel` (158), `Hell_HippoRenderer`, `Hell_HippoAnimations` (3294)
- **Texturas:** 14 (macho/hembra × silla/armadura/cofre/algas + bebé)
- **14 `AnimationState`** manuales: eat, idle, attack, walk, swim, sprint, waterIdle, bite, intimidate, shake, sleepPreparing, sleep, awakening, death

### B) `NirasmosaurusEntity` — Nirasmosaurus
1014 líneas + ~13.500 de animaciones (5 archivos: agua adulto 5221, tierra adulto 2968, agua extra 639, agua bebé 2469, tierra bebé 2407).

- **Base:** `AmphibiousEntity implements ISleepThreatEvaluator, ISleepAwareness`
- **Tamaño:** 1.75×1.6 · **Categoría:** CREATURE · spawn `NO_RESTRICTIONS`
- **Sistemas propios:**
  - **Multi-hitbox:** cabeza + trasera (`headBox`, `rearBodyBox`), solo adulto, escalado para bebé.
  - **4 ataques enum** `NirasmosaurusAttacks` con `durationTicks` + `damageFrames`, `NirasmosaurusAttackController` (193).
  - **Agarrar y sacudir:** `NirasGrabShakeController` (139), `HELD_MOB_ID` sincronizado, `getJawGrabPosition()`, capa de render `NirasHeldMobLayer`.
  - **Roll / tilt:** `NirasRollController` (178), `prevTilt`/`tilt`/`currentRoll`.
  - **Doma por señuelo:** `PreAggroTameGoal` (627) + `NirasLureTameConfig` + `AnimKey` + `LURE_ACTIVE`/`ANIM_KEY`/`ANIM_END_TICK`/`PENDING_TAME`/`PENDING_TAME_OWNER`. Anims: landEat, landTamed, warning1d/2/3.
  - **Montura:** ensillable, `travel()` custom (66 líneas), descenso con tecla **X** (`IS_DESCENDING`), `OUT_OF_WATER_RIDING_TICKS`, capa `NirasRiderLayer`.
  - **Muerte con animación:** `tickDeath()` override + flag `DYING`.
  - Doma: `Items.TROPICAL_FISH` · Cría: `Items.SEAGRASS`
- **Goals:** `NirasmosaurusAttackGoal`, `NirasTargetPreyGoal`, `PreAggroTameGoal`
- **Cliente:** `NirasmosaurusModel` (215), `Baby_NirasmosaurusModel` (164), `NirasmosaurusRender`, 2 layers
- **Item asociado:** `niras_spear` + `NirasSpearEntity`

### C) `KriftognathusEntity` — Kriftognathus (volador)
657 líneas + 4532 de animaciones (adulto 2967, bebé 1565).

- **Base:** `FlyingEntity implements ISleepThreatEvaluator, ISleepAwareness, CustomEggBorn`
- **Tamaño:** 1×1 · **Categoría:** CREATURE
- **Sistemas propios:**
  - **Volar/aterrizar automático:** heredado de `FlyingEntity` (`switchNavigation`, `handleAutoNavigationSwitch`, `maxGroundTicks`/`maxGroundedTicksWhileFlying`). Bebés no vuelan.
  - **Posarse en la cabeza del jugador:** `ON_PLAYERS_HEAD`, reposiciona cada tick sobre `player.getEyeY()`, frena la caída del jugador (`onHeadFallingAnimationState`), sale con crouch.
  - **Robar objetos:** `StealItemGoal` (161) + `RunAwayAfterStealGoal`, `tickLootBehavior()`.
  - **Bioma de spawn guardado:** `SPAWN_BIOME` sincronizado.
  - **Swoop:** `swoopAnimationState`.
  - **Nace de huevo custom:** `CustomEggBorn.onEggBorn`.
- **Goals:** `FlyFromNowAndThenGoal`, `FollowOwnerFlyingGoal`, `RandomStrollAndFlightGoal`, `StealItemGoal`, `RunAwayAfterStealGoal`
- **Cliente:** `KriftognathusModel` (187), `KriftoBabyModel` (154), `KriftognathusRender`

### D) `TangofteroEntity` — Tangoftero
399 líneas + 2475 de animaciones (adulto 1304, bebé 1171).

- **Base:** `BaseEntity implements ISleepThreatEvaluator, ISleepAwareness, RandomVariantCapable`
- **Tamaño:** 1×1 · **Categoría:** CREATURE
- **Sistemas propios:**
  - **Variantes** (`TangofteroVariant`, aleatoria en `finalizeSpawn`).
  - **Rugido que espanta no-muertos:** `scareUndeadMobs()` en radio 10, cooldown 600 t, disparado al alimentar con carne podrida. Eventos de entidad byte 42 (bite) / 43 (roar).
  - **Blend de caminar suavizado:** `updateWalkBlend()` con EMA + histéresis (`speedEma`, `walkBlend`) — un mini-sistema de blending hecho a mano.
  - **Curación por alimentación:** `handleFeeding()`, +6 con carne podrida, +3 resto.
  - Doma: `Items.RABBIT` (1/3 prob.) · Cría: `Items.CHICKEN` · Tentación: `Items.ROTTEN_FLESH`
  - Protege huevos (`FLEE` al romperse), asiste a la bandada (`AssistFlockGoal`).
- **Goals:** `TangofteroTemptGoal`, `CustomWanderGoal`, `AssistFlockGoal`
- **Estructura asociada:** `TangofteroNestStructure` (259) + `TangofteroNestRandomSpread`
- **Item asociado:** `tango_arrow` + `TangoArrowEntity`

### E) `GTEntity` — Grand Tyrant (jefe)
295 líneas + 5428 de animaciones (GTAnimations 3365 + GTAnimationsBase 2063).

- **Base:** `GenderedEntity implements ISleepThreatEvaluator, ISleepAwareness`
- **Tamaño:** 3.2×6.2 · **300 HP** · `maxUpStep = 2.5`
- **Sistemas propios:**
  - **7 hitboxes multipart** (front, neck, head, back, tail1-3) posicionadas por trigonometría en `aiStep()`.
  - **4 ataques enum** `GTAttacks` (BITE, HORN_SWING, CLAW_SWING, STOMP) con `durationTicks`, `damageFrames[]`, knockback Z/Y y daño. `GTAttackController` (275) con `AttackHitbox` propio.
  - **STOMP** con 3 frames de daño + `StompDustFXPacket` + `ShakeCameraPacket`.
  - **Rugido** con sonido propio (`ModSounds.GT_ROAR`, `roar1.ogg`), 100 t.
  - **Dropea `gt_head`** al morir → bloque decorativo con BlockEntity y modelo 3D.
  - `GTMoveControl` propio.
- **Goals:** `GTAttackGoal` (189), `GTTargetPlayerGoal`, `GTTargetPreyGoal`, `RoarOnTargetGoal`

### F) `SalmonEntity` — Salmón
305 líneas + 981 de animaciones.

- **Base:** `WaterEntity implements ISleepThreatEvaluator, ISleepAwareness`
- **Tamaño:** 1.5×1 · **Categoría:** WATER_AMBIENT · spawn `IN_WATER` en superficie
- **Sistemas propios:** `SalmonDigGoal` (198) — cava en el lecho y dropea según sustrato (`SAND_DROPS`/`GRAVEL_DROPS`/`MUD_DROPS`/`DIRT_DROPS` en `ModItems`), con `digCommandCooldown`. Pone `salmon_roe_eggs` en agua (`RoeEggsBlock`, 3-6 crías, 200-800 t).
- **Goals:** `SalmonAttackGoal`, `SalmonDigGoal`

### G) Entidades-proyectil
| Entidad | Líneas | Notas |
|---|---|---|
| `NirasSpearEntity` | 156 | Lanza arrojadiza. Renderer + `NirasSpearModel` + **BEWLR** (`NirasSpearItemBewlr`) para el modelo 3D en mano |
| `TangoArrowEntity` | ~50 | Flecha custom, daño base 2.0 |

## 1.3 Items (26 registros)

| Categoría | Item | Clase | Notas de port |
|---|---|---|---|
| **Misc** | `nirasmo_beak`, `krifto_wing`, `tango_feather` | `Item` | Trivial |
| **Comida** | `hell_hippo_raw_meat`, `hell_hippo_cooked_meat`, `raw_salmon`, `nirasmo_meat`, `cooked_nirasmo_meat`, `krifto_meat`, `cooked_krifto_meat`, `tango_leg`, `cooked_tango_leg` | `Item` + `ModFoods` | `FoodProperties` cambió de API |
| **Comida especial** | `krifto_stew` | `BowlFoodItem` | En 26.1 el "devolver bowl" es el componente `USE_REMAINDER` |
| **Huevos de spawn** | 6 (`hell_hippo`, `tangoftero`, `kriftognathus`, `salmon`, `niras`, `gt`) | `ForgeSpawnEggItem` | ⚠️ Ya no existe → `SpawnEggItem` vanilla |
| **Armadura** | `hellhippo_armor` | `HorseArmorItem` | ⚠️ **Riesgo alto** — clase eliminada, sistema de armadura animal reescrito con data components |
| **Arma** | `niras_spear` | `NirasSpearItem extends SpearItem` | ⚠️ `Multimap<Attribute,AttributeModifier>` → `ItemAttributeModifiers` component; `initializeClient`/BEWLR → sistema nuevo de modelos de item |
| | `niras_spear_inventory` | `Item` (sprite auxiliar) | Ya no hace falta: el nuevo sistema de `assets/<ns>/items/*.json` maneja variantes |
| **Munición** | `tango_arrow` | `ArrowItem` | `createArrow` cambió de firma |
| **BlockItems** | `tangoftero_egg`, `krifto_egg`, `salmon_roe_eggs`, `niras_egg`, `gt_head` | `BlockItem` | → `DeferredRegister.Items#registerSimpleBlockItem` |

**Listas de drops** (`SAND_DROPS`, `GRAVEL_DROPS`, `MUD_DROPS`, `DIRT_DROPS`) viven en `ModItems` y las consume `SalmonDigGoal`.

## 1.4 Bloques (5)

| Bloque | Clase | Líneas | Notas |
|---|---|---|---|
| `tangoftero_egg` | `SmallEggsBlock` | 183 | 3 etapas × 4 cantidades = 12 modelos JSON |
| `krifto_egg` | `SmallEggsBlock` | — | idem, 12 modelos |
| `salmon_roe_eggs` | `RoeEggsBlock` | 176 | En agua, 3-6 crías, eclosión 200-800 t, translúcido |
| `niras_egg` | `EggBlock` | 148 | 3 modelos, 8×10 px |
| `gt_head` | `HeadBlock` + `HeadBlockEntity` + `HeadBlockRenderer` + `GrandTyrantHeadModel` (128) | — | Cabeza decorativa con modelo 3D vía BER |

Efecto de partículas al agrietarse: `CrackFX` (cliente).

## 1.5 Resto

- **Efectos:** `ModEffects.FEAR` → `FearEffect`
- **Sonidos:** `gt_roar` (`sounds/gt/roar1.ogg`)
- **Estructuras:** `TangofteroNestStructure` (259) + `TangofteroNestRandomSpread` + `StructurePlacementTypeRegister`
- **Tags:** `ModTags.Blocks.EGG_BLOCKS`
- **Red (4 paquetes, `SimpleChannel`):** `StoCSyncFlying` (S2C), `RiderActionPacket` (C2S: ATTACK/FEAR/OPEN_INVENTORY/DESCEND_START/DESCEND_STOP), `ShakeCameraPacket` (S2C), `StompDustFXPacket` (S2C)
- **Teclas:** R = atacar montado, G = miedo, V = inventario, X = descender
- **Mixins (2):** `HumanoidModelMixin` (post `PlayerPoseEvent` al final de `setupAnim`), `LivingEntityRendererMixin` (post `ModelRotationEvent` en `setupRotations`) — ambos para la pose del jinete
- **Proxies:** `ClientProxy` / `CommonProxy` vía `DistExecutor`
- **Creative tab:** `smop_tab`
- **Recursos:** 32 texturas de entidad, 27 modelos de item, 30 modelos de bloque, 5 blockstates, 1 sonido

---

# PARTE 2 — DeluxeLib: qué nos sirve

DeluxeLib ya está portada a NeoForge 26.1 y es del mismo autor y las mismas convenciones. **Cubre la mayoría de la infraestructura que SMOP tiene escrita a mano, y mejor.**

## 2.1 Mapa de sustitución directo

| Sistema SMOP 1.20.1 | Sustituto en DeluxeLib | Ganancia |
|---|---|---|
| `AnimationState` + `updateBaseAnimations()`/`updateFlyingAnimations()`/`updateAquaticAnimations()`/`playOnly()`/`stopAllXExcept()` (~1200 líneas repartidas) | **`MobAnimator` + `Animatable` + `StandardAnimation` + `BlendLayer` + `Loop`** | Capas, prioridades, blending N-vías, `canPlay()` declarativo, auto-tick vía `EntityTickEvent`, auto-registro en `EntityJoinLevelEvent`. **Elimina casi toda la lógica manual de animación del mod.** |
| `TangofteroEntity.updateWalkBlend()` (EMA + histéresis a mano) | **`MovementHysteresis`** + `registerWalk`/`walkSelector` del animator | Ya resuelto y sincronizado cliente/servidor |
| `BaseEntity.deathAnimationState` + `NirasmosaurusEntity.tickDeath()` + flag `DYING` | **`MobAnimator.registerDeath()` / `tickDeath()` / `registerFallingDeath()`** | Selección aleatoria con condiciones, el cadáver dura lo que dura la animación, hook automático en `LivingDeathEvent` |
| `GTAttackController` + `GTAttacks.damageFrames[]` + `AttackHitbox` interno (275 líneas)<br>`NirasmosaurusAttackController` (193) | **`HitWindow` + `AttackShape` (Sector/Capsule/Sphere/Box/Beam) + `AttackAnchor`** | Ventanas de daño atadas a ticks de la animación, barrido interpolado, un golpe por víctima, debug con partículas (`/deluxelib debug hitboxes`) |
| `FlyingEntity` (338) + `NewFlyingPathNavigation` + auto-switch tierra/aire | **`AbstractFlyingEntity`** + `SmartFlyingNavigation` + `SmoothFlyingMoveControl` + `PathCarrot` + `FlyingMobRenderer` | Ciclo despegue→vuelo→aterrizaje con hooks de animación, pitch/roll de vuelo, navegación sin zigzag |
| `SMOPPackets` (`SimpleChannel`, ya no existe) + `Packet` + 4 paquetes | **`NetworkCreator` + `AbstractNetworkPacket` + `Side`** | API casi idéntica a la vieja (`regPacket`/`sendToServer`/`sendToClient`/`sendToPlayersInLevel`) sobre el payload system de NeoForge |
| `ShakeCameraPacket` + `CameraUtil` | **`ScreenShake` (fluent) / `ScreenShakes` (registro) + `ShakeProfile` + `CameraShaker`** | Ruido fBm en vez de jitter, shakes apilables, ya con packet propio |
| `ICustomPlayerRidePose` + `RenderUtil` + `HumanoidModelMixin` + `LivingEntityRendererMixin` + `PlayerPoseEvent` + `ModelRotationEvent` + `ForgeClientEvents` | **`RiderPoseHandler` + `RiderPassengerLayer` + `RiderRenderEvents` + `RiderStateAccess` + `HumanoidPoseApplier` + `RiderPoseTuner`** | **Borra los 2 mixins de SMOP.** Además: poses de jinete autoradas en Blockbench, y tuner en vivo con numpad (`/deluxelib debug riderpose`) |
| `NirasRiderLayer` (posicionar jinete a mano) | `RiderPassengerLayer` (automático al implementar `RiderPoseHandler`) | |
| `CustomMobRenderer` de SMOP (199) | **`CustomMobRenderer` de DeluxeLib** | Ya adaptado a render states de 26.1, quita el death-flip y el tinte rojo de muerte |
| `StompDustFXPacket` | **`ParticleFx`** | |
| Spawn placements + biomas a mano | **`DeluxeBiomeSpawns` + `DeluxeBiomeSpawnBuilder` + `DeluxeBiomeSpawnProvider`** | Datagen a `add_spawns` biome modifiers |
| Lang / loot a mano | **`DeluxeLangProvider` + `DeluxeEntityLootProvider`/`SubProvider`** | Nombres automáticos desde el registry path |
| `SmoothBodyRotationControl` no existe en SMOP (rotaciones bruscas) | **`SmoothBodyRotationControl`** + `TurnLeanAdditive` | Mejora gratis |
| — | **`Rig` + `RigComponent` + `LookAtAdditive` + `KeyframeBlend`** | El modelo declara su rig; sustituye `setupAnim` manual. Ya adaptado a `DeluxeEntityRenderState` |
| — | **`IArmoredEntity`** | Para la armadura del Hell_Hippo |
| — | **`StatueConfig`/`StatueRenderer`/`StatueItemRenderer`** | Reutilizable para `gt_head` (bloque decorativo con modelo 3D + item con modelo 3D) |

## 2.2 Sistemas de DeluxeLib nuevos que conviene adoptar

| Sistema | Aplicación en SMOP |
|---|---|
| **`Cortex` (FSM de combate)** + `Behavior`/`BehaviorContext`/`GlobalRule`/`Targeting` + behaviors listos (`AnimatedMeleeBehavior`, `ChaseTargetBehavior`, `GuardBehavior`, `WanderBehavior`, `TimedAnimationBehavior`, `AttackSelector`) | **GT** es el candidato perfecto: 4 ataques + rugido + wander. Sustituye `GTAttackGoal`+`GTAttackController` por una máquina de estados declarativa. **Niras** también (4 ataques + agarre + roll). |
| **Poise / stagger** (`Staggerable`, `PoiseTracker`, `WeaponPoise`, `StaggerGoal`, `PoiseEvents`, `PoiseBarRenderer`) | Nuevo para SMOP. Encaja natural en GT (jefe) y Hell_Hippo. Opcional. |
| **`GuardingMeleeEntity` + `GuardedMeleeAttackGoal`** | Si algún mob debe pelear con ritmo de duelista |
| **Tuners en vivo** (`/deluxelib debug riderpose`, `itempose`, `statuetune`, `hitboxes`, `poise`) | Ahorra muchísimo tiempo re-ajustando asientos de montura y hitboxes de GT/Niras |
| **`Interpolation`** | Sustituye lerps a mano |

## 2.3 Lo que DeluxeLib **NO** cubre — hay que portar a mano

1. **Sistema de sueño** (`SleepCycleController` + 3 interfaces) — nada equivalente. Portar tal cual; considerar contribuirlo a DeluxeLib.
2. **Género** (`Gendered`/`GenderedEntity`) — portar.
3. **Sistema completo de huevos** (bloques `EggBlock`/`SmallEggsBlock`/`RoeEggsBlock`, goals de poner/proteger, `EggGoalRegistry`, tag, evento de rotura) — portar.
4. **Comportamiento de grupo** (`IGroupBehaviour`/`GroupType`/`GroupReaction`/`IHasLeader`/`GroupUtil`) — portar.
5. **Doma por señuelo** (`PreAggroTameGoal`, 627 líneas) — muy específica de Niras, portar.
6. **`AmphibiousEntity` / `WaterEntity`** — DeluxeLib tiene `AbstractFlyingEntity` pero **no** un equivalente acuático/anfibio. Hay que portar y reconstruir sobre `MobAnimator`. Buen candidato para meterlo en DeluxeLib como `AbstractAquaticEntity`/`AbstractAmphibiousEntity`.
7. **Todo lo específico de mob:** trust/miedo/inventario/steering del Hell_Hippo, agarrar-y-sacudir de Niras, posarse-en-cabeza y robar de Krifto, espantar-no-muertos de Tangoftero, cavar del Salmón.
8. **Multi-hitbox** (`HitboxEntity`/`PartEntity`) — verificar el estado de `PartEntity` en NeoForge 26.1 (⚠️ riesgo).
9. **Estructura** `TangofteroNestStructure`.
10. **Efecto `FEAR`**, sonidos, tags, creative tab.

---

# PARTE 3 — Cambios de API que hay que aplicar

## 3.1 🟢 Buena noticia: las animaciones portan SIN CAMBIOS

Comparadas línea a línea, `AnimationDefinition.Builder` / `AnimationChannel` / `Keyframe` / `KeyframeAnimations.degreeVec/posVec/scaleVec` / `Interpolations.CATMULLROM|LINEAR` **son idénticas** entre el SMOP de 1.20.1 y el `ArpyAnimation` de DeluxeLib 26.1.

→ **~30.000 líneas (64% del mod) se copian y pegan cambiando solo el `package`.** El coste real del port está en las ~17.000 líneas restantes.

Lo que sí cambió es *cómo se aplican*: `KeyframeAnimations.animate(model, def, …)` fue eliminado; ahora se hace `AnimationDefinition#bake` → `KeyframeAnimation#apply(time, scale)`. Eso ya está encapsulado en `KeyframeBlender` de DeluxeLib — no nos afecta si usamos `Rig`.

## 3.2 Cambios obligatorios

| 1.20.1 Forge | 26.1 NeoForge |
|---|---|
| `net.minecraftforge.*` | `net.neoforged.*` |
| `@Mod.EventBusSubscriber` | `@EventBusSubscriber` (`net.neoforged.fml.common`) |
| `FMLJavaModLoadingContext` en el ctor del `@Mod` | `(IEventBus modEventBus, ModContainer modContainer)` inyectados |
| `RegistryObject<T>` | `DeferredHolder<R,T>` / `DeferredItem` / `DeferredBlock` |
| `DeferredRegister.create(ForgeRegistries.X, id)` | `DeferredRegister.createBlocks/createItems(id)` o `DeferredRegister.create(Registries.X, id)` |
| `ResourceLocation` | **`Identifier`** (`net.minecraft.resources.Identifier`) |
| `defineSynchedData()` + `entityData.define(...)` | `defineSynchedData(SynchedEntityData.Builder builder)` + `builder.define(...)` |
| `addAdditionalSaveData(CompoundTag)` | `addAdditionalSaveData(ValueOutput)` |
| `readAdditionalSaveData(CompoundTag)` | `readAdditionalSaveData(ValueInput)` — `input.getBooleanOr("k", def)` |
| `EntityModel<T extends Entity>` + `setupAnim(entity, …)` | `EntityModel<DeluxeEntityRenderState>` + `setupAnim(renderState)` — **el modelo ya no ve la entidad** |
| `RenderLayer` con acceso a la entidad | Render states; lo que se necesite se captura en `extractRenderState` |
| `BlockPathTypes` | `PathType` |
| `MobType.UNDEAD` / `getMobType()` | ⚠️ **Eliminado** → `entity.getType().is(EntityTypeTags.UNDEAD)` (afecta a Tangoftero) |
| `AttributeModifier(UUID, name, …)` | `AttributeModifier(Identifier, …)` (afecta a la armadura del Hell_Hippo y a `SpearItem`) |
| `Multimap<Attribute,AttributeModifier>` en items | Componente `ItemAttributeModifiers` |
| `ItemProperties.register(item, "throwing", …)` | Definiciones de modelo de item en `assets/<ns>/items/*.json` con `select`/`condition` (ver `deluxelib/items/dori_spear.json`) |
| `IClientItemExtensions` + BEWLR | Sistema nuevo de modelos especiales (`LayerRenderState.setupSpecialModel`, ver `HelmetInteriorRenderer`/`StatueItemRenderer`) |
| `ForgeSpawnEggItem` | `SpawnEggItem` vanilla |
| `HorseArmorItem` | ⚠️ Eliminado — armadura animal por data components |
| `item.isEdible()` | `stack.get(DataComponents.FOOD) != null` |
| `SimpleChannel` / `NetworkRegistry` | `NetworkCreator` de DeluxeLib (payload system) |
| `DistExecutor` + proxies | `Dist`-gated `@EventBusSubscriber` / clases cliente separadas |
| `LivingAttackEvent` | `LivingIncomingDamageEvent` |
| `spawnAtLocation(Item)` | firma con `ServerLevel` |
| `checkAnimalSpawnRules(...)` | firma cambiada (`ServerLevelAccessor`, `EntitySpawnReason`) |
| `AnimationState.getAccumulatedTime()` | Privatizado → `AnimSource` de DeluxeLib |

## 3.3 Riesgos identificados

| Riesgo | Afecta | Mitigación |
|---|---|---|
| 🔴 **`PartEntity` / multipart** — verificar que sigue existiendo y con qué API en NeoForge 26.1 | GT (7 hitboxes), Niras (2) | Investigar primero; alternativa: entidades hijas reales o `AttackShape` de DeluxeLib para el daño + hitbox único grande |
| 🔴 **`HorseArmorItem` eliminado** | `hellhippo_armor` | Reimplementar con data components; ver cómo lo hace vanilla con `AnimalArmorItem`/llamas |
| 🔴 **`AbstractChestedHorse`** cambió mucho (inventario, `ContainerEntity`) | Hell_Hippo | Evaluar si conviene **rehacerlo sobre `BaseEntity` + `IArmoredEntity`** en vez de heredar de la clase vanilla (recomendado — resuelve también el problema estructural nº1) |
| 🟡 **BEWLR eliminado** | `niras_spear` en mano | Copiar el patrón de `HelmetInteriorRenderer`/`StatueItemRenderer` de DeluxeLib |
| 🟡 **`ServerBossEvent` como barra de cooldown** | Hell_Hippo (miedo, ataque montado) | Sigue existiendo, pero es un uso raro; considerar HUD propio estilo `PoiseBarRenderer` |
| 🟡 **Estructuras** — API de `StructurePlacementType` y `StructurePiece` | Nido de Tangoftero | Portar al final, es aislado |
| 🟡 **`MobType` eliminado** | Tangoftero (espantar no-muertos, `ENEMY_SELECTOR`) | `EntityTypeTags.UNDEAD` |

---

# PARTE 4 — Plan de implementación

## Principios

1. **DeluxeLib primero.** Antes de portar cualquier clase de infraestructura de SMOP, comprobar si DeluxeLib ya lo cubre (§2.1). Si lo cubre, se borra la de SMOP.
2. **Reescribir animación, no portarla.** Las clases `*Animations.java` se copian tal cual; toda la lógica `AnimationState`/`updateXAnimations()` se **tira** y se rehace como registro declarativo en `registerAnimations()` del `Animatable`.
3. **Un mob a la vez, compilando y probando en juego** antes de pasar al siguiente.
4. **Orden por complejidad ascendente** — el mob más simple valida la infraestructura antes de atacar el más difícil.
5. **Sin `System.out.println`.** Logger o nada.
6. **Paquete nuevo:** `net.darkblade.smop` (el proyecto 26.1 ya lo usa), no `net.darkblade.smopmod`.

## Fase 0 — Infraestructura de proyecto ✅ COMPLETA

- [x] **DeluxeLib cableada como composite build.** `settings.gradle` hace `includeBuild('../Deluxelib-Neoforge-26.1')` con `dependencySubstitution` explícita (`net.darkblade.deluxelib:deluxelib` → `project(':')`). La substitución automática de Gradle no servía: el nombre del proyecto Gradle de DeluxeLib es el de su carpeta (`Deluxelib-Neoforge-26.1`), que no coincide con el artifact id `deluxelib` que produce su `base.archivesName`.
  - Ventaja: editar fuentes de DeluxeLib se refleja en la siguiente build de SMOP, sin paso de publicación.
  - `build.gradle`: `implementation "net.darkblade.deluxelib:deluxelib:${deluxelib_version}"`.
  - `gradle.properties`: `deluxelib_version=1.0.0` (debe seguir al `mod_version` de DeluxeLib).
- [x] **Dependencia declarada en `neoforge.mods.toml`** con `ordering="AFTER"`, para que los `@EventBusSubscriber` de DeluxeLib (los hooks de `MobAnimator` sobre tick/join/death, el cableado de render del jinete) estén activos antes de que cualquier contenido de SMOP los toque. `deluxelib_version` añadida a `replaceProperties` de `generateModMetadata`.
- [x] **Template limpiado:** borrados `Config.java`, `SpectacularMobsOfPeligoro.java`, `SpectacularMobsOfPeligoroClient.java` y todo el contenido de ejemplo (`EXAMPLE_BLOCK`, `EXAMPLE_ITEM`, `EXAMPLE_TAB`).
- [x] **Clase principal renombrada a `SMOP`** (+ `SMOPClient`), igual que en 1.20.1, para que el port del resto sea mecánico: `SMOP.MOD_ID` y `SMOP.id(path)` se usan por todo el código viejo. `SMOPClient` es `@Mod(dist = Dist.CLIENT)` y sustituye al par `ClientProxy`/`CommonProxy` + `DistExecutor`.
- [x] **Assets copiados** (156 archivos): 85 texturas, 63 modelos, 5 blockstates, `sounds/` + `sounds.json`, `lang/en_us.json` real.
- [x] `-Xmaxerrs/-Xmaxwarns 2000` en `JavaCompile` (igual que DeluxeLib) para que un port parcial muestre todos los errores reales de golpe en vez de abortar a los 100 y generar cascadas de falsos "cannot find symbol".
- [x] **Verificado end-to-end:**
  - `compileJava` OK, con clase temporal de humo importando `DeluxeLib`, `MobAnimator`, `AttackShape`, `NetworkCreator`, `AbstractFlyingEntity`, `CustomMobRenderer` (borrada después).
  - `runtimeClasspath` resuelve `net.darkblade.deluxelib:deluxelib:1.0.0 -> project :Deluxelib-Neoforge-26.1`.
  - `runData` arranca el mod loader: FML descubre `deluxelib-1.0.0.jar` como mod válido, carga su entrypoint, aplica `deluxelib.mixins.json` y suscribe `MobAnimator$Events` al game bus. `smop` carga con entrypoints `[SMOP, SMOPClient]`. **Cero ERROR/FATAL.**

**Notas / pendientes que salieron de esta fase:**

- La estructura de paquetes se crea en la Fase 1 junto con sus clases de registro (los directorios vacíos no sobreviven a git).
- **`data/` NO se copió.** Los datapacks de 1.20.1 usan rutas que cambiaron en 1.21+ y un JSON que no parsea puede tumbar la carga del datapack. Hay que portarlos fase por fase aplicando los renombres:

  | 1.20.1 | 26.1 |
  |---|---|
  | `data/smop/loot_tables/` | `data/smop/loot_table/` |
  | `data/smop/recipes/` | `data/smop/recipe/` |
  | `data/smop/tags/blocks/` | `data/smop/tags/block/` |
  | `data/minecraft/tags/items/` | `data/minecraft/tags/item/` |
  | `data/smop/forge/biome_modifier/` | `data/smop/neoforge/biome_modifier/` |
  | `data/smop/structures/` | `data/smop/structure/` |

  Loot tables y recipes se regenerarán con datagen (Fase 9); worldgen/structure/template_pool se portan a mano en la Fase 9 con el nido de Tangoftero.
- `smop.mixins.json` y los 2 mixins **no se copian**: los sustituye el sistema de pose de jinete de DeluxeLib.
- El `lang/en_us.json` copiado no tiene claves para krifto/niras/salmon ni para los keybinds; se completa con `DeluxeLangProvider` en la Fase 1.

## Fase 1 — Registros base (sin entidades) ✅ COMPLETA

- [x] **`SMOPItems`** — 13 items (3 misc + 10 comida) + creative tab `smop_tab`.
- [x] **`SMOPFoods`** — nutrición y efectos al comer.
- [x] **`SMOPEffects` + `FearEffect`**, **`SMOPSounds`** (`gt_roar`), **`SMOPTags`** (clave `EGG_BLOCKS`).
- [x] **Red:** `SMOPNetwork` sobre `NetworkCreator` + `RiderActionServerPacket`.
- [x] **Keybinds** R/G/V/X con categoría propia.
- [x] **Datagen de lang** sobre `DeluxeLangProvider`; el `en_us.json` manual se borró.
- [x] **13 `assets/smop/items/*.json`** (capa de definición de item de 26.1) + `data/minecraft/tags/item/meat.json`.
- [x] **Verificado:** `compileJava` OK · `runData` genera el lang correcto · **arranque completo de servidor dedicado** (`Done (7.727s)!`, mundo generado, datapacks cargados) con **cero ERROR y cero WARN**.

**Cambios de API que salieron al portar (añadir a §3.2):**

| 1.20.1 | 26.1 | Dónde afectó |
|---|---|---|
| `FoodProperties.Builder.effect(Supplier, float)` | **Eliminado.** Efectos al comer → componente `Consumable`: `Consumables.defaultFood().onConsume(new ApplyStatusEffectsConsumeEffect(...)).build()`, pasado a `Item.Properties#food(FoodProperties, Consumable)` | `SMOPFoods` |
| `FoodProperties.Builder.meat()` | **Eliminado.** Su único efecto real era permitir alimentar lobos → tag `minecraft:meat` (que `minecraft:wolf_food` incluye vía `#minecraft:meat`) | `data/minecraft/tags/item/meat.json` |
| `.saturationMod(f)` | `.saturationModifier(f)` | `SMOPFoods` |
| `MobEffects.DAMAGE_BOOST` | `MobEffects.STRENGTH` (y todos son `Holder<MobEffect>`) | `SMOPFoods` |
| `BowlFoodItem` | **Eliminado.** → `Item.Properties#usingConvertsTo(Items.BOWL)` | `krifto_stew` |
| `MobEffect.addAttributeModifier(Attribute, String uuid, double, Operation)` | `addAttributeModifier(Holder<Attribute>, Identifier, double, Operation)` | `FearEffect` |
| `Operation.ADDITION` / `MULTIPLY_TOTAL` | `ADD_VALUE` / `ADD_MULTIPLIED_TOTAL` | `FearEffect` |
| `MobEffect.isDurationEffectTick(int,int)` | `shouldApplyEffectTickThisTick(int,int)` | `FearEffect` |
| `new KeyMapping(name, key, "key.categories.smopmod")` | `new KeyMapping(name, InputConstants.Type.KEYSYM, key, KeyMapping.Category)`. La categoría es un objeto construido con un `Identifier` y registrado con `RegisterKeyMappingsEvent#registerCategory`; su clave de idioma se deriva como `key.category.<ns>.<path>` | `SMOPKeybinds` |
| `@Mod.EventBusSubscriber(bus = Bus.MOD)` | **El atributo `bus` ya no existe** — 26.1 deduce el bus del tipo de evento, así que handlers de mod-bus y game-bus conviven en la misma clase | `SMOPKeybinds` |
| Solo `models/item/<id>.json` | Además hace falta `assets/<ns>/items/<id>.json` (capa de definición de item que apunta al modelo) o el item sale como "modelo faltante" | 13 archivos nuevos |

**Decisiones tomadas en esta fase:**

- **`StoCSyncFlying` eliminado, no portado.** Existía solo porque el `FlyingEntity` de 1.20.1 cambiaba un campo no sincronizado en `switchNavigation()`. `AbstractFlyingEntity` de DeluxeLib guarda el estado de vuelo en un `EntityDataAccessor` sincronizado, así que el cliente ya está de acuerdo sin paquete. **De los 4 paquetes originales solo sobrevive 1.**
- **`RiderActionPacket` rediseñado sobre una interfaz.** El original tenía un `switch` sobre clases concretas (`Hell_HippoEntity`, `NirasmosaurusEntity`), lo que obligaba a la capa de red a importar cada montura y a editarse con cada nueva. Ahora existe `RiderControllable` (`onRiderAction(ServerPlayer, RiderAction)`): el paquete solo sabe "el vehículo gestiona sus propias acciones" y cada montura se apunta implementando la interfaz en su fase. Además el paquete no lleva id de entidad — el servidor lee `player.getVehicle()`, así que un cliente no puede falsear en qué montura va.
- **Lang por datagen.** Un `en_us.json` a mano en `src/main/resources` colisionaría con el generado en `src/generated/resources` (misma ruta, dos source dirs). Los nombres automáticos salen idénticos a los que había escritos a mano; solo `cooked_nirasmo_meat` → "Nirasmo Cooked Meat" necesita override.
- **`entity.smop.gt` decía "Grant Tyrant"** (errata: el bloque decía "Grand Tyrant Head"). Se corregirá a "Grand Tyrant" cuando la entidad se registre en la Fase 6.
- **No se crearon `SMOPBlocks` ni `SMOPEntities` vacíos** — se crean con su contenido en las Fases 2 y 3.
- **Datos diferidos por dependencias:** el tag `smop:egg_blocks` (necesita los bloques, Fase 2), `minecraft:arrows` con `smop:tango_arrow` (Fase 7) y el `DeluxeEntityLootProvider` (necesita entidades, Fase 3). Un tag que apunta a algo no registrado tumba la carga del datapack.

## Fase 2 — Núcleo de entidades SMOP ✅ COMPLETA

**33 clases.** Compila y arranca servidor con cero ERROR/WARN; `EggBreakHandler` verificado suscrito al game bus.

- [x] **`SMOPAnimal`** (ex-`BaseEntity`) — sueño, roar, huevos, sit/follow/wander.
- [x] **`GenderedSMOPAnimal`** + `Gendered`.
- [x] **Sueño:** `SleepGoal` + `SleepUrge` + `ISleepingEntity` / `ISleepAwareness` / `ISleepThreatEvaluator` (ver §"El sueño como Goal").
- [x] **Grupo:** `GroupType`, `GroupReaction`, `IGroupBehaviour`, `IHasLeader`, `GroupUtil`.
- [x] **Goals genéricos (8):** `GenericBreedGoal`, `FollowOwnerBaseGoal`, `FollowGroupLeaderGoal`, `OneAttackGoal`, `RoarOnHurtGoal`, `RoarOnTargetGoal`, `SMOPRandomStrollGoal`, `SMOPRandomSwimmingGoal`.
- [x] **Huevos:** `AbstractEggBlock` + `EggBlock` + `SmallEggsBlock` + `RoeEggsBlock`, `ProtectEggBaseGoal` + `ProtectOwnEggGoal` + `ProtectNearestEggGoal`, `GenericLayEggGoal`, `EggGoalRegistry`, `EggBreakHandler`, `CustomEggBorn`, `RandomVariantCapable`.

### El cambio de fondo: la animación pasa a ser declarativa

`BaseEntity` tenía ~12 campos `AnimationState` y `updateBaseAnimations()`, una cascada imperativa de ~100 líneas de `start()`/`stop()` que cada subclase reescribía (`updateFlyingAnimations`, `updateAquaticAnimations`, `playOnly`, `stopAllXExcept`...) y que se peleaba consigo misma. **Todo eso desaparece.**

`SMOPAnimal` solo expone **estado sincronizado**; cada mob atará sus clips de Blockbench a ese estado con `setPlayCondition` en su `registerAnimations()`, y `MobAnimator` se encarga de capas, prioridades, blending y arranque automático.

Consecuencia importante: como las play conditions se evalúan **en ambos lados**, todo lo que lean tiene que coincidir en ambos lados. Por eso `isMoving()` es un flag sincronizado alimentado por un hold-timer y no una lectura directa de `getDeltaMovement()` (que no se sincroniza para mobs y haría parpadear el clip de caminar en cliente).

### El sueño como Goal (refactor posterior a la Fase 2)

La primera versión fue un `SleepCycleController` tickeado desde `SMOPAnimal.tick()` que volteaba flags, y **cada** otro goal llevaba su gate `!isInSleepCycle()`. Se cambió a un `Goal` real antes de escribir el primer mob. Reparto de responsabilidades:

- **`SleepGoal`** — la *actividad*. Declara `MOVE`, `LOOK`, `JUMP`, así que el `GoalSelector` preempta al resto en lugar de que cada goal pregunte si el mob duerme. Es la misma forma que usa el zorro de vanilla (`Fox.java:793`). Máquina de fases preparing→sleeping→awakening, escaneo de amenazas y despertar.
- **`SleepUrge`** — el *impulso*. Contadores de "es de noche, llevo N ticks sin objetivo, hace M que no me despertaron", tickeado sin condiciones desde la entidad.

**Por qué el impulso vive fuera del goal.** En `GoalSelector.tick()` la comprobación es
`goalCanBeReplacedForAllFlags(...) && goal.canUse()`, y Java cortocircuita: si un goal de mayor prioridad tiene el flag MOVE bloqueado, **`canUse()` ni se llama**. Como `RandomStrollGoal` bloquea MOVE, unos contadores dentro de `canUse()` solo avanzarían en los huecos entre paseos y el mob se dormiría de forma errática. Con el reloj en la entidad es monótono y `canUse()` queda como predicado puro.

**El goal se suelta solo.** Amenaza cerca, amanecer o petición de despertar desde `hurt()` pasan el ciclo a la fase de awakening; al terminar, el goal para y el combate toma el control. Por eso puede vivir en prioridad alta sin dejar al mob bloqueado sin reaccionar.

**Lo que el Goal NO elimina.** `goalSelector` y `targetSelector` son dos `GoalSelector` distintos, **cada uno con su propio `lockedFlags`**, así que los target goals no se preemptan: hay que seguir metiéndoles `!isInSleepCycle()` a mano. El propio zorro de vanilla lo hace (`Fox.java:1005`, `1093`, `1127`). Documentado en el javadoc de `createSleepGoal()`.

**`isImmobile()` no sirve.** Devolver `true` ahí hace que `LivingEntity.aiStep():3077` salte `serverAiStep()` entero — dejarían de tickear los goals, incluido el propio `SleepGoal` que tiene que contar y despertar al mob. El freeze sigue en `travel()`, ahora tras un único hook `isMovementLocked()` en vez de condiciones sueltas.

Resultado: se fueron los gates de sueño de `GenericBreedGoal`, `SMOPRandomStrollGoal` y `SMOPRandomSwimmingGoal` (estos dos quedan con una sola condición extra opcional), y `OneAttackGoal` conserva solo el de rugido — los goals de rugido no tienen sus flags bloqueados hasta que *arrancan*, y un golpe no debe empezar en el tick intermedio.

### Simplificaciones aplicadas

| Antes (1.20.1) | Ahora | Por qué |
|---|---|---|
| `EggBlock` (148) + `SmallEggsBlock` (183), ~80% idénticas | `AbstractEggBlock` + 2 subclases delgadas | Incubación, pisoteo, soporte y eclosión son compartidos; las subclases solo dicen su forma y qué significa "se rompe un huevo" |
| `FollowOwnerBaseGoal` con ~40 líneas de teleport a mano | `TamableAnimal#tryToTeleportToOwner()` de vanilla | 26.1 lo trae mantenido, con hook `canFlyToOwner()` |
| `GenericLayEggGoal` acoplado a `ProtectOwnEggGoal` (+ constructor con `null`) | Recibe un `Consumer<BlockPos>` | Poner huevos ya no sabe nada de defenderlos; `EggGoalRegistry` los une |
| `SleepCycleController` recibía 3 `AnimationState`… | …que **nunca usaba** | Parámetros muertos, eliminados |
| Ciclo de sueño como controlador + gate `!isInSleepCycle()` en cada goal | `SleepGoal` (flags MOVE/LOOK/JUMP) + `SleepUrge` | La arbitración la hace el `GoalSelector`, no un `if` en `travel()` |
| `isAwakeing` / `setAwakeing` | `isAwakening` / `setAwakening` | Errata |
| `System.out.println` en sueño, roar, ataque, huevos | Eliminados | ~30 llamadas de debug |

### Cambios de API encontrados (añadir a §3.2)

| 1.20.1 | 26.1 |
|---|---|
| `Level#getDayTime()` | `getOverworldClockTime()` |
| `Player#displayClientMessage(c, true)` | `sendOverlayMessage(c)` |
| `LivingEntity#hurt(DamageSource, float)` | `hurtServer(ServerLevel, DamageSource, float)` |
| `Entity#moveTo(x,y,z,yaw,pitch)` | `snapTo(...)` |
| `EntityType#create(Level)` | `create(Level, EntitySpawnReason)` |
| `MobSpawnType` | `EntitySpawnReason` |
| `Mob#finalizeSpawn(..., SpawnGroupData, CompoundTag)` | 4 args, sin `CompoundTag` |
| `mob.doHurtTarget(target)` | `doHurtTarget(ServerLevel, target)` |
| `MeleeAttackGoal#checkAndPerformAttack(target, distSq)` | `checkAndPerformAttack(target)`; alcance vía `Mob#isWithinMeleeAttackRange` |
| `BlockBehaviour.Properties.copy(b)` | `ofFullCopy(b)` |
| `updateShape(state, dir, neighbourState, level, pos, neighbourPos)` | `updateShape(state, LevelReader, ScheduledTickAccess, pos, dir, neighbourPos, neighbourState, RandomSource)` |
| `propagatesSkylightDown(state, getter, pos)` | `propagatesSkylightDown(state)` |
| `entityInside(state, level, pos, entity)` | `entityInside(..., InsideBlockEffectApplier, boolean isPrecise)` — y la clase vive en `net.minecraft.world.entity`, no en `...level.block` |
| `ForgeEventFactory.getMobGriefingEvent(level, entity)` | `EventHooks.canEntityGrief(ServerLevel, Entity)` |
| `BlockPathTypes` | `PathType` |
| `Block.codec()` | No hace falta implementarlo (DeluxeLib tampoco lo hace) |

### Pendiente que arrastra esta fase

- **`SMOPBlocks` no existe todavía.** Los bloques de huevo se construyen con un `Supplier<EntityType<…>>` y no hay entidades registradas hasta la Fase 3. Las **clases** están listas; el **registro**, los modelos/blockstates y el tag `smop:egg_blocks` se hacen al llegar cada mob con su huevo.
- **`MovementHysteresis` de DeluxeLib es package-private**, así que no se puede usar desde SMOP. `SMOPAnimal` lleva su propia copia de 10 líneas (`MoveHold`). Candidata clara a hacerse `public` en DeluxeLib y deduplicar.
- **`CrackFX`** (partículas al agrietarse el huevo) no se portó: era cliente puro y lo cubre `ParticleFx` de DeluxeLib; se conecta cuando haya un huevo registrado que lo dispare.

## Fase 3 — Mob 1: **Tangoftero** ✅ COMPLETA

Primer mob end-to-end. Compila, genera datos y arranca servidor con cero ERROR/WARN.

- [x] `TangofteroEntity` sobre `SMOPAnimal` · `TangofteroVariant` · `AssistFlockGoal`
- [x] `TangofteroModel` / `TangoBabyModel` como `EntityModel<DeluxeEntityRenderState>` + `Rig`
- [x] `TangoAnimations` + `TangoBabyAnimations` — **2475 líneas copiadas, solo cambió la línea `package`** (verificado con md5 tras normalizar fin de línea)
- [x] `TangofteroRenderState` + `TangofteroRenderer` · `SMOPEntities` · `SMOPBlocks` · huevo + block item + spawn egg
- [x] Datagen de lang y de loot table; tag `smop:egg_blocks` ya con contenido

### El patrón de animación, ya validado

```java
StandardAnimation idle = clip("idle", TangoAnimations.idle, TangoBabyAnimations.idle, Loop.REPEATING, 3, 0.8F);
idle.setPlayCondition(a -> this.canPlayLocomotion() && !this.isInWater() && !this.isMoving());
```

Dos reglas que salieron de escribirlo y que aplican a los cinco mobs restantes:

1. **La exclusión entre clips se hace por play condition, no por prioridad.** `MobAnimator#play` solo detiene animaciones de prioridad `<=` a la entrante, así que un idle de prioridad baja seguiría sonando por debajo de un ataque. Las cuatro condiciones de locomoción se escribieron mutuamente excluyentes a propósito.
2. **`Loop.REPEATING` arranca solo cuando su condición se cumple; `Loop.PLAY_ONCE` hay que dispararlo** con `animator().play(...)`. Por eso `bite` y `roar` no llevan play condition — los lanza el código de alimentación.

### 🔴 `PLAY_ONCE` + play condition = clip que no suena nunca

Síntoma en juego: los Tangofteros **no reproducían ninguna de las dos transiciones de sueño**. Se quedaban congelados y pasaban directos al bucle `sleep`, o volvían a `idle` sin más.

**Causa.** El bucle de auto-arranque de `MobAnimator.tick()` filtra por `Loop`:

```java
if (!anim.isPlaying() && anim instanceof BaseAnimation base && base.getLoop().repeats() && anim.canPlay())
```

`base.getLoop().repeats()` es `true` solo para `REPEATING`. **Un clip `PLAY_ONCE` con play condition no arranca jamás**, por muy cierta que sea la condición. `sleep` es `REPEATING` y sí arrancaba — de ahí el salto directo al bucle. Y como durante `awakening` el mob sigue en `isInSleepCycle()`, la locomoción también estaba bloqueada: de ahí el congelado.

Lo tenía escrito en mis propias notas de esta misma fase ("`PLAY_ONCE` hay que dispararlo") y aun así registré las transiciones con condición y sin disparo.

**Corrección.** `ISleepingEntity` gana tres hooks (`onPreparingSleepBegin`, `onSleepBegin`, `onAwakeningBegin`), `SleepGoal` los llama en cada transición y `SMOPAnimal` los implementa reproduciendo clips por nombre convencional (`preparing_sleep` / `sleep` / `awakening`) **si el mob los registró**. Es la misma forma que `AbstractFlyingEntity` usa con `onTakeoffBegin`/`onLandingBegin`.

Las play conditions **se quedan**, pero su papel es el inverso: `BaseAnimation#tick` detiene un clip cuya condición se vuelve falsa, así que un mob despertado a media transición corta el clip en vez de terminarlo.

**Segundo fallo del mismo sitio:** las fases duraban `duración + offset(0–9)` mientras que los clips duran exactamente la duración, así que el mob se quedaba hasta 9 ticks clavado en el último fotograma. El desfase por entidad se movió a `SleepUrge` — la manada se desincroniza decidiendo dormir en momentos distintos, no estirando las animaciones.

**Regla:** la duración de fase y la del clip tienen que ser el mismo número. `getPreparingSleepDuration()` = 20 ticks ↔ clip de 1.0 s.

### 🔴 Clases de animación son cliente-only: nunca referenciarlas fuera de un lambda

Crash **solo en servidor dedicado**, encontrado por la prueba de humo:

```
ClassNotFoundException: net.minecraft.client.animation.AnimationDefinition$Builder
    at TangoAnimations.<clinit>
    at TangofteroEntity.registerAnimations
```

`AnimationDefinition` es `@OnlyIn(Dist.CLIENT)`, y `registerAnimations()` corre en **ambos lados** (`MobAnimator` engancha `EntityJoinLevelEvent`). Leer una constante de animación ahí carga la clase y mata al servidor.

Los mobs de DeluxeLib no caen porque la referencia va **dentro del lambda**: `new AnimSource(() -> OwlAnimation.FLY_IDLE)` — el cuerpo no se ejecuta hasta que el cliente renderiza. Mi helper `clip()` recibía las definiciones como **argumentos**, que Java evalúa de forma anticipada, y eso rompía la pereza. Corregido pasando `Supplier<Object>`.

Dos detalles que lo hacen especialmente traicionero:

- **Singleplayer nunca lo reproduce.** El servidor integrado tiene las clases de cliente en el classpath. Solo aparece en servidor dedicado, es decir, en multijugador de verdad.
- **El `try/catch` de `MobAnimator` no te salva.** Captura `Exception`, pero un inicializador estático que falla lanza `ExceptionInInitializerError` / `NoClassDefFoundError`, que son `Error`.

**Regla:** en código común, toda referencia a `*Animations.*` va dentro de un lambda. Nunca como argumento.

### 🔴 Trampa de orden de inicialización: nada que use `registerGoals()` puede ser un inicializador de campo

Crash real al soltar el primer Tangoftero:

```
NullPointerException: Cannot invoke "SleepUrge.wantsToSleep()" because "this.urge" is null
    at SleepGoal.canUse(SleepGoal.java:58)
```

**Causa.** `Mob`'s constructor llama a `registerGoals()` (`Mob.java:158`), y en Java los **inicializadores de campo de una subclase no corren hasta que el constructor de la superclase ha vuelto**. Así que cualquier cosa que `registerGoals()` toque sigue valiendo `null` en ese momento:

```java
private final SleepUrge sleepUrge = new SleepUrge(this);   // ← todavía null en registerGoals()

protected SleepGoal<SMOPAnimal> createSleepGoal() {
    return new SleepGoal<>(this, this.sleepUrge, ...);      // ← pasa null
}
```

No falla al construir: falla en el **primer tick** del **primer mob** que se spawnee, dentro de `GoalSelector.tick()`.

**Corrección.** `sleepUrge()` y `animator()` se construyen perezosamente y **todo** acceso pasa por el accessor. Lo que sí es seguro leer en `registerGoals()` es cualquier cosa que `Mob` haya asignado antes de la línea 158 — `goalSelector`, `navigation`, `moveControl`, `lookControl`, `sensing` — y `getId()`, que es un inicializador de campo de `Entity` y por tanto ya tiene valor.

**Regla para las fases siguientes:** en `SMOPAnimal` y derivadas, nada que un goal necesite en su constructor puede vivir en un inicializador de campo. Si lo necesita, accessor perezoso. Los argumentos que solo se usan dentro de lambdas (`attackCondition`, `onAttack`) son seguros porque se evalúan más tarde.

### 🔴 Huesos animados que no existen en el modelo — afecta a 6 de 10 parejas

**El fallo latente más grave del port.** En 1.20.1, `KeyframeAnimations.animate` resolvía cada hueso con `getAnyDescendantWithName` → `Optional` y **saltaba en silencio** los que no encontraba. En 26.1 las animaciones se *bakean* (`AnimationDefinition#bake` → `KeyframeAnimation.bake`) y eso **lanza**:

```
IllegalArgumentException: Cannot animate <hueso>, which does not exist in model
```

Los exports de Blockbench del mod arrastran canales para huesos que sus modelos no tienen (restos de copiar el rig del adulto al bebé, sobre todo). Eran inofensivos en 1.20.1 y son un **crash de cliente** aquí. Peor: el bake es perezoso — revienta la primera vez que *ese* clip concreto suena en *ese* modelo concreto, así que una prueba rápida puede no tocarlo.

Barrido sobre el mod 1.20.1 completo:

| Modelo | Estado | Huesos huérfanos |
|---|---|---|
| Hell Hippo adulto | 🔴 | `neck2` |
| Hell Hippo bebé | 🔴 | `left_calf`, `neck2`, `nose_hairs`, `right_calf` |
| Krifto adulto | 🟢 | — |
| Krifto bebé | 🔴 | `left_calf`, `left_claws1/2`, `right_calf`, `right_claws1/2` |
| Niras adulto | 🔴 | `gCoral1`–`gCoral5`, `gEyes` |
| Niras bebé | 🔴 | `gChest_n_corals`, `gCoral1`–`gCoral5`, `gCorals`, `gLeft_saliva`, `gRight_saliva` |
| Salmón | 🟢 | — |
| GT | 🟢 | — |
| Tangoftero adulto | 🟢 | — |
| Tangoftero bebé | 🔴 → ✅ | `arms`, `epiglotis`, `tail_tip` — **corregido** |

**Corrección aplicada al Tangoftero bebé:** eliminados los canales muertos de los 12 clips (1171 → 1055 líneas). Es **sin pérdida**: esos canales tampoco hacían nada en 1.20.1, porque los huesos no existen. El bebé tiene la cola de 2 segmentos (sin `tail_tip`), los brazos colgando directos de `body_parts` (sin grupo `arms`) y no tiene `epiglotis`.

**Herramienta:** `tools/check-bones.sh <Model.java> <Animations.java...>` cruza ambos y lista los clips culpables. **Ejecutarlo para cada pareja modelo/animación antes de portar cada mob** — evita descubrirlo jugando.

```bash
tools/check-bones.sh src/main/java/.../TangoBabyModel.java src/main/java/.../TangoBabyAnimations.java
```

### Adulto y bebé: por qué el supplier dinámico es obligatorio

El modelo bebé **no tiene** `tail_tip`, `epiglotis` ni `muscles`, y los clips adultos los animan. Bakear un clip adulto contra el modelo bebé lanza `Cannot animate tail_tip, which does not exist in model` y tumba el cliente (es exactamente el fallo que DeluxeLib documenta para el búho). Como `AnimSource` resuelve su `Supplier` en cada consulta, cada clip elige definición por edad:

```java
new AnimSource(() -> this.isBaby() ? baby : adult)
```

y queda en sincronía con el modelo que el renderer intercambió, porque ambos leen `isBaby` dentro del mismo tick.

### Reestructuración del ataque tras revisar Athenian/Spartan

La primera versión del ataque del Tango era un port literal: `OneAttackGoal` levantaba un flag sincronizado `isAttacking()`, el clip era `REPEATING` con play condition sobre ese flag, y el daño lo aplicaba el goal contando ticks a mano. Al revisar cómo lo hacen los mobs de DeluxeLib resultó ser el patrón equivocado.

**Cómo lo hace `AthenianEntity`:**

```java
this.guardGoal = new GuardedMeleeAttackGoal(this, 1.8)
    .onAttack((enemy, animator) -> { animator.play(animator.getByName("attack")); return 10; });

StandardAnimation attack = new StandardAnimation("attack", attackData, Loop.PLAY_ONCE, 0, 0, 0.8125F);

HitWindow.of(4, 7)                            // el daño vive EN la animación
        .shape(AttackShape.capsule(1.35F, 0.5F))
        .anchor(0.5F, -0.4F, 1.3F)
        .damage(5.0F)
        .applyTo(attack);
```

Tres diferencias de fondo:

1. **El daño vive en la animación, no en el goal.** `HitWindow.of(inicio, fin)` registra eventos de frame que barren una forma en los ticks exactos del clip. Sin flag, sin contar ticks, y el número de duración deja de estar duplicado entre goal y registro.
2. **Los clips de ataque son `PLAY_ONCE` sin play condition**, disparados imperativamente. No hacen falta condiciones evaluables en cliente porque llegan por el paquete de sincronización.
3. **Las play conditions leen estado del goal directamente** (`guardGoal.isGuarding()`), con guarda de nulo. Se puede porque `registerGoals()` es **solo servidor** (`Mob.java:158`) y las play conditions son de facto autoritativas de servidor: el cliente no se auto-interrumpe, solo refleja lo que le sincronizan.

**Cambios aplicados al Tangoftero:**

| Antes | Ahora |
|---|---|
| `OneAttackGoal` propio (~120 líneas) | `AnimatableMeleeAttackGoal` de DeluxeLib — era una reimplementación peor de algo que la librería ya traía |
| `attack` `REPEATING` + play condition `isAttacking()` | `PLAY_ONCE` disparado desde `.onAttack(...)` |
| Daño por `doHurtTarget` en el tick `getBaseAttackDelay()` | `HitWindow.of(6, 8)` con `AttackShape.sector(2.0F, 70.0F)` — arco frontal real, un golpe por víctima, filtro para no morderse entre ellos, y depurable con `/deluxelib debug hitboxes` |
| Flag sincronizado `COMBAT_SPRINT` | `isAggressive()` de vanilla, que `MeleeAttackGoal` ya levanta |
| `canPlayLocomotion()` leía `isAttacking()` | Lee `animator().isPlaying("attack")` |

**Eliminado de `SMOPAnimal`:** los EDA `ATTACKING` y `COMBAT_SPRINT`, más `isAttacking`/`setAttacking`, `isCombatSprinting`/`setCombatSprinting`, `getBaseAttackDelay` y `getAttackAnimationDuration`. Existían solo para servir a `OneAttackGoal`; dejarlos habría sido API muerta que los cinco mobs restantes copiarían por inercia.

`RoarOnHurtGoal` también perdió su comprobación `isAttacking()`: el goal de rugido y el de melee sostienen ambos MOVE y LOOK, así que el que esté registrado más arriba gana por el mapa de flags del selector — la misma arbitración que separa sueño y locomoción.

### Cableado goal → estado → play condition

El contrato es: **los goals escriben estado sincronizado, las play conditions lo leen.** Ningún goal toca el animator directamente.

Hay **dos** mecanismos, y elegir el correcto importa:

- **Clips en bucle** (locomoción, sueño) → `REPEATING` + play condition. La condición tiene que ser **evaluable en cliente**, porque el bucle de auto-arranque de `MobAnimator` corre en ambos lados. De ahí que `isMoving()` sea un EDA sincronizado y no una lectura de `getDeltaMovement()`.
- **Clips de un disparo** (ataque, rugido, muerte) → `PLAY_ONCE` + `animator().play(...)` imperativo. No necesitan condición: el servidor decide y el paquete de sincronización lo replica.

| Estado | Lo escribe | Lo leen |
|---|---|---|
| `isAggressive()` (vanilla) | `MeleeAttackGoal` | clips `walk` / `sprint` |
| `isMoving()` | hold-timer de `SMOPAnimal` | clips `idle` / `walk` / `sprint` / `swim` |
| `isRoaring()` | goals de rugido / el rugido por alimentación | `canPlayLocomotion()` + `isMovementLocked()` |
| fases de sueño | `SleepGoal` | clips `preparing_sleep` / `sleep` / `awakening` |
| clip `attack` en curso | `AnimatableMeleeAttackGoal` vía `animator.play` | `canPlayLocomotion()` con `isPlaying("attack")` |

**Fallo encontrado en la auditoría y corregido:** el rugido por alimentación del Tangoftero disparaba el clip (`animator().play("roar")`) pero **no levantaba `isRoaring()`**. Como `MobAnimator#play` solo detiene animaciones de prioridad `<=` a la entrante, y el rugido está en 0 con idle en 3, el resultado era el idle sonando por debajo del rugido *y* el mob paseándose durante sus propios 4 segundos de rugido (`isMovementLocked()` nunca se activaba). Ahora el estado se levanta durante la duración del clip. Nota: en 1.20.1 pasaba lo mismo — el `setupAnim` viejo aplicaba `roar` e `idle` en la misma pasada — así que esto **corrige** un fallo heredado, no lo reproduce.

También se ataron las duraciones a una sola constante por clip (`ATTACK_CLIP_TICKS`, `ROAR_CLIP_TICKS`), porque antes el número vivía duplicado en el goal y en el registro de la animación.

### Simplificaciones

| Antes | Ahora |
|---|---|
| `TangofteroTemptGoal` (solo existía para el gate de sueño) | `TemptGoal` de vanilla |
| `CustomWanderGoal` + flag `isFollowingOwner` | `SMOPRandomStrollGoal`; seguir al dueño ahora tiene prioridad **por encima** de vagar, así que el flag sobra (en 1.20.1 vagar estaba por encima y hacía falta el flag para desempatar) |
| `updateWalkBlend()` con EMA e histéresis a mano | El `isMoving()` sincronizado de `SMOPAnimal` + el blending de `KeyframeBlend` |
| `handleEntityEvent` con bytes 42/43 para bite/roar | `animator().play(...)`; `BaseAnimation` ya sincroniza al cliente |
| `setupAnim` con 10 llamadas a `animate()` | `RIG.apply(state, this, AnimContext.from(state))` |

### Cambios de API encontrados

| 1.20.1 | 26.1 |
|---|---|
| `entity.getType().is(tag)` | `entity.is(EntityTypeTags.X)` — el helper está en `Entity` |
| `SoundEvents.CHICKEN_AMBIENT/HURT/DEATH` | **Eliminados**: los sonidos de gallina adulta pasaron a variantes (`ChickenSoundVariants`). Sustituidos por `FOX_*`, que sí existen y encajan mejor con un carroñero — es un cambio de voz respecto al original |
| `new ForgeSpawnEggItem(type, c1, c2, props)` | `new SpawnEggItem(props.spawnEgg(type))`; la entidad es un componente de datos y los colores salen de la propia entidad |
| `SpawnPlacements.register(...)` en `commonSetup` | Evento `RegisterSpawnPlacementsEvent` |
| `MobRenderer` con `this.model = isBaby ? ... : ...` | `AgeableMobRenderer<T, S, M>`, genérico sobre el render state |
| `getTextureLocation(entity)` | `getTextureLocation(renderState)` — lo que decida la textura hay que extraerlo al state primero |
| `HierarchicalModel<T>` + `setupAnim(entity, ...)` | `EntityModel<DeluxeEntityRenderState>` + `setupAnim(state)` |
| `EntityType.Builder.build(String)` | `build(ResourceKey<EntityType<?>>)` |
| `Animal.createLivingAttributes()` | `Animal.createAnimalAttributes()` — 26.1 hace que `TemptGoal` lea `Attributes.TEMPT_RANGE`, y un atributo no declarado lanza `Can't find attribute minecraft:tempt_range` en el primer tick |
| `ResourceKey#location()` | `ResourceKey#identifier()` |
| `Level#getSharedSpawnPos()` | Eliminado; usar `getHeightmapPos(...)` o `ServerLevel#getRespawnData()` |
| `BlockBehaviour.Properties` libres | Las properties deben llevar el id del bloque → `DeferredRegister.Blocks#registerBlock(name, factory, propsSupplier)` |

### Problema de build encontrado y resuelto

**Los dos runs de datagen se borraban la salida mutuamente.** `GatherDataEvent` es abstracto en 26.1 y sus subclases `Client`/`Server` corren en **JVMs distintas** (`DataClient` vs `DataServer`). Cada run poda todo lo que haya bajo su `--output` y no haya generado él, así que apuntando ambos a `src/generated/resources` el segundo borraba el trabajo del primero (`removed stale: 2`). Verificado empíricamente en las dos direcciones.

Arreglado con un directorio de salida por run — `src/generated/client` y `src/generated/server` — ambos dados de alta como `srcDir` de recursos. **DeluxeLib tiene el mismo bug latente**: registra providers de cliente y de servidor pero su `build.gradle` solo define el run `data` (`clientData()`), así que sus providers de servidor (biome spawns, entity loot) nunca llegan a ejecutarse.

### Pendiente

- **No se ha probado en cliente.** El servidor dedicado valida registros, datapack y AI, pero no el render: modelos, `Rig`, texturas por variante y el intercambio adulto/bebé necesitan una sesión de juego real.
- Falta la loot table del **bloque** de huevo (la de la entidad ya está) y el spawn por bioma, que van con `DeluxeBiomeSpawns` en la Fase 9.

## Fase 3 (referencia original) — Mob 1: **Tangoftero** (el más simple, valida la infraestructura)

- [ ] `TangofteroEntity` sobre `SMOPAnimal`.
- [ ] `TangofteroModel` + `TangoBabyModel` → `EntityModel<DeluxeEntityRenderState>` + `Rig`.
- [ ] Copiar `TangoAnimations` + `TangoBabyAnimations` (2475 líneas, sin cambios).
- [ ] `registerAnimations()`: idle/walk/bite/roar/sleep×3/death con capas y prioridades. **`updateWalkBlend()` → `MovementHysteresis` + `walkSelector`.**
- [ ] Variantes, espantar-no-muertos (con `EntityTypeTags.UNDEAD`), alimentación, doma, cría.
- [ ] Goals: `TangofteroTemptGoal`, `CustomWanderGoal`, `AssistFlockGoal`.
- [ ] Bloque `tangoftero_egg` + item + spawn egg.
- [ ] **Hito: un mob completo funcionando end-to-end.** Aquí se valida el patrón para los otros 5.

## Fase 4 — Mob 2: **Salmón** + base acuática ✅ COMPLETA

- [x] `SMOPWaterAnimal` (ex-`WaterEntity`) sobre `MobAnimator`. Las ~140 líneas de `AnimationState` +
      `updateAquaticAnimations()` desaparecen; queda la física de nado, el daño por sequedad, el
      coletazo y la puesta en agua. `SWIMMING_FAST` va sincronizado con histéresis, como `isMoving()`.
- [x] `SalmonEntity`, `SalmonModel` (sobre `Rig` + `lookAtChain` cabeza→cuerpo→cola), `SalmonAnimations` (981, copia-pega).
- [x] `SalmonDigGoal` + listas de drops. El bloque se rompe en un `onFrame` del clip `dig`, no en un contador paralelo.
- [x] `RoeEggsBlock` registrado, forma plana tipo frogspawn, 3–6 alevines.
- [x] Registro completo: entidad, bloque, huevo de spawn, atributos, spawn `IN_WATER`, bioma `#is_river`, loot, lang.
- [x] `SmartSwimmingNavigation` — ver §nota de navegación abajo.

### Errores encontrados y corregidos (útiles para los mobs siguientes)

1. **`GenderedSMOPAnimal.canMate` tiraba la condición de vanilla.** Comprobaba sólo el sexo, y
   `BreedGoal` elige pareja con `canMate` pero continúa con `canContinueToUse`, que exige
   `partner.isInLove()`. Enganchaba al del sexo contrario más cercano estuviera alimentado o no y
   abortaba al tick siguiente. **Afectaba a todos los mobs de huevo, no sólo al salmón.**
2. **`GenericBreedGoal.breed()` abría en código las edades y el reset del amor**, perdiendo
   corazones, XP, estadística y logro. Ahora delega en `finalizeSpawnChildFromBreeding(level, partner, null)`.
3. **La puesta probaba una sola posición** — la del propio pez, que flota a media agua y nunca tiene
   suelo sólido debajo. Ahora busca hacia abajo hasta 6.
4. **Los alevines nacían en `pos.getY() - 0.5`**, o sea enterrados en el lecho: asfixiaban antes de verse.
5. **Detección de bloque excavable.** La regla de 1.20.1 (agua sólo en horizontal) falla en un río
   real; la de "agua sólo encima" falla en un estanque excavado, donde las caras alcanzables son las
   paredes. Vale cualquiera de las dos. Y `isSource()` sobraba: el agua corriente también es agua.
6. **Navegar al bloque objetivo, siendo sólido.** `SwimNodeEvaluator` lo tipa BLOCKED, A* devuelve
   ruta parcial y `moveTo` informa éxito igualmente. Hay que navegar a la casilla de agua contigua.
7. **`blockPosition().distSqr(...)` cuantiza los dos extremos** a esquinas de bloque — hasta bloque y
   medio de error. Para comprobar "estoy en rango" hay que medir de la posición real al centro del bloque.

### Nota de navegación

`SmartSwimmingNavigation` traslada al agua las dos ideas de `SmartFlyingNavigation`: rutas directas
(`canMoveDirectly` con `ClipContext.Fluid.NONE`) y aceptación de nodo tolerante. **El margen sólo
puede aplicarse a nodos intermedios** — aplicarlo al último avanza la ruta más allá de su propio
destino y el mob se planta dos bloques corto informando "he llegado".

Las clases de la librería no son reutilizables tal cual para nadar: `SmartFlyingNavigation` extiende
`FlyingPathNavigation`, y el escaneo de atajos de `PathCarrot` exige suelo firme y **rechaza fluidos**.
→ *Candidato real a subir a DeluxeLib: una navegación de "medio abierto" compartida entre vuelo y
nado, con `PathCarrot` parametrizable por medio. El Nirasmosaurus la va a querer igual.*

## Fase 5 — Mob 3: **Kriftognathus** + base voladora ✅ COMPLETA (código)

### Hecho (compila — **sin arrancar cliente ni servidor tras el cambio de vuelo**)

- [x] `SMOPFlyingAnimal` sobre `SMOPAnimal`, ya con el **ciclo de vuelo completo** de
      `AbstractFlyingEntity` traído dentro (ver §"El ciclo de vuelo" abajo). Reutiliza
      `SmartFlyingNavigation` y `SmoothFlyingMoveControl` de la librería (ambos aceptan un `Mob` a
      secas) en lugar del `NewFlyingPathNavigation` escrito a mano.
- [x] Se borra el paquete `StoCSyncFlying`: `FLYING` es entity data sincronizada.
- [x] Los bebés no vuelan, aplicado en `setFlying()` y no en cada punto de llamada.
- [x] `KriftognathusEntity`, modelos adulto + bebé sobre `Rig`, 4532 líneas de animación (copia-pega).
- [x] Percha en la cabeza del jugador (frena la caída a −0.15), pelaje según bioma de nacimiento.
- [x] Bloque `krifto_egg`, `CustomEggBorn`, huevo de spawn, atributos, spawn placement, loot, lang.
- [x] Spawns por bioma en **una sola entrada**: `DeluxeBiomeSpawnProvider` nombra su fichero por
      entidad, así que una segunda entrada del mismo mob sobrescribiría la primera.
- [x] `FollowOwnerFlyingGoal` reescrita sobre el `FollowOwnerGoal` interno del Owl.
- [x] `flightPitch`/`flightRoll` al render state, aplicados en `KriftognathusRenderer`.

### ⚠️ Por qué la herencia NO es opción (verificado contra las fuentes de 26.1)

`AbstractFlyingEntity extends PathfinderMob implements Enemy` (línea 72).

```
TamableAnimal → Animal → AgeableMob → PathfinderMob
AbstractFlyingEntity ─────────────────┘
```

- El `implements Enemy` **no** es el bloqueo, y tener dueño **tampoco**: el Owl es `AbstractFlyingEntity`
  y resuelve la propiedad con un simple `@Nullable UUID ownerUUID` + `findOwner()`, sin `TamableAnimal`.
- El bloqueo real es que `AbstractFlyingEntity` y `AgeableMob` son ramas **hermanas** de
  `PathfinderMob`, y el Krifto necesita la rama ageable: tiene polluelo, nace de huevo con
  `setAge(-24000)` y se cría. El Owl no tiene crías, por eso a él le basta `PathfinderMob`.
- *(Corrección a una versión anterior de esta nota, que decía que las hermanas eran `AgeableMob` y
  `Animal`. No: `Animal extends AgeableMob`. La conclusión no cambia.)*

→ Se trae el sistema, no se hereda la clase. Igual que con `SMOPWaterAnimal`.

### El ciclo de vuelo

`SMOPFlyingAnimal` se reescribió entera. Fuera: el interruptor binario de 1.20.1 —
`switchNavigation()`, `handleAutoNavigationSwitch()`, `groundTicks`/`maxGroundTicks`,
`isTouchingSolidGround()`, el EDA `WANTS_TO_FLY`, `FlyFromNowAndThenGoal` y
`RandomStrollAndFlightGoal`. Dentro:

```
REPOSO ──(groundRestTimer llega a 0)──▶ TakeoffGoal
  beginTakeoff()    → FLYING, TAKING_OFF, nav aérea, sin gravedad, onTakeoffBegin()
  completeTakeoff() → TAKING_OFF off, onTakeoffComplete()
VUELO ──(flightDurationTimer ≥ maxFlightTicks)──▶ descenso con motor
  seekingGround     → onSeekGroundBegin()          ← el picado
──(dentro de getLandingApproachAltitude())──▶ LandingGoal
  beginLanding()    → LANDING, onLandingBegin()
  completeLanding() → en tierra, gravedad, temporizador de reposo, onLandingComplete()
```

Los clips quedan enganchados así, y `swoop` **suena por primera vez** desde que existe el mod:

| Clip | Quién lo dispara |
|---|---|
| `start_flight` | `onTakeoffBegin()`. La fase dura lo que el clip: `shouldCompleteTakeoff()` devuelve `!isPlaying("start_flight")` |
| `swoop` | `onSeekGroundBegin()`. En 1.20.1 solo lo lanzaba `StealItemGoal`, que estaba comentado |
| `landing` | `onLandingBegin()`. `shouldCompleteLanding()` exige contacto **y** clip terminado; `getMaxLandingTicks()` sale del propio clip |

**Cinco cosas que no fueron copia-pega:**

1. **`DATA_IS_GROUND_MOVING` de la librería no se trajo.** `SMOPAnimal` ya tiene `MOVING`/`isMoving()`
   con el mismo hold-timer; lo único que hacía falta era que fuera `false` en el aire, o sea un
   override de `isMovingNow()`. Duplicar el flag habría sido API muerta que Niras y Hell Hippo
   copiarían por inercia.
2. **Bebés.** La invariante vivía solo en `setFlying()`. Ahora también cierra `TakeoffGoal.canUse()`
   y `beginTakeoff()`: con el ciclo puesto, el goal podía entrar en `start()` y quedarse a medias
   porque `setFlying` lo rechazaba en silencio.
3. **Dormir en el aire.** `TakeoffGoal` **no sostiene ningún flag** — es deliberado en la librería,
   para que los goals de tierra sigan corriendo mientras despega — así que el selector habría
   lanzado al aire a un mob dormido. `createSleepGoal()` se sobreescribe con `!isFlying()`.
4. **El rugido no inmoviliza en vuelo.** `isMovementLocked()` anula la horizontal en
   `SMOPAnimal#travel`; en tierra eso es "plantarse", en el aire es congelarse a media altura hasta
   que el goal de aterrizaje lo baje.
5. **Órdenes de quedarse.** El clamp de `isOrderedToSit()` de `SMOPAnimal` solo para la navegación y
   mata la velocidad — con la gravedad apagada eso es quedarse colgado del cielo. `aiStep` llama a
   `requestLanding()`, que vence el temporizador de vuelo y manda al mob por el camino normal de
   picado y aterrizaje. Su espejo `requestTakeoff()` vence el de reposo en vez de llamar a
   `beginTakeoff()` desde fuera, para que las puertas (bebé, dormido, posado) sigan en un solo sitio.

### `FollowOwnerFlyingGoal` — del pathfinding al control directo

La versión de 1.20.1 pathfindeaba hasta el dueño, recalculaba cada 10 ticks y **se teletransportaba a
los 12 bloques** — que en la práctica significa teletransportarse constantemente, porque una
navegación aérea rara vez cierra ese hueco en diez ticks. La nueva es una adaptación del
`FollowOwnerGoal` interno del Owl (`test/OwlEntity.java:1207`): navegación parada y vuelo por
control de velocidad directo.

- **Controlador PD**, `accel = kp·error − kd·velocidad`, con las ganancias del Owl sin tocar
  (`kp=0.04`, `kd=0.4`). No son perillas independientes: subir kp o bajar kd vuelve complejos los
  autovalores de la recurrencia y el mob empieza a oscilar — el "se acerca, se aleja, se acerca" que
  produce la versión ingenua (velocidad objetivo proporcional a la distancia pasada por un segundo
  suavizado, o sea dos retardos de primer orden en cascada).
- Punto de escolta al costado y por encima del dueño; **órbita** tras ~5 s de dueño quieto.
- **En tierra no dirige nada**: pide despegue con `requestTakeoff()` y se aparta. El seguimiento a
  pie a corta distancia lo hace `FollowOwnerBaseGoal`, que sube de prioridad 12 a **8** — estaba por
  debajo del wander, así que nunca ganaba una arbitración.
- El teletransporte sobrevive como último recurso a 24 bloques (borde de chunk), con
  `tryToTeleportToOwner()` de vanilla en vez de las ~40 líneas a mano.

### El orden de los goals importa más que de costumbre

`LandingGoal` va en prioridad **3**, por encima de la escolta (4) y del melee (5). Los tres sostienen
MOVE: con el aterrizaje debajo, un mob que fija objetivo o ve a su dueño a mitad del descenso deja al
`LandingGoal` sin su flag y se queda colgado en estado de aterrizaje sin nada que lo baje. Un pájaro
comprometido con la toma la termina primero. Por la misma razón `FollowOwnerFlyingGoal` se aparta
mientras `isTakingOff() || isLanding()`.

Orden final: 0 Float · 1 Sleep · 2 Takeoff · 3 Landing · 4 FollowOwnerFlying · 5 Melee · 6 Breed ·
7 FlightWander · 8 FollowOwnerBase · 9 stroll de tierra · 10 huevos · 11-12 mirar.

### Prioridad de clips: por qué los bucles de vuelo NO se apagan durante las transiciones

Primer instinto: apagar `fly_idle`/`flight` mientras `isTakingOff() || isLanding()`. Es un error.
En este mod **número de prioridad más bajo = se renderiza encima** (locomoción en 2-3, one-shots en
0-1), así que `start_flight`/`landing`/`swoop` en 1 ya tapan a los bucles en 2. Y si el clip termina
antes que su fase (el `landing` dura 26 ticks y la fase puede llegar a 78), apagar el bucle deja al
modelo cayendo a la pose bind durante la diferencia. Se quedan encendidos a propósito: como
`isFlyingMoving()` se fuerza a `false` durante despegue y aterrizaje, lo que asoma debajo es el
planeo, que es la cama correcta.

### Otros cabos sueltos

- `registerFallingDeath` (caer muerto y rematar al tocar suelo) **sigue sin poderse usar**, pero no
  por el vuelo: `KriftoAnimations` tiene un único `death` de 1.5 s, no la pareja caída/impacto que
  ese registro pide. Es exactamente el fallo que la librería documenta para el búho. Se conecta
  cuando exista el clip.
- `StealItemGoal` + `RunAwayAfterStealGoal` **no portados**. Estaban **comentados** en el
  `registerGoals` de 1.20.1 (líneas 309-310), o sea desactivados. Decidir si se quieren.
- Renderer propio en vez de `FlyingMobRenderer`: esa base está fijada a `AbstractFlyingEntity` y a
  `DeluxeEntityRenderState`, y el Krifto necesita render state propio (sexo + bioma) y swap
  adulto/polluelo. Sus cuatro líneas (lerp en `extractRenderState` + dos rotaciones en
  `setupRotations`) están copiadas en `KriftognathusRenderer`.
- Se perdió un arreglo que ya no aplica: la corrección del test invertido de
  `FlyFromNowAndThenGoal.findLandingSpot()` (aterrizaba dentro del agua) se fue con la clase. El
  descenso nuevo usa `findGroundY`, que sube por encima de lo que el mob esté rozando y busca el
  primer sólido hacia abajo.

## Fase 6 — Mob 4: **Grand Tyrant** (valida Cortex + HitWindow)

- [ ] ⚠️ **Primero: resolver el estado de `PartEntity` en 26.1.** Si no está disponible, decidir alternativa antes de escribir nada.
- [ ] `GTEntity` — evaluar `CortexMonster` con estados IDLE/WANDER/CHASE/ROAR/BITE/HORN/CLAW/STOMP.
- [ ] **Los 4 ataques → `HitWindow` + `AttackShape` + `AttackAnchor`**, borrando `GTAttackController` (275 líneas).
- [ ] STOMP → `ScreenShakes.play(...)` + `ParticleFx` (borra 2 packets).
- [ ] `GTModel`, `GTAnimations` + `GTAnimationsBase` (5428).
- [ ] `gt_head`: bloque + BER. **Evaluar `StatueConfig`/`StatueRenderer`/`StatueItemRenderer` de DeluxeLib** — hace exactamente esto (modelo 3D en bloque *y* en item).
- [ ] Opcional: poise/stagger (`Staggerable`) para el jefe.

## Fase 7 — Mob 5: **Nirasmosaurus** (el segundo más complejo)

- [ ] `SMOPAmphibiousAnimal` (ex-`AmphibiousEntity`) sobre `MobAnimator` — con animaciones duales agua/tierra ahora resueltas por `canPlay()` en vez de `onSyncedDataUpdated`.
- [ ] `NirasmosaurusEntity`, modelos adulto + bebé, 5 archivos de animación (13.500 líneas).
- [ ] 4 ataques → `HitWindow`/`AttackShape`; borrar `NirasmosaurusAttackController`.
- [ ] Agarrar-y-sacudir (`NirasGrabShakeController` + `NirasHeldMobLayer`).
- [ ] Roll/tilt (`NirasRollController`) — evaluar `TurnLeanAdditive` de DeluxeLib.
- [ ] Doma por señuelo: `PreAggroTameGoal` (627) + config + `AnimKey` — el `AnimKey`/`playAnim`/`ANIM_END_TICK` sincronizado **desaparece**, lo hace `MobAnimator`.
- [ ] Montura: `RiderPoseHandler` + `RiderPassengerLayer` (borra `NirasRiderLayer` y los 2 mixins). Ajustar el asiento con `/deluxelib debug riderpose`.
- [ ] Muerte con animación → `registerDeath()`.
- [ ] Bloque `niras_egg`.
- [ ] Item `niras_spear` + `NirasSpearEntity` + renderer + modelo en mano (patrón `ThrownDoriSpear`/`DoriSpearItem` de DeluxeLib es casi idéntico — **usarlo como plantilla directa**).

## Fase 8 — Mob 6: **Hell Hippo** (el más complejo)

- [ ] **Decisión de diseño previa:** ¿seguir con `AbstractChestedHorse` o rehacerlo sobre `SMOPAnimal` + `IArmoredEntity` + inventario propio? → **Recomendado rehacerlo**: la clase vanilla cambió mucho y heredarla es lo que causó las 1461 líneas duplicadas.
- [ ] Modelos adulto + bebé, `Hell_HippoAnimations` (3294), 14 texturas.
- [ ] Trust system, miedo + efecto FEAR + barras de cooldown.
- [ ] Montura + ataque montado + `RiderPoseHandler`.
- [ ] Inventario + menú + armadura (⚠️ data components).
- [ ] Algas / sacudida al salir del agua.
- [ ] Sueño ambiental → unificar con `SleepCycleController`.
- [ ] Grupo + líder.
- [ ] 8 goals propios.

## Fase 9 — Cierre

- [ ] `TangofteroNestStructure` + placement type.
- [ ] Spawns por bioma con `DeluxeBiomeSpawns` + datagen.
- [ ] Creative tab completa.
- [ ] Loot tables reales.
- [ ] Lang completo.
- [ ] Barrido: eliminar `System.out.println` restantes, TODOs, código muerto.
- [ ] Pruebas en juego de cada mob.

## Estimación de esfuerzo relativo

| Fase | Peso |
|---|---|
| 0-2 (infraestructura) | ████████ 20% |
| 3 Tangoftero | ███ 8% |
| 4 Salmón | ██ 5% |
| 5 Krifto | ████ 10% |
| 6 GT | ██████ 15% |
| 7 Niras | ████████ 20% |
| 8 Hell Hippo | ██████ 17% |
| 9 Cierre | ██ 5% |

## Decisiones pendientes (bloquean fases concretas)

1. **`PartEntity` en 26.1** — bloquea Fase 6 y 7. Investigar primero.
2. **Hell_Hippo: ¿`AbstractChestedHorse` o reescritura?** — bloquea Fase 8.
3. ~~**Krifto: ¿`AbstractFlyingEntity` de DeluxeLib o portar `FlyingEntity`?**~~ — resuelto en la
   Fase 5: se trae el sistema dentro de `SMOPFlyingAnimal`, no se hereda la clase.
4. **¿Promover `WaterEntity`/`AmphibiousEntity` a DeluxeLib?** — afecta a dónde vive el código de Fase 4 y 7.
5. **Armadura animal en 26.1** — bloquea la parte de armadura de Fase 8.
