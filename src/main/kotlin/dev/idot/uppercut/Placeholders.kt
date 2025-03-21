package dev.idot.uppercut

import dev.idot.text.color.convertColors
import dev.idot.text.color.convertColorsAndFormat
import me.clip.placeholderapi.PlaceholderAPI
import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.OfflinePlayer
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class Placeholders(val plugin: Uppercut) : PlaceholderExpansion() {
    override fun getIdentifier(): String = "uppercut"
    override fun getAuthor(): String = plugin.description.authors.first()
    override fun getVersion(): String = plugin.description.version
    override fun persist(): Boolean = true
    override fun onRequest(player: OfflinePlayer?, params: String): String? {
        player ?: return null
        val args = params.split("_", limit = 2)
        return when (args.firstOrNull()) {
            "puncher" -> {
                val puncher = Uppercut.lastPunched[player] ?: return null
                val placeholder = args.getOrNull(1) ?: return puncher.name
                PlaceholderAPI.setBracketPlaceholders(player, placeholder)
            }

            "target" -> {
                val target = Uppercut.lastTargeted[player] ?: return "null"
                val placeholder = args.getOrNull(2) ?: return target.name
                PlaceholderAPI.setBracketPlaceholders(target, placeholder)
            }

            "punchable" -> {
                val uuid = player.uniqueId
                val offCooldown = Uppercut.cooldowns.getOrDefault(uuid, 0) >= System.currentTimeMillis()
                val toggledOn = !Uppercut.toggledOff.contains(uuid)
                (offCooldown && toggledOn).toString()
            }

            "toggled" -> {
                (!Uppercut.toggledOff.contains(player.uniqueId)).toString()
            }

            else -> null
        }
    }


    companion object {
        val isPlaceholderApi = try {
            Class.forName("me.clip.placeholderapi.PlaceholderAPI")
            true
        } catch (_: ClassNotFoundException) {
            false
        }

        fun String.sendTo(sender: CommandSender? = null) {
            val msg = if (isPlaceholderApi) PlaceholderAPI.setPlaceholders(sender as Player, this) else this
            msg
                .replace("{prefix}", Uppercut.msg_prefix)
                .convertColors(minify = true)
                .also { sender?.sendMessage(it) }
        }

        fun CommandSender.message(string: String) {
            val msg = if (isPlaceholderApi) PlaceholderAPI.setPlaceholders(this as Player, string) else string
            msg
                .replace("{prefix}", Uppercut.msg_prefix)
                .convertColors(minify = true)
                .also { sendMessage(it) }
        }
    }
}