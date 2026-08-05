package io.github.teekas.hexlove.config

import dev.architectury.event.events.common.PlayerEvent
import io.github.teekas.hexlove.Hexlove
import io.github.teekas.hexlove.networking.msg.MsgSyncConfigS2C
import me.shedaniel.autoconfig.AutoConfig
import me.shedaniel.autoconfig.ConfigData
import me.shedaniel.autoconfig.ConfigHolder
import me.shedaniel.autoconfig.annotation.Config
import me.shedaniel.autoconfig.annotation.ConfigEntry.Category
import me.shedaniel.autoconfig.annotation.ConfigEntry.Gui.CollapsibleObject
import me.shedaniel.autoconfig.annotation.ConfigEntry.Gui.Tooltip
import me.shedaniel.autoconfig.annotation.ConfigEntry.Gui.TransitiveObject
import me.shedaniel.autoconfig.serializer.PartitioningSerializer
import me.shedaniel.autoconfig.serializer.PartitioningSerializer.GlobalData
import me.shedaniel.autoconfig.serializer.Toml4jConfigSerializer
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.world.InteractionResult

object HexloveServerConfig {
    /**
     * Bumped whenever the *meaning* of a value changes rather than its name.
     *
     * A config file written by an older build is not wrong, it answers a question we no longer ask:
     * `resistancePerHealth = 1.0` made sense when a strength of thirty was the ordinary case. Left
     * alone it silently makes the current spell four times weaker than the book promises, which is
     * indistinguishable from a bug. So the affected sections are reset to today's defaults once.
     */
    const val CONFIG_VERSION = 5

    @JvmStatic
    lateinit var holder: ConfigHolder<GlobalConfig>

    @JvmStatic
    val config get() = syncedServerConfig ?: holder.config.server

    // only used on the client
    private var syncedServerConfig: ServerConfig? = null

    fun init() {
        holder = AutoConfig.register(
            GlobalConfig::class.java,
            PartitioningSerializer.wrap(::Toml4jConfigSerializer),
        )

        // must happen while the holder can still write: an outdated file is rewritten exactly once
        if (holder.config.server.migrate()) {
            Hexlove.LOGGER.info("Updated the Hex: Amancy server config to version {}.", CONFIG_VERSION)
            holder.save()
        }

        // prevent this holder from saving the server config; that happens in the client config gui
        holder.registerSaveListener { _, _ -> InteractionResult.FAIL }

        // Requirement 6.7: out-of-range values fall back to the default and log a warning
        holder.config.server.validate()
    }

    fun initServer() {
        PlayerEvent.PLAYER_JOIN.register { player ->
            MsgSyncConfigS2C(holder.config.server).sendToPlayer(player)
        }
    }

    fun onSyncConfig(serverConfig: ServerConfig?) {
        syncedServerConfig = serverConfig
    }

    @Config(name = Hexlove.MODID)
    class GlobalConfig(
        @Category("server")
        @TransitiveObject
        val server: ServerConfig = ServerConfig(),
    ) : GlobalData()

    @Config(name = "server")
    class ServerConfig : ConfigData {
        /** Written by the mod, not meant to be edited. Zero means "a file from before this existed". */
        @Tooltip
        var configVersion: Int = 0

        @Tooltip
        @CollapsibleObject
        var costs = Costs()

        @Tooltip
        @CollapsibleObject
        var charm = Charm()

        @Tooltip
        @CollapsibleObject
        var breeding = Breeding()

        @Tooltip
        @CollapsibleObject
        var jealousy = Jealousy()

        @Tooltip
        @CollapsibleObject
        var love = Love()

        @Tooltip
        @CollapsibleObject
        var tithe = Tithe()

        @Tooltip
        @CollapsibleObject
        var siren = Siren()

        @Tooltip
        @CollapsibleObject
        var pheromones = Pheromones()

        @Tooltip
        @CollapsibleObject
        var phantomIdeal = PhantomIdeal()

        @Tooltip
        @CollapsibleObject
        var heartbreak = Heartbreak()

        @Tooltip
        @CollapsibleObject
        var toggles = Toggles()

        @Tooltip
        @CollapsibleObject
        var nature = Nature()

        /**
         * Returns true if anything changed. Only the sections whose numbers changed meaning are
         * reset; prices are left exactly as the server owner set them.
         */
        fun migrate(): Boolean {
            if (configVersion >= CONFIG_VERSION) return false
            charm = Charm()
            breeding = Breeding()
            jealousy = Jealousy()
            love = Love()
            toggles = Toggles()
            nature = Nature()
            configVersion = CONFIG_VERSION
            return true
        }

        /** Requirement 6.7: clamp anything nonsensical back to the default, loudly. */
        fun validate() {
            costs.validate()
            charm.validate()
            breeding.validate()
            jealousy.validate()
            love.validate()
            tithe.validate()
            siren.validate()
            pheromones.validate()
            phantomIdeal.validate()
            heartbreak.validate()
            nature.validate()
        }

