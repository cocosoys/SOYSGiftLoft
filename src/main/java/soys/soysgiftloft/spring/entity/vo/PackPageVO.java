package soys.soysgiftloft.spring.entity.vo;

import java.util.List;

/**
 * 礼包列表分页 VO —— GET /api/plugins/SOYSGiftLoft/packs。
 * 字段序对齐 v1.8：{@code total} → {@code packs}。
 */
public class PackPageVO {

    private int total;
    private List<PackSummaryVO> packs;

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public List<PackSummaryVO> getPacks() {
        return packs;
    }

    public void setPacks(List<PackSummaryVO> packs) {
        this.packs = packs;
    }
}
