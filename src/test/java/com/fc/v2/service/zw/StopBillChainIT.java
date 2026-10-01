package com.fc.v2.service.zw;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import javax.annotation.Resource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TSysDictDataMapper;
import com.fc.v2.mapper.auto.TZwCaseFlowMapper;
import com.fc.v2.mapper.auto.TZwImpRowMapper;
import com.fc.v2.mapper.auto.TZwSamPlanMapper;
import com.fc.v2.mapper.auto.TZwSamTaskMapper;
import com.fc.v2.mapper.auto.TZwSourceMapper;
import com.fc.v2.mapper.auto.TZwStopBillMapper;
import com.fc.v2.mapper.auto.TZwStopSignMapper;
import com.fc.v2.model.auto.TSysDictData;
import com.fc.v2.model.auto.TZwCaseFlow;
import com.fc.v2.model.auto.TZwImpRow;
import com.fc.v2.model.auto.TZwSamPlan;
import com.fc.v2.model.auto.TZwSource;
import com.fc.v2.model.auto.TZwStopBill;
import com.fc.v2.model.auto.TZwStopSign;
import com.fc.v2.service.ITZwSamTaskService;
import com.fc.v2.service.ITZwStopBillService;

/**
 * 三道关口全链路：H2 内存库 + 真实 Mapper，验落库后的关口次序、两笔不并、
 * 整轮作废、终止锁死、退役回写与派单停发。
 */
@SpringJUnitConfig(classes = H2Db.class)
public class StopBillChainIT {

    private static final String SITE = "ZS00";
    private static final String TPL = "因{stop}，水源点【{siteNo}】{siteName}停水，替代方案：{altPlan}。";

    @Resource
    private ITZwStopBillService service;
    @Resource
    private ITZwSamTaskService samTaskService;
    @Resource
    private TZwSourceMapper sourceMapper;
    @Resource
    private TZwStopBillMapper billMapper;
    @Resource
    private TZwStopSignMapper signMapper;
    @Resource
    private TZwCaseFlowMapper caseMapper;
    @Resource
    private TZwImpRowMapper impRowMapper;
    @Resource
    private TSysDictDataMapper dictMapper;
    @Resource
    private TZwSamPlanMapper planMapper;
    @Resource
    private TZwSamTaskMapper taskMapper;

    private static Date ts(String s) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(s);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @BeforeEach
    public void seed() {
        // 上下文跨用例复用同一个内存库：每例先清账，再播种
        signMapper.delete(null);
        billMapper.delete(null);
        caseMapper.delete(null);
        impRowMapper.delete(null);
        dictMapper.delete(null);
        taskMapper.delete(null);
        planMapper.delete(null);
        sourceMapper.delete(null);

        TZwSource s = new TZwSource();
        s.setSiteNo(SITE);
        s.setSiteName("青坪市城北水源地");
        s.setSiteType("水源地");
        s.setStatus(0);
        s.setDelFlag(0);
        sourceMapper.insert(s);

        for (String code : new String[]{"DEPLETION", "EXCEED", "REPAIR"}) {
            TSysDictData d = new TSysDictData();
            d.setDictType(StopNoticeText.DICT_TYPE);
            d.setDictValue(code);
            d.setDictLabel(code);
            d.setStatus("0");
            d.setRemark(TPL.replace("{stop}", code));
            dictMapper.insert(d);
        }
    }

    private StopActionResult submitRepair() {
        return service.submit(SITE, "REPAIR", "邻厂联网降压供水", "yuanke-zhang", ts("2026-10-01 09:00:00"));
    }

    private Long happyToDispatch(Date at) {
        StopActionResult submit = submitRepair();
        assertTrue(submit.getOutcome() == StopActionResult.Outcome.ADVANCED, submit.getMessage());
        Long id = billOf(submit);
        StopActionResult verify = service.verify(id, "shui-wu", "REPAIR", true, "检修事由属实", at);
        assertEquals(StopActionResult.Outcome.ADVANCED, verify.getOutcome(), verify.getMessage());
        StopActionResult qty = service.evaluate(id, "DISP_QTY", "diao-li", true, "水量够", at);
        assertEquals(StopActionResult.Outcome.ADVANCED, qty.getOutcome(), qty.getMessage());
        StopActionResult net = service.evaluate(id, "DISP_NET", "diao-wang", true, "管网可切", at);
        assertEquals(StopActionResult.Outcome.ADVANCED, net.getOutcome(), net.getMessage());
        return id;
    }

    private Long billOf(StopActionResult r) {
        // 通过台账按单号反取，避免给结论对象塞单据 id
        List<TZwStopBill> list = billMapper.selectList(new QueryWrapper<TZwStopBill>().eq("site_no", SITE));
        return list.get(list.size() - 1).getId();
    }