        fun encode(buf: FriendlyByteBuf) {
            costs.encode(buf)
            charm.encode(buf)
            breeding.encode(buf)
            jealousy.encode(buf)
            love.encode(buf)
            tithe.encode(buf)
            siren.encode(buf)
            pheromones.encode(buf)
            phantomIdeal.encode(buf)
            heartbreak.encode(buf)
            toggles.encode(buf)
            nature.encode(buf)
        }

        fun decode(buf: FriendlyByteBuf): ServerConfig {
            costs.decode(buf)
            charm.decode(buf)
            breeding.decode(buf)
            jealousy.decode(buf)
            love.decode(buf)
            tithe.decode(buf)
            siren.decode(buf)
            pheromones.decode(buf)
            phantomIdeal.decode(buf)
            heartbreak.decode(buf)
            toggles.decode(buf)
            nature.decode(buf)
            return this
        }
    }

    /** All costs are in amethyst dust units: 1 shard = 5 dust, 1 charged amethyst = 10 dust. */
    class Costs {
        @Tooltip var cupidsCharmBase: Double = 100.0
        @Tooltip var cupidsCharmPerPower: Double = 1.0

        /** Strength of feeling that is still "cheap": above it the price grows geometrically. */
        @Tooltip var cupidsCharmPowerSoftCap: Double = 5.0

        /** Every point of strength past the soft cap multiplies the surcharge by this much. */
        @Tooltip var cupidsCharmPowerGrowth: Double = 2.5
        @Tooltip var cupidsCharmBreeding: Double = 30.0
        @Tooltip var cupidsCharmZombieVillager: Double = 60.0
        @Tooltip var matchmakersPurification: Double = 0.25
        @Tooltip var animalPassion: Double = 10.0
        @Tooltip var breathExchange: Double = 25.0
        @Tooltip var blindingJealousyBase: Double = 10.0
        @Tooltip var blindingJealousyPerSecond: Double = 0.1
        @Tooltip var weaversPurification: Double = 0.25
        @Tooltip var heartbreak: Double = 10.0
        @Tooltip var succubusTithe: Double = 50.0
        @Tooltip var sirensSongBase: Double = 5.0
        @Tooltip var sirensSongPerSecond: Double = 0.1
        @Tooltip var soulbind: Double = 10.0
        @Tooltip var heartsReflection: Double = 0.25
        @Tooltip var pheromoneMistBase: Double = 8.0
        @Tooltip var pheromoneMistPerBlock: Double = 0.5
        @Tooltip var pheromoneMistPerSecond: Double = 0.25
        @Tooltip var phantomIdealBase: Double = 20.0
        @Tooltip var phantomIdealPerSecond: Double = 0.5

        fun validate() {
            cupidsCharmBase = clamp("costs.cupidsCharmBase", cupidsCharmBase, 0.0, 1_000_000.0, 100.0)
            cupidsCharmPerPower = clamp("costs.cupidsCharmPerPower", cupidsCharmPerPower, 0.0, 10_000.0, 1.0)
            cupidsCharmPowerSoftCap =
                clamp("costs.cupidsCharmPowerSoftCap", cupidsCharmPowerSoftCap, 1.0, 1_000.0, 5.0)
            cupidsCharmPowerGrowth =
                clamp("costs.cupidsCharmPowerGrowth", cupidsCharmPowerGrowth, 1.0, 100.0, 2.5)
            cupidsCharmBreeding = clamp("costs.cupidsCharmBreeding", cupidsCharmBreeding, 0.0, 1_000_000.0, 30.0)
            cupidsCharmZombieVillager =
                clamp("costs.cupidsCharmZombieVillager", cupidsCharmZombieVillager, 0.0, 1_000_000.0, 60.0)
            matchmakersPurification =
                clamp("costs.matchmakersPurification", matchmakersPurification, 0.0, 1_000_000.0, 0.25)
            animalPassion = clamp("costs.animalPassion", animalPassion, 0.0, 1_000_000.0, 10.0)
            breathExchange = clamp("costs.breathExchange", breathExchange, 0.0, 1_000_000.0, 25.0)
            blindingJealousyBase = clamp("costs.blindingJealousyBase", blindingJealousyBase, 0.0, 1_000_000.0, 10.0)
            blindingJealousyPerSecond =
                clamp("costs.blindingJealousyPerSecond", blindingJealousyPerSecond, 0.0, 10_000.0, 0.1)
            weaversPurification = clamp("costs.weaversPurification", weaversPurification, 0.0, 1_000_000.0, 0.25)
            heartbreak = clamp("costs.heartbreak", heartbreak, 0.0, 1_000_000.0, 10.0)
            succubusTithe = clamp("costs.succubusTithe", succubusTithe, 0.0, 1_000_000.0, 50.0)
            sirensSongBase = clamp("costs.sirensSongBase", sirensSongBase, 0.0, 1_000_000.0, 5.0)
            sirensSongPerSecond = clamp("costs.sirensSongPerSecond", sirensSongPerSecond, 0.0, 10_000.0, 0.1)
            soulbind = clamp("costs.soulbind", soulbind, 0.0, 1_000_000.0, 10.0)
            heartsReflection = clamp("costs.heartsReflection", heartsReflection, 0.0, 1_000_000.0, 0.25)
            pheromoneMistBase = clamp("costs.pheromoneMistBase", pheromoneMistBase, 0.0, 1_000_000.0, 8.0)
            pheromoneMistPerBlock = clamp("costs.pheromoneMistPerBlock", pheromoneMistPerBlock, 0.0, 1_000_000.0, 0.5)
            pheromoneMistPerSecond =
                clamp("costs.pheromoneMistPerSecond", pheromoneMistPerSecond, 0.0, 1_000_000.0, 0.25)
            phantomIdealBase = clamp("costs.phantomIdealBase", phantomIdealBase, 0.0, 1_000_000.0, 20.0)
            phantomIdealPerSecond =
                clamp("costs.phantomIdealPerSecond", phantomIdealPerSecond, 0.0, 1_000_000.0, 0.5)
        }

