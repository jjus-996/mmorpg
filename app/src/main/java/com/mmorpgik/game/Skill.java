package com.mmorpgik.game;

/** Tek bir yetenek tanımı. Android'e bağımlı değildir. */
public final class Skill {
    public enum Type { MELEE, BOLT, AOE_SELF, AOE_TARGET, HEAL, SHIELD, HASTE, POWER, DASH, SUMMON }

    public final String name;
    public final Type type;
    public final float cooldown;   // saniye
    public final int mana;
    public final float mult;       // saldırı çarpanı
    public final float size;       // menzil / yarıçap / süre
    public final String info;

    public Skill(String name, Type type, float cooldown, int mana, float mult, float size, String info) {
        this.name = name;
        this.type = type;
        this.cooldown = cooldown;
        this.mana = mana;
        this.mult = mult;
        this.size = size;
        this.info = info;
    }
}
