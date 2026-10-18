package com.grimesmp;

public enum Tier {
    S(45), A(35), B(25), C(20), D(15), F(10);

    private final int defaultPoints;

    Tier(int defaultPoints) {
        this.defaultPoints = defaultPoints;
    }

    /** Kill value of this tier (read from config.yml, falls back to the default). */
    public int points(GrimeSMP plugin) {
        return plugin.getConfig().getInt("tier-points." + name(), defaultPoints);
    }

    public static Tier parse(String s) {
        for (Tier t : values()) {
            if (t.name().equalsIgnoreCase(s)) return t;
        }
        return null;
    }
}