        fun encode(buf: FriendlyByteBuf) {
            for (v in arrayOf(
                cupidsCharmBase, cupidsCharmPerPower, cupidsCharmPowerSoftCap, cupidsCharmPowerGrowth,
                cupidsCharmBreeding, cupidsCharmZombieVillager,
                matchmakersPurification, animalPassion, breathExchange, blindingJealousyBase, blindingJealousyPerSecond,
                weaversPurification, heartbreak, succubusTithe, sirensSongBase, sirensSongPerSecond,
                soulbind, heartsReflection, pheromoneMistBase, pheromoneMistPerBlock, pheromoneMistPerSecond,
                phantomIdealBase, phantomIdealPerSecond,
            )) buf.writeDouble(v)
        }

        fun decode(buf: FriendlyByteBuf) {
            cupidsCharmBase = buf.readDouble()
            cupidsCharmPerPower = buf.readDouble()
            cupidsCharmPowerSoftCap = buf.readDouble()
            cupidsCharmPowerGrowth = buf.readDouble()
            cupidsCharmBreeding = buf.readDouble()
            cupidsCharmZombieVillager = buf.readDouble()
            matchmakersPurification = buf.readDouble()
            animalPassion = buf.readDouble()
            breathExchange = buf.readDouble()
            blindingJealousyBase = buf.readDouble()
            blindingJealousyPerSecond = buf.readDouble()
            weaversPurification = buf.readDouble()
            heartbreak = buf.readDouble()
            succubusTithe = buf.readDouble()
            sirensSongBase = buf.readDouble()
            sirensSongPerSecond = buf.readDouble()
            soulbind = buf.readDouble()
            heartsReflection = buf.readDouble()
            pheromoneMistBase = buf.readDouble()
            pheromoneMistPerBlock = buf.readDouble()
            pheromoneMistPerSecond = buf.readDouble()
            phantomIdealBase = buf.readDouble()
            phantomIdealPerSecond = buf.readDouble()
        }
    }

    class Charm {
        /**
         * A quarter of a heart of resistance per point of health, so that the numbers in the book are
         * the numbers in the world: a cow or a sheep (10 and 8 health) is won over forever at a
         * strength of two and a half, a zombie, creeper or skeleton (20 health) at five.
         */
        @Tooltip var resistancePerHealth: Double = 0.25
        @Tooltip var playerResistance: Double = 8.0

        /**
         * A shade under one, so that the round numbers in the book are comfortably inside forever
         * rather than balanced on its edge: five held a zombie for five minutes and one tick short of
         * forever, which is the least satisfying possible outcome.
         */
        @Tooltip var permanenceThreshold: Double = 0.9
        @Tooltip var durationMultiplierTicks: Long = 6000
        @Tooltip var maxDurationTicks: Long = 172800
        @Tooltip var maxPlayerDurationTicks: Long = 12000

        /**
         * A heart this large is not stubborn, it is not a heart. Bosses are refused outright rather
         * than priced out: no amount of feeling reaches them.
         */
        @Tooltip var bossHealthThreshold: Double = 200.0

        /** The largest ordinary creature a strength inside the cheap range can hold at all. */
        @Tooltip var charmableHealthBase: Double = 100.0

        /** Every point of strength past the soft cap adds this much reach, up to the boss threshold. */
        @Tooltip var charmableHealthPerPower: Double = 10.0

