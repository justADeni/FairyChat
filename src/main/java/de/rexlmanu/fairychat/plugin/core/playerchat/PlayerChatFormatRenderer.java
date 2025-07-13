package de.rexlmanu.fairychat.plugin.core.playerchat;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import de.rexlmanu.fairychat.plugin.configuration.PluginConfiguration;
import de.rexlmanu.fairychat.plugin.core.capitals.CapitalsService;
import de.rexlmanu.fairychat.plugin.core.ignore.UserIgnoreService;
import de.rexlmanu.fairychat.plugin.core.mentions.MentionService;
import de.rexlmanu.fairychat.plugin.integration.IntegrationRegistry;
import de.rexlmanu.fairychat.plugin.integration.chat.PlaceholderSupport;
import de.rexlmanu.fairychat.plugin.paper.DefaultChatRenderer;
import de.rexlmanu.fairychat.plugin.permission.PermissionProvider;
import de.rexlmanu.fairychat.plugin.utility.LegacySupport;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class PlayerChatFormatRenderer implements DefaultChatRenderer {
  private final Provider<PluginConfiguration> configurationProvider;
  private final MiniMessage miniMessage;
  private final PermissionProvider permissionProvider;
  private final MentionService mentionService;
  private final CapitalsService capitalsService;
  private final IntegrationRegistry registry;
  private final UserIgnoreService userIgnoreService;

  @Named("colorMiniMessage")
  private final MiniMessage colorMiniMessage;

  @Override
  public @NotNull Component render(
      @NotNull Player source,
      @NotNull Component sourceDisplayName,
      @NotNull Component message,
      @NotNull Audience viewer) {

    Component formattedMessage = this.formatMessage(source, message);
    if (!(viewer instanceof Player player)) {
      return formattedMessage;
    }
    if (this.userIgnoreService.isIgnored(player.getUniqueId(), source.getUniqueId())) {
      return formattedMessage;
    }
    return this.mentionService.checkMentions(player, formattedMessage);
  }

  @NotNull
  public Component formatMessage(@NotNull Player source, @NotNull Component message) {
    String group = this.permissionProvider.getGroup(source.getUniqueId());
    String chatFormat = this.configurationProvider.get().chatFormat();

    if (group != null) {
      chatFormat = this.configurationProvider.get().groupFormats().getOrDefault(group, chatFormat);
    }

    Function<Component, String> serializer = PlainTextComponentSerializer.plainText()::serialize;
    if (this.configurationProvider.get().legacyColorSupport()) {
      serializer = LegacyComponentSerializer.legacyAmpersand()::serialize;
    }
    final String textMessage = this.resolveMessageModifiers(source, serializer.apply(
      source.hasPermission("fairychat.bypass.capitals") ? message : this.capitalsService.checkCapitals(message))
    );

    // Check if the player has the permission to use mini message
    if (source.hasPermission("fairychat.feature.minimessage")) {
      String miniMessageFormatted = LegacySupport.replaceLegacyWithTags(textMessage);
      message =
          this.colorMiniMessage.deserialize(
              miniMessageFormatted,
              TagResolver.resolver(
                  this.registry.getPlaceholderSupports().stream()
                      .map(
                          chatPlaceholder ->
                              chatPlaceholder.resolveChatMessagePlaceholder(
                                  source, miniMessageFormatted))
                      .toList()));
    } else {
      message =
          MiniMessage.builder()
              .tags(
                  TagResolver.resolver(
                      this.registry.getPlaceholderSupports().stream()
                          .map(
                              chatPlaceholder ->
                                  chatPlaceholder.resolveChatMessagePlaceholder(
                                      source, textMessage))
                          .toList()))
              .build()
              .deserialize(textMessage);
    }

    @NotNull Component finalMessage = message;

    List<TagResolver> tagResolvers =
        new ArrayList<>(
            this.registry.getPlaceholderSupports().stream()
                .map(
                    chatPlaceholder ->
                        chatPlaceholder.resolvePlayerPlaceholderWithChatMessage(
                            source, finalMessage))
                .toList());

    tagResolvers.add(Placeholder.component("message", message));
    tagResolvers.add(
        Placeholder.unparsed("server_name", this.configurationProvider.get().serverName()));

    return this.miniMessage.deserialize(chatFormat, TagResolver.resolver(tagResolvers));
  }

  private String resolveMessageModifiers(@NotNull Player source, String textMessage) {
    for (PlaceholderSupport placeholderSupport : this.registry.getPlaceholderSupports()) {
      textMessage = placeholderSupport.resolveChatMessageModifier(source.getPlayer(), textMessage);
    }
    return textMessage;
  }
}
