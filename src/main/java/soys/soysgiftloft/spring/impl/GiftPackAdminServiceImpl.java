package soys.soysgiftloft.spring.impl;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import soys.soysgiftloft.SOYSGiftLoft;
import soys.soysgiftloft.manager.GiftLoftManager;
import soys.soysgiftloft.spring.entity.vo.ClaimEffectsVO;
import soys.soysgiftloft.spring.entity.vo.ConditionNodeVO;
import soys.soysgiftloft.spring.entity.vo.GiftPackSaveVO;
import soys.soysgiftloft.spring.entity.vo.RewardVO;
import soys.soysgiftloft.spring.service.GiftPackAdminService;
import com.github.cocosoys.mc.soyshttpovermc.util.AjaxResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 礼包 ERP 管理写操作实现：直接操作 giftpacks.yml 的 FileConfiguration，
 * 写盘后调用 {@link GiftLoftManager#load()} 同步内存缓存。
 *
 * <p>线程模型：本实现由 SOYS 网关 worker 线程调用。磁盘写（FileConfiguration.save）在
 * worker 线程完成即可（纯 IO，无共享状态）；但 {@code GiftLoftManager.load()} 会清空并重建
 * 内存 {@code packs} 裸 {@link java.util.LinkedHashMap}，而玩家领取读路径在主线程通过
 * {@code getPack/getPacks} 并发访问同一 Map——若在此 worker 线程直接重建将产生
 * ConcurrentModificationException / 撕裂读。因此 load() 必须收敛到主线程执行
 * （与 onEnable / /sgiftloft reload 的 reload 路径一致）。</p>
 */
public class GiftPackAdminServiceImpl implements GiftPackAdminService {

    private final SOYSGiftLoft plugin;
    private final GiftLoftManager manager;

    public GiftPackAdminServiceImpl(SOYSGiftLoft plugin, GiftLoftManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public AjaxResult create(GiftPackSaveVO vo) {
        String id = validateId(vo.getId());
        if (id == null) {
            return AjaxResult.error("礼包 id 不能为空（小写字母/数字/连字符）");
        }
        FileConfiguration cfg = plugin.getGiftPackConfig();
        if (cfg == null) {
            return AjaxResult.error("giftpacks.yml 未加载");
        }
        if (cfg.contains("giftpacks." + id)) {
            return AjaxResult.error("礼包已存在: " + id);
        }
        writePack(cfg, id, vo);
        return saveAndReload("新建礼包成功: " + id);
    }

    @Override
    public AjaxResult update(GiftPackSaveVO vo) {
        String id = validateId(vo.getId());
        if (id == null) {
            return AjaxResult.error("礼包 id 不能为空");
        }
        FileConfiguration cfg = plugin.getGiftPackConfig();
        if (cfg == null) {
            return AjaxResult.error("giftpacks.yml 未加载");
        }
        if (!cfg.contains("giftpacks." + id)) {
            return AjaxResult.error("礼包不存在: " + id);
        }
        writePack(cfg, id, vo);
        return saveAndReload("更新礼包成功: " + id);
    }

    @Override
    public AjaxResult remove(String id) {
        id = validateId(id);
        if (id == null) {
            return AjaxResult.error("礼包 id 不能为空");
        }
        FileConfiguration cfg = plugin.getGiftPackConfig();
        if (cfg == null) {
            return AjaxResult.error("giftpacks.yml 未加载");
        }
        if (!cfg.contains("giftpacks." + id)) {
            return AjaxResult.error("礼包不存在: " + id);
        }
        cfg.set("giftpacks." + id, null);
        return saveAndReload("删除礼包成功: " + id);
    }

    // ================================================================
    // 内部：YAML → VO（与 writePack 严格镜像，保证编辑往返无损）
    // ================================================================

    @Override
    public GiftPackSaveVO readPack(String id) {
        id = validateId(id);
        if (id == null) {
            return null;
        }
        FileConfiguration cfg = plugin.getGiftPackConfig();
        if (cfg == null) {
            return null;
        }
        String base = "giftpacks." + id;
        if (!cfg.contains(base)) {
            return null;
        }
        GiftPackSaveVO vo = new GiftPackSaveVO();
        vo.setId(id);
        vo.setDisplay(cfg.getString(base + ".display", id));
        vo.setDescription(cfg.getString(base + ".description", ""));
        vo.setClaimMode(cfg.getString(base + ".claim-mode", "ONCE"));
        vo.setCooldownSeconds(cfg.getLong(base + ".cooldown", 0L));
        vo.setRarity(cfg.getString(base + ".rarity", "COMMON"));
        vo.setConditions(fromConditionObject(cfg.get(base + ".unlock-conditions")));
        vo.setRewards(fromRewardList(cfg.getMapList(base + ".rewards")));
        vo.setClaimEffects(fromClaimEffectsSection(cfg.getConfigurationSection(base + ".claim-effects")));
        return vo;
    }

    /**
     * 条件树：YAML 对象（或 MemorySection / 旧式扁平 List）→ ConditionNodeVO。
     * 与 {@link #toConditionObject(ConditionNodeVO)} 严格互逆。
     */
    private ConditionNodeVO fromConditionObject(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof ConfigurationSection) {
            obj = ((ConfigurationSection) obj).getValues(false);
        }
        if (obj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) obj;
            Object mode = m.get("mode");
            Object type = m.get("type");
            boolean isGroup = mode != null || (type == null && m.containsKey("list"));
            if (isGroup) {
                ConditionNodeVO g = new ConditionNodeVO();
                g.setMode(mode == null ? "AND" : String.valueOf(mode).toUpperCase());
                List<ConditionNodeVO> kids = new ArrayList<>();
                Object list = m.get("list");
                if (list instanceof List) {
                    for (Object o : (List<?>) list) {
                        ConditionNodeVO c = fromConditionObject(o);
                        if (c != null) {
                            kids.add(c);
                        }
                    }
                }
                g.setList(kids);
                return g;
            }
            ConditionNodeVO v = new ConditionNodeVO();
            v.setType(type == null ? null : String.valueOf(type).toUpperCase());
            fillLeafCondition(v, m);
            return v;
        }
        if (obj instanceof List) {
            // 旧式扁平列表 = AND 组
            ConditionNodeVO g = new ConditionNodeVO();
            g.setMode("AND");
            List<ConditionNodeVO> kids = new ArrayList<>();
            for (Object o : (List<?>) obj) {
                ConditionNodeVO c = fromConditionObject(o);
                if (c != null) {
                    kids.add(c);
                }
            }
            g.setList(kids);
            return g;
        }
        return null;
    }

    private void fillLeafCondition(ConditionNodeVO v, Map<String, Object> m) {
        String t = v.getType();
        if (t == null) {
            return;
        }
        switch (t) {
            case "PLACEHOLDER":
                v.setPlaceholder(str(m, "placeholder"));
                v.setOperator(str(m, "operator"));
                v.setValue(str(m, "value"));
                break;
            case "DATE":
                v.setStart(str(m, "start"));
                v.setEnd(str(m, "end"));
                break;
            case "STAT":
                v.setStat(str(m, "stat"));
                v.setEntity(str(m, "entity"));
                v.setMaterial(str(m, "material"));
                v.setOperator(str(m, "operator"));
                v.setValue(str(m, "value"));
                break;
            default:
                v.setValue(str(m, "value"));
        }
    }

    private List<RewardVO> fromRewardList(List<Map<?, ?>> maps) {
        List<RewardVO> out = new ArrayList<>();
        if (maps == null) {
            return out;
        }
        for (Map<?, ?> m : maps) {
            @SuppressWarnings("unchecked")
            RewardVO r = fromRewardMap((Map<String, Object>) m);
            if (r != null) {
                out.add(r);
            }
        }
        return out;
    }

    /**
     * 奖励：YAML Map → RewardVO（含 RANDOM 池递归）。与 {@link #toRewardMap(RewardVO)} 严格互逆。
     */
    private RewardVO fromRewardMap(Map<String, Object> m) {
        if (m == null || !m.containsKey("type")) {
            return null;
        }
        Object typeObj = m.get("type");
        if (typeObj == null) {
            return null;
        }
        String type = String.valueOf(typeObj).toUpperCase();
        RewardVO r = new RewardVO();
        r.setType(type);
        switch (type) {
            case "MONEY":
            case "POINTS":
            case "COMMAND":
                r.setValue(num(m, "value"));
                if ("COMMAND".equals(type) && m.containsKey("console")) {
                    r.setConsole(bool(m, "console"));
                }
                break;
            case "EXP":
                r.setValue(num(m, "value"));
                if (m.containsKey("levels")) {
                    r.setLevels(bool(m, "levels"));
                }
                break;
            case "ITEM":
                r.setMaterial(str(m, "material"));
                if (m.containsKey("amount")) {
                    r.setAmount(intv(m, "amount"));
                }
                r.setName(str(m, "name"));
                if (m.containsKey("lore")) {
                    r.setLore(strList(m, "lore"));
                }
                if (m.containsKey("enchant")) {
                    r.setEnchant(strList(m, "enchant"));
                }
                break;
            case "POTION":
                r.setEffect(str(m, "effect"));
                if (m.containsKey("duration")) {
                    r.setDuration(intv(m, "duration"));
                }
                if (m.containsKey("amplifier")) {
                    r.setAmplifier(intv(m, "amplifier"));
                }
                if (m.containsKey("particles")) {
                    r.setParticles(bool(m, "particles"));
                }
                break;
            case "CUSTOM_ITEM":
                r.setPlugin(str(m, "plugin"));
                r.setId(str(m, "id"));
                if (m.containsKey("amount")) {
                    r.setAmount(intv(m, "amount"));
                }
                if (m.containsKey("mmo-type")) {
                    r.setMmoType(str(m, "mmo-type"));
                }
                break;
            case "RANDOM":
                if (m.containsKey("rolls")) {
                    r.setRolls(intv(m, "rolls"));
                }
                if (m.containsKey("replace")) {
                    r.setReplace(bool(m, "replace"));
                }
                List<RewardVO.PoolEntry> pool = new ArrayList<>();
                Object pl = m.get("pool");
                if (pl instanceof List) {
                    for (Object e : (List<?>) pl) {
                        if (!(e instanceof Map)) {
                            continue;
                        }
                        @SuppressWarnings("unchecked")
                        Map<String, Object> em = (Map<String, Object>) e;
                        RewardVO.PoolEntry pe = new RewardVO.PoolEntry();
                        pe.setWeight(dbl(em, "weight"));
                        Object rewObj = em.get("reward");
                        pe.setReward(rewObj instanceof Map ? fromRewardMap((Map<String, Object>) rewObj) : null);
                        pool.add(pe);
                    }
                }
                r.setPool(pool);
                break;
            default:
                break;
        }
        return r;
    }

    private ClaimEffectsVO fromClaimEffectsSection(ConfigurationSection sec) {
        if (sec == null) {
            return null;
        }
        ClaimEffectsVO fx = new ClaimEffectsVO();
        fx.setSound(sec.getString("sound", null));
        fx.setVolume(sec.getDouble("volume", 1.0));
        fx.setPitch(sec.getDouble("pitch", 1.0));
        fx.setParticle(sec.getString("particle", null));
        fx.setParticleCount(sec.getInt("particle-count", 16));
        fx.setBroadcast(sec.getBoolean("broadcast", false));
        return fx;
    }

    private static String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private static Double num(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) {
            return null;
        }
        if (v instanceof Number) {
            return ((Number) v).doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer intv(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) {
            return null;
        }
        if (v instanceof Number) {
            return ((Number) v).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Boolean bool(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) {
            return null;
        }
        if (v instanceof Boolean) {
            return (Boolean) v;
        }
        return Boolean.parseBoolean(String.valueOf(v));
    }

    private static double dbl(Map<String, Object> m, String key) {
        Double d = num(m, key);
        return d == null ? 0.0 : d;
    }

    private static List<String> strList(Map<String, Object> m, String key) {
        Object v = m.get(key);
        List<String> out = new ArrayList<>();
        if (v instanceof List) {
            for (Object o : (List<?>) v) {
                out.add(String.valueOf(o));
            }
        }
        return out;
    }

    // ================================================================
    // 内部：VO → YAML Map
    // ================================================================

    private void writePack(FileConfiguration cfg, String id, GiftPackSaveVO vo) {
        String base = "giftpacks." + id;
        cfg.set(base + ".display", vo.getDisplay() == null ? id : vo.getDisplay());
        cfg.set(base + ".description", vo.getDescription() == null ? "" : vo.getDescription());
        cfg.set(base + ".claim-mode", vo.getClaimMode() == null ? "ONCE" : vo.getClaimMode().toUpperCase());
        cfg.set(base + ".rarity", vo.getRarity() == null ? "COMMON" : vo.getRarity().toUpperCase());
        if ("COOLDOWN".equalsIgnoreCase(vo.getClaimMode()) && vo.getCooldownSeconds() > 0) {
            cfg.set(base + ".cooldown", vo.getCooldownSeconds());
        }

        // 解锁条件
        Object cond = toConditionObject(vo.getConditions());
        if (cond != null) {
            cfg.set(base + ".unlock-conditions", cond);
        } else {
            cfg.set(base + ".unlock-conditions", null);
        }

        // 奖励列表
        List<Map<String, Object>> rewards = toRewardList(vo.getRewards());
        cfg.set(base + ".rewards", rewards);

        // 领取特效
        Map<String, Object> fx = toClaimEffectsMap(vo.getClaimEffects());
        if (fx != null) {
            cfg.set(base + ".claim-effects", fx);
        } else {
            cfg.set(base + ".claim-effects", null);
        }
    }

    /**
     * 条件 VO → YAML 对象（Map 或 List）。
     * 与 GiftPack.fromConfig 的读取格式双向兼容。
     */
    private Object toConditionObject(ConditionNodeVO node) {
        if (node == null) {
            return null;
        }
        // 条件组
        boolean isGroup = node.getMode() != null || (node.getType() == null && node.getList() != null);
        if (isGroup) {
            Map<String, Object> group = new LinkedHashMap<>();
            group.put("mode", node.getMode() == null ? "AND" : node.getMode().toUpperCase());
            List<Object> kids = new ArrayList<>();
            if (node.getList() != null) {
                for (ConditionNodeVO child : node.getList()) {
                    Object c = toConditionObject(child);
                    if (c != null) {
                        kids.add(c);
                    }
                }
            }
            group.put("list", kids);
            return group;
        }
        // 叶子条件
        if (node.getType() == null) {
            return null;
        }
        Map<String, Object> leaf = new LinkedHashMap<>();
        leaf.put("type", node.getType().toUpperCase());
        String type = node.getType().toUpperCase();
        switch (type) {
            case "PLACEHOLDER":
                if (node.getPlaceholder() != null) leaf.put("placeholder", node.getPlaceholder());
                if (node.getOperator() != null) leaf.put("operator", node.getOperator());
                if (node.getValue() != null) leaf.put("value", node.getValue());
                break;
            case "DATE":
                if (node.getStart() != null) leaf.put("start", node.getStart());
                if (node.getEnd() != null) leaf.put("end", node.getEnd());
                break;
            case "STAT":
                if (node.getStat() != null) leaf.put("stat", node.getStat());
                if (node.getEntity() != null && !node.getEntity().isEmpty()) leaf.put("entity", node.getEntity());
                if (node.getMaterial() != null && !node.getMaterial().isEmpty()) leaf.put("material", node.getMaterial());
                if (node.getOperator() != null) leaf.put("operator", node.getOperator());
                if (node.getValue() != null) leaf.put("value", node.getValue());
                break;
            default:
                if (node.getValue() != null) leaf.put("value", node.getValue());
        }
        return leaf;
    }

    private List<Map<String, Object>> toRewardList(List<RewardVO> rewards) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (rewards == null) {
            return out;
        }
        for (RewardVO r : rewards) {
            Map<String, Object> m = toRewardMap(r);
            if (m != null) {
                out.add(m);
            }
        }
        return out;
    }

    private Map<String, Object> toRewardMap(RewardVO r) {
        if (r == null || r.getType() == null) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        String type = r.getType().toUpperCase();
        m.put("type", type);
        switch (type) {
            case "MONEY":
            case "POINTS":
            case "COMMAND":
                if (r.getValue() != null) m.put("value", r.getValue());
                if ("COMMAND".equals(type) && r.getConsole() != null) m.put("console", r.getConsole());
                break;
            case "EXP":
                if (r.getValue() != null) m.put("value", r.getValue());
                if (r.getLevels() != null) m.put("levels", r.getLevels());
                break;
            case "ITEM":
                if (r.getMaterial() != null) m.put("material", r.getMaterial());
                if (r.getAmount() != null) m.put("amount", r.getAmount());
                if (r.getName() != null) m.put("name", r.getName());
                if (r.getLore() != null) m.put("lore", r.getLore());
                if (r.getEnchant() != null) m.put("enchant", r.getEnchant());
                break;
            case "POTION":
                if (r.getEffect() != null) m.put("effect", r.getEffect());
                if (r.getDuration() != null) m.put("duration", r.getDuration());
                if (r.getAmplifier() != null) m.put("amplifier", r.getAmplifier());
                if (r.getParticles() != null) m.put("particles", r.getParticles());
                break;
            case "CUSTOM_ITEM":
                if (r.getPlugin() != null) m.put("plugin", r.getPlugin());
                if (r.getId() != null) m.put("id", r.getId());
                if (r.getAmount() != null) m.put("amount", r.getAmount());
                if (r.getMmoType() != null && !r.getMmoType().isEmpty()) m.put("mmo-type", r.getMmoType());
                break;
            case "RANDOM":
                if (r.getRolls() != null) m.put("rolls", r.getRolls());
                if (r.getReplace() != null) m.put("replace", r.getReplace());
                List<Map<String, Object>> pool = new ArrayList<>();
                if (r.getPool() != null) {
                    for (RewardVO.PoolEntry pe : r.getPool()) {
                        if (pe == null || pe.getReward() == null) continue;
                        Map<String, Object> entry = new LinkedHashMap<>();
                        entry.put("weight", pe.getWeight());
                        entry.put("reward", toRewardMap(pe.getReward()));
                        pool.add(entry);
                    }
                }
                m.put("pool", pool);
                break;
        }
        return m;
    }

    private Map<String, Object> toClaimEffectsMap(ClaimEffectsVO fx) {
        if (fx == null) {
            return null;
        }
        boolean hasAny = fx.getSound() != null || fx.getParticle() != null || fx.isBroadcast();
        if (!hasAny) {
            return null;
        }
        Map<String, Object> m = new HashMap<>();
        if (fx.getSound() != null) {
            m.put("sound", fx.getSound());
            m.put("volume", fx.getVolume());
            m.put("pitch", fx.getPitch());
        }
        if (fx.getParticle() != null) {
            m.put("particle", fx.getParticle());
            m.put("particle-count", fx.getParticleCount());
        }
        m.put("broadcast", fx.isBroadcast());
        return m;
    }

    // ================================================================
    // 内部：写盘 + 同步内存
    // ================================================================

    private AjaxResult saveAndReload(String msg) {
        try {
            plugin.getGiftPackConfig().save(plugin.getGiftPackFile());
        } catch (Exception e) {
            plugin.getLogger().warning("[ERP] 写回 giftpacks.yml 失败: " + e.getMessage());
            return AjaxResult.error("写回文件失败: " + e.getMessage());
        }
        // 重建内存 packs 必须收敛到主线程：GiftLoftManager.packs 是裸 LinkedHashMap，
        // 与玩家领取读路径（主线程）跨线程并发会产生竞态；磁盘 IO 已在上一步 worker 线程完成。
        Bukkit.getScheduler().runTask(plugin, manager::load);
        plugin.getLogger().info("[ERP] " + msg);
        return AjaxResult.success(msg);
    }

    private String validateId(String id) {
        if (id == null || id.trim().isEmpty()) {
            return null;
        }
        String s = id.trim().toLowerCase();
        if (!s.matches("^[a-z0-9][a-z0-9\\-_]*$")) {
            return null;
        }
        return s;
    }
}
