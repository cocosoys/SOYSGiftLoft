package soys.soysgiftloft.spring.entity.vo;

import java.util.List;

/**
 * 插件状态 VO —— GET /api/plugins/SOYSGiftLoft/status。
 *
 * <p>字段序与 v1.8 手写 {@code LinkedHashMap} 键序一致（JsonWriter 按 getter 声明序输出），
 * 保证迁移后 JSON 结构与旧版逐字段兼容。</p>
 */
public class StatusVO {

    private String plugin;
    private String version;
    private int packs;
    private String primaryStorage;
    private List<String> secondaryStorages;
    private int onlinePlayers;
    private long uptimeSeconds;

    public String getPlugin() {
        return plugin;
    }

    public void setPlugin(String plugin) {
        this.plugin = plugin;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public int getPacks() {
        return packs;
    }

    public void setPacks(int packs) {
        this.packs = packs;
    }

    public String getPrimaryStorage() {
        return primaryStorage;
    }

    public void setPrimaryStorage(String primaryStorage) {
        this.primaryStorage = primaryStorage;
    }

    public List<String> getSecondaryStorages() {
        return secondaryStorages;
    }

    public void setSecondaryStorages(List<String> secondaryStorages) {
        this.secondaryStorages = secondaryStorages;
    }

    public int getOnlinePlayers() {
        return onlinePlayers;
    }

    public void setOnlinePlayers(int onlinePlayers) {
        this.onlinePlayers = onlinePlayers;
    }

    public long getUptimeSeconds() {
        return uptimeSeconds;
    }

    public void setUptimeSeconds(long uptimeSeconds) {
        this.uptimeSeconds = uptimeSeconds;
    }
}
