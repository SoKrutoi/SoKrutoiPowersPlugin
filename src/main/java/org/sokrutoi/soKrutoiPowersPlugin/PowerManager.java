package org.sokrutoi.soKrutoiPowersPlugin;

import org.sokrutoi.soKrutoiPowersPlugin.powers.SuperPower;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class PowerManager {

    // Имя → суперсила (нижний регистр для удобства команды)
    private final Map<String, SuperPower> powers = new LinkedHashMap<>();

    public void register(SuperPower power) {
        powers.put(power.getName().toLowerCase(), power);
        power.register();
    }

    public Optional<SuperPower> getByName(String name) {
        return Optional.ofNullable(powers.get(name.toLowerCase()));
    }

    public Collection<SuperPower> getAll() {
        return powers.values();
    }
}