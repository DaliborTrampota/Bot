package co.groovybot.bot.commands.settings;

import co.groovybot.bot.core.command.Command;
import co.groovybot.bot.core.command.CommandCategory;
import co.groovybot.bot.core.command.CommandEvent;
import co.groovybot.bot.core.command.Result;
import co.groovybot.bot.core.command.permission.Permissions;
import co.groovybot.bot.core.entity.EntityProvider;
import co.groovybot.bot.core.entity.User;
import co.groovybot.bot.core.translation.TranslationManager;

import java.util.Locale;

public class LanguageCommand extends Command {

    public LanguageCommand() {
        super(new String[]{"language", "lang"}, CommandCategory.SETTINGS, Permissions.everyone(), "Lets you set your language", "[language-tag]");
    }

    @Override
    public Result run(String[] args, CommandEvent event) {
        User user = EntityProvider.getUser(event.getAuthor().getIdLong());

        if (args.length == 0)
            return send(info(event.translate("command.language.info.title"), String.format(event.translate("command.language.info.description"), user.getLocale().getLanguage(), formatAvailableLanguages(event.getBot().getTranslationManager()))));

        Locale locale;

        try {
            locale = Locale.forLanguageTag(args[0].replace("_", "-"));
        } catch (Exception e) {
            return send(error(event.translate("command.language.invalid.title"), event.translate("command.language.invalid.description")));
        }

        if (!event.getBot().getTranslationManager().isTranslated(locale))
            return send(error(event.translate("command.language.nottranslated.title"), event.translate("command.language.nottranslated.description")));

        user.setLocale(locale);
        return send(success(event.translate("command.language.set.title"), String.format(event.translate("command.language.set.description"), locale.getLanguage())));
    }

    private String formatAvailableLanguages(TranslationManager translationManager) {
        StringBuilder builder = new StringBuilder();
        translationManager.getLocales().forEach(locale -> builder.append(locale.getLanguageName()).append("(`").append(locale.getLocale().toLanguageTag().replace("-", "_")).append("`)").append("\n"));
        return builder.toString();
    }
}