        fun validate() {
            resistancePerHealth = clamp("charm.resistancePerHealth", resistancePerHealth, 0.001, 1000.0, 0.25)
            playerResistance = clamp("charm.playerResistance", playerResistance, 0.001, 100_000.0, 8.0)
            permanenceThreshold = clamp("charm.permanenceThreshold", permanenceThreshold, 0.1, 10_000.0, 0.9)
            durationMultiplierTicks =
                clamp("charm.durationMultiplierTicks", durationMultiplierTicks, 1, 1_728_000, 6000)
            maxDurationTicks = clamp("charm.maxDurationTicks", maxDurationTicks, 1, 172_800_000, 172800)
            maxPlayerDurationTicks = clamp("charm.maxPlayerDurationTicks", maxPlayerDurationTicks, 1, 172_800_000, 12000)
            bossHealthThreshold =
                clamp("charm.bossHealthThreshold", bossHealthThreshold, 1.0, 1_000_000_000.0, 200.0)
            charmableHealthBase = clamp("charm.charmableHealthBase", charmableHealthBase, 1.0, 1_000_000.0, 100.0)
            charmableHealthPerPower =
                clamp("charm.charmableHealthPerPower", charmableHealthPerPower, 0.0, 1_000_000.0, 10.0)
        }

        fun encode(buf: FriendlyByteBuf) {
            buf.writeDouble(resistancePerHealth)
            buf.writeDouble(playerResistance)
            buf.writeDouble(permanenceThreshold)
            buf.writeLong(durationMultiplierTicks)
            buf.writeLong(maxDurationTicks)
            buf.writeLong(maxPlayerDurationTicks)
            buf.writeDouble(bossHealthThreshold)
            buf.writeDouble(charmableHealthBase)
            buf.writeDouble(charmableHealthPerPower)
        }

        fun decode(buf: FriendlyByteBuf) {
            resistancePerHealth = buf.readDouble()
            playerResistance = buf.readDouble()
            permanenceThreshold = buf.readDouble()
            durationMultiplierTicks = buf.readLong()
            maxDurationTicks = buf.readLong()
            maxPlayerDurationTicks = buf.readLong()
            bossHealthThreshold = buf.readDouble()
            charmableHealthBase = buf.readDouble()
            charmableHealthPerPower = buf.readDouble()
        }
    }

    class Breeding {
        @Tooltip var minOffspring: Int = 3
        @Tooltip var maxOffspring: Int = 6
        @Tooltip var maxOffspringPerCast: Int = 6
        @Tooltip var breedCooldownTicks: Int = 6000
        @Tooltip var chimeraChance: Double = 1.0
        @Tooltip var chimeraMaxHealth: Double = 40.0
        @Tooltip var chimeraMaxSize: Double = 1.5
        @Tooltip var passionCooldownReductionTicks: Int = 600
        @Tooltip var passionAgeReductionTicks: Int = 600

        /** How long the pair has to stand together before the children appear. */
        @Tooltip var courtshipContactTicks: Int = 60

        /** A courtship that never reaches its partner gives up after this long. */
        @Tooltip var courtshipTimeoutTicks: Int = 1200

        /** How close the two have to be to count as touching. */
        @Tooltip var courtshipContactDistance: Double = 2.0

        @Tooltip var courtshipSpeed: Double = 1.15

        fun validate() {
            minOffspring = clamp("breeding.minOffspring", minOffspring, 1, 64, 3)
            maxOffspring = clamp("breeding.maxOffspring", maxOffspring, minOffspring, 64, 6)
            maxOffspringPerCast = clamp("breeding.maxOffspringPerCast", maxOffspringPerCast, 1, 64, 6)
            breedCooldownTicks = clamp("breeding.breedCooldownTicks", breedCooldownTicks, 0, 1_728_000, 6000)
            chimeraChance = clamp("breeding.chimeraChance", chimeraChance, 0.0, 1.0, 1.0)
            chimeraMaxHealth = clamp("breeding.chimeraMaxHealth", chimeraMaxHealth, 1.0, 1024.0, 40.0)
            chimeraMaxSize = clamp("breeding.chimeraMaxSize", chimeraMaxSize, 0.1, 8.0, 1.5)
            passionCooldownReductionTicks =
                clamp("breeding.passionCooldownReductionTicks", passionCooldownReductionTicks, 1, 1_728_000, 600)
            passionAgeReductionTicks =
                clamp("breeding.passionAgeReductionTicks", passionAgeReductionTicks, 1, 1_728_000, 600)
            courtshipContactTicks = clamp("breeding.courtshipContactTicks", courtshipContactTicks, 0, 12_000, 60)
            courtshipTimeoutTicks =
                clamp("breeding.courtshipTimeoutTicks", courtshipTimeoutTicks, 20, 1_728_000, 1200)
            courtshipContactDistance =
                clamp("breeding.courtshipContactDistance", courtshipContactDistance, 0.5, 16.0, 2.0)
            courtshipSpeed = clamp("breeding.courtshipSpeed", courtshipSpeed, 0.1, 4.0, 1.15)
        }

