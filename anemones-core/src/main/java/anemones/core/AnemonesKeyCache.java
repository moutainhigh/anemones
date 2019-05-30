package anemones.core;

public class AnemonesKeyCache {
    private static final String SET_PREFIX = ":zset";
    private static final String LIST_PREFIX = ":list";
    private String zsetKey;
    private String listKey;

    public AnemonesKeyCache(String finalPrefix, String queue) {
        String commonKeyPrefix = finalPrefix + queue;
        this.zsetKey = commonKeyPrefix + SET_PREFIX;
        this.listKey = commonKeyPrefix + LIST_PREFIX;
    }

    public String getListKey() {
        return listKey;
    }

    public String getZsetKey() {
        return zsetKey;
    }
}
