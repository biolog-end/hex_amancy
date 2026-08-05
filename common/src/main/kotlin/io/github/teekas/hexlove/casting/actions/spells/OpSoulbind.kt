package io.github.teekas.hexlove.casting.actions.spells

import at.petrak.hexcasting.api.casting.ParticleSpray
import at.petrak.hexcasting.api.casting.RenderedSpell
import at.petrak.hexcasting.api.casting.castables.SpellAction
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment
import at.petrak.hexcasting.api.casting.iota.EntityIota
import at.petrak.hexcasting.api.casting.iota.Iota
import io.github.teekas.hexlove.casting.ActionGuard
import io.github.teekas.hexlove.advancement.HexloveAdvancements
import io.github.teekas.hexlove.casting.InfatuationSpellAction
import io.github.teekas.hexlove.casting.HexloveMishaps
import io.github.teekas.hexlove.casting.MediaCosts
import io.github.teekas.hexlove.fx.BondFx
import io.github.teekas.hexlove.marriage.RingData
import io.github.teekas.hexlove.registry.HexloveItems
import io.github.teekas.hexlove.world.HexloveWorldData
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.item.ItemEntity

/** Writes a player identity into an inert ring. The altar is the only thing allowed to add a spouse. */
object OpSoulbind : InfatuationSpellAction {
    override val argc = 2

    override fun execute(args: List<Iota>, env: CastingEnvironment): SpellAction.Result =
        ActionGuard.guard("soulbind") {
            // Both values are entity iotas, and the former implementation exposed a raw internal
            // type-key whenever the caster drew them in the intuitive opposite order. Accept the
            // player and dropped ring in either order and give one focused explanation otherwise.
            val entities = args.mapNotNull { (it as? EntityIota)?.entity }
            val owner = entities.filterIsInstance<ServerPlayer>().singleOrNull()
                ?: throw HexloveMishaps.soulbindInstruction("format")
            val item = entities.filterIsInstance<ItemEntity>()
                .singleOrNull { it.item.`is`(HexloveItems.SOULBOUND_RING.value) }
                ?: throw HexloveMishaps.soulbindInstruction("ring")
            env.assertEntityInRange(owner)
            env.assertEntityInRange(item)

            val alreadyBound = RingData.boundTo(item.item)
            if (alreadyBound != null && alreadyBound != owner.uuid) {
                throw HexloveMishaps.soulbindInstruction("belongs_elsewhere")
            }

            SpellAction.Result(
                Spell(owner, item),
                MediaCosts.soulbind(),
                listOf(ParticleSpray.burst(item.position(), 0.6)),
            )
        }

    private data class Spell(val owner: ServerPlayer, val item: ItemEntity) : RenderedSpell {
        override fun cast(env: CastingEnvironment) = ActionGuard.guardCast("soulbind") {
            if (!owner.isAlive || !item.isAlive || !item.item.`is`(HexloveItems.SOULBOUND_RING.value)) return@guardCast
            RingData.bind(item.item, owner.uuid, owner.gameProfile.name)
            // A person already in a marriage cannot make a second, separate soul ring. A newly
            // bound ring immediately inherits their one existing vow and its pair colour.
            HexloveWorldData.get(env.world.server).marriageOf(owner.uuid)?.let { marriage ->
                val partner = marriage.other(owner.uuid)
                val partnerName = env.world.server.playerList.getPlayer(partner)?.gameProfile?.name
                    ?: env.world.server.profileCache?.get(partner)?.orElse(null)?.name
                RingData.marry(
                    item.item,
                    owner.uuid,
                    partner,
                    marriage.colour,
                    marriage.accentColour,
                    owner.gameProfile.name,
                    partnerName,
                )
            }
            item.setItem(item.item)
            HexloveAdvancements.grant(owner, HexloveAdvancements.SOULBOUND)
            BondFx.soulbind(env.world, owner, item, RingData.auraColor(item.item))
        }
    }
}
