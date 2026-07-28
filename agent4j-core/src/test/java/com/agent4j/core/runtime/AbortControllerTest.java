package com.agent4j.core.runtime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AbortControllerTest {
    @Test
    void exposesSingleAbortReasonThroughSignal() {
        AbortController controller = new AbortController();
        AbortSignal signal = controller.signal();

        assertThat(signal.aborted()).isFalse();
        assertThat(controller.abort("stop")).isTrue();
        assertThat(controller.abort("other")).isFalse();

        assertThat(signal.aborted()).isTrue();
        assertThat(signal.reason()).contains("stop");
        assertThatThrownBy(signal::throwIfAborted)
                .isInstanceOf(AgentAbortException.class)
                .hasMessage("stop");
    }
}
