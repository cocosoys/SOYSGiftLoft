package soys.soysgiftloft.spring.entity.vo;

import java.util.List;

/**
 * 玩家状态 VO —— GET /api/plugins/SOYSGiftLoft/player/{name} 或 ?uuid=。
 * 字段序对齐 v1.8 {@code buildPlayerJson} 键序。
 */
public class PlayerVO {

    private String name;
    private String uuid;
    private boolean online;
    private long playtimeSeconds;
    private List<PackStatusVO> packs;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public boolean isOnline() {
        return online;
    }

    public void setOnline(boolean online) {
        this.online = online;
    }

    public long getPlaytimeSeconds() {
        return playtimeSeconds;
    }

    public void setPlaytimeSeconds(long playtimeSeconds) {
        this.playtimeSeconds = playtimeSeconds;
    }

    public List<PackStatusVO> getPacks() {
        return packs;
    }

    public void setPacks(List<PackStatusVO> packs) {
        this.packs = packs;
    }
}
