package com.lovelyspy.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ConfigExpectedValuesTest {
    private ConfigExpectedValuesTest() {
    }

    public static void main(String[] args) throws Exception {
        dottedTranslationKeysSurviveYamlRoundTrip();
    }

    private static void dottedTranslationKeysSurviveYamlRoundTrip() throws Exception {
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("key.meteor-client.open-gui", "Open GUI");
        expected.put("key.meteor-client.open-commands", "Open Commands");

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("mods.meteor_client.expected_values", expected);
        YamlConfiguration loaded = new YamlConfiguration();
        loaded.loadFromString(yaml.saveToString());

        ConfigurationSection section =
                loaded.getConfigurationSection("mods.meteor_client.expected_values");
        Map<String, String> actual = Config.readStringMap(section);
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected literal dotted translation keys "
                    + expected + " but got " + actual);
        }
    }
}
