package com.fc.v2.service.zw;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fc.v2.model.auto.TZwStopSign;

/**
 * 各关落名枚数的唯一点算法——顺着签核记录一笔笔累出来，库里没有别的计数口径，
 * 屏上摆的数与服务层回的数必须同出此处一次计数（少一笔这道就不算满）。
 *
 * <p>只数「本轮 + 有效 + 同意」的落名：
 * <ul>
 *   <li>业务提（0）/水质核（1）：有名即过，按落名人去重，本人连点两笔只算一笔；</li>
 *   <li>调度评（2）：两名点齐——水量调配 {@code DISP_QTY}、管网影响 {@code DISP_NET}
 *       两个角色各有一人，且必须是<b>两个不同的人</b>。同一人两口都签只算一人，
 *       不算点齐（各评各的、互不替签）；</li>
 *   <li>主管准（3）：主管有名即过。</li>
 * </ul>
 * 作废轮次（valid_flag=0）、不同意、撤回一律不进枚数。
 *
 * @author fuce
 * @date 2026-10-01
 */
public final class SignTally {

    /** 关口 */
    public static final int NODE_APPLY = 0;
    public static final int NODE_WATER_QA = 1;
    public static final int NODE_DISPATCH = 2;
    public static final int NODE_HEAD = 3;

    /** 落名角色 */
    public static final String ROLE_APPLY = "APPLY";
    public static final String ROLE_WQ = "WQ";
    public static final String ROLE_DISP_QTY = "DISP_QTY";
    public static final String ROLE_DISP_NET = "DISP_NET";
    public static final String ROLE_DISPATCH_HEAD = "DISPATCH_HEAD";

    /** 动作 */
    public static final int ACTION_AGREE = 0;
    public static final int ACTION_DISAGREE = 1;
    public static final int ACTION_WITHDRAW = 2;
    public static final int ACTION_HEAD_REJECT = 3;

    /** 关口 → 本关有效落名（按人去重后的账号集） */
    private final Map<Integer, Set<String>> actorsByNode = new HashMap<Integer, Set<String>>();
    /** 调度关：角色 → 落名人（同角色重复签只留一个） */
    private final Map<String, String> dispatchActorByRole = new HashMap<String, String>();

    private SignTally() {
    }

    /**
     * 顺着某一轮的签核记录点算。传入全量记录也无妨——轮次不符、已作废、非「同意」的一律不数。
     */
    public static SignTally of(List<TZwStopSign> records, int roundNo) {
        SignTally t = new SignTally();
        if (records == null) {
            return t;
        }
        for (TZwStopSign s : records) {
            if (s == null || s.getRoundNo() == null || s.getRoundNo().intValue() != roundNo) {
                continue;
            }
            if (s.getValidFlag() == null || s.getValidFlag().intValue() != 1) {
                continue;
            }
            if (s.getAction() == null || s.getAction().intValue() != ACTION_AGREE) {
                continue;
            }
            int node = s.getNodeNo() == null ? 0 : s.getNodeNo();
            String actor = s.getActor();
            if (actor == null || actor.trim().isEmpty()) {
                // 没落名账号的不当一笔——代签当没签，无账号更不能算数
                continue;
            }
            Set<String> actors = t.actorsByNode.get(node);
            if (actors == null) {
                actors = new HashSet<String>();
                t.actorsByNode.put(node, actors);
            }
            actors.add(actor.trim());
            if (node == NODE_DISPATCH && (ROLE_DISP_QTY.equals(s.getRoleCode())
                    || ROLE_DISP_NET.equals(s.getRoleCode()))) {
                // 同角色后写不覆盖先写的人；两口是否两个不同的人由 filled() 判
                String role = s.getRoleCode();
                if (!t.dispatchActorByRole.containsKey(role)) {
                    t.dispatchActorByRole.put(role, actor.trim());
                }
            }
        }
        return t;
    }

    /** 本关去重后的落名枚数（调度关 = 已签的不同角色数 0/1/2） */
    public int signedCount(int node) {
        if (node == NODE_DISPATCH) {
            return dispatchActorByRole.size();
        }
        Set<String> actors = actorsByNode.get(node);
        return actors == null ? 0 : actors.size();
    }

    /** 本关应落名数：业务提/水质核/主管各 1，调度评 2 */
    public static int needCount(int node) {
        return node == NODE_DISPATCH ? 2 : 1;
    }

    /** 本关是否已满：调度关要求两口都有人且不是同一人，其余关有名即满 */
    public boolean filled(int node) {
        if (node == NODE_DISPATCH) {
            String qty = dispatchActorByRole.get(ROLE_DISP_QTY);
            String net = dispatchActorByRole.get(ROLE_DISP_NET);
            // 同一人把两口都签了也只算一人，两名未点齐——代签、兼签都当没签满
            return qty != null && net != null && !qty.equals(net);
        }
        return signedCount(node) >= 1;
    }

    /** 调度关某角色的落名人（未签回 null） */
    public String dispatchActor(String roleCode) {
        return dispatchActorByRole.get(roleCode);
    }

    /** 本轮某关的有效落名账号（台账/倒查展示用，拷贝一份） */
    public List<String> actorsOf(int node) {
        Set<String> actors = actorsByNode.get(node);
        return actors == null ? new ArrayList<String>() : new ArrayList<String>(actors);
    }
}
