package com.fc.v2.model.auto;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.Date;

/**
 * 停供/限供申请签核单对象 t_zw_stop_bill
 *
 * @author fuce
 * @date 2026-09-12
 */
@TableName("t_zw_stop_bill")
@ApiModel(value = "TZwStopBill", description = "停供/限供申请签核单")
public class TZwStopBill implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 停供/限供申请签核单号 */
    @TableField("bill_no")
    @ApiModelProperty(value = "停供/限供申请签核单号")
    private String billNo;

    /** 当前所在道 0..3（业务提/水质核/调度评/主管准）；批准落定后停在 3 */
    @TableField("node_no")
    @ApiModelProperty(value = "当前所在道 0..3（业务提/水质核/调度评/主管准）")
    private Integer nodeNo;

    /** 同口并印办法 0任一人 1两名点齐（老签法沿用） */
    @TableField("sign_mode")
    @ApiModelProperty(value = "同口并印办法 0任一人 1两名点齐")
    private Integer signMode;

    /** 本口应落印数（老签法沿用） */
    @TableField("need_count")
    @ApiModelProperty(value = "本口应落印数")
    private Integer needCount;

    /** 本口已落印数（老签法沿用；新签法此列留空，落名枚数从签核记录一笔笔累出） */
    @TableField("sign_count")
    @ApiModelProperty(value = "本口已落印数")
    private Integer signCount;

    /** 攒签轮次：主管打回一轮 +1，作废轮次只留痕不删除 */
    @TableField("round_no")
    @ApiModelProperty(value = "攒签轮次：主管打回一轮+1")
    private Integer roundNo;

    /** 水源点代号（核实只认代号不认名字） */
    @TableField("site_no")
    @ApiModelProperty(value = "水源点代号")
    private String siteNo;

    /** 停水事由 DEPLETION/EXCEED/REPAIR */
    @TableField("stop_reason")
    @ApiModelProperty(value = "停水事由 DEPLETION水源枯竭/EXCEED指标连续超标/REPAIR检修")
    private String stopReason;

    /** 停水后的替代供水方案 */
    @TableField("alt_plan")
    @ApiModelProperty(value = "停水后的替代供水方案")
    private String altPlan;

    /** 申请年度（同一水源点当年第二份当场单独点名） */
    @TableField("apply_year")
    @ApiModelProperty(value = "申请年度")
    private Integer applyYear;

    /** 同一水源点当年序（01 起顺排，不拦截只点名） */
    @TableField("year_seq")
    @ApiModelProperty(value = "同一水源点当年序")
    private Integer yearSeq;

    /** 停水公告文案（批准时按事由从名录带出，不许人手抄录） */
    @TableField("notice_text")
    @ApiModelProperty(value = "停水公告文案（名录带出）")
    private String noticeText;

    /** 主管批准时刻 */
    @TableField("approved_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "主管批准时刻")
    private Date approvedTime;

    /** 报批情形 0在核 1已核讫 2已打回(老签法) 3已终止(不同意/撤回) */
    @TableField("status")
    @ApiModelProperty(value = "报批情形 0在核 1已核讫 2已打回 3已终止")
    private Integer status;

    /** 删除标记 0正常 1删除 */
    @TableField("del_flag")
    @ApiModelProperty(value = "删除标记 0正常 1删除")
    private Integer delFlag;

    /** 创建者 */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建者")
    private String createBy;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    /** 更新者 */
    @TableField(value = "update_by", fill = FieldFill.UPDATE)
    @ApiModelProperty(value = "更新者")
    private String updateBy;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "更新时间")
    private Date updateTime;

    /** 备注 */
    @TableField("remark")
    @ApiModelProperty(value = "备注")
    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBillNo() {
        return billNo;
    }

    public void setBillNo(String billNo) {
        this.billNo = billNo;
    }

    public Integer getNodeNo() {
        return nodeNo;
    }

    public void setNodeNo(Integer nodeNo) {
        this.nodeNo = nodeNo;
    }

    public Integer getSignMode() {
        return signMode;
    }

    public void setSignMode(Integer signMode) {
        this.signMode = signMode;
    }

    public Integer getNeedCount() {
        return needCount;
    }

    public void setNeedCount(Integer needCount) {
        this.needCount = needCount;
    }

    public Integer getSignCount() {
        return signCount;
    }

    public void setSignCount(Integer signCount) {
        this.signCount = signCount;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getDelFlag() {
        return delFlag;
    }

    public void setDelFlag(Integer delFlag) {
        this.delFlag = delFlag;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Integer getRoundNo() {
        return roundNo;
    }

    public void setRoundNo(Integer roundNo) {
        this.roundNo = roundNo;
    }

    public String getSiteNo() {
        return siteNo;
    }

    public void setSiteNo(String siteNo) {
        this.siteNo = siteNo;
    }

    public String getStopReason() {
        return stopReason;
    }

    public void setStopReason(String stopReason) {
        this.stopReason = stopReason;
    }

    public String getAltPlan() {
        return altPlan;
    }

    public void setAltPlan(String altPlan) {
        this.altPlan = altPlan;
    }

    public Integer getApplyYear() {
        return applyYear;
    }

    public void setApplyYear(Integer applyYear) {
        this.applyYear = applyYear;
    }

    public Integer getYearSeq() {
        return yearSeq;
    }

    public void setYearSeq(Integer yearSeq) {
        this.yearSeq = yearSeq;
    }

    public String getNoticeText() {
        return noticeText;
    }

    public void setNoticeText(String noticeText) {
        this.noticeText = noticeText;
    }

    public Date getApprovedTime() {
        return approvedTime;
    }

    public void setApprovedTime(Date approvedTime) {
        this.approvedTime = approvedTime;
    }
}
