package dev.yukio.originweapon.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.yukio.originweapon.OriginWeapon;
import dev.yukio.originweapon.item.OriginItem;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;

/** /originweapon give &lt;id&gt; [player] | reload | list (алиас /ow). */
public final class OriginWeaponCommand {

    private static final String PERMISSION = "originweapon.admin";

    private OriginWeaponCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> build(OriginWeapon plugin) {
        return Commands.literal("originweapon")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(Commands.literal("reload").executes(ctx -> {
                    plugin.reload();
                    plugin.send(ctx.getSource().getSender(), "reloaded");
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("list").executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    plugin.send(sender, "list-header");
                    for (OriginItem item : plugin.registry().all()) {
                        plugin.send(sender, item.isEnabled() ? "list-entry-enabled" : "list-entry-disabled",
                                Placeholder.unparsed("id", item.id()));
                    }
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("give")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    String input = builder.getRemainingLowerCase();
                                    for (OriginItem item : plugin.registry().all()) {
                                        if (item.isEnabled() && item.id().startsWith(input)) {
                                            builder.suggest(item.id());
                                        }
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    if (!(ctx.getSource().getExecutor() instanceof Player self)) {
                                        plugin.send(ctx.getSource().getSender(), "player-only");
                                        return 0;
                                    }
                                    return give(plugin, ctx, self);
                                })
                                .then(Commands.argument("player", ArgumentTypes.player())
                                        .executes(ctx -> {
                                            Player target = ctx.getArgument("player", PlayerSelectorArgumentResolver.class)
                                                    .resolve(ctx.getSource()).getFirst();
                                            return give(plugin, ctx, target);
                                        }))))
                .build();
    }

    private static int give(OriginWeapon plugin, CommandContext<CommandSourceStack> ctx, Player target)
            throws CommandSyntaxException {
        CommandSender sender = ctx.getSource().getSender();
        String id = StringArgumentType.getString(ctx, "id").toLowerCase(Locale.ROOT);
        OriginItem item = plugin.registry().get(id);
        if (item == null) {
            plugin.send(sender, "unknown-item", Placeholder.unparsed("id", id));
            return 0;
        }
        if (!item.isEnabled()) {
            plugin.send(sender, "item-disabled", Placeholder.unparsed("id", id));
            return 0;
        }
        ItemStack stack = item.createItem();
        target.getInventory().addItem(stack).values()
                .forEach(left -> target.getWorld().dropItemNaturally(target.getLocation(), left));
        plugin.send(sender, "given", Placeholder.unparsed("id", id), Placeholder.unparsed("player", target.getName()));
        return Command.SINGLE_SUCCESS;
    }
}
