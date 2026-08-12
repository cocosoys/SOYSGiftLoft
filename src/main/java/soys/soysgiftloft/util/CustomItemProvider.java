package soys.soysgiftloft.util;

import org.bukkit.inventory.ItemStack;

/**
 * 第三方自定义物品解析器（无编译期依赖）。
 * 通过运行时反射探测 ItemsAdder / Oraxen / MMOCore(MMOItems) 的类与方法，
 * 按配置中的 namespaced id 获取成品 ItemStack。任一插件未安装或版本不兼容时
 * 返回 null，由调用方决定降级处理（告警并跳过），不影响其余奖励发放。
 */
public class CustomItemProvider {

    /** 支持的自定义物品来源插件。 */
    public enum Plugin {
        ITEMSADDER, ORAXEN, MMOCORE, MMOITEMS
    }

    /**
     * 解析自定义物品。
     *
     * @param plugin  来源插件
     * @param id      物品 id（ItemsAdder/Oraxen 的 namespaced id；MMOItems 的物品 id）
     * @param mmoType MMOItems 的物品类型（如 SWORD），其它插件可传 null
     * @return 解析到的 ItemStack，失败返回 null
     */
    public static ItemStack resolve(Plugin plugin, String id, String mmoType) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        switch (plugin) {
            case ITEMSADDER:
                return itemsAdder(id);
            case ORAXEN:
                return oraxen(id);
            case MMOCORE:
            case MMOITEMS:
                return mmoItems(mmoType, id);
            default:
                return null;
        }
    }

    private static ItemStack itemsAdder(String id) {
        // 1) 旧版 CustomStack API：CustomStack.getInstance(id).getItemStack()
        try {
            Class<?> cs = Class.forName("fr.maxlego.itemsadder.api.CustomStack");
            Object inst = cs.getMethod("getInstance", String.class).invoke(null, id);
            if (inst != null) {
                return (ItemStack) inst.getClass().getMethod("getItemStack").invoke(inst);
            }
        } catch (Throwable ignored) {
            // 版本/类不匹配，尝试下一方案
        }
        // 2) 新版 ItemsAdderAPI：ItemsAdderAPI.getItemsManager().getItemStack(id)
        try {
            Class<?> api = Class.forName("fr.maxlego.itemsadder.api.ItemsAdderAPI");
            Object mgr = api.getMethod("getItemsManager").invoke(null);
            return (ItemStack) mgr.getClass().getMethod("getItemStack", String.class).invoke(mgr, id);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static ItemStack oraxen(String id) {
        try {
            Class<?> oi = Class.forName("io.th0rgal.oraxen.api.OraxenItems");
            return (ItemStack) oi.getMethod("getItemById", String.class).invoke(null, id);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static ItemStack mmoItems(String mmoType, String id) {
        try {
            Class<?> mmo = Class.forName("net.Indyuce.mmoitems.MMOItems");
            Object pluginInst = mmo.getField("plugin").get(null);
            if (pluginInst == null || mmoType == null || mmoType.isEmpty()) {
                return null;
            }
            Class<?> typeCls = Class.forName("net.Indyuce.mmoitems.util.MMOItemType");
            Object typeObj;
            try {
                typeObj = typeCls.getMethod("get", String.class).invoke(null, mmoType);
            } catch (Throwable t) {
                typeObj = typeCls.getMethod("valueOf", String.class).invoke(null, mmoType.toUpperCase());
            }
            if (typeObj == null) {
                return null;
            }
            return (ItemStack) mmo.getMethod("getItem", typeCls, String.class).invoke(pluginInst, typeObj, id);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