        fun encode(buf: FriendlyByteBuf) {
            buf.writeInt(minOffspring)
            buf.writeInt(maxOffspring)
            buf.writeInt(maxOffspringPerCast)
            buf.writeInt(breedCooldownTicks)
            buf.writeDouble(chimeraChance)
            buf.writeDouble(chimeraMaxHealth)
            buf.writeDouble(chimeraMaxSize)
            buf.writeInt(passionCooldownReductionTicks)
            buf.writeInt(passionAgeReductionTicks)
            buf.writeInt(courtshipContactTicks)
            buf.writeInt(courtshipTimeoutTicks)
            buf.writeDouble(courtshipContactDistance)
            buf.writeDouble(courtshipSpeed)
        }

        fun decode(buf: FriendlyByteBuf) {
            minOffspring = buf.readInt()
            maxOffspring = buf.readInt()
            maxOffspringPerCast = buf.readInt()
            breedCooldownTicks = buf.readInt()
            chimeraChance = buf.readDouble()
            chimeraMaxHealth = buf.readDouble()
            chimeraMaxSize = buf.readDouble()
            passionCooldownReductionTicks = buf.readInt()
            passionAgeReductionTicks = buf.readInt()
            courtshipContactTicks = buf.readInt()
            courtshipTimeoutTicks = buf.readInt()
            courtshipContactDistance = buf.readDouble()
            courtshipSpeed = buf.readDouble()
        }
    }

    class Jealousy {
        @Tooltip var passiveDamage: Double = 3.0

        /** Anyone who comes this close to the guarded one is already too close. */
        @Tooltip var guardRadius: Double = 6.0
        @Tooltip var ultimatumTicks: Int = 100
        @Tooltip var damageReflectFraction: Double = 0.5
        @Tooltip var maxDurationTicks: Long = 72000

        /** A jealous creature charges: this is a run, not a stroll. */
        @Tooltip var chaseSpeed: Double = 1.6

        fun validate() {
            passiveDamage = clamp("jealousy.passiveDamage", passiveDamage, 0.0, 1024.0, 3.0)
            guardRadius = clamp("jealousy.guardRadius", guardRadius, 1.0, 128.0, 6.0)
            ultimatumTicks = clamp("jealousy.ultimatumTicks", ultimatumTicks, 0, 1_728_000, 100)
            damageReflectFraction = clamp("jealousy.damageReflectFraction", damageReflectFraction, 0.0, 1.0, 0.5)
            maxDurationTicks = clamp("jealousy.maxDurationTicks", maxDurationTicks, 1, 172_800_000, 72000)
            chaseSpeed = clamp("jealousy.chaseSpeed", chaseSpeed, 0.1, 4.0, 1.6)
        }

        fun encode(buf: FriendlyByteBuf) {
            buf.writeDouble(passiveDamage)
            buf.writeDouble(guardRadius)
            buf.writeInt(ultimatumTicks)
            buf.writeDouble(damageReflectFraction)
            buf.writeLong(maxDurationTicks)
            buf.writeDouble(chaseSpeed)
        }

        fun decode(buf: FriendlyByteBuf) {
            passiveDamage = buf.readDouble()
            guardRadius = buf.readDouble()
            ultimatumTicks = buf.readInt()
            damageReflectFraction = buf.readDouble()
            maxDurationTicks = buf.readLong()
            chaseSpeed = buf.readDouble()
        }
    }

    class Love {
        @Tooltip var pullDistance: Double = 8.0

        /** Peak strength of the tug, reached only [pullRampDistance] blocks past [pullDistance]. */
        @Tooltip var pullStrength: Double = 0.035

        /** Over how many blocks the tug fades in. Large values make it a breeze, not a wall. */
        @Tooltip var pullRampDistance: Double = 16.0
        @Tooltip var followDistance: Double = 3.0

        /** A charmed creature keeps up: walking speed near the beloved, a run when it falls behind. */
        @Tooltip var followSpeed: Double = 1.0
        @Tooltip var sprintSpeed: Double = 1.6

        /** Past this distance from the beloved, the charmed creature runs. */
        @Tooltip var sprintDistance: Double = 10.0

        /**
         * A fight for the beloved outranks standing next to them. Stray this far and the fight is
         * over as far as the charmed creature is concerned: it forgets the grudge outright and comes
         * back, the way a wolf gives up on a chase.
         */
        @Tooltip var combatLeashDistance: Double = 20.0

        /**
         * How recently the beloved must have hit somebody, or been hit, for it to count as a fight
         * worth joining. An hour-old grudge is not a fight, it is a memory.
         */
        @Tooltip var grudgeMemoryTicks: Int = 100