    @Test
    public void happyPathApprovesRetiresAndBringsNotice() {
        Date at = ts("2026-10-01 10:00:00");
        Long id = happyToDispatch(at);

        ChainView before = service.chainView(id);
        assertEquals(SignTally.NODE_HEAD, before.getCurrentNode());
        assertEquals(2, before.getSignedCount(SignTally.NODE_DISPATCH));
        assertTrue(before.isFilled(SignTally.NODE_DISPATCH));

        StopActionResult approved = service.approve(id, "diao-zhuren", "准予", at);
        assertEquals(StopActionResult.Outcome.ADVANCED, approved.getOutcome(), approved.getMessage());

        TZwBillProbe probe = reload(id);
        assertEquals(1, probe.bill.getStatus().intValue());
        assertEquals(SignTally.NODE_HEAD, probe.bill.getNodeNo().intValue());
        assertNotNull(probe.bill.getApprovedTime());
        // 公告从名录带出，含代号与替代方案，不是人手抄的
        assertTrue(probe.bill.getNoticeText().contains(SITE));
        assertTrue(probe.bill.getNoticeText().contains("邻厂联网降压供水"));
        // 底档翻成退役
        TZwSource src = sourceMapper.selectList(new QueryWrapper<TZwSource>().eq("site_no", SITE)).get(0);
        assertEquals(1, src.getStatus().intValue());
        // 两口两笔：即使同分钟也是两条记录，不并成一条
        long dispSigns = signMapper.selectCount(new QueryWrapper<TZwStopSign>()
                .eq("bill_id", id).eq("node_no", SignTally.NODE_DISPATCH).eq("valid_flag", 1));
        assertEquals(2L, dispSigns);
        // 落定后谁也不能再追记
        StopActionResult late = service.evaluate(id, "DISP_NET", "late-guy", true, "补签", at);
        assertEquals(StopActionResult.Outcome.REJECTED, late.getOutcome());
        assertNull(service.approve(id, "diao-zhuren", "再批")); // 老签法对新单不接
    }

    @Test
    public void submitRejectsMissingElementsAndBadReason() {
        Date at = ts("2026-10-01 09:00:00");
        assertEquals(StopActionResult.Outcome.REJECTED,
                service.submit(SITE, "REPAIR", null, "yw", at).getOutcome());
        assertEquals(StopActionResult.Outcome.REJECTED,
                service.submit(SITE, "水源枯竭", "方案", "yw", at).getOutcome());
        assertEquals(StopActionResult.Outcome.REJECTED,
                service.submit("NO-SUCH", "REPAIR", "方案", "yw", at).getOutcome());
    }

    @Test
    public void secondApplicationSameYearIsCalledOut() {
        Date at = ts("2026-10-01 09:00:00");
        StopActionResult first = submitRepair();
        assertFalse(first.getView().isSecondOrLaterThisYear());
        StopActionResult second = service.submit(SITE, "DEPLETION", "限压供水", "yuanke-zhang",
                ts("2026-11-01 09:00:00"));
        assertTrue(second.getOutcome() == StopActionResult.Outcome.ADVANCED);
        assertTrue(second.getView().isSecondOrLaterThisYear());
        assertTrue(second.getMessage().contains("第 2 份"));
    }

    @Test
    public void openCaseFlowBlocksVerify() {
        Date at = ts("2026-10-01 09:30:00");
        Long id = billOf(submitRepair());
        TZwCaseFlow open = new TZwCaseFlow();
        open.setBizNo("CASE-1");
        open.setSiteNo(SITE);
        open.setStage(1);
        open.setStatus(1); // 在办，未完结
        open.setDelFlag(0);
        caseMapper.insert(open);

        StopActionResult r = service.verify(id, "shui-wu", "REPAIR", true, "ok", at);
        assertEquals(StopActionResult.Outcome.BLOCKED, r.getOutcome());
        // 压住：单子仍在水质科、仍在核，处置单封卷后可再办
        TZwBillProbe probe = reload(id);
        assertEquals(SignTally.NODE_WATER_QA, probe.bill.getNodeNo().intValue());
        assertEquals(0, probe.bill.getStatus().intValue());
    }

    @Test
    public void exceedReasonCheckedAgainstRecentRows() {
        Date at = ts("2026-10-01 09:30:00");
        StopActionResult submit = service.submit(SITE, "EXCEED", "应急水车", "yw", at);
        Long id = billOf(submit);

        // 理由代号写错就算没过（在数据对照之前先拦）
        assertEquals(StopActionResult.Outcome.REJECTED,
                service.verify(id, "shui-wu", "REPAIR", true, "", at).getOutcome());

        // 近月无数据：压住，不硬过也不硬否
        assertEquals(StopActionResult.Outcome.BLOCKED,
                service.verify(id, "shui-wu", "EXCEED", true, "", at).getOutcome());

        addRow("2026-09-20 08:00:00", 3); // 超标
        // 仅 1 条超标：对不上「连续」，拒
        assertEquals(StopActionResult.Outcome.REJECTED,
                service.verify(id, "shui-wu", "EXCEED", true, "", at).getOutcome());

        addRow("2026-09-25 08:00:00", 4); // 严重超标
        StopActionResult pass = service.verify(id, "shui-wu", "EXCEED", true, "连续超标属实", at);
        assertEquals(StopActionResult.Outcome.ADVANCED, pass.getOutcome(), pass.getMessage());
    }

