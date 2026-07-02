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
        namedPolicyTakesPriorityOverVectorFallback();
        vectorPolicyIsUsedWhenNamedPolicyIsAbsent();
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

    private static void namedPolicyTakesPriorityOverVectorFallback() {
        Config config = new Config(null);
        Config.ModEntry fallback = policy("fallback", "privacy_probe");
        Config.ModEntry named = policy("opsec", "other_vector");
        config.modEntries.put("fallback", fallback);
        config.modEntries.put("OpSec", named);

        if (config.findPolicy("opsec", "privacy_probe") != named) {
            throw new AssertionError("The explicitly named OpSec policy must take priority");
        }
    }

    private static void vectorPolicyIsUsedWhenNamedPolicyIsAbsent() {
        Config config = new Config(null);
        Config.ModEntry fallback = policy("fallback", "privacy_probe");
        config.modEntries.put("fallback", fallback);

        if (config.findPolicy("opsec", "privacy_probe") != fallback) {
            throw new AssertionError("Privacy vector policy should be used as a fallback");
        }
    }

    private static Config.ModEntry policy(String name, String vector) {
        return new Config.ModEntry(name, java.util.List.of(), "BAN",
                "test policy", vector, true);
    }
}