        fun validate() {
            pullDistance = clamp("love.pullDistance", pullDistance, 0.5, 256.0, 8.0)
            pullStrength = clamp("love.pullStrength", pullStrength, 0.0, 4.0, 0.035)
            pullRampDistance = clamp("love.pullRampDistance", pullRampDistance, 0.5, 256.0, 16.0)
            followDistance = clamp("love.followDistance", followDistance, 1.0, 64.0, 3.0)
            followSpeed = clamp("love.followSpeed", followSpeed, 0.1, 4.0, 1.0)
            sprintSpeed = clamp("love.sprintSpeed", sprintSpeed, 0.1, 4.0, 1.6)
            sprintDistance = clamp("love.sprintDistance", sprintDistance, 1.0, 128.0, 10.0)
            combatLeashDistance = clamp("love.combatLeashDistance", combatLeashDistance, 1.0, 256.0, 20.0)
            grudgeMemoryTicks = clamp("love.grudgeMemoryTicks", grudgeMemoryTicks, 1, 1_728_000, 100)
        }

        fun encode(buf: FriendlyByteBuf) {
            buf.writeDouble(pullDistance)
            buf.writeDouble(pullStrength)
            buf.writeDouble(pullRampDistance)
            buf.writeDouble(followDistance)
            buf.writeDouble(followSpeed)
            buf.writeDouble(sprintSpeed)
            buf.writeDouble(sprintDistance)
            buf.writeDouble(combatLeashDistance)
            buf.writeInt(grudgeMemoryTicks)
        }

        fun decode(buf: FriendlyByteBuf) {
            pullDistance = buf.readDouble()
            pullStrength = buf.readDouble()
            pullRampDistance = buf.readDouble()
            followDistance = buf.readDouble()
            followSpeed = buf.readDouble()
            sprintSpeed = buf.readDouble()
            sprintDistance = buf.readDouble()
            combatLeashDistance = buf.readDouble()
            grudgeMemoryTicks = buf.readInt()
        }
    }

    class Tithe {
        @Tooltip var maxHealthPerCast: Double = 20.0
        @Tooltip var cooldownTicks: Int = 200

        fun validate() {
            maxHealthPerCast = clamp("tithe.maxHealthPerCast", maxHealthPerCast, 0.5, 1024.0, 20.0)
            cooldownTicks = clamp("tithe.cooldownTicks", cooldownTicks, 0, 1_728_000, 200)
        }

        fun encode(buf: FriendlyByteBuf) {
            buf.writeDouble(maxHealthPerCast)
            buf.writeInt(cooldownTicks)
        }

        fun decode(buf: FriendlyByteBuf) {
            maxHealthPerCast = buf.readDouble()
            cooldownTicks = buf.readInt()
        }
    }

    class Siren {
        @Tooltip var maxDurationTicks: Long = 6000

        /** Requirement 20: the song leads players too, by the same gentle tug love uses. */
        @Tooltip var playerPullStrength: Double = 0.05

        /** How close a lured player has to get before the song lets go of them for a moment. */
        @Tooltip var playerPullDistance: Double = 3.0

        /** While lured, a player's view is held on the caller. */
        @Tooltip var lockPlayerView: Boolean = true

        fun validate() {
            maxDurationTicks = clamp("siren.maxDurationTicks", maxDurationTicks, 1, 172_800_000, 6000)
            playerPullStrength = clamp("siren.playerPullStrength", playerPullStrength, 0.0, 4.0, 0.05)
            playerPullDistance = clamp("siren.playerPullDistance", playerPullDistance, 0.5, 128.0, 3.0)
        }

        fun encode(buf: FriendlyByteBuf) {
            buf.writeLong(maxDurationTicks)
            buf.writeDouble(playerPullStrength)
            buf.writeDouble(playerPullDistance)
            buf.writeBoolean(lockPlayerView)
        }

        fun decode(buf: FriendlyByteBuf) {
            maxDurationTicks = buf.readLong()
            playerPullStrength = buf.readDouble()
            playerPullDistance = buf.readDouble()
            lockPlayerView = buf.readBoolean()
        }
    }

    class Pheromones {
        /** Covers a village street without making a single cast scan an entire loaded region. */
        @Tooltip var maxRadius: Double = 16.0
        @Tooltip var maxDurationTicks: Long = 6000

        fun validate() {
            maxRadius = clamp("pheromones.maxRadius", maxRadius, 0.5, 64.0, 16.0)
            maxDurationTicks = clamp("pheromones.maxDurationTicks", maxDurationTicks, 1, 172_800_000, 6000)
        }

        fun encode(buf: FriendlyByteBuf) {
            buf.writeDouble(maxRadius)
            buf.writeLong(maxDurationTicks)
        }

        fun decode(buf: FriendlyByteBuf) {
            maxRadius = buf.readDouble()
            maxDurationTicks = buf.readLong()
        }
    }

    class PhantomIdeal {
        @Tooltip var maxDurationTicks: Long = 6000

