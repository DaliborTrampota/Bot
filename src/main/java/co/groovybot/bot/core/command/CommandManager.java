package co.groovybot.bot.core.command;

import co.groovybot.bot.GroovyBot;
import co.groovybot.bot.core.entity.Guild;
import co.groovybot.bot.core.events.command.CommandExecutedEvent;
import co.groovybot.bot.core.events.command.CommandFailEvent;
import co.groovybot.bot.core.events.command.NoPermissionEvent;
import co.groovybot.bot.util.EmbedUtil;
import co.groovybot.bot.util.NameThreadFactory;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.core.hooks.SubscribeEvent;

import java.io.Closeable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Log4j2
public class CommandManager implements Closeable {

    @Getter
    private final Map<String, Command> commandAssociations;
    private final String defaultPrefix;
    private final GroovyBot bot;
    private final ExecutorService executor;

    public CommandManager(String defaultPrefix, GroovyBot bot) {
        commandAssociations = new HashMap<>();
        this.defaultPrefix = defaultPrefix;
        this.bot = bot;
        this.executor = Executors.newCachedThreadPool(new NameThreadFactory("CommandExecutor"));
    }

    @SubscribeEvent
    @SuppressWarnings("unused")
    private void onMessageReceived(GuildMessageReceivedEvent event) {
        // Do not execute if bot is not ready
        if (!bot.isAllShardsInitialized())
            return;

        // Check if event is not a CommandEvent
        if (event instanceof CommandEvent) return;

        // Check if invoker is real
        User author = event.getAuthor();
        if (author.isBot() || author.isFake() || event.isWebhookMessage()) return;
        executor.execute(() -> parseCommands(event));
    }

    private void parseCommands(GuildMessageReceivedEvent event) {
        String prefix = null;
        String content = event.getMessage().getContentRaw();
        String lowerCasedContent = content.toLowerCase();
        Guild guild = bot.getGuildCache().get(event.getGuild().getIdLong());

        // Check prefix
        if (lowerCasedContent.startsWith(defaultPrefix))
            prefix = defaultPrefix;
        else {
            String mention = event.getGuild().getSelfMember().getAsMention();
            if (lowerCasedContent.startsWith(mention))
                prefix = mention;
            else {
                if (bot.getGuildCache() == null)
                    return;
                String customPrefix = guild.getPrefix();
                if (lowerCasedContent.startsWith(customPrefix))
                    prefix = customPrefix;
            }
        }

        // Abort if message don't start with the right prefix
        if (prefix == null) return;

        //Remove prefix
        String beheaded = content.substring(prefix.length()).trim();

        //Split arguments
        String[] allArgs = beheaded.split("\\s+");
        String invocation = allArgs[0].toLowerCase();

        //Search for commands
        if (!commandAssociations.containsKey(invocation)) return;

        // Check if channel is not commandschannel
        if (guild.hasCommandsChannel())
            if (event.getChannel().getIdLong() != guild.getBotChannel()) {
                EmbedUtil.sendMessage(event.getChannel(), EmbedUtil.error("Not allowed!", String.format("It is **not allowed** to use me in **this channel** as %s is the **only** channel for **commands**!", bot.getShardManager().getTextChannelById(guild.getBotChannel()))), 5);
                return;
            }

        // Check if channel is blacklisted
        if (guild.isChannelBlacklisted(event.getChannel().getIdLong())) {
            EmbedUtil.sendMessage(event.getChannel(), EmbedUtil.error("Not allowed!", "It is **not allowed** to use me in **this channel** as this channel is **blacklisted**!"), 5);
            return;
        }

        Command command = commandAssociations.get(invocation);

        //Remove invocation
        String[] commandArgs = new String[allArgs.length - 1];
        System.arraycopy(allArgs, 1, commandArgs, 0, commandArgs.length);
        String[] args;

        //Check for sub commands
        if (commandArgs.length > 0 && command.getSubCommandAssociations().containsKey(commandArgs[0])) {
            command = command.getSubCommandAssociations().get(commandArgs[0]);
            args = new String[commandArgs.length - 1];
            System.arraycopy(commandArgs, 1, args, 0, args.length);
        } else
            args = commandArgs;
        CommandEvent commandEvent = new CommandEvent(event, bot, args, invocation);
        callCommand(command, commandEvent);
    }

    private void callCommand(Command command, CommandEvent commandEvent) {
        //Check permission
        if (!command.getPermissions().isCovered(bot.getUserCache().get(commandEvent.getAuthor().getIdLong()).getPermissions(), commandEvent)) {
            bot.getEventManager().handle(new NoPermissionEvent(commandEvent, command));
            return;
        }

        //Run command
        try {
            TextChannel channel = commandEvent.getChannel();

            //Send typing
            channel.sendTyping().queue();

            //Delete invoke message
            if (commandEvent.getGuild().getSelfMember().hasPermission(commandEvent.getChannel(), Permission.MESSAGE_MANAGE))
                commandEvent.getMessage().delete().queue();

            //Run the commands run() method
            Result result = command.run(commandEvent.getArgs(), commandEvent);
            if (result != null)
                result.sendMessage(channel, 60);
            bot.getEventManager().handle(new CommandExecutedEvent(commandEvent, command));
        } catch (Exception e) {
            bot.getEventManager().handle(new CommandFailEvent(commandEvent, command, e));
        }
    }

    /**
     * Registers commands
     *
     * @param commands The command handlers {@link Command}
     */
    public void registerCommands(Command... commands) {
        for (Command command : commands) {
            registerCommand(command);
        }
    }

    /**
     * Registers a command
     *
     * @param command The command handler {@link Command}
     */
    public void registerCommand(Command command) {
        for (String alias : command.getAliases()) {
            if (commandAssociations.containsKey(alias))
                log.warn(String.format("[CommandManager] Alias %s is already taken by %s", alias, commandAssociations.get(alias).getClass().getCanonicalName()));
            else
                commandAssociations.put(alias, command);
        }
    }

    @Override
    public void close() {
        executor.shutdown();
    }
}