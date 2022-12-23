package dev.vankka.dsrvdownloader.model;

public enum UserAgent {

    LIKELY_MANUAL((byte) 1),
    LIKELY_AUTOMATED((byte) 2),
    LIKELY_SCRAPER((byte) 3),
    UNKNOWN((byte) -1);

    private final byte sql;

    UserAgent(byte sql) {
        this.sql = sql;
    }

    public byte sql() {
        return sql;
    }

    public static UserAgent getBySql(String sql) {
        for (UserAgent value : values()) {
            if (((Byte) value.sql()).toString().equals(sql)) {
                return value;
            }
        }
        throw new IllegalArgumentException();
    }
}
