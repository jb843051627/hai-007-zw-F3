package com.fc.v2.service.zw;

/**
 * 一道签核动作的结论——页面只凭它说话：过口、压住（前置未齐，不进不退）、
 * 落锤终止（写明不同意/撤回，单到此为止）三者必须分得清，不许混成一句「操作失败」。
 *
 * @author fuce
 * @date 2026-10-01
 */
public class StopActionResult {

    public enum Outcome {
        /** 动作落账，单子在关口上前进或收口（批准落定） */
        ADVANCED,
        /** 前置条件未齐压在本关（如名下还有未完结处置单、近月无检测数据），未终止、可再来 */
        BLOCKED,
        /** 本关写明不同意：单到此为止并锁死，要改只能另开新单 */
        VETOED,
        /** 动作不被受理（越关、单已落定、代签/兼签等），库内什么都没动 */
        REJECTED
    }

    private final Outcome outcome;
    private final String message;
    private final ChainView view;

    private StopActionResult(Outcome outcome, String message, ChainView view) {
        this.outcome = outcome;
        this.message = message;
        this.view = view;
    }

    public static StopActionResult advanced(ChainView view, String message) {
        return new StopActionResult(Outcome.ADVANCED, message, view);
    }

    public static StopActionResult blocked(String message) {
        return new StopActionResult(Outcome.BLOCKED, message, null);
    }

    public static StopActionResult vetoed(ChainView view, String message) {
        return new StopActionResult(Outcome.VETOED, message, view);
    }

    public static StopActionResult rejected(String message) {
        return new StopActionResult(Outcome.REJECTED, message, null);
    }

    public boolean isAllowed() {
        return outcome == Outcome.ADVANCED;
    }

    public Outcome getOutcome() {
        return outcome;
    }

    public String getMessage() {
        return message;
    }

    public ChainView getView() {
        return view;
    }
}
