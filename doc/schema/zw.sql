-- zw 城市供水水质检测调度与达标管理 -- schema (hai-007)
-- 列名与基线实体契约（@TableName/@TableField）逐列对齐，改列必须同步实体。
-- 库：hai_007

CREATE TABLE IF NOT EXISTS t_zw_arch_card (
  id bigint NOT NULL COMMENT '主键',
  bill_no varchar(64) DEFAULT NULL COMMENT '水源地/水厂档案卡号',
  node_no int DEFAULT NULL COMMENT '本卡版本次序',
  site_id int DEFAULT NULL COMMENT '所属底档',
  site_no varchar(64) DEFAULT NULL COMMENT '所属底档代号',
  qty decimal(12,2) DEFAULT NULL COMMENT '本卡供水规模(万吨/日)',
  fine_amt decimal(12,2) DEFAULT NULL COMMENT '本年度新增供水规模(万吨/日)',
  content varchar(255) DEFAULT NULL COMMENT '校验串（随版本次序走）',
  grade_level int DEFAULT NULL COMMENT '供水规模折到的保障强度层级',
  status int DEFAULT NULL COMMENT '进展 0待核对 1已核对 2已压死',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='水源地/水厂档案卡';

CREATE TABLE IF NOT EXISTS t_zw_case_flow (
  id bigint NOT NULL COMMENT '主键',
  biz_no varchar(64) DEFAULT NULL COMMENT '处置工单号',
  site_no varchar(64) DEFAULT NULL COMMENT '涉事水源点代号（停供申请前置检查按代号点名下属处置单，只认代号不认名字）',
  stage int DEFAULT NULL COMMENT '当前处置段 0..3（登记/初检/处置/复核封卷）',
  status int DEFAULT NULL COMMENT '工单落定 0未起 1在办 2已封存',
  content varchar(255) DEFAULT NULL COMMENT '工单记事',
  last_action varchar(64) DEFAULT NULL COMMENT '最近一次过口动作',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='水质异常处置工单';

CREATE TABLE IF NOT EXISTS t_zw_imp_row (
  id bigint NOT NULL COMMENT '主键',
  batch_no varchar(64) DEFAULT NULL COMMENT '检测数据上传批次号',
  row_no int DEFAULT NULL COMMENT '原表行次',
  site_no varchar(64) DEFAULT NULL COMMENT '采样水源点代号（水质科拿近月数据对照申请时只认代号）',
  item_code varchar(64) DEFAULT NULL COMMENT '被校验出的数据项代号',
  qty decimal(12,2) DEFAULT NULL COMMENT '本行检测读数',
  sample_type varchar(32) DEFAULT NULL COMMENT '样本类别(水源水/出厂水/管网末梢)',
  test_at datetime DEFAULT NULL COMMENT '检测发生那天（用哪条线只看这天，不看今天）',
  grade_level int DEFAULT NULL COMMENT '评价等级 1合格 2关注 3超标 4严重超标；空=未定级(无可用标准)',
  rule_code varchar(64) DEFAULT NULL COMMENT '定级所依标准线代号（服务层回值钉死，写过不再改）',
  rule_eff_date datetime DEFAULT NULL COMMENT '所依标准的生效那天（历史复核只认此编号上下文）',
  status int DEFAULT NULL COMMENT '行落地情形 0待核 1已入库 2退回',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='检测数据导入核对行';

CREATE TABLE IF NOT EXISTS t_zw_lim_rule (
  id bigint NOT NULL COMMENT '主键',
  rule_code varchar(64) DEFAULT NULL COMMENT '水质限值判定线代号',
  rule_name varchar(128) DEFAULT NULL COMMENT '判定线名',
  sample_type varchar(32) DEFAULT NULL COMMENT '适用样本类别(水源水/出厂水/管网末梢)',
  th1_max decimal(12,2) DEFAULT NULL COMMENT '关注档指标上限',
  th2_max decimal(12,2) DEFAULT NULL COMMENT '超标档指标上限',
  th3_max decimal(12,2) DEFAULT NULL COMMENT '严重超标档指标上限',
  eff_start datetime DEFAULT NULL COMMENT '启用之日',
  eff_end datetime DEFAULT NULL COMMENT '交棒之日(不含)',
  priority int DEFAULT NULL COMMENT '判定线顺位(数值大的先说话)',
  status int DEFAULT NULL COMMENT '判定线情形 0现行 1已让位',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='水质指标限值分档判定线';

CREATE TABLE IF NOT EXISTS t_zw_sam_plan (
  id bigint NOT NULL COMMENT '主键',
  plan_no varchar(64) DEFAULT NULL COMMENT '检测计划周期代号',
  site_no varchar(64) DEFAULT NULL COMMENT '点位代号（派单时带入事项条）',
  team_id bigint DEFAULT NULL COMMENT '直挂承接班组(部门主键)；空=派发时按各班组当前负载加权分派',
  period_days int DEFAULT NULL COMMENT '周期自然日数（隔多少天采一次）',
  amount decimal(12,2) DEFAULT NULL COMMENT '提前浮出待办的自然日数（全站唯一可调档）',
  first_due date DEFAULT NULL COMMENT '首个约定采样日（其后按周期顺排）',
  eff_start datetime DEFAULT NULL COMMENT '计划生效之时',
  eff_end datetime DEFAULT NULL COMMENT '计划停用之时(不含)；空=长期现行',
  content varchar(255) DEFAULT NULL COMMENT '采样事由',
  status int DEFAULT NULL COMMENT '计划情形 0现行 1停用',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_sam_plan_no (plan_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='采样检测计划周期底档';

CREATE TABLE IF NOT EXISTS t_zw_sam_task (
  id bigint NOT NULL COMMENT '主键',
  item_no varchar(64) DEFAULT NULL COMMENT '采样事项号（计划代号-采样日，系统一次生成）',
  plan_no varchar(64) DEFAULT NULL COMMENT '检测计划周期代号（底档带入）',
  site_no varchar(64) DEFAULT NULL COMMENT '点位代号（底档带入）',
  team_id bigint DEFAULT NULL COMMENT '承接班组(部门主键)；空=派发时按当前负载加权分派后回写',
  due_at datetime DEFAULT NULL COMMENT '约定采样那一天的终结时刻(当日23:59:59)——全站唯一口径，任何动作不得挪动',
  amount decimal(12,2) DEFAULT NULL COMMENT '提前浮出待办的自然日数（唯一可调档）',
  dispatch_at datetime DEFAULT NULL COMMENT '真正派出时刻（首次钉库，后到动作不得覆盖）',
  cancel_at datetime DEFAULT NULL COMMENT '撤单时刻',
  content varchar(255) DEFAULT NULL COMMENT '采样事由与受派班组记要',
  hang_reason varchar(255) DEFAULT NULL COMMENT '挂起原因（点位退役/班组停班/无班可派）',
  status int DEFAULT NULL COMMENT '条目情形 0待派 1已派 2挂起 3撤单 4转作他理',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注（撤单/转理留痕追加于此）',
  PRIMARY KEY (id),
  UNIQUE KEY uk_sam_item_no (item_no),
  UNIQUE KEY uk_sam_plan_site_due (plan_no, site_no, due_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='采样任务到期派单';

CREATE TABLE IF NOT EXISTS t_zw_source (
  id bigint NOT NULL COMMENT '主键',
  site_no varchar(64) DEFAULT NULL COMMENT '水源地/水厂档案代号',
  site_name varchar(128) DEFAULT NULL COMMENT '档案名称',
  site_type varchar(32) DEFAULT NULL COMMENT '档案类别(水源地/水厂)',
  road_name varchar(128) DEFAULT NULL COMMENT '所属行政区划(省—市—区—街道)',
  th1_max decimal(12,2) DEFAULT NULL COMMENT '单档供水规模一档上限(万吨/日)',
  th2_max decimal(12,2) DEFAULT NULL COMMENT '二档上限(万吨/日)',
  th3_max decimal(12,2) DEFAULT NULL COMMENT '三档上限(万吨/日)',
  status int DEFAULT NULL COMMENT '底档情形 0在用 1已退役',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='水源地/水厂底档';

CREATE TABLE IF NOT EXISTS t_zw_stop_bill (
  id bigint NOT NULL COMMENT '主键',
  bill_no varchar(64) DEFAULT NULL COMMENT '停供/限供申请签核单号（点位代号-年度-当年序）',
  node_no int DEFAULT NULL COMMENT '当前所在道 0..3（业务提/水质核/调度评/主管准）',
  sign_mode int DEFAULT NULL COMMENT '同口并印办法 0任一人 1两名点齐（老签法沿用；新签法计数只认签核记录）',
  need_count int DEFAULT NULL COMMENT '本口应落印数（老签法沿用）',
  sign_count int DEFAULT NULL COMMENT '本口已落印数（老签法沿用；新签法此列留空，落名枚数从签核记录一笔笔累出）',
  round_no int DEFAULT 1 COMMENT '攒签轮次：主管打回一轮+1，作废轮次只留痕不删除',
  site_no varchar(64) DEFAULT NULL COMMENT '水源点代号（核实只认代号不认名字）',
  stop_reason varchar(16) DEFAULT NULL COMMENT '停水事由 DEPLETION水源枯竭/EXCEED指标连续超标/REPAIR检修',
  alt_plan varchar(500) DEFAULT NULL COMMENT '停水后的替代供水方案',
  apply_year int DEFAULT NULL COMMENT '申请年度（同一水源点当年第二份当场单独点名）',
  year_seq int DEFAULT NULL COMMENT '同一水源点当年序（01 起顺排，不拦截只点名）',
  notice_text varchar(500) DEFAULT NULL COMMENT '停水公告文案（批准时按事由从名录带出，不许人手抄录）',
  approved_time datetime DEFAULT NULL COMMENT '主管批准时刻',
  status int DEFAULT NULL COMMENT '报批情形 0在核 1已核讫 2已打回(老签法) 3已终止(不同意/撤回)',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_stop_bill_no (bill_no),
  UNIQUE KEY uk_stop_site_year_seq (site_no, apply_year, year_seq)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='停供/限供申请签核单';

-- 每一笔落名各记各的：同一分钟两人签完也是两笔，不并成一笔；代签（用别人账号）当没签——
-- 服务层只认真实落账的账号，页面没有让人代填落名数的格子。
-- 作废（主管打回）不删行：旧轮 action=0 的签整体置 valid_flag=0，与新轮补签分开摆，倒查时逐笔对得上经办屏。
CREATE TABLE IF NOT EXISTS t_zw_stop_sign (
  id bigint NOT NULL COMMENT '主键',
  bill_id bigint NOT NULL COMMENT '签核单主键',
  round_no int NOT NULL COMMENT '攒签轮次（打回后在新轮重攒，旧轮留痕）',
  node_no int NOT NULL COMMENT '落名关口 0业务提 1水质核 2调度评 3主管准',
  role_code varchar(32) NOT NULL COMMENT '落名角色 APPLY提单人/WQ水质核实/DISP_QTY水量调配/DISP_NET管网影响/DISPATCH_HEAD调度主管',
  actor varchar(64) NOT NULL COMMENT '落名人系统账号（调度两口同一账号只算一人，互不替签）',
  action int NOT NULL COMMENT '落名动作 0同意 1不同意(单到此为止) 2撤回(单到此为止) 3主管打回(旧轮作废回第二道)',
  opinion varchar(500) DEFAULT NULL COMMENT '当时经办意见（倒查凭据，落定不改）',
  valid_flag int NOT NULL DEFAULT 1 COMMENT '这笔还算不算数 1有效 0随旧轮作废；作废的与补签的不摆在一起',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime NOT NULL COMMENT '落名时刻',
  PRIMARY KEY (id),
  UNIQUE KEY uk_stop_sign_once (bill_id, round_no, node_no, role_code, actor, action),
  KEY idx_stop_sign_bill_round (bill_id, round_no, valid_flag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='停供申请各关签核记录';

-- 初始档案数据：id=0 在用、id=1 已退役
-- 供水规模上限 30/80/200 万吨/日：等于上限算高一档。
INSERT IGNORE INTO t_zw_source (id, site_no, site_name, site_type, road_name, th1_max, th2_max, th3_max, status, del_flag, create_by, create_time)
VALUES (0, 'ZS00', '青坪市城北水源地（在用）', '水源地', '青坪省—青坪市—城东区—东湖街道', 30.00, 80.00, 200.00, 0, 0, 'seed', NOW()),
       (1, 'ZS01', '青坪市城南水厂（已退役）', '水厂', '青坪省—青坪市—城南区—望湖街道', 30.00, 80.00, 200.00, 1, 0, 'seed', NOW());

-- 检测计划周期底档种子：
-- PL-ZS00-M：城北水源地月度例行（30 天一周期，提前 3 天浮出，班组不直挂→按负载加权派班）；
-- PL-ZS01-Q：城南水厂季度（直挂 2 号班组；点位已退役，开单前即转挂起，用来演示挂起不拖垮整轮）。
INSERT IGNORE INTO t_zw_sam_plan (id, plan_no, site_no, team_id, period_days, amount, first_due, eff_start, eff_end, content, status, del_flag, create_by, create_time)
VALUES (10, 'PL-ZS00-M', 'ZS00', NULL, 30, 3.00, '2026-09-01', '2026-09-01 00:00:00', NULL, '城北水源地月度例行采样', 0, 0, 'seed', NOW()),
       (11, 'PL-ZS01-Q', 'ZS01', 2, 90, 3.00, '2026-09-15', '2026-09-15 00:00:00', NULL, '城南水厂季度采样（点位已退役，开单即挂起）', 0, 0, 'seed', NOW());

-- 水质限值分档判定线种子（出厂水余氯 mg/L）：
-- 100 旧线 CL-FW-2024：上年口径，上限松，严重超标一格早期入册本就留空（空=不设此档，随超标档下推），
--     管到 2026-01-01（不含），现已让位，只作回溯依据，不参与现行评价；
-- 101 新线 CL-FW-2026：今年收紧，自 2026-01-01 00:00:00 起现行——交接日全厂一套口径，旧线管到进程、新线从结局起算；
-- 102/103 水源水、管网末梢各一条，与出厂水各管各的。
-- 读数值与界值相同算高档；顺位相同并行使权时按代号序列排后者胜出（确定性，四季一致）。
INSERT IGNORE INTO t_zw_lim_rule (id, rule_code, rule_name, sample_type, th1_max, th2_max, th3_max, eff_start, eff_end, priority, status, del_flag, create_by, create_time, remark)
VALUES (100, 'CL-FW-2024', '出厂水余氯分档线(上年口径)', '出厂水', 0.30, 0.50, NULL, '2024-01-01 00:00:00', '2026-01-01 00:00:00', 10, 1, 0, 'seed', NOW(), '上年宽口径；严重超标档留空即不设此档'),
       (101, 'CL-FW-2026', '出厂水余氯分档线(今年收紧)', '出厂水', 0.20, 0.40, 0.60, '2026-01-01 00:00:00', NULL, 10, 0, 0, 'seed', NOW(), '今年收紧口径'),
       (102, 'CL-SW-2026', '水源水余氯分档线', '水源水', 0.20, 0.30, 0.50, '2026-01-01 00:00:00', NULL, 10, 0, 0, 'seed', NOW(), NULL),
       (103, 'CL-PN-2026', '管网末梢余氯分档线', '管网末梢', 0.15, 0.25, 0.40, '2026-01-01 00:00:00', NULL, 10, 0, 0, 'seed', NOW(), NULL);


-- 停水公告文案名录（字典 zw_stop_notice_tpl）：批准后公告文案一律从名录带出，占位符由系统填入，
-- 不再另请人抄录；名录里没有对应事由时不得自编文案。
INSERT IGNORE INTO t_sys_dict_type (id, dict_name, dict_type, status, create_by, create_time, remark)
VALUES (7010, '停水公告文案名录', 'zw_stop_notice_tpl', '0', 'seed', NOW(), '停供申请批准后对用户公告的事由模板，从名录带出不经人手抄录');

INSERT IGNORE INTO t_sys_dict_data (id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
VALUES (7011, 1, '水源枯竭停水公告', 'DEPLETION', 'zw_stop_notice_tpl', '', 'default', 'N', '0', 'seed', NOW(),
        '因水源枯竭，水源点【{siteNo}】{siteName}自即日起停止供水，请按以下方案取水：{altPlan}。给您带来的不便，敬请谅解。'),
       (7012, 2, '指标连续超标停水公告', 'EXCEED', 'zw_stop_notice_tpl', '', 'important', 'N', '0', 'seed', NOW(),
        '因水质指标连续超标，为保障用水安全，水源点【{siteNo}】{siteName}自即日起停止供水，替代供水方案：{altPlan}。恢复供水时间另行通知。'),
       (7013, 3, '检修停水公告', 'REPAIR', 'zw_stop_notice_tpl', '', 'info', 'N', '0', 'seed', NOW(),
        '因水厂/管网检修，水源点【{siteNo}】{siteName}将于即日起暂停供水，检修期间替代供水方案：{altPlan}。检修完成后即恢复供水。');

-- 停供申请菜单与按钮权限（挂在水质调度域目录下，perms 与控制器 @RequiresPermissions 逐字对齐）
INSERT IGNORE INTO t_sys_permission (id, name, descripion, url, is_blank, pid, perms, type, icon, order_num, visible, create_by, create_time)
VALUES (7020, '停供申请签核', '停供/限供申请台账', '/zwStopBill/view', 0, NULL, 'zw:zwStopBill:view', 1, 'layui-icon layui-icon-close-fill', 20, 0, 'seed', NOW()),
       (7021, '停供申请集合', '台账查询', '/zwStopBill/list', 0, 7020, 'zw:zwStopBill:list', 2, NULL, NULL, 0, 'seed', NOW()),
       (7022, '业务科提单', '三要素缺一挡回', '/zwStopBill/submit', 0, 7020, 'zw:zwStopBill:submit', 2, NULL, NULL, 0, 'seed', NOW()),
       (7023, '水质科核实', '近月数据对照、未完结处置单前置检查', '/zwStopBill/verify', 0, 7020, 'zw:zwStopBill:verify', 2, NULL, NULL, 0, 'seed', NOW()),
       (7024, '调度评估', '水量调配/管网影响两口各评', '/zwStopBill/evaluate', 0, 7020, 'zw:zwStopBill:evaluate', 2, NULL, NULL, 0, 'seed', NOW()),
       (7025, '主管批准', '批准后水源点退役并带出公告', '/zwStopBill/approve', 0, 7020, 'zw:zwStopBill:approve', 2, NULL, NULL, 0, 'seed', NOW()),
       (7026, '申请撤回', '撤回即终止锁死', '/zwStopBill/withdraw', 0, 7020, 'zw:zwStopBill:withdraw', 2, NULL, NULL, 0, 'seed', NOW());
