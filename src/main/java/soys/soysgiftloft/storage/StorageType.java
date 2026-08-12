package soys.soysgiftloft.storage;

/**
 * 存储后端类型。
 * <p>
 * priority 决定主存储的选取：在所有已启用的后端中，priority 最高者作为主存储，
 * 其余作为辅助存储被镜像写入。固定优先级为 MYSQL &gt; SQLITE &gt; YAML。
 * </p>
 */
public enum StorageType {

    YAML("yaml", 10, "YAML 文件"),
    SQLITE("sqlite", 20, "SQLite 数据库"),
    MYSQL("mysql", 30, "MySQL 数据库");

    private final String id;
    private final int priority;
    private final String displayName;

    StorageType(String id, int priority, String displayName) {
        this.id = id;
        this.priority = priority;
        this.displayName = displayName;
    }

    public String getId() {
        return id;
    }

    public int getPriority() {
        return priority;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * 按 id 解析，未匹配时返回 null。
     */
    public static StorageType fromId(String input) {
        if (input == null) {
            return null;
        }
        String normalized = input.trim().toLowerCase();
        for (StorageType type : values()) {
            if (type.id.equals(normalized)) {
                return type;
            }
        }
        return null;
    }
}
