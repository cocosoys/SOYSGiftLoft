package soys.soysgiftloft.spring.entity.vo;

/**
 * 领取特效 VO —— ERP 编辑礼包时的 claim-effects 段。
 * 全部字段可选；任一为空则跳过对应效果。
 */
public class ClaimEffectsVO {

    private String sound;
    private double volume = 1.0;
    private double pitch = 1.0;
    private String particle;
    private int particleCount = 16;
    private boolean broadcast = false;

    public String getSound() { return sound; }
    public void setSound(String sound) { this.sound = sound; }

    public double getVolume() { return volume; }
    public void setVolume(double volume) { this.volume = volume; }

    public double getPitch() { return pitch; }
    public void setPitch(double pitch) { this.pitch = pitch; }

    public String getParticle() { return particle; }
    public void setParticle(String particle) { this.particle = particle; }

    public int getParticleCount() { return particleCount; }
    public void setParticleCount(int particleCount) { this.particleCount = particleCount; }

    public boolean isBroadcast() { return broadcast; }
    public void setBroadcast(boolean broadcast) { this.broadcast = broadcast; }
}
