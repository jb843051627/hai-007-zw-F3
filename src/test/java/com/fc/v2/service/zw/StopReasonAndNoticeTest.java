package com.fc.v2.service.zw;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * 停水事由只认代号、公告文案只渲染名录模板的用例。
 */
public class StopReasonAndNoticeTest {

    @Test
    public void reasonAcceptsOnlyCodes() {
        assertEquals(StopReason.DEPLETION, StopReason.fromCode("DEPLETION"));
        assertEquals(StopReason.EXCEED, StopReason.fromCode(" EXCEED "));
        assertEquals(StopReason.REPAIR, StopReason.fromCode("REPAIR"));
        // 名字、错别字、空一律不认——核实只认代号不认名字
        assertNull(StopReason.fromCode("水源枯竭"));
        assertNull(StopReason.fromCode("exceed"));
        assertNull(StopReason.fromCode(""));
        assertNull(StopReason.fromCode(null));
    }

    @Test
    public void noticeRendersDirectoryTemplate() {
        String tpl = "水源点【{siteNo}】{siteName}停水，方案：{altPlan}。";
        assertEquals("水源点【ZS00】城北水源地停水，方案：邻厂联网。",
                StopNoticeText.render(tpl, "ZS00", "城北水源地", "邻厂联网"));
        // 名录里没有该事由（模板空）→ null，由服务层拒绝，绝不自编
        assertNull(StopNoticeText.render("  ", "ZS00", "城北", "方案"));
        assertNull(StopNoticeText.render(null, "ZS00", "城北", "方案"));
    }
}
