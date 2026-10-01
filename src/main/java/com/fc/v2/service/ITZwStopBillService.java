package com.fc.v2.service;

import java.util.Date;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fc.v2.model.auto.TZwStopBill;
import com.fc.v2.service.zw.ChainView;
import com.fc.v2.service.zw.StopActionResult;

/**
 * 停供/限供申请签核单 Service接口。
 *
 * <p><b>两套签法并存</b>：老签核方法 {@link #approve(Long, String, String)}、
 * {@link #reject(Long, String, String)}、{@link #rollback(Long, String)} 入参与返回照旧不动；
 * 今年收进系统的三道关口新签法以<b>同名重载</b>另立（带 {@code at} 时刻的一组），
 * 互不顶替。单子走到哪一关、这一关过没过、各关落名几枚，页面只认
 * {@link #chainView(Long)} 这套签核方法给回的 {@link ChainView}。
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITZwStopBillService {

    /** 按主键回查单据 */
    TZwStopBill selectTZwStopBillById(Long id);

    /** 台账查询 */
    List<TZwStopBill> selectTZwStopBillList(Wrapper<TZwStopBill> queryWrapper);

    // ===================== 老签法（入参与返回照旧，不许改） =====================

    /** 签批一票：返回更新后的单据；被拒返回 null */
    TZwStopBill approve(Long id, String approver, String comment);

    /** 否决：返回更新后的单据；被拒返回 null */
    TZwStopBill reject(Long id, String approver, String comment);

    /** 退回上一环节：返回更新后的单据；被拒返回 null */
    TZwStopBill rollback(Long id, String comment);

    // ===================== 三道关口新签法（同名方法另立） =====================

    /**
     * 第一道 业务科提单：水源点代号、停水事由、替代供水方案三样缺一当场挡回。
     * 提单即落业务科自己的名，单子进第二道（水质科）。同一水源点当年第二份不拦截，
     * 但在回值里当场单独点名（{@link ChainView#isSecondOrLaterThisYear()}）。
     */
    StopActionResult submit(String siteNo, String stopReason, String altPlan,
                            String applicant, Date at);

    /**
     * 第二道 水质科核实：只认点位代号，先查前置（名下还有未完结处置单 → 压在本道不进不退），
     * 再拿近月检测数据与申请对照；{@code confirmedReason} 与单据事由不符即「理由写错」，本道不过；
     * {@code agree=false} 为写明不同意，单到此为止锁死。
     */
    StopActionResult verify(Long id, String actor, String confirmedReason, boolean agree,
                            String opinion, Date at);

    /**
     * 第三道 调度评估：{@code roleCode} 取 DISP_QTY（水量调配）/DISP_NET（管网影响），
     * 两口并行、各评各的。同一人不能两口并签（互不替签），同口重复签不受理；
     * 两口由两个不同的人点齐才进主管那道，只到一位就停在本道。
     * {@code agree=false} 写明不同意，单到此为止。
     */
    StopActionResult evaluate(Long id, String roleCode, String actor, boolean agree,
                              String opinion, Date at);

    /**
     * 主管批准（新签法）：必须两口已点齐。批准一落到底——报批情形落已核讫、
     * 水源点底档回写退役（该点不再生成新采样派单，旧采样记录可查不可追加）、
     * 公告文案按事由从名录带出钉在单上，各栏随即锁死。名录无该事由文案则不批准。
     */
    StopActionResult approve(Long id, String approver, String opinion, Date at);

    /**
     * 主管认为理由不符把单打回（新签法）：之前各关所有签核整轮作废（旧行置 valid_flag=0，
     * 不与补签摆在一起），轮次 +1，单子退回第二道（水质科）重新攒起。
     */
    StopActionResult reject(Long id, String approver, String opinion, Date at);

    /** 申请撤回：在核中的单由申请方撤回，单到此为止锁死；已落定的不受理。 */
    StopActionResult withdraw(Long id, String actor, String opinion, Date at);

    /**
     * 唯一签核结论：当前关口、各关过没过、各关落名枚数全部顺着签核记录经
     * {@link com.fc.v2.service.zw.SignTally} 同一次点算给出，并附逐笔轨迹供倒查。
     * 屏上摆的与本回值不许是两份。
     */
    ChainView chainView(Long id);
}
