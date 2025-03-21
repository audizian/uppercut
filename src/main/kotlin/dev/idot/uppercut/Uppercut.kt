package dev.idot.uppercut

import com.comphenix.protocol.*
import com.comphenix.protocol.events.*
import com.comphenix.protocol.wrappers.EnumWrappers
import dev.idot.text.color.convertColorsAndFormat
import dev.idot.uppercut.Placeholders.Companion.isPlaceholderApi
import dev.idot.uppercut.Placeholders.Companion.sendTo
import org.bukkit.*
import org.bukkit.command.CommandExecutor
import org.bukkit.command.TabCompleter
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDamageEvent.DamageCause.FALL
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.util.Vector
import java.util.*
import java.util.concurrent.*

class Uppercut : JavaPlugin() {

    internal fun reload() {
        saveDefaultConfig()
        reloadConfig()
        cooldowns.clear()
        saveConfig()
    }

    override fun reloadConfig() {
        super.reloadConfig()
        messages = try {
            config.getConfigurationSection("messages")
        } catch (_: Exception) {
            config.set("messages", emptyMap<String, String>())
            config.getConfigurationSection("messages")
        }!!
        msg_prefix = messages.getString("prefix") ?: ""
        msg_invalidPlayer = messages.getString("invalid-player") ?: "&cInvalid player!"
        msg_noPermission = messages.getString("no-permission") ?: "&cNo permission."
        msg_reload = messages.getString("reload") ?: "{#a0a>}[Uppercut]{#f5f<} &aReloaded!"

        msg_target = messages.getString("target")
        msg_puncher = messages.getString("puncher")

        cooldownSeconds = config.getOrSet("settings.cooldown-seconds", 30)
        logger.info("cooldown-seconds: $cooldownSeconds")

        power = config.getOrSet("settings.power", 4.0)
    }

    override fun onEnable() {
        instance = this
        if (Bukkit.getPluginManager().isPluginEnabled("ProtocolLib")) {
            logger.info("ProtocolLib was found!")
            protocolManager = ProtocolLibrary.getProtocolManager()
        } else {
            logger.severe("ProtocolLib was not found! Disabling...")
            pluginLoader.disablePlugin(instance)
            return
        }
        if (isPlaceholderApi && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            logger.info("PlaceholderAPI was found! Enabling expansion...")
            Placeholders(instance).register()
        }

        val upcutCommand = getCommand("uppercut") ?: return logger.warning("/uppercut command returned null. Is the plugin.yml broken? Disabling plugin...")
        upcutCommand.setExecutor(upcutExecutor)
        upcutCommand.tabCompleter = upcutTabCompleter

        protocolManager.addPacketListener(object : PacketAdapter(this, ListenerPriority.NORMAL, PacketType.Play.Client.USE_ENTITY) {
            override fun onPacketReceiving(ev: PacketEvent) {
                if (ev.packet.enumEntityUseActions.read(0).action != EnumWrappers.EntityUseAction.ATTACK) return
                server.scheduler.runTask(instance) { _ ->
                    val instant = System.currentTimeMillis()
                    val puncher = ev.player
                    val target = protocolManager.getEntityFromID(puncher.world, ev.packet.integers.read(0)) as? Player
                        ?: return@runTask
                    val targetUuid = target.uniqueId

                    if (!puncher.hasPermission("uppercut.puncher")
                        || !target.hasPermission("uppercut.target")
                        || toggledOff.contains(targetUuid)
                        || (cooldowns.getOrDefault(targetUuid, 0) >= instant)
                    ) return@runTask

                    lastTargeted[puncher] = target
                    lastPunched[target] = puncher
                    cooldowns[targetUuid] = cooldownSeconds * 1000 + instant
                    noFallDamage.add(targetUuid)
                    target.world.spawnParticle(Particle.EXPLOSION_HUGE, target.location, 1)
                    target.world.playSound(target.location, Sound.ENTITY_GENERIC_EXPLODE, 1.0F, 1.0F)
                    // TODO(support 1.8)
                    target.velocity = Vector().setY(power)
                    msg_puncher?.sendTo(puncher)
                    msg_target?.sendTo(target)
                }
            }
        })

        server.pluginManager.registerEvents(object : Listener {

            @EventHandler
            fun onFallDamage(ev: EntityDamageEvent) {
                if (ev.cause != FALL || ev.entity !is Player) return
                ev.isCancelled = noFallDamage.remove(ev.entity.uniqueId)
            }

            @EventHandler
            fun onLeave(ev: PlayerQuitEvent) {
                cooldowns.remove(ev.player.uniqueId)
            }
        }, this)

        reload()
    }

