package com.fc.v2.service.zw;

/**
 * 停水事由三选一枚举——业务科提单只收这三个名目，多写少写、写错代号一律当场挡回。
 *
 * @author fuce
 * @date 2026-10-01
 */
public enum StopReason {

    /** 水源枯竭 */
    DEPLETION("水源枯竭"),
    /** 指标连续超标 */
    EXCEED("指标连续超标"),
    /** 水厂检修 */
    REPAIR("检修");

    private final String label;

    StopReason(String label) {
        this.label = label;
    }

    public String code() {
        return name();
    }

    public String label() {
        return label;
    }

    /** 只认代号：名册外的代号（含空串、错别字）一律 null，提单/核实两关同此一把尺 */
    public static StopReason fromCode(String code) {
        if (code == null) {
            return null;
        }
        String trimmed = code.trim();
        for (StopReason r : values()) {
            if (r.name().equals(trimmed)) {
                return r;
            }
        }
        return null;
    }
}