        /** The promise is precise by default: creatures within twelve blocks hear it. */
        @Tooltip var lureRadius: Double = 12.0
        @Tooltip var lureSpeed: Double = 1.2

        fun validate() {
            maxDurationTicks = clamp("phantomIdeal.maxDurationTicks", maxDurationTicks, 1, 172_800_000, 6000)
            lureRadius = clamp("phantomIdeal.lureRadius", lureRadius, 1.0, 64.0, 12.0)
            lureSpeed = clamp("phantomIdeal.lureSpeed", lureSpeed, 0.1, 4.0, 1.2)
        }

        fun encode(buf: FriendlyByteBuf) {
            buf.writeLong(maxDurationTicks)
            buf.writeDouble(lureRadius)
            buf.writeDouble(lureSpeed)
        }

        fun decode(buf: FriendlyByteBuf) {
            maxDurationTicks = buf.readLong()
            lureRadius = buf.readDouble()
            lureSpeed = buf.readDouble()
        }
    }

    class Heartbreak {
        @Tooltip var maxDurationTicks: Long = 12000

        fun validate() {
            maxDurationTicks = clamp("heartbreak.maxDurationTicks", maxDurationTicks, 1, 172_800_000, 12000)
        }

        fun encode(buf: FriendlyByteBuf) = buf.writeLong(maxDurationTicks)

        fun decode(buf: FriendlyByteBuf) {
            maxDurationTicks = buf.readLong()
        }
    }

    class Toggles {
        @Tooltip var charmPlayers: Boolean = true

        /** Two animals of different species court each other and produce a blended child. */
        @Tooltip var allowChimera: Boolean = true
        @Tooltip var allowZombieBreeding: Boolean = true
        @Tooltip var allowZombieVillagerBreeding: Boolean = true
        @Tooltip var allowSirensSong: Boolean = true
        @Tooltip var allowPheromoneMist: Boolean = true
        @Tooltip var allowPhantomIdeal: Boolean = true

        /**
         * The glow that marks a bonded creature and the arrow of light a cast draws. Already kept to
         * a handful of particles per second, but a server with a thousand charmed animals in one
         * barn may still want the switch.
         */
        @Tooltip var bondParticles: Boolean = true

        fun encode(buf: FriendlyByteBuf) {
            buf.writeBoolean(charmPlayers)
            buf.writeBoolean(allowChimera)
            buf.writeBoolean(allowZombieBreeding)
            buf.writeBoolean(allowZombieVillagerBreeding)
            buf.writeBoolean(allowSirensSong)
            buf.writeBoolean(allowPheromoneMist)
            buf.writeBoolean(allowPhantomIdeal)
            buf.writeBoolean(bondParticles)
        }

        fun decode(buf: FriendlyByteBuf) {
            charmPlayers = buf.readBoolean()
            allowChimera = buf.readBoolean()
            allowZombieBreeding = buf.readBoolean()
            allowZombieVillagerBreeding = buf.readBoolean()
            allowSirensSong = buf.readBoolean()
            allowPheromoneMist = buf.readBoolean()
            allowPhantomIdeal = buf.readBoolean()
            bondParticles = buf.readBoolean()
        }
    }

    class Nature {
        /** How long the Serene Chimera effect lasts after drinking the stew, in ticks. */
        @Tooltip var serenityDurationTicks: Int = 9600

        /** How long the Ferocious Chimera effect lasts after drinking the stew, in ticks. */
        @Tooltip var ferocityDurationTicks: Int = 9600

        /** Fraction of food exhaustion that actually counts under serene nature (0.5 = half hunger drain). */
        @Tooltip var sereneExhaustionMultiplier: Double = 0.5

        /** Dig-speed multiplier applied to a serene player (0.8 = 20 % slower). */
        @Tooltip var sereneDigSpeedMultiplier: Double = 0.8

        /** Multiplier on food exhaustion under ferocious nature (3.0 = triple hunger drain). */
        @Tooltip var ferociousExhaustionMultiplier: Double = 3.0

        /** Passive exhaustion added per tick while ferocious, after the multiplier is applied. */
        @Tooltip var ferociousIdleExhaustion: Double = 0.006

        /** Chance (0–1) that killing a mob heals the ferocious player by half a heart. */
        @Tooltip var ferociousKillHealChance: Double = 0.25

        /** Minimum duration of the Humiliation effect in ticks, even if serenity was almost gone. */
        @Tooltip var humiliationMinTicks: Int = 600

        /** Magic damage dealt to the player when an animal completes its advance, in half-hearts. */
        @Tooltip var humiliationDamage: Double = 7.0

        /** Minimum amethyst dust drops when humiliation is triggered. */
        @Tooltip var humiliationDustMin: Int = 2

        /** Maximum amethyst dust drops when humiliation is triggered. */
        @Tooltip var humiliationDustMax: Int = 4

