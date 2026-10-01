package com.fc.v2.service.zw;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fc.v2.model.auto.TZwStopSign;

/**
 * 各关落名枚数唯一点算法的用例：两口是否点齐、作废/他轮/不同意不进数、同人兼签不算两名。
 */
public class SignTallyTest {

    private static Date t() {
        return new Date(1_780_000_000_000L);
    }

    private static TZwStopSign sign(int round, int node, String role, String actor, int action, int valid) {
        TZwStopSign s = new TZwStopSign();
        s.setRoundNo(round);
        s.setNodeNo(node);
        s.setRoleCode(role);
        s.setActor(actor);
        s.setAction(action);
        s.setValidFlag(valid);
        s.setCreateTime(t());
        return s;
    }

    private static TZwStopSign agree(int round, int node, String role, String actor) {
        return sign(round, node, role, actor, SignTally.ACTION_AGREE, 1);
    }

    @Test
    public void singleActorGateFillsOnOneName() {
        List<TZwStopSign> all = new ArrayList<TZwStopSign>();
        all.add(agree(1, SignTally.NODE_APPLY, SignTally.ROLE_APPLY, "yw"));
        all.add(agree(1, SignTally.NODE_WATER_QA, SignTally.ROLE_WQ, "sz"));
        SignTally tally = SignTally.of(all, 1);
        assertTrue(tally.filled(SignTally.NODE_APPLY));
        assertTrue(tally.filled(SignTally.NODE_WATER_QA));
        assertFalse(tally.filled(SignTally.NODE_DISPATCH));
        assertFalse(tally.filled(SignTally.NODE_HEAD));
    }

    @Test
    public void dispatchNeedsTwoDifferentPeopleOnePerRole() {
        List<TZwStopSign> all = new ArrayList<TZwStopSign>();
        // 只到一位：停在本道
        all.add(agree(1, SignTally.NODE_DISPATCH, SignTally.ROLE_DISP_QTY, "dd-li"));
        SignTally one = SignTally.of(all, 1);
        assertEquals(1, one.signedCount(SignTally.NODE_DISPATCH));
        assertFalse(one.filled(SignTally.NODE_DISPATCH));

        // 两位不同的人各签一口：点齐
        all.add(agree(1, SignTally.NODE_DISPATCH, SignTally.ROLE_DISP_NET, "dd-wang"));
        SignTally two = SignTally.of(all, 1);
        assertEquals(2, two.signedCount(SignTally.NODE_DISPATCH));
        assertTrue(two.filled(SignTally.NODE_DISPATCH));
    }

    @Test
    public void samePersonSigningBothRolesIsNotTwo() {
        // 服务层本就拒收第二口；即使记录里混进来，点算也不认作两名——互不替签
        List<TZwStopSign> all = new ArrayList<TZwStopSign>();
        all.add(agree(1, SignTally.NODE_DISPATCH, SignTally.ROLE_DISP_QTY, "dd-li"));
        all.add(agree(1, SignTally.NODE_DISPATCH, SignTally.ROLE_DISP_NET, "dd-li"));
        SignTally tally = SignTally.of(all, 1);
        assertFalse(tally.filled(SignTally.NODE_DISPATCH));
    }

    @Test
    public void duplicateSignBySameActorCountsOnce() {
        List<TZwStopSign> all = new ArrayList<TZwStopSign>();
        all.add(agree(1, SignTally.NODE_WATER_QA, SignTally.ROLE_WQ, "sz"));
        all.add(agree(1, SignTally.NODE_WATER_QA, SignTally.ROLE_WQ, "sz"));
        SignTally tally = SignTally.of(all, 1);
        assertEquals(1, tally.signedCount(SignTally.NODE_WATER_QA));
    }

    @Test
    public void voidedOtherRoundAndDisagreeNeverCount() {
        List<TZwStopSign> all = new ArrayList<TZwStopSign>();
        all.add(agree(1, SignTally.NODE_DISPATCH, SignTally.ROLE_DISP_QTY, "dd-li"));
        all.add(sign(1, SignTally.NODE_DISPATCH, SignTally.ROLE_DISP_NET, "dd-wang",
                SignTally.ACTION_AGREE, 0)); // 主管打回后整轮作废
        all.add(agree(2, SignTally.NODE_DISPATCH, SignTally.ROLE_DISP_QTY, "dd-li")); // 新一轮只到一位
        all.add(sign(1, SignTally.NODE_WATER_QA, SignTally.ROLE_WQ, "sz2",
                SignTally.ACTION_DISAGREE, 1)); // 写明不同意不进枚数

        SignTally r2 = SignTally.of(all, 2);
        assertEquals(1, r2.signedCount(SignTally.NODE_DISPATCH));
        assertFalse(r2.filled(SignTally.NODE_DISPATCH));

        SignTally r1 = SignTally.of(all, 1);
        // 作废那笔不算，第一轮调度口只有水量一人
        assertEquals(1, r1.signedCount(SignTally.NODE_DISPATCH));
        assertEquals(0, r1.signedCount(SignTally.NODE_WATER_QA));
    }

    @Test
    public void blankActorIsNoSignature() {
        List<TZwStopSign> all = new ArrayList<TZwStopSign>();
        all.add(agree(1, SignTally.NODE_HEAD, SignTally.ROLE_DISPATCH_HEAD, "  "));
        SignTally tally = SignTally.of(all, 1);
        assertFalse(tally.filled(SignTally.NODE_HEAD));
        assertNull(tally.dispatchActor(SignTally.ROLE_DISP_QTY));
    }
}
