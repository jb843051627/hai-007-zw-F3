package com.fc.v2.service.impl;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.mapper.auto.TSysDictDataMapper;
import com.fc.v2.mapper.auto.TZwCaseFlowMapper;
import com.fc.v2.mapper.auto.TZwImpRowMapper;
import com.fc.v2.mapper.auto.TZwSourceMapper;
import com.fc.v2.mapper.auto.TZwStopBillMapper;
import com.fc.v2.mapper.auto.TZwStopSignMapper;
import com.fc.v2.model.auto.TSysDictData;
import com.fc.v2.model.auto.TZwCaseFlow;
import com.fc.v2.model.auto.TZwImpRow;
import com.fc.v2.model.auto.TZwSource;
import com.fc.v2.model.auto.TZwStopBill;
import com.fc.v2.model.auto.TZwStopSign;
import com.fc.v2.service.ITZwStopBillService;
import com.fc.v2.service.zw.ChainView;
import com.fc.v2.service.zw.SignTally;
import com.fc.v2.service.zw.StopActionResult;
import com.fc.v2.service.zw.StopNoticeText;
import com.fc.v2.service.zw.StopReason;

/**
 * 停供/限供申请签核单 Service业务层处理。
 *
 * <p><b>两套签法同表并存</b>：{@link #approve(Long, String, String)} 一组老方法入参返回照旧，
 * 只认老签法单（round_no 为空），新签法单（round_no 非空）一律不接，防止两套账互相串。
 * 新签法 {@link #submit}/{@link #verify}/{@link #evaluate}/{@link #approve(Long, String, String, Date)}/
 * {@link #reject(Long, String, String, Date)}/{@link #withdraw} 是同名另立的一组：
 * <ul>
 *   <li>每笔落名单独一行（{@link TZwStopSign}），两人同分钟签完也是两笔，不并一笔；</li>
 *   <li>各关过没过、落名几枚只由 {@link SignTally} 顺着<b>本轮有效同意</b>记录点算，
 *       单据上没有可代填的计数格子，屏上数与库内数同出一次计数；</li>
 *   <li>推进只认关口次序，条件更新兜底并发，越级/落定后追记 0 行回滚；</li>
 *   <li>主管打回整轮作废（旧行 valid_flag=0，不与补签同列），轮次 +1，回第二道重攒；</li>
 *   <li>任一关写明不同意、或申请撤回，报批情形落「已终止」，各栏锁死，要改只能另开新单。</li>
 * </ul>
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TZwStopBillServiceImpl implements ITZwStopBillService {

    // ---- 老签法常量（不动） ----
    private static final int MAX_NODE = 2;
    private static final int MODE_OR = 0;
    private static final int MODE_AND = 1;

    // ---- 报批情形 ----
    private static final int STATUS_RUNNING = 0;
    private static final int STATUS_PASS = 1;
    /** 老签法「已打回」（否决后须走退回环节） */
    private static final int STATUS_VETO_OLD = 2;
    /** 新签法「已终止」：写明不同意 / 撤回，落锤锁死 */
    private static final int STATUS_TERMINATED = 3;

    /** 底档 0在用 1已退役；处置工单 0未起 1在办 2已封存；检测行 1已入库；水质档 3超标 4严重超标 */
    private static final int SOURCE_IN_USE = 0;
    private static final int SOURCE_RETIRED = 1;
    private static final int CASE_SEALED = 2;
    private static final int ROW_OK = 1;
    private static final int GRADE_EXCEED = 3;

    /** 近月对照窗口（自然月） */
    private static final int RECENT_MONTHS = 1;
    /** 「指标连续超标」近月至少要有这么多条超标（含严重）入库读数才对得上申请理由 */
    private static final int MIN_EXCEED_ROWS = 2;

    @javax.annotation.Resource
    private TZwStopBillMapper zwStopBillMapper;
    @javax.annotation.Resource
    private TZwStopSignMapper zwStopSignMapper;
    @javax.annotation.Resource
    private TZwSourceMapper zwSourceMapper;
    @javax.annotation.Resource
    private TZwCaseFlowMapper zwCaseFlowMapper;
    @javax.annotation.Resource
    private TZwImpRowMapper zwImpRowMapper;
    @javax.annotation.Resource
    private TSysDictDataMapper sysDictDataMapper;

    @Override
    public TZwStopBill selectTZwStopBillById(Long id) {
        return this.zwStopBillMapper.selectById(id);
    }

    @Override
    public List<TZwStopBill> selectTZwStopBillList(
            com.baomidou.mybatisplus.core.conditions.Wrapper<TZwStopBill> queryWrapper) {
        return this.zwStopBillMapper.selectList(queryWrapper);
    }

    // ==================================================================
    // 老签法：入参与返回照旧；只接老单（round_no 为空），新单一律不串进来
    // ==================================================================

    /**
     * 签批一票。
     * 任一人制：一票即进下一道；两名点齐制：本口落印数 +1，未点齐留在本口继续在核，点齐才进道。
     * 推进到调度准（最后一道）即落"已核讫"。非"在核"单、越道签批一律被拒。
     */
    @Override
    public TZwStopBill approve(Long id, String approver, String comment) {
        TZwStopBill r = this.zwStopBillMapper.selectById(id);
        if (r == null || approver == null || approver.trim().isEmpty()) {
            return null;
        }
        if (isNewChain(r)) {
            return null;
        }
        // 只收在核的票：已核讫的不再签，已打回的须先退回环节
        if (r.getStatus() == null || r.getStatus() != STATUS_RUNNING) {
            return null;
        }
        int node = r.getNodeNo() == null ? 0 : r.getNodeNo();
        if (node >= MAX_NODE) {
            return null;
        }
        int mode = r.getSignMode() == null ? MODE_OR : r.getSignMode();
        int signed = r.getSignCount() == null ? 0 : r.getSignCount();
        int need = r.getNeedCount() == null ? 1 : Math.max(1, r.getNeedCount().intValue());

        int nextNode;
        int nextSigned;
        int nextStatus;
        if (mode == MODE_AND && signed + 1 < need) {
            // 两名未点齐：留在本口，继续在核
            nextNode = node;
            nextSigned = signed + 1;
            nextStatus = STATUS_RUNNING;
        } else {
            // 本口点齐（或任一人制）：印数清零后进下一道；到调度准即核讫
            nextNode = node + 1;
            nextSigned = 0;
            nextStatus = nextNode >= MAX_NODE ? STATUS_PASS : STATUS_RUNNING;
        }
        int rows = this.zwStopBillMapper.update(null, new UpdateWrapper<TZwStopBill>()
                .set("node_no", nextNode)
                .set("sign_count", nextSigned)
                // 道次、落印数、报批情形三样一次钉齐，缺一样都不算成
                .set("status", nextStatus)
                .set("remark", appendTrace(r.getRemark(),
                        "同意 " + ts() + " " + approver + " " + safe(comment)))
                .eq("id", id)
                .eq("node_no", node)
                .eq("status", STATUS_RUNNING)
                .eq("del_flag", 0));
        return rows > 0 ? this.zwStopBillMapper.selectById(id) : null;
    }

    /**
     * 否决：落"已打回"，单停在被否的那一道留痕；之后须走退回环节改回在核才能再签。
     * 非"在核"单（含已打回）重复否决一律被拒。
     */
    @Override
    public TZwStopBill reject(Long id, String approver, String comment) {
        TZwStopBill r = this.zwStopBillMapper.selectById(id);
        if (r == null || approver == null || approver.trim().isEmpty()) {
            return null;
        }
        if (isNewChain(r)) {
            return null;
        }
        if (r.getStatus() == null || r.getStatus() != STATUS_RUNNING) {
            return null;
        }
        int rows = this.zwStopBillMapper.update(null, new UpdateWrapper<TZwStopBill>()
                // 否决必须把报批情形落成"已打回"——不许只留痕不落状态，让单子还挂在核里被重复签
                .set("status", STATUS_VETO_OLD)
                .set("remark", appendTrace(r.getRemark(),
                        "打回 " + ts() + " " + approver + " " + safe(comment)))
                .eq("id", id)
                .eq("status", STATUS_RUNNING)
                .eq("del_flag", 0));
        return rows > 0 ? this.zwStopBillMapper.selectById(id) : null;
    }

    /**
     * 退回上一环节：道次退一格、本口落印数清零、报批情形一并改回"在核"——
     * 不许只退道次还留着"已核讫/已打回"，造成两边口径对不上。第一道无可退，被拒。
     */
    @Override
    public TZwStopBill rollback(Long id, String comment) {
        TZwStopBill r = this.zwStopBillMapper.selectById(id);
        if (r == null) {
            return null;
        }
        if (isNewChain(r)) {
            return null;
        }
        int node = r.getNodeNo() == null ? 0 : r.getNodeNo();
        if (node <= 0) {
            return null;
        }
        int rows = this.zwStopBillMapper.update(null, new UpdateWrapper<TZwStopBill>()
                .set("node_no", node - 1)
                .set("sign_count", 0)
                // 退回与改回在核同一条更新，超时重试/回滚后两边看到的都是"上一道在核"
                .set("status", STATUS_RUNNING)
                .set("remark", appendTrace(r.getRemark(), "退回 " + ts() + " " + safe(comment)))
                .eq("id", id)
                .eq("node_no", node)
                .eq("del_flag", 0));
        return rows > 0 ? this.zwStopBillMapper.selectById(id) : null;
    }

    private static boolean isNewChain(TZwStopBill r) {
        return r.getRoundNo() != null;
    }

    // ==================================================================
    // 新签法：三道关口（业务提 / 水质核 / 调度评两口 / 主管准）
    // ==================================================================

    /**
     * 第一道 业务科提单。三要素（点位代号、停水事由、替代供水方案）缺一当场挡回；
     * 只认代号——底档里按代号查不到、或点位已退役的，不受理。
     * 同一水源点当年第二份不拦截，单号当年序顺排并在结论里当场点名。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public StopActionResult submit(String siteNo, String stopReason, String altPlan,
                                   String applicant, Date at) {
        Date now = at == null ? new Date() : at;
        String site = trim(siteNo);
        String reason = trim(stopReason);
        String plan = trim(altPlan);
        String who = trim(applicant);
        // 三样缺一当场挡回——没有「先收下再补」的口子
        if (site.isEmpty() || plan.isEmpty() || who.isEmpty()) {
            return StopActionResult.rejected("提单被挡回：水源点代号、替代供水方案、提单人缺一不可");
        }
        StopReason reasonEnum = StopReason.fromCode(reason);
        if (reasonEnum == null) {
            return StopActionResult.rejected(
                    "提单被挡回：停水事由只认 DEPLETION(水源枯竭)/EXCEED(指标连续超标)/REPAIR(检修) 三个代号");
        }
        TZwSource source = findActiveSource(site);
        if (source == null) {
            return StopActionResult.rejected("提单被挡回：代号「" + site + "」在底档中查不到，或点位已退役——核实只认代号");
        }

        int year = yearOf(now);
        int seq = nextYearSeq(site, year);
        TZwStopBill bill = new TZwStopBill();
        bill.setBillNo(site + "-" + year + "-" + String.format("%02d", seq));
        bill.setNodeNo(SignTally.NODE_WATER_QA);
        bill.setRoundNo(1);
        bill.setSiteNo(site);
        bill.setStopReason(reasonEnum.code());
        bill.setAltPlan(plan);
        bill.setApplyYear(year);
        bill.setYearSeq(seq);
        bill.setStatus(STATUS_RUNNING);
        bill.setDelFlag(0);
        bill.setCreateBy(who);
        bill.setCreateTime(now);
        try {
            this.zwStopBillMapper.insert(bill);
        } catch (org.springframework.dao.DataIntegrityViolationException dup) {
            // 并发同年序撞号（uk_stop_site_year_seq）：一笔都不留
            return StopActionResult.rejected("该水源点 " + year + " 年度申请单编号冲突，请重新提单");
        }
        // 提单即落业务科自己的名——第一关同样有签核记录，倒查时从它数起
        insertSign(bill.getId(), 1, SignTally.NODE_APPLY, SignTally.ROLE_APPLY,
                who, SignTally.ACTION_AGREE, "提单：" + reasonEnum.label() + "；替代方案：" + plan, now);
        String msg = "提单已受理（单号 " + bill.getBillNo() + "），已转水质科核实";
        if (seq >= 2) {
            msg += "；注意：这是该水源点当年第 " + seq + " 份申请，当场点名";
        }
        return StopActionResult.advanced(chainView(bill.getId()), msg);
    }

    /**
     * 第二道 水质科核实。
     * 先做底档前置检查：名下还有未完结处置单 → 压在本道（不进不退，也不算否决）；
     * 再拿近月检测数据与申请对照——只认点位代号；核实理由代号与单据事由不符即「理由写错」，本道没过；
     * EXCEED 须有近月检测数据且至少 {@link #MIN_EXCEED_ROWS} 条超标读数，无数据压住、数据不支持拒签；
     * agree=false 写明不同意 → 单到此为止锁死。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public StopActionResult verify(Long id, String actor, String confirmedReason, boolean agree,
                                   String opinion, Date at) {
        Date now = at == null ? new Date() : at;
        String who = trim(actor);
        TZwStopBill bill = gate(id, who, SignTally.NODE_WATER_QA);
        if (bill == null) {
            return StopActionResult.rejected("水质科核实未受理：单子不在水质科这一道，或已落定/已终止，不许越关签");
        }
        // 前置检查：名下还有未完结处置单（未起/在办），申请压在这一道不再往前
        Integer openCases = this.zwCaseFlowMapper.selectCount(new QueryWrapper<TZwCaseFlow>()
                .eq("site_no", bill.getSiteNo())
                .ne("status", CASE_SEALED)
                .eq("del_flag", 0));
        if (openCases != null && openCases > 0) {
            return StopActionResult.blocked("压在水质科：该点位名下还有 " + openCases
                    + " 张未完结处置单，前置检查未过，暂不往调度中心送");
        }
        StopReason confirmed = StopReason.fromCode(confirmedReason);
        if (confirmed == null) {
            return StopActionResult.rejected("核实未过：理由代号写错，只认 DEPLETION/EXCEED/REPAIR");
        }
        // 只认代号不认名字：核实理由与提单事由对不上，就算没过
        if (!confirmed.code().equals(bill.getStopReason())) {
            return StopActionResult.rejected(
                    "核实未过：核实事由「" + confirmed.code() + "」与申请事由「" + bill.getStopReason() + "」不符，理由写错就算没过");
        }
        if (confirmed == StopReason.EXCEED) {
            List<TZwImpRow> recent = recentRows(bill.getSiteNo(), now);
            if (recent.isEmpty()) {
                // 无数据可对照——不能硬过也不能否，压在本道等数据
                return StopActionResult.blocked("压在水质科：近月查不到该点位已入库检测数据，无法对照超标申请，数据到了再核实");
            }
            int exceed = 0;
            for (TZwImpRow row : recent) {
                if (row.getGradeLevel() != null && row.getGradeLevel() >= GRADE_EXCEED) {
                    exceed++;
                }
            }
            if (exceed < MIN_EXCEED_ROWS) {
                return StopActionResult.rejected("核实未过：近月仅 " + exceed
                        + " 条超标（含严重超标）读数，不足 " + MIN_EXCEED_ROWS
                        + " 条，对不上「指标连续超标」的申请理由");
            }
        }
        if (!agree) {
            return terminate(bill, SignTally.NODE_WATER_QA, SignTally.ROLE_WQ, who,
                    SignTally.ACTION_DISAGREE, opinion, now, "水质科写明不同意，申请到此为止");
        }
        insertSign(bill.getId(), bill.getRoundNo(), SignTally.NODE_WATER_QA, SignTally.ROLE_WQ,
                who, SignTally.ACTION_AGREE, opinion, now);
        int rows = moveBill(bill, SignTally.NODE_DISPATCH, STATUS_RUNNING, now);
        if (rows == 0) {
            throw new IllegalStateException("并发落锤：水质科核实已被另一笔动作抢先");
        }
        return StopActionResult.advanced(chainView(id), "水质科核实通过，已转调度中心评估替代方案");
    }

    /**
     * 第三道 调度评估。两口并行、各评各的：
     * DISP_QTY 看水量调配、DISP_NET 看管网影响，一口只认一个人，同口重签/顶签不受理；
     * 同一账号两口都签按兼签处理，不受理（互不替签）；两位不同的人各签一笔才算点齐，
     * 只到一位单仍停在本道，到不了主管那道。agree=false 写明不同意 → 终止锁死。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public StopActionResult evaluate(Long id, String roleCode, String actor, boolean agree,
                                     String opinion, Date at) {
        Date now = at == null ? new Date() : at;
        String who = trim(actor);
        String role = trim(roleCode);
        if (!SignTally.ROLE_DISP_QTY.equals(role) && !SignTally.ROLE_DISP_NET.equals(role)) {
            return StopActionResult.rejected("调度评估未受理：评估角色只认 DISP_QTY(水量调配)/DISP_NET(管网影响)");
        }
        TZwStopBill bill = gate(id, who, SignTally.NODE_DISPATCH);
        if (bill == null) {
            return StopActionResult.rejected("调度评估未受理：单子不在调度评估这一道，或已落定/已终止，不许越关签");
        }
        List<TZwStopSign> mine = validAgreeSigns(id, bill.getRoundNo(), SignTally.NODE_DISPATCH);
        for (TZwStopSign s : mine) {
            if (role.equals(s.getRoleCode())) {
                // 这一口已经有人落名：本人重签或别人顶签都不受理，更不许替签
                return StopActionResult.rejected(s.getActor().equals(who)
                        ? "本口你已签过，即使同一分钟也是同一笔，不许重复落名"
                        : "本口已有「" + s.getActor() + "」落名，一口只认一人，不许顶签/替签");
            }
            if (who.equals(s.getActor())) {
                // 另一口是同一人签的：兼签当没签满，这一笔也不收
                return StopActionResult.rejected("水量调配与管网影响必须两个人各评各的，同一人不得两口并签（互不替签）");
            }
        }
        if (!agree) {
            return terminate(bill, SignTally.NODE_DISPATCH, role, who,
                    SignTally.ACTION_DISAGREE, opinion, now,
                    (SignTally.ROLE_DISP_QTY.equals(role) ? "水量调配" : "管网影响") + "评估写明不同意，申请到此为止");
        }
        insertSign(bill.getId(), bill.getRoundNo(), SignTally.NODE_DISPATCH, role, who,
                SignTally.ACTION_AGREE, opinion, now);

        SignTally tally = SignTally.of(allSigns(id), bill.getRoundNo());
        boolean filled = tally.filled(SignTally.NODE_DISPATCH);
        int nextNode = filled ? SignTally.NODE_HEAD : SignTally.NODE_DISPATCH;
        int rows = moveBill(bill, nextNode, STATUS_RUNNING, now);
        if (rows == 0) {
            throw new IllegalStateException("并发落锤：调度评估已被另一笔动作抢先");
        }
        String msg = filled
                ? "水量调配、管网影响两口已由两名评估人点齐，转调度中心主管批准"
                : "本口评估已落名；两口须两名不同评估人点齐，还差一口，单仍停在调度评估这一道";
        return StopActionResult.advanced(chainView(id), msg);
    }

    /**
     * 主管批准（新签法）。两口点齐才收；批准一落到底，同一事务内：
     * 报批情形落已核讫、水源点底档回写退役（条件更新，已被退役的不重复落）、
     * 公告文案按事由从名录带出钉在单上（名录无此文不得自编）。各栏随即锁死。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public StopActionResult approve(Long id, String approver, String opinion, Date at) {
        Date now = at == null ? new Date() : at;
        String who = trim(approver);
        TZwStopBill bill = gate(id, who, SignTally.NODE_HEAD);
        if (bill == null) {
            return StopActionResult.rejected("主管批准未受理：评估两口尚未点齐，或单子已落定/已终止——不许越级批准");
        }
        TZwSource source = findActiveSource(bill.getSiteNo());
        if (source == null) {
            return StopActionResult.rejected("批准未受理：点位底档不存在或已退役，无法回写退役");
        }
        String template = noticeTemplate(bill.getStopReason());
        String notice = StopNoticeText.render(template, bill.getSiteNo(), source.getSiteName(), bill.getAltPlan());
        if (notice == null) {
            // 名录里没有该事由文案：不批准，绝不自编一句兜底
            return StopActionResult.rejected("批准未受理：公告名录中查不到事由「" + bill.getStopReason() + "」的文案，公告不得自编");
        }
        insertSign(bill.getId(), bill.getRoundNo(), SignTally.NODE_HEAD,
                SignTally.ROLE_DISPATCH_HEAD, who, SignTally.ACTION_AGREE, opinion, now);

        int rows = this.zwStopBillMapper.update(null, new UpdateWrapper<TZwStopBill>()
                .set("status", STATUS_PASS)
                .set("approved_time", now)
                .set("notice_text", notice)
                .set("update_by", who)
                .set("update_time", now)
                .eq("id", id)
                .eq("node_no", SignTally.NODE_HEAD)
                .eq("status", STATUS_RUNNING)
                .eq("del_flag", 0));
        if (rows == 0) {
            throw new IllegalStateException("并发落锤：主管批准已被另一笔动作抢先");
        }
        // 批准后水源点情形翻成退役：条件更新只翻「在用」的点，重复批准/已退役不二次落
        int retired = this.zwSourceMapper.update(null, new UpdateWrapper<TZwSource>()
                .set("status", SOURCE_RETIRED)
                .set("update_by", who)
                .set("update_time", now)
                .eq("site_no", bill.getSiteNo())
                .eq("status", SOURCE_IN_USE)
                .eq("del_flag", 0));
        if (retired == 0) {
            throw new IllegalStateException("底档回写退役失败：点位已不在在用状态");
        }
        return StopActionResult.advanced(chainView(id),
                "主管已批准：水源点「" + bill.getSiteNo() + "」回写退役，不再生成新采样派单；公告文案已按名录带出");
    }

    /**
     * 主管认为理由不符把单打回（新签法）：本轮各关所有「同意」签核整批作废
     * （旧行置 valid_flag=0，留痕不删，与新轮补签不摆在一起），轮次 +1，
     * 单子退回第二道（水质科）重新攒起；第一道的申请内容仍在，不重提。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public StopActionResult reject(Long id, String approver, String opinion, Date at) {
        Date now = at == null ? new Date() : at;
        String who = trim(approver);
        TZwStopBill bill = gate(id, who, SignTally.NODE_HEAD);
        if (bill == null) {
            return StopActionResult.rejected("主管打回未受理：单子还没到主管这一道，或已落定/已终止");
        }
        int oldRound = bill.getRoundNo();
        // 打回这一笔单独落名（动作 3，不进任何枚数），旧轮同意签随后整批作废
        insertSign(bill.getId(), oldRound, SignTally.NODE_HEAD, SignTally.ROLE_DISPATCH_HEAD,
                who, SignTally.ACTION_HEAD_REJECT, opinion, now);
        int voided = this.zwStopSignMapper.update(null, new UpdateWrapper<TZwStopSign>()
                .set("valid_flag", 0)
                .eq("bill_id", id)
                .eq("round_no", oldRound)
                .eq("valid_flag", 1)
                .eq("action", SignTally.ACTION_AGREE));
        int rows = this.zwStopBillMapper.update(null, new UpdateWrapper<TZwStopBill>()
                // 轮次+1、退回第二道、在核——三样一条更新钉齐，作废轮与新轮各走各的 round_no
                .set("round_no", oldRound + 1)
                .set("node_no", SignTally.NODE_WATER_QA)
                .set("status", STATUS_RUNNING)
                .set("update_by", who)
                .set("update_time", now)
                .eq("id", id)
                .eq("node_no", SignTally.NODE_HEAD)
                .eq("status", STATUS_RUNNING)
                .eq("round_no", oldRound)
                .eq("del_flag", 0));
        if (rows == 0) {
            throw new IllegalStateException("并发落锤：主管打回已被另一笔动作抢先");
        }
        return StopActionResult.advanced(chainView(id),
                "主管以理由不符打回：前序 " + voided + " 笔签核整轮作废留痕，退回水质科（第二道）重新攒起");
    }

    /** 申请撤回：在核中的单由申请方撤回即终止锁死；已核讫/已终止的不受理。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public StopActionResult withdraw(Long id, String actor, String opinion, Date at) {
        Date now = at == null ? new Date() : at;
        String who = trim(actor);
        TZwStopBill bill = this.zwStopBillMapper.selectById(id);
        if (bill == null || who.isEmpty() || bill.getRoundNo() == null) {
            return StopActionResult.rejected("撤回未受理：申请不存在或不是新签法申请");
        }
        if (bill.getStatus() == null || bill.getStatus() != STATUS_RUNNING) {
            return StopActionResult.rejected("撤回未受理：单子已核讫或已终止，各栏锁死，不能撤回");
        }
        int node = bill.getNodeNo() == null ? 0 : bill.getNodeNo();
        return terminate(bill, node, SignTally.ROLE_APPLY, who,
                SignTally.ACTION_WITHDRAW, opinion, now, "申请已撤回，单子到此为止，各栏锁死");
    }

    /**
     * 唯一签核结论：顺着签核记录一次点算，当前关口/各关过没过/落名枚数与逐笔轨迹同源给出。
     */
    @Override
    public ChainView chainView(Long id) {
        TZwStopBill bill = this.zwStopBillMapper.selectById(id);
        if (bill == null || bill.getRoundNo() == null) {
            return null;
        }
        List<TZwStopSign> all = allSigns(id);
        SignTally tally = SignTally.of(all, bill.getRoundNo());
        // 轨迹按轮次、关口、时刻倒序：倒查从最后一道一笔笔退回第一道；作废轮仍可见（validFlag=0）
        List<TZwStopSign> trail = new ArrayList<TZwStopSign>(all);
        java.util.Collections.sort(trail, new java.util.Comparator<TZwStopSign>() {
            @Override
            public int compare(TZwStopSign a, TZwStopSign b) {
                int byRound = Integer.compare(b.getRoundNo(), a.getRoundNo());
                if (byRound != 0) {
                    return byRound;
                }
                int byNode = Integer.compare(
                        b.getNodeNo() == null ? 0 : b.getNodeNo(),
                        a.getNodeNo() == null ? 0 : a.getNodeNo());
                if (byNode != 0) {
                    return byNode;
                }
                return Long.compare(b.getId(), a.getId());
            }
        });
        int seq = bill.getYearSeq() == null ? 1 : bill.getYearSeq();
        return new ChainView(bill, tally, seq, trail);
    }

    // ---------------- 内家把式 ----------------

    /** 关口受理条件：单存在、新签法、在核、正停在该道；落名人不能为空。不合回 null（调用方按未受理拒） */
    private TZwStopBill gate(Long id, String who, int expectNode) {
        if (id == null || who == null || who.isEmpty()) {
            return null;
        }
        TZwStopBill bill = this.zwStopBillMapper.selectById(id);
        if (bill == null || bill.getRoundNo() == null) {
            return null;
        }
        if (bill.getStatus() == null || bill.getStatus() != STATUS_RUNNING) {
            return null;
        }
        int node = bill.getNodeNo() == null ? 0 : bill.getNodeNo();
        if (node != expectNode) {
            return null;
        }
        return bill;
    }

    /** 落锤：写明不同意/撤回，单到此为止，各栏锁死 */
    private StopActionResult terminate(TZwStopBill bill, int node, String role, String who,
                                       int action, String opinion, Date now, String msg) {
        insertSign(bill.getId(), bill.getRoundNo(), node, role, who, action, opinion, now);
        int rows = this.zwStopBillMapper.update(null, new UpdateWrapper<TZwStopBill>()
                .set("status", STATUS_TERMINATED)
                .set("update_by", who)
                .set("update_time", now)
                .eq("id", bill.getId())
                .eq("node_no", node)
                .eq("status", STATUS_RUNNING)
                .eq("round_no", bill.getRoundNo())
                .eq("del_flag", 0));
        if (rows == 0) {
            throw new IllegalStateException("并发落锤：终止动作已被另一笔抢先");
        }
        return StopActionResult.vetoed(chainView(bill.getId()), msg);
    }

    /** 推进/停留的条件更新：WHERE 钉死当前道与在核，越关与并发撞车 0 行即回滚 */
    private int moveBill(TZwStopBill bill, int nextNode, int nextStatus, Date now) {
        return this.zwStopBillMapper.update(null, new UpdateWrapper<TZwStopBill>()
                .set("node_no", nextNode)
                .set("status", nextStatus)
                .set("update_time", now)
                .eq("id", bill.getId())
                .eq("node_no", bill.getNodeNo())
                .eq("status", STATUS_RUNNING)
                .eq("round_no", bill.getRoundNo())
                .eq("del_flag", 0));
    }

    private void insertSign(Long billId, int round, int node, String role, String actor,
                            int action, String opinion, Date at) {
        TZwStopSign s = new TZwStopSign();
        s.setBillId(billId);
        s.setRoundNo(round);
        s.setNodeNo(node);
        s.setRoleCode(role);
        s.setActor(actor);
        s.setAction(action);
        s.setOpinion(opinion);
        s.setValidFlag(1);
        s.setCreateBy(actor);
        s.setCreateTime(at);
        this.zwStopSignMapper.insert(s);
    }

    private List<TZwStopSign> allSigns(Long billId) {
        return this.zwStopSignMapper.selectList(new QueryWrapper<TZwStopSign>()
                .eq("bill_id", billId)
                .orderByAsc("round_no", "node_no", "id"));
    }

    private List<TZwStopSign> validAgreeSigns(Long billId, int round, int node) {
        return this.zwStopSignMapper.selectList(new QueryWrapper<TZwStopSign>()
                .eq("bill_id", billId)
                .eq("round_no", round)
                .eq("node_no", node)
                .eq("valid_flag", 1)
                .eq("action", SignTally.ACTION_AGREE));
    }

    private TZwSource findActiveSource(String siteNo) {
        List<TZwSource> list = this.zwSourceMapper.selectList(new QueryWrapper<TZwSource>()
                .eq("site_no", siteNo)
                .eq("status", SOURCE_IN_USE)
                .eq("del_flag", 0));
        return list.isEmpty() ? null : list.get(0);
    }

    /** 近月（自然月）已入库检测读数，按检测发生日落窗，只认点位代号 */
    private List<TZwImpRow> recentRows(String siteNo, Date at) {
        Calendar c = Calendar.getInstance();
        c.setTime(at);
        c.add(Calendar.MONTH, -RECENT_MONTHS);
        Date from = c.getTime();
        return this.zwImpRowMapper.selectList(new QueryWrapper<TZwImpRow>()
                .eq("site_no", siteNo)
                .eq("status", ROW_OK)
                .eq("del_flag", 0)
                .ge("test_at", from)
                .le("test_at", at));
    }

    /** 公告名录：按事由代号取模板（模板正文存在该字典项备注里） */
    private String noticeTemplate(String reasonCode) {
        List<TSysDictData> list = this.sysDictDataMapper.selectList(new QueryWrapper<TSysDictData>()
                .eq("dict_type", StopNoticeText.DICT_TYPE)
                .eq("dict_value", reasonCode)
                .eq("status", "0"));
        if (list.isEmpty()) {
            return null;
        }
        String remark = list.get(0).getRemark();
        return remark == null || remark.trim().isEmpty() ? null : remark;
    }

    private int nextYearSeq(String siteNo, int year) {
        Integer max = this.zwStopBillMapper.selectCount(new QueryWrapper<TZwStopBill>()
                .eq("site_no", siteNo)
                .eq("apply_year", year));
        return (max == null ? 0 : max) + 1;
    }

    private static int yearOf(Date d) {
        Calendar c = Calendar.getInstance();
        c.setTime(d);
        return c.get(Calendar.YEAR);
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private static String appendTrace(String old, String line) {
        if (old == null || old.isEmpty()) {
            return line;
        }
        return old + "；" + line;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String ts() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    }
}