    @Test
    public void dispatchStaysUntilTwoDifferentPeopleAndBansProxy() {
        Date at = ts("2026-10-01 10:00:00");
        Long id = billOf(submitRepair());
        service.verify(id, "shui-wu", "REPAIR", true, "", at);

        // 只到一位：仍停调度评估，到不了主管
        StopActionResult one = service.evaluate(id, "DISP_QTY", "diao-li", true, "", at);
        assertEquals(SignTally.NODE_DISPATCH, service.chainView(id).getCurrentNode());

        // 同一人想把另一口也签了：兼签/替签不受理
        StopActionResult sameGuy = service.evaluate(id, "DISP_NET", "diao-li", true, "", at);
        assertEquals(StopActionResult.Outcome.REJECTED, sameGuy.getOutcome());
        // 同口重签不受理
        assertEquals(StopActionResult.Outcome.REJECTED,
                service.evaluate(id, "DISP_QTY", "diao-li", true, "", at).getOutcome());

        StopActionResult two = service.evaluate(id, "DISP_NET", "diao-wang", true, "", at);
        assertEquals(SignTally.NODE_HEAD, service.chainView(id).getCurrentNode());
        assertTrue(two.getMessage().contains("点齐"));
    }

    @Test
    public void cannotSkipGate() {
        Date at = ts("2026-10-01 10:00:00");
        Long id = billOf(submitRepair());
        // 还停在水质科，谁也不能替它跳到调度评估或主管
        assertEquals(StopActionResult.Outcome.REJECTED,
                service.evaluate(id, "DISP_QTY", "diao-li", true, "", at).getOutcome());
        assertEquals(StopActionResult.Outcome.REJECTED,
                service.approve(id, "zhuren", "", at).getOutcome());
    }

    @Test
    public void headRejectVoidsRoundAndRestartsAtWaterQa() {
        Date at = ts("2026-10-01 10:00:00");
        Long id = happyToDispatch(at);
        StopActionResult back = service.reject(id, "diao-zhuren", "理由与数据不符", at);
        assertEquals(StopActionResult.Outcome.ADVANCED, back.getOutcome(), back.getMessage());

        ChainView v = service.chainView(id);
        assertEquals(2, v.getRoundNo());
        assertEquals(SignTally.NODE_WATER_QA, v.getCurrentNode());
        // 新轮一枚都还没有，旧轮同意签全部作废
        assertEquals(0, v.getSignedCount(SignTally.NODE_DISPATCH));
        long voided = signMapper.selectCount(new QueryWrapper<TZwStopSign>()
                .eq("bill_id", id).eq("valid_flag", 0).eq("action", SignTally.ACTION_AGREE));
        assertTrue(voided >= 4); // 业务提+水质+两口 至少四笔
        // 打回那笔单独记动作 3，留在旧轮
        long rejectMarks = signMapper.selectCount(new QueryWrapper<TZwStopSign>()
                .eq("bill_id", id).eq("action", SignTally.ACTION_HEAD_REJECT));
        assertEquals(1L, rejectMarks);

        // 重新从第二道攒起
        StopActionResult reVerify = service.verify(id, "shui-wu-2", "REPAIR", true, "重新核实", at);
        assertEquals(StopActionResult.Outcome.ADVANCED, reVerify.getOutcome());
        assertEquals(1, service.chainView(id).getSignedCount(SignTally.NODE_WATER_QA));
    }

    @Test
    public void disagreeAndWithdrawTerminateAndLock() {
        Date at = ts("2026-10-01 09:30:00");
        Long id = billOf(submitRepair());
        StopActionResult no = service.verify(id, "shui-wu", "REPAIR", false, "数据对不上", at);
        assertEquals(StopActionResult.Outcome.VETOED, no.getOutcome());
        assertEquals(3, reload(id).bill.getStatus().intValue());
        // 锁死：任何关口都不再受理
        assertEquals(StopActionResult.Outcome.REJECTED,
                service.verify(id, "shui-wu", "REPAIR", true, "", at).getOutcome());
        assertEquals(StopActionResult.Outcome.REJECTED,
                service.withdraw(id, "yw", "想撤", at).getOutcome());

        // 另一份：在调度评估时撤回
        Date at2 = ts("2026-10-02 09:00:00");
        Long id2 = billOf(service.submit(SITE, "REPAIR", "方案二", "yw2", at2));
        service.verify(id2, "shui-wu", "REPAIR", true, "", at2);
        service.evaluate(id2, "DISP_QTY", "diao-li", true, "", at2);
        StopActionResult wd = service.withdraw(id2, "yw2", "情况变化撤回", at2);
        assertEquals(StopActionResult.Outcome.VETOED, wd.getOutcome());
        assertEquals(3, reload(id2).bill.getStatus().intValue());
        assertEquals(StopActionResult.Outcome.REJECTED,
                service.evaluate(id2, "DISP_NET", "diao-wang", true, "", at2).getOutcome());
    }

