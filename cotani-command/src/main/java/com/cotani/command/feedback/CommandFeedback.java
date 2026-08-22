package com.cotani.command.feedback;

import com.cotani.text.MiniMessages;
import com.cotani.text.Placeholders;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import net.kyori.adventure.text.Component;

/**
 * Message provider generating formatted MiniMessage feedback components for command events and errors.
 */
public interface CommandFeedback {
    Component formatUnknownCommand();

    Component formatUnknownSubcommand(String input, String usage);

    Component formatInvalidUsage(String usage);

    Component formatPermissionDenied(Optional<String> permission);

    Component formatPlayerOnly();

    Component formatConsoleOnly();

    Component formatCooldownActive(Duration remaining);

    Component formatExecutionError();

    /**
     * Returns the default Cotani command feedback implementation.
     *
     * @return default feedback provider
     */
    static CommandFeedback defaultFeedback() {
        return DefaultCommandFeedback.DEFAULT;
    }

    /**
     * Returns standard feedback messages in Brazilian Portuguese (pt-BR).
     *
     * @return pt-BR feedback provider
     */
    static CommandFeedback ptBR() {
        return builder()
                .unknownCommand("<red>Comando desconhecido. Digite <yellow>/help</yellow> para obter ajuda.</red>")
                .unknownSubcommand(
                        "<red>Subcomando desconhecido '<yellow><input></yellow>'. Uso correto: <yellow><usage></yellow></red>")
                .invalidUsage("<red>Uso incorreto do comando. Formato correto: <yellow><usage></yellow></red>")
                .permissionDenied("<red>Você não tem permissão para executar este comando.</red>")
                .playerOnly("<red>Este comando só pode ser executado por jogadores dentro do jogo.</red>")
                .consoleOnly("<red>Este comando só pode ser executado através do console do servidor.</red>")
                .cooldownActive(
                        "<red>Por favor aguarde <yellow><remaining></yellow> antes de usar este comando novamente.</red>")
                .executionError("<red>Ocorreu um erro interno ao executar este comando.</red>")
                .build();
    }

    /**
     * Creates a new feedback builder to customize messages.
     *
     * @return builder instance
     */
    static Builder builder() {
        return new Builder();
    }

    final class Builder {
        private static final String TEMPLATE = "template";

        private String unknownCommand = "<red>Unknown command. Type <yellow>/help</yellow> for help.</red>";
        private String unknownSubcommand =
                "<red>Unknown subcommand '<yellow><input></yellow>'. Usage: <yellow><usage></yellow></red>";
        private String invalidUsage = "<red>Invalid command usage. Format: <yellow><usage></yellow></red>";
        private String permissionDenied = "<red>You do not have permission to execute this command.</red>";
        private String playerOnly = "<red>This command can only be executed by in-game players.</red>";
        private String consoleOnly = "<red>This command can only be executed from the server console.</red>";
        private String cooldownActive =
                "<red>Please wait <yellow><remaining></yellow> before using this command again.</red>";
        private String executionError = "<red>An internal error occurred while executing this command.</red>";

        public Builder unknownCommand(String template) {
            this.unknownCommand = Objects.requireNonNull(template, TEMPLATE);
            return this;
        }

        public Builder unknownSubcommand(String template) {
            this.unknownSubcommand = Objects.requireNonNull(template, TEMPLATE);
            return this;
        }

        public Builder invalidUsage(String template) {
            this.invalidUsage = Objects.requireNonNull(template, TEMPLATE);
            return this;
        }

        public Builder permissionDenied(String template) {
            this.permissionDenied = Objects.requireNonNull(template, TEMPLATE);
            return this;
        }

        public Builder playerOnly(String template) {
            this.playerOnly = Objects.requireNonNull(template, TEMPLATE);
            return this;
        }

        public Builder consoleOnly(String template) {
            this.consoleOnly = Objects.requireNonNull(template, TEMPLATE);
            return this;
        }

        public Builder cooldownActive(String template) {
            this.cooldownActive = Objects.requireNonNull(template, TEMPLATE);
            return this;
        }

        public Builder executionError(String template) {
            this.executionError = Objects.requireNonNull(template, TEMPLATE);
            return this;
        }

        public CommandFeedback build() {
            return new DefaultCommandFeedback(
                    unknownCommand,
                    unknownSubcommand,
                    invalidUsage,
                    permissionDenied,
                    playerOnly,
                    consoleOnly,
                    cooldownActive,
                    executionError);
        }
    }
}

record DefaultCommandFeedback(
        String unknownCommandTemplate,
        String unknownSubcommandTemplate,
        String invalidUsageTemplate,
        String permissionDeniedTemplate,
        String playerOnlyTemplate,
        String consoleOnlyTemplate,
        String cooldownActiveTemplate,
        String executionErrorTemplate)
        implements CommandFeedback {

    static final DefaultCommandFeedback DEFAULT =
            (DefaultCommandFeedback) CommandFeedback.builder().build();

    @Override
    public Component formatUnknownCommand() {
        return MiniMessages.parse(unknownCommandTemplate);
    }

    @Override
    public Component formatUnknownSubcommand(String input, String usage) {
        return MiniMessages.parse(
                unknownSubcommandTemplate,
                Placeholders.unparsed("input", input),
                Placeholders.unparsed("usage", usage));
    }

    @Override
    public Component formatInvalidUsage(String usage) {
        return MiniMessages.parse(invalidUsageTemplate, Placeholders.unparsed("usage", usage));
    }

    @Override
    public Component formatPermissionDenied(Optional<String> permission) {
        return MiniMessages.parse(permissionDeniedTemplate, Placeholders.unparsed("permission", permission.orElse("")));
    }

    @Override
    public Component formatPlayerOnly() {
        return MiniMessages.parse(playerOnlyTemplate);
    }

    @Override
    public Component formatConsoleOnly() {
        return MiniMessages.parse(consoleOnlyTemplate);
    }

    @Override
    public Component formatCooldownActive(Duration remaining) {
        var remainingFormatted = formatDuration(remaining);
        return MiniMessages.parse(cooldownActiveTemplate, Placeholders.unparsed("remaining", remainingFormatted));
    }

    @Override
    public Component formatExecutionError() {
        return MiniMessages.parse(executionErrorTemplate);
    }

    private static String formatDuration(Duration duration) {
        var seconds = duration.toSeconds();
        if (seconds < 60) {
            return Math.max(1, seconds) + "s";
        }
        var minutes = seconds / 60;
        var remainingSecs = seconds % 60;
        if (remainingSecs == 0) {
            return minutes + "m";
        }
        return minutes + "m " + remainingSecs + "s";
    }
}
