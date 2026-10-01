package com.fc.v2.model.auto;

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
 * 停供申请各关签核记录对象 t_zw_stop_sign。
 *
 * <p>一笔落名一行：两名评估人同一分钟签完也是两笔，不并成一笔。
 * 倒查从最后一道顺着这些记录一笔笔退回第一道；主管打回不删旧行，
 * 只把旧轮 {@code valid_flag} 置 0，作废的与补签的不摆在一起。
 *
 * @author fuce
 * @date 2026-10-01
 */
@TableName("t_zw_stop_sign")
@ApiModel(value = "TZwStopSign", description = "停供申请各关签核记录")
public class TZwStopSign implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 签核单主键 */
    @TableField("bill_id")
    @ApiModelProperty(value = "签核单主键")
    private Long billId;

    /** 攒签轮次（打回后在新轮重攒，旧轮留痕） */
    @TableField("round_no")
    @ApiModelProperty(value = "攒签轮次")
    private Integer roundNo;

    /** 落名关口 0业务提 1水质核 2调度评 3主管准 */
    @TableField("node_no")
    @ApiModelProperty(value = "落名关口 0业务提 1水质核 2调度评 3主管准")
    private Integer nodeNo;

    /** 落名角色 APPLY/WQ/DISP_QTY/DISP_NET/DISPATCH_HEAD */
    @TableField("role_code")
    @ApiModelProperty(value = "落名角色")
    private String roleCode;

    /** 落名人系统账号（调度两口同一账号只算一人，互不替签） */
    @TableField("actor")
    @ApiModelProperty(value = "落名人系统账号")
    private String actor;

    /** 落名动作 0同意 1不同意 2撤回 3主管打回(旧轮作废) */
    @TableField("action")
    @ApiModelProperty(value = "落名动作 0同意 1不同意 2撤回 3主管打回")
    private Integer action;

    /** 当时经办意见（倒查凭据，落定不改） */
    @TableField("opinion")
    @ApiModelProperty(value = "当时经办意见")
    private String opinion;

    /** 这笔还算不算数 1有效 0随旧轮作废 */
    @TableField("valid_flag")
    @ApiModelProperty(value = "这笔还算不算数 1有效 0随旧轮作废")
    private Integer validFlag;

    /** 创建者 */
    @TableField("create_by")
    @ApiModelProperty(value = "创建者")
    private String createBy;

    /** 落名时刻 */
    @TableField("create_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "落名时刻")
    private Date createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getBillId() {
        return billId;
    }

    public void setBillId(Long billId) {
        this.billId = billId;
    }

    public Integer getRoundNo() {
        return roundNo;
    }

    public void setRoundNo(Integer roundNo) {
        this.roundNo = roundNo;
    }

    public Integer getNodeNo() {
        return nodeNo;
    }

    public void setNodeNo(Integer nodeNo) {
        this.nodeNo = nodeNo;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public void setRoleCode(String roleCode) {
        this.roleCode = roleCode;
    }

    public String getActor() {
        return actor;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

    public Integer getAction() {
        return action;
    }

    public void setAction(Integer action) {
        this.action = action;
    }

    public String getOpinion() {
        return opinion;
    }

    public void setOpinion(String opinion) {
        this.opinion = opinion;
    }

    public Integer getValidFlag() {
        return validFlag;
    }

    public void setValidFlag(Integer validFlag) {
        this.validFlag = validFlag;
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
}