    companion object {

        lateinit var instance: Uppercut
            private set

        private lateinit var protocolManager: ProtocolManager

        internal val cooldowns = ConcurrentHashMap<UUID, Long>()
        internal val noFallDamage = hashSetOf<UUID>()
        internal val toggledOff = hashSetOf<UUID>()

        /**
         * @return Map of Puncher, Target
         */
        internal val lastPunched = ConcurrentHashMap<OfflinePlayer, OfflinePlayer>()
        /**
         * @return Map of Target, Puncher
         */
        internal val lastTargeted = ConcurrentHashMap<OfflinePlayer, OfflinePlayer>()

        internal lateinit var messages: ConfigurationSection
            private set
        internal lateinit var msg_prefix: String
            private set


        private val msg_commandUsage = """
            &bUsage: &d/uppercut 
            &d|   &7toggle [&otrue&7|&ofalse&7] [player]
            &d|   &7reload
        """.convertColorsAndFormat().trimIndent()
        private lateinit var msg_invalidPlayer: String
        private lateinit var msg_noPermission: String
        private lateinit var msg_reload: String

        private var msg_target: String? = null
        private var msg_puncher: String? = null

        private var cooldownSeconds: Int = 30
        private var power: Double = 4.0

        private val upcutExecutor = CommandExecutor exec@ { sender, _, _, args ->
            val args = args.toMutableList()
            when (args.removeFirstOrNull()) {
                "reload" -> {
                    if (sender.hasPermission("uppercut.reload")) {
                        instance.reload()
                        msg_reload.sendTo(sender)
                    } else msg_noPermission.sendTo(sender)
                }

                "toggle" -> {
                    val target = args.removeFirstOrNull()?.let(Bukkit::getPlayer) ?: sender as? Player
                    if (target == null) {
                        msg_invalidPlayer.sendTo(sender)
                        return@exec true
                    }
                    val permToggleOther = !sender.hasPermission("uppercut.toggle.others") && args.size > 1
                    val permToggle = !sender.hasPermission("uppercut.toggle") && target == sender
                    if (permToggleOther || permToggle) {
                        msg_noPermission.sendTo(sender)
                        return@exec true
                    }
                    target.run {
                        val other = this == sender
                        val path = "toggled." + if (other) "" else "other-"
                        when (args.getOrNull(if (other) 1 else 2)?.lowercase()?.toBooleanStrictOrNull()) {
                            true -> takeIf { toggledOff.remove(uniqueId) }?.let { messages.getString(path + "true") }
                            null -> if (toggledOff.remove(uniqueId)) messages.getString(path + "true") else {
                                toggledOff.add(uniqueId)
                                messages.getString(path + "false")
                            }
                            false -> takeIf { toggledOff.add(uniqueId) }?.let { messages.getString(path + "false") }
                        }?.replace("{target}", name)?.sendTo(sender)
                    }
                }

                else -> {
                    val hasPerms = sender.effectivePermissions.firstOrNull { it.permission.startsWith("uppercut.") } != null
                    val msg = if (hasPerms) msg_noPermission else msg_commandUsage
                    msg.sendTo(sender)
                }
            }
            true
        }

        private val upcutTabCompleter = TabCompleter { sender, command, alias, args ->
            val result = mutableListOf<String>()
            when (args.size) {
                1 -> {
                    val subCommands = mutableListOf<String>()
                    if (sender.hasPermission("uppercut.reload"))
                        subCommands.add("reload")

                    if (sender.hasPermission("uppercut.toggle") || sender.hasPermission("uppercut.toggle.others"))
                        subCommands.add("toggle")

                    subCommands.stringFilter(args[0])
                }
                2 -> when (args.first()) {
                    "toggle" -> {
                        if (!sender.hasPermission("uppercut.toggle.others")) result
                        else instance.server.onlinePlayers.stringFilter(args[1]) { player ->
                            player.name.takeIf { (sender as Player).canSee(player) }
                        }
                    }
                    else -> result
                }
                else -> result
            }
        }

        private inline fun <reified T : Any> FileConfiguration.getOrSet(key: String, defaultValue: T): T =
            if (get(key) == null) {
                set(key, defaultValue)
                defaultValue
            } else when (T::class) {
                Int::class -> getInt(key, defaultValue as Int) as T
                Long::class -> getLong(key, defaultValue as Long) as T
                Double::class -> getDouble(key, defaultValue as Double) as T
                Boolean::class -> getBoolean(key, defaultValue as Boolean) as T
                String::class -> getString(key, defaultValue as String) as T
                Color::class -> getColor(key, defaultValue as Color) as T
                Vector::class -> getVector(key, defaultValue as Vector) as T
                ItemStack::class -> getItemStack(key, defaultValue as ItemStack) as T
                //List::class -> getList(key, defaultValue as List<*>) as T
                else -> getObject(key, T::class.java, defaultValue) ?: defaultValue
            }

        private fun Collection<String>.stringFilter(query: String): List<String> {
            val result = mutableListOf<String>()
            for (it in this) {
                if (query.length <= 1) { if (it.startsWith(query, true)) result.add(it) }
                else { if (it.contains(query, true)) result.add(it) }
            }
            return result
        }

        private inline fun <T> Collection<T>.stringFilter(query: String, task: (T) -> String?): List<String> {
            val result = mutableListOf<String>()
            for (it in this) {
                val it = task(it) ?: continue
                if (query.length <= 1) { if (it.startsWith(query, true)) result.add(it) }
                else { if (it.contains(query, true)) result.add(it) }
            }
            return result
        }
    }
}