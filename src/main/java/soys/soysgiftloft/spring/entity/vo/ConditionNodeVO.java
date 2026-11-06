package soys.soysgiftloft.spring.entity.vo;

import java.util.List;

/**
 * 解锁条件树节点 VO —— 胖节点模式，同时支持叶子条件与条件组。
 *
 * <p>判定规则：</p>
 * <ul>
 *   <li>含 {@code mode} 或含 {@code list}（无 type）→ 条件组（AND/OR 嵌套）</li>
 *   <li>含 {@code type} → 叶子条件，按 type 取用对应字段</li>
 * </ul>
 *
 * <p>叶子 type 与字段对应关系：</p>
 * <ul>
 *   <li>PLAYTIME / POINTS / MONEY / ONLINE_DAYS / JOIN_DAYS / PERMGROUP / WORLD / GAMEMODE：value</li>
 *   <li>PERMISSION：value（权限节点）</li>
 *   <li>PLACEHOLDER：placeholder / operator / value</li>
 *   <li>DATE：start / end（yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss）</li>
 *   <li>STAT：stat / entity / material / operator / value</li>
 * </ul>
 */
public class ConditionNodeVO {

    /** 条件组模式：AND / OR；为 null 且含 list 时默认 AND。 */
    private String mode;

    /** 条件组子节点列表。 */
    private List<ConditionNodeVO> list;

    /** 叶子条件类型。 */
    private String type;

    // —— 叶子通用字段 ——
    private String value;

    // —— PLACEHOLDER ——
    private String placeholder;
    private String operator;

    // —— DATE ——
    private String start;
    private String end;

    // —— STAT ——
    private String stat;
    private String entity;
    private String material;

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public List<ConditionNodeVO> getList() { return list; }
    public void setList(List<ConditionNodeVO> list) { this.list = list; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    public String getPlaceholder() { return placeholder; }
    public void setPlaceholder(String placeholder) { this.placeholder = placeholder; }

    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }

    public String getStart() { return start; }
    public void setStart(String start) { this.start = start; }

    public String getEnd() { return end; }
    public void setEnd(String end) { this.end = end; }

    public String getStat() { return stat; }
    public void setStat(String stat) { this.stat = stat; }

    public String getEntity() { return entity; }
    public void setEntity(String entity) { this.entity = entity; }

    public String getMaterial() { return material; }
    public void setMaterial(String material) { this.material = material; }
}
