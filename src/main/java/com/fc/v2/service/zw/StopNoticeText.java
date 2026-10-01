package com.fc.v2.service.zw;

/**
 * 停水公告文案只从名录带出：本类只负责把名录模板里的占位符按单据内容填齐，
 * 名录里查不到对应事由时由服务层拒绝批准，不许另请人抄录、不许自编文案。
 *
 * <p>占位符三个：{@code {siteNo}} 点位代号、{@code {siteName}} 档案名称、{@code {altPlan}} 替代供水方案。
 *
 * @author fuce
 * @date 2026-10-01
 */
public final class StopNoticeText {

    /** 名录字典类型（t_sys_dict_data.dict_type） */
    public static final String DICT_TYPE = "zw_stop_notice_tpl";

    private StopNoticeText() {
    }

    /**
     * 用水源点与替代方案渲染名录模板；模板为空（名录里没有该事由）回 null，
     * 由调用方按「名录无此条」拒绝，绝不在这里造一句兜底文案。
     */
    public static String render(String template, String siteNo, String siteName, String altPlan) {
        if (template == null || template.trim().isEmpty()) {
            return null;
        }
        return template
                .replace("{siteNo}", nz(siteNo))
                .replace("{siteName}", nz(siteName))
                .replace("{altPlan}", nz(altPlan));
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
