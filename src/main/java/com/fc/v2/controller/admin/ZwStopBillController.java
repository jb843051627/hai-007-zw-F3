package com.fc.v2.controller.admin;

import java.util.Date;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TZwStopBill;
import com.fc.v2.service.ITZwStopBillService;
import com.fc.v2.service.zw.ChainView;
import com.fc.v2.service.zw.StopActionResult;
import com.fc.v2.shiro.util.ShiroUtils;
import com.fc.v2.util.StringUtils;
import com.github.pagehelper.PageInfo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

/**
 * 停供/限供申请签核 Controller（approval-chain 形状）。
 *
 * <p>页面只做一件事：把经办动作转给服务层，把服务层那套签核方法给回的
 * {@link ChainView}/{@link StopActionResult} 原样摆出来。单子走到哪一关、
 * 各关过没过、各关落名几枚，控制器不另算、不接页面传来的计数，
 * 也没有能直接印、能撤、能改名的旁路。
 *
 * @author fuce
 * @date 2026-10-01
 */
@Api(value = "停供/限供申请签核")
@Controller
@RequestMapping("/zwStopBill")
public class ZwStopBillController extends BaseController {

    private final String prefix = "admin/zwStopBill";

    @Autowired
    private ITZwStopBillService zwStopBillService;

    /** 落名人只认当前登录账号，页面传什么都不算——代签当没签 */
    private static String currentActor() {
        return ShiroUtils.getLoginName();
    }

    @ApiOperation(value = "停供申请台账跳转", notes = "停供申请台账跳转")
    @GetMapping("/view")
    @RequiresPermissions("zw:zwStopBill:view")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    @Log(title = "停供申请台账查询", action = "list")
    @ApiOperation(value = "台账查询", notes = "在核/已核讫/已终止的单都翻得到")
    @GetMapping("/list")
    @RequiresPermissions("zw:zwStopBill:list")
    @ResponseBody
    public ResultTable list(TZwStopBill record) {
        QueryWrapper<TZwStopBill> queryWrapper = new QueryWrapper<TZwStopBill>();
        queryWrapper.like(StringUtils.isNotEmpty(record.getBillNo()), "bill_no", record.getBillNo());
        queryWrapper.eq(StringUtils.isNotEmpty(record.getSiteNo()), "site_no", record.getSiteNo());
        queryWrapper.eq(StringUtils.isNotNull(record.getStatus()), "status", record.getStatus());
        queryWrapper.orderByDesc("apply_year", "site_no", "year_seq");
        startPage();
        PageInfo<TZwStopBill> page =
                new PageInfo<TZwStopBill>(zwStopBillService.selectTZwStopBillList(queryWrapper));
        return pageTable(page.getList(), page.getTotal());
    }

    @ApiOperation(value = "签核轨迹跳转", notes = "逐笔签核从最后一道退回第一道")
    @GetMapping("/detail")
    @RequiresPermissions("zw:zwStopBill:view")
    public String detail(Long id, ModelMap model) {
        TZwStopBill bill = zwStopBillService.selectTZwStopBillById(id);
        model.put("bill", bill);
        return prefix + "/detail";
    }

    @ApiOperation(value = "签核结论", notes = "当前关口/各关落名枚数/逐笔轨迹，只取服务层这一套")
    @GetMapping("/chain")
    @RequiresPermissions("zw:zwStopBill:view")
    @ResponseBody
    public AjaxResult chain(Long id) {
        ChainView view = zwStopBillService.chainView(id);
        return view == null ? AjaxResult.error("申请不存在") : AjaxResult.successData(200, view);
    }

    @Log(title = "业务科停供提单", action = "submit")
    @ApiOperation(value = "业务科提单", notes = "点位代号/事由/替代方案三要素缺一挡回")
    @PostMapping("/submit")
    @RequiresPermissions("zw:zwStopBill:submit")
    @ResponseBody
    public AjaxResult submit(String siteNo, String stopReason, String altPlan) {
        StopActionResult r = zwStopBillService.submit(siteNo, stopReason, altPlan,
                currentActor(), new Date());
        return toResult(r);
    }

    @Log(title = "水质科核实", action = "verify")
    @ApiOperation(value = "水质科核实", notes = "前置未齐压住；理由代号写错不过；不同意即终止")
    @PostMapping("/verify")
    @RequiresPermissions("zw:zwStopBill:verify")
    @ResponseBody
    public AjaxResult verify(Long id, String confirmedReason, boolean agree, String opinion) {
        StopActionResult r = zwStopBillService.verify(id, currentActor(), confirmedReason,
                agree, opinion, new Date());
        return toResult(r);
    }

    @Log(title = "调度评估", action = "evaluate")
    @ApiOperation(value = "调度评估", notes = "水量调配/管网影响两口并行、两名点齐才进主管")
    @PostMapping("/evaluate")
    @RequiresPermissions("zw:zwStopBill:evaluate")
    @ResponseBody
    public AjaxResult evaluate(Long id, String roleCode, boolean agree, String opinion) {
        StopActionResult r = zwStopBillService.evaluate(id, roleCode, currentActor(),
                agree, opinion, new Date());
        return toResult(r);
    }

    @Log(title = "主管批准停供", action = "approve")
    @ApiOperation(value = "主管批准", notes = "批准后水源点退役、公告按名录带出、各栏锁死")
    @PostMapping("/approve")
    @RequiresPermissions("zw:zwStopBill:approve")
    @ResponseBody
    public AjaxResult approve(Long id, String opinion) {
        StopActionResult r = zwStopBillService.approve(id, currentActor(), opinion, new Date());
        return toResult(r);
    }

    @Log(title = "主管打回停供申请", action = "reject")
    @ApiOperation(value = "主管打回", notes = "旧轮签核整轮作废，退回第二道重新攒")
    @PostMapping("/reject")
    @RequiresPermissions("zw:zwStopBill:approve")
    @ResponseBody
    public AjaxResult reject(Long id, String opinion) {
        StopActionResult r = zwStopBillService.reject(id, currentActor(), opinion, new Date());
        return toResult(r);
    }

    @Log(title = "停供申请撤回", action = "withdraw")
    @ApiOperation(value = "申请撤回", notes = "撤回即终止锁死")
    @PostMapping("/withdraw")
    @RequiresPermissions("zw:zwStopBill:withdraw")
    @ResponseBody
    public AjaxResult withdraw(Long id, String opinion) {
        StopActionResult r = zwStopBillService.withdraw(id, currentActor(), opinion, new Date());
        return toResult(r);
    }

    /**
     * 三种非成功结局在屏上分得清：ADVANCED 成功；BLOCKED 压住（在核、可再来）；
     * VETOED 终止锁死；REJECTED 未受理。后两种不是同一回事。
     */
    private AjaxResult toResult(StopActionResult r) {
        if (r.getOutcome() == StopActionResult.Outcome.ADVANCED) {
            return AjaxResult.successData(200, r);
        }
        if (r.getOutcome() == StopActionResult.Outcome.BLOCKED) {
            return AjaxResult.successData(202, r);
        }
        return AjaxResult.error(r.getMessage());
    }
}