    @Test
    public void oldChainMethodsStillServeOldBillsOnly() {
        // 手工造一张老签法单（round_no 为空，老口径 node 0..2）
        TZwStopBill old = new TZwStopBill();
        old.setBillNo("OLD-1");
        old.setNodeNo(0);
        old.setSignMode(1);
        old.setNeedCount(2);
        old.setSignCount(0);
        old.setStatus(0);
        old.setDelFlag(0);
        billMapper.insert(old);

        TZwStopBill after1 = service.approve(old.getId(), "u", "c"); // 第一笔，未点齐留本口
        assertNotNull(after1);
        assertEquals(0, after1.getNodeNo().intValue());
        assertEquals(1, after1.getSignCount().intValue());
        TZwStopBill after2 = service.approve(old.getId(), "v", "c"); // 点齐进下一道
        assertEquals(1, after2.getNodeNo().intValue());

        // 新签法单不接老方法
        Long newId = billOf(submitRepair());
        assertNull(service.approve(newId, "u", "c"));
        assertNull(service.reject(newId, "u", "c"));
        assertNull(service.rollback(newId, "c"));
    }

    @Test
    public void retiredSiteStopsGeneratingSamplingTasks() {
        Date at = ts("2026-10-01 08:00:00");
        TZwSamPlan plan = new TZwSamPlan();
        plan.setPlanNo("PL-ZS00-M");
        plan.setSiteNo(SITE);
        plan.setPeriodDays(30);
        plan.setAmount(new BigDecimal("3"));
        plan.setFirstDue(new java.sql.Date(ts("2026-09-20 00:00:00").getTime()));
        plan.setEffStart(ts("2026-09-01 00:00:00"));
        plan.setContent("月度例行");
        plan.setStatus(0);
        plan.setDelFlag(0);
        planMapper.insert(plan);

        int before = service_countTasks();
        int gen = samTaskService.generate(at);
        assertTrue(gen >= 1, "退役前应能生成事项条");

        Long id = happyToDispatch(at);
        service.approve(id, "diao-zhuren", "准", at);
        int genAfter = samTaskService.generate(ts("2026-12-31 08:00:00"));
        assertEquals(0, genAfter, "退役后不再生成新采样派单");
        // 旧事项条仍在账上可查
        assertTrue(service_countTasks() > before);
    }

    private int service_countTasks() {
        return taskMapper.selectList(new QueryWrapper<com.fc.v2.model.auto.TZwSamTask>()
                .eq("site_no", SITE)).size();
    }

    @Test
    public void chainViewCountsAndTrailAreSingleSource() {
        Date at = ts("2026-10-01 10:00:00");
        Long id = happyToDispatch(at);
        ChainView v = service.chainView(id);
        // 各关枚数全部来自签核记录，单据上没有 sign_count 之类代填列
        assertEquals(1, v.getSignedCount(0));
        assertEquals(1, v.getSignedCount(1));
        assertEquals(2, v.getSignedCount(2));
        assertEquals(0, v.getSignedCount(3));
        // 轨迹从后道往前道排，首条即最新一道
        List<TZwStopSign> trail = v.getTrail();
        assertEquals(SignTally.NODE_DISPATCH, trail.get(0).getNodeNo().intValue());
        assertEquals(4, trail.size()); // 提单+水质+两口
    }

    private TZwBillProbe reload(Long id) {
        return new TZwBillProbe(billMapper.selectById(id));
    }

    private void addRow(String day, int grade) {
        TZwImpRow row = new TZwImpRow();
        row.setBatchNo("B-" + day);
        row.setSiteNo(SITE);
        row.setItemCode("CL");
        row.setQty(new BigDecimal("0.55"));
        row.setSampleType("水源水");
        row.setTestAt(ts(day));
        row.setGradeLevel(grade);
        row.setStatus(1);
        row.setDelFlag(0);
        impRowMapper.insert(row);
    }

    private static final class TZwBillProbe {
        final TZwStopBill bill;

        TZwBillProbe(TZwStopBill bill) {
            this.bill = bill;
        }
    }
}
