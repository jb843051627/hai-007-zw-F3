package com.fc.v2.service.zw;

import java.util.ArrayList;
import java.util.List;

import com.fc.v2.model.auto.TZwStopBill;
import com.fc.v2.model.auto.TZwStopSign;

/**
 * 一份停供申请「走到哪一关、这一关过没过、各关落了几人名」的唯一结论。
 *
 * <p>页面与台账只认服务层这套签核方法给回的本对象：当前关口、是否过满、各关落名枚数
 * 全由同一批签核记录经 {@link SignTally} 一次点算得出，不许页面另设格子代填、
 * 也不许屏上的数与库里的数是两份。另带逐笔签核轨迹供倒查（从主管准一笔笔退回业务提）。
 *
 * @author fuce
 * @date 2026-10-01
 */
public class ChainView {

    /** 报批情形 0在核 1已核讫 3已终止 */
    private final int status;
    /** 当前所在道 0..3 */
    private final int currentNode;
    /** 攒签轮次 */
    private final int roundNo;
    /** 各关（0..3）本关有效落名枚数，与库内出自同一次计数 */
    private final int[] signedCounts;
    /** 各关（0..3）应落名枚数 */
    private final int[] needCounts;
    /** 各关（0..3）是否已满 */
    private final boolean[] filled;
    /** 同一水源点当年第几份（从 1 起；第二份起前端当场点名） */
    private final int yearSeq;
    /** 逐笔签核轨迹（作废轮次也在其中，validFlag 分得清），按轮次/关口/时刻倒序 */
    private final List<TZwStopSign> trail;

    public ChainView(TZwStopBill bill, SignTally tally, int yearSeq, List<TZwStopSign> trail) {
        this.status = bill.getStatus() == null ? 0 : bill.getStatus();
        this.currentNode = bill.getNodeNo() == null ? 0 : bill.getNodeNo();
        this.roundNo = bill.getRoundNo() == null ? 1 : bill.getRoundNo();
        this.signedCounts = new int[SignTally.NODE_HEAD + 1];
        this.needCounts = new int[SignTally.NODE_HEAD + 1];
        this.filled = new boolean[SignTally.NODE_HEAD + 1];
        for (int node = SignTally.NODE_APPLY; node <= SignTally.NODE_HEAD; node++) {
            this.signedCounts[node] = tally.signedCount(node);
            this.needCounts[node] = SignTally.needCount(node);
            this.filled[node] = tally.filled(node);
        }
        this.yearSeq = yearSeq;
        this.trail = trail == null ? new ArrayList<TZwStopSign>() : trail;
    }

    public int getStatus() {
        return status;
    }

    public int getCurrentNode() {
        return currentNode;
    }

    public int getRoundNo() {
        return roundNo;
    }

    /** 某关已落名枚数（越界关回 0） */
    public int getSignedCount(int node) {
        return in(node) ? signedCounts[node] : 0;
    }

    /** 某关应落名枚数 */
    public int getNeedCount(int node) {
        return in(node) ? needCounts[node] : 0;
    }

    /** 某关过没过 */
    public boolean isFilled(int node) {
        return in(node) && filled[node];
    }

    public int getYearSeq() {
        return yearSeq;
    }

    /** 当年第二份及以后：当场单独点名 */
    public boolean isSecondOrLaterThisYear() {
        return yearSeq >= 2;
    }

    public List<TZwStopSign> getTrail() {
        return trail;
    }

    private static boolean in(int node) {
        return node >= SignTally.NODE_APPLY && node <= SignTally.NODE_HEAD;
    }
}
