-- 测试用 H2（MODE=MySQL）内存库：只列新签核链路与采样生成用到的列，MySQL 专有子句省略。
CREATE TABLE t_zw_source (
  id bigint NOT NULL,
  site_no varchar(64),
  site_name varchar(128),
  site_type varchar(32),
  road_name varchar(128),
  th1_max decimal(12,2),
  th2_max decimal(12,2),
  th3_max decimal(12,2),
  status int,
  del_flag int DEFAULT 0,
  create_by varchar(64),
  create_time datetime,
  update_by varchar(64),
  update_time datetime,
  remark varchar(500),
  PRIMARY KEY (id)
);

CREATE TABLE t_zw_stop_bill (
  id bigint NOT NULL,
  bill_no varchar(64),
  node_no int,
  sign_mode int,
  need_count int,
  sign_count int,
  round_no int,
  site_no varchar(64),
  stop_reason varchar(16),
  alt_plan varchar(500),
  apply_year int,
  year_seq int,
  notice_text varchar(500),
  approved_time datetime,
  status int,
  del_flag int DEFAULT 0,
  create_by varchar(64),
  create_time datetime,
  update_by varchar(64),
  update_time datetime,
  remark varchar(500),
  PRIMARY KEY (id),
  UNIQUE (bill_no),
  UNIQUE (site_no, apply_year, year_seq)
);

CREATE TABLE t_zw_stop_sign (
  id bigint NOT NULL,
  bill_id bigint NOT NULL,
  round_no int NOT NULL,
  node_no int NOT NULL,
  role_code varchar(32) NOT NULL,
  actor varchar(64) NOT NULL,
  action int NOT NULL,
  opinion varchar(500),
  valid_flag int NOT NULL DEFAULT 1,
  create_by varchar(64),
  create_time datetime NOT NULL,
  PRIMARY KEY (id),
  UNIQUE (bill_id, round_no, node_no, role_code, actor, action)
);

CREATE TABLE t_zw_case_flow (
  id bigint NOT NULL,
  biz_no varchar(64),
  site_no varchar(64),
  stage int,
  status int,
  content varchar(255),
  last_action varchar(64),
  del_flag int DEFAULT 0,
  create_by varchar(64),
  create_time datetime,
  update_by varchar(64),
  update_time datetime,
  remark varchar(500),
  PRIMARY KEY (id)
);

CREATE TABLE t_zw_imp_row (
  id bigint NOT NULL,
  batch_no varchar(64),
  row_no int,
  site_no varchar(64),
  item_code varchar(64),
  qty decimal(12,2),
  sample_type varchar(32),
  test_at datetime,
  grade_level int,
  rule_code varchar(64),
  rule_eff_date datetime,
  status int,
  del_flag int DEFAULT 0,
  create_by varchar(64),
  create_time datetime,
  update_by varchar(64),
  update_time datetime,
  remark varchar(500),
  PRIMARY KEY (id)
);

CREATE TABLE t_sys_dict_data (
  id bigint NOT NULL,
  dict_sort int,
  dict_label varchar(100),
  dict_value varchar(100),
  dict_type varchar(100),
  css_class varchar(100),
  list_class varchar(100),
  is_default char(1),
  status char(1),
  create_by varchar(64),
  create_time datetime,
  update_by varchar(64),
  update_time datetime,
  remark varchar(500),
  PRIMARY KEY (id)
);

CREATE TABLE t_zw_sam_plan (
  id bigint NOT NULL,
  plan_no varchar(64),
  site_no varchar(64),
  team_id bigint,
  period_days int,
  amount decimal(12,2),
  first_due date,
  eff_start datetime,
  eff_end datetime,
  content varchar(255),
  status int,
  del_flag int DEFAULT 0,
  create_by varchar(64),
  create_time datetime,
  update_by varchar(64),
  update_time datetime,
  remark varchar(500),
  PRIMARY KEY (id),
  UNIQUE (plan_no)
);

CREATE TABLE t_zw_sam_task (
  id bigint NOT NULL,
  item_no varchar(64),
  plan_no varchar(64),
  site_no varchar(64),
  team_id bigint,
  due_at datetime,
  amount decimal(12,2),
  dispatch_at datetime,
  cancel_at datetime,
  content varchar(255),
  hang_reason varchar(255),
  status int,
  del_flag int DEFAULT 0,
  create_by varchar(64),
  create_time datetime,
  update_by varchar(64),
  update_time datetime,
  remark varchar(500),
  PRIMARY KEY (id),
  UNIQUE (item_no),
  UNIQUE (plan_no, site_no, due_at)
);

CREATE TABLE t_sys_department (
  id bigint NOT NULL PRIMARY KEY
);