        /**
         * How recently (in ticks) the serene player must have eaten near an animal for the
         * animal-feeding interaction to trigger an advance.
         */
        @Tooltip var advanceMealWindowTicks: Int = 600

        /** Ticks before an animal gives up advancing on a player it cannot reach. */
        @Tooltip var advanceTimeoutTicks: Int = 400

        /** Master switch: set to false to disable all chimera-nature mechanics. */
        @Tooltip var allowChimeraNatures: Boolean = true

        fun validate() {
            serenityDurationTicks = clamp("nature.serenityDurationTicks", serenityDurationTicks, 1, 1_728_000, 9600)
            ferocityDurationTicks = clamp("nature.ferocityDurationTicks", ferocityDurationTicks, 1, 1_728_000, 9600)
            sereneExhaustionMultiplier = clamp("nature.sereneExhaustionMultiplier", sereneExhaustionMultiplier, 0.0, 2.0, 0.5)
            sereneDigSpeedMultiplier = clamp("nature.sereneDigSpeedMultiplier", sereneDigSpeedMultiplier, 0.0, 2.0, 0.8)
            ferociousExhaustionMultiplier = clamp("nature.ferociousExhaustionMultiplier", ferociousExhaustionMultiplier, 0.0, 10.0, 3.0)
            ferociousIdleExhaustion = clamp("nature.ferociousIdleExhaustion", ferociousIdleExhaustion, 0.0, 1.0, 0.006)
            ferociousKillHealChance = clamp("nature.ferociousKillHealChance", ferociousKillHealChance, 0.0, 1.0, 0.25)
            humiliationMinTicks = clamp("nature.humiliationMinTicks", humiliationMinTicks, 1, 1_728_000, 600)
            humiliationDamage = clamp("nature.humiliationDamage", humiliationDamage, 0.0, 1024.0, 7.0)
            humiliationDustMin = clamp("nature.humiliationDustMin", humiliationDustMin, 0, 64, 2)
            humiliationDustMax = clamp("nature.humiliationDustMax", humiliationDustMax, humiliationDustMin, 64, 4)
            advanceMealWindowTicks = clamp("nature.advanceMealWindowTicks", advanceMealWindowTicks, 1, 1_728_000, 600)
            advanceTimeoutTicks = clamp("nature.advanceTimeoutTicks", advanceTimeoutTicks, 1, 1_728_000, 400)
        }

        fun encode(buf: FriendlyByteBuf) {
            buf.writeInt(serenityDurationTicks)
            buf.writeInt(ferocityDurationTicks)
            buf.writeDouble(sereneExhaustionMultiplier)
            buf.writeDouble(sereneDigSpeedMultiplier)
            buf.writeDouble(ferociousExhaustionMultiplier)
            buf.writeDouble(ferociousIdleExhaustion)
            buf.writeDouble(ferociousKillHealChance)
            buf.writeInt(humiliationMinTicks)
            buf.writeDouble(humiliationDamage)
            buf.writeInt(humiliationDustMin)
            buf.writeInt(humiliationDustMax)
            buf.writeInt(advanceMealWindowTicks)
            buf.writeInt(advanceTimeoutTicks)
            buf.writeBoolean(allowChimeraNatures)
        }

        fun decode(buf: FriendlyByteBuf) {
            serenityDurationTicks = buf.readInt()
            ferocityDurationTicks = buf.readInt()
            sereneExhaustionMultiplier = buf.readDouble()
            sereneDigSpeedMultiplier = buf.readDouble()
            ferociousExhaustionMultiplier = buf.readDouble()
            ferociousIdleExhaustion = buf.readDouble()
            ferociousKillHealChance = buf.readDouble()
            humiliationMinTicks = buf.readInt()
            humiliationDamage = buf.readDouble()
            humiliationDustMin = buf.readInt()
            humiliationDustMax = buf.readInt()
            advanceMealWindowTicks = buf.readInt()
            advanceTimeoutTicks = buf.readInt()
            allowChimeraNatures = buf.readBoolean()
        }
    }
}

private fun clamp(key: String, value: Double, min: Double, max: Double, default: Double): Double =
    if (value.isNaN() || value < min || value > max) {
        Hexlove.LOGGER.warn("Config value {} is out of range [{}, {}]: {}. Using default {}.", key, min, max, value, default)
        default
    } else value

private fun clamp(key: String, value: Int, min: Int, max: Int, default: Int): Int =
    if (value < min || value > max) {
        Hexlove.LOGGER.warn("Config value {} is out of range [{}, {}]: {}. Using default {}.", key, min, max, value, default)
        default
    } else value

private fun clamp(key: String, value: Long, min: Long, max: Long, default: Long): Long =
    if (value < min || value > max) {
        Hexlove.LOGGER.warn("Config value {} is out of range [{}, {}]: {}. Using default {}.", key, min, max, value, default)
        default
    } else value
