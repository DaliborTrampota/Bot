package io.groovybot.bot.commands.general;

import io.groovybot.bot.core.command.Command;
import io.groovybot.bot.core.command.CommandCategory;
import io.groovybot.bot.core.command.CommandEvent;
import io.groovybot.bot.core.command.Result;
import io.groovybot.bot.core.command.permission.Permissions;

public class PingCommand extends Command {

    public PingCommand() {
        super(new String[]{"ping", "latency"}, CommandCategory.GENERAL, Permissions.everyone(), "Shows the Groovy's current ping", "");
    }

    @Override
    public Result run(String[] args, CommandEvent event) {
        return send(noTitle(String.format("**%s** ms", Math.ceil(event.getBot().getShardManager().getAveragePing()))));
    }
}
