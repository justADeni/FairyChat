package de.rexlmanu.fairychat.plugin.core.capitals;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import de.rexlmanu.fairychat.plugin.configuration.PluginConfiguration;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class CapitalsService {
  private final Provider<PluginConfiguration> configurationProvider;
  private final MiniMessage miniMessage;
  private final BukkitAudiences bukkitAudiences;

  public Component checkCapitals(Component message) {

    if (!this.configurationProvider.get().preventExcessiveCapitals()) {
        return message;
    }

    String miniTextMessage = this.miniMessage.serialize(message);
    int count = 0, len = miniTextMessage.length();
    for (int i = 0; i < len; i++) {
        char c = miniTextMessage.charAt(i);
        if (c >= 'A' && c <= 'Z') count++;
    }
    double percentageCapitals = len == 0 ? 0.0 : (double) count / len;

    if (percentageCapitals < this.configurationProvider.get().capitalPercentage()) {
        return message;
    }

    StringBuilder sb = new StringBuilder(len);
    for (int i = 0; i < len; i++) {
        char c = miniTextMessage.charAt(i);
        if (c >= 'A' && c <= 'Z') {
            sb.append((char)(c + 32));
        } else {
            sb.append(c);
        }
    }
    return this.miniMessage.deserialize(sb.toString());
  }
}
