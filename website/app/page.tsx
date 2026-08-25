export default function Home() {
  return (
    <main>
      <section className="hero" id="top">
        <nav className="nav shell" aria-label="Primary navigation">
          <a className="brand" href="#top" aria-label="agent4j home"><span className="brand-mark">a4</span><span>agent4j</span></a>
          <div className="nav-links"><a href="#runtime">Runtime</a><a href="#agents">Agents</a><a href="#start">Start here</a></div>
          <a className="nav-github" href="https://github.com/agent4j/agent4j">GitHub <span aria-hidden="true">↗</span></a>
        </nav>
        <div className="hero-grid shell">
          <div className="hero-copy">
            <p className="eyebrow"><span className="signal" /> A Java-native coding agent harness</p>
            <h1>Build agents with a runtime you can inspect.</h1>
            <p className="lede">Streaming model calls, durable JSONL sessions, workspace tools, and task queues—composed as ordinary Java.</p>
            <div className="hero-actions"><a className="button primary" href="#runtime">Explore the runtime <span>↓</span></a><a className="button secondary" href="#start">Quick start <span>→</span></a></div>
            <div className="proofs"><span>Provider-neutral</span><span>Event-driven</span><span>Session-aware</span></div>
          </div>
          <div className="runtime-card" aria-label="Agent runtime event flow">
            <div className="runtime-topline"><span>agent4j/runtime</span><span className="live"><i /> LIVE</span></div>
            <div className="runtime-content">
              <p className="runtime-label">ONE TURN, MADE LEGIBLE</p>
              <div className="event event-model"><b>01</b><span className="event-icon">✦</span><div><strong>Model stream</strong><small>assistant deltas arrive</small></div><em>OPEN</em></div><div className="connector"><span /></div>
              <div className="event event-loop"><b>02</b><span className="event-icon">↻</span><div><strong>Agent loop</strong><small>decides the next action</small></div><em>RUNNING</em></div><div className="connector"><span /></div>
              <div className="event event-tool"><b>03</b><span className="event-icon">⌘</span><div><strong>Workspace tool</strong><small>read · patch · test</small></div><em>BOUND</em></div><div className="connector"><span /></div>
              <div className="event event-session"><b>04</b><span className="event-icon">≡</span><div><strong>JSONL session</strong><small>records the turn</small></div><em>SEALED</em></div>
            </div>
            <div className="runtime-footer"><span>SESSION / 27f1...b8e2</span><span>4 EVENTS</span></div>
          </div>
        </div>
        <div className="grid-haze" />
      </section>
      <section className="architecture section shell" id="runtime">
        <div className="section-intro"><p className="eyebrow">THE RUNTIME</p><h2>Small modules. One coherent loop.</h2><p>Use only the layers you need, or run the full coding-agent stack through the CLI.</p></div>
        <div className="module-grid">
          <article className="module"><span>01</span><h3>agent4j-ai</h3><p>Provider integration and streamed model events.</p><code>ModelProvider</code></article>
          <article className="module"><span>02</span><h3>agent4j-core</h3><p>Sessions, state, queues, and the portable runtime.</p><code>AgentSession</code></article>
          <article className="module"><span>03</span><h3>agent4j-coding</h3><p>Workspace operations and the coding-agent loop.</p><code>ToolExecutor</code></article>
          <article className="module"><span>04</span><h3>agent4j-agents</h3><p>Focused, constrained workflows built on the core.</p><code>BuildTriageAgent</code></article>
          <article className="module"><span>05</span><h3>agent4j-cli</h3><p>An interactive terminal interface for agent sessions.</p><code>Agent4jCli</code></article>
        </div>
      </section>
      <section className="path-section"><div className="shell execution-grid"><div><p className="eyebrow">AN INSPECTABLE PATH</p><h2>Every useful action leaves a useful trace.</h2></div><ol className="execution-path"><li><span>01</span><div><strong>Prompt enters a session</strong><p>Messages and settings become durable state.</p></div></li><li><span>02</span><div><strong>The model streams its answer</strong><p>Events reach the UI as they happen.</p></div></li><li><span>03</span><div><strong>Tools execute under policy</strong><p>Operations are explicit, bounded, and observable.</p></div></li><li><span>04</span><div><strong>The session writes its history</strong><p>Replay and diagnosis stay close to the runtime.</p></div></li></ol></div></section>
      <section className="agents section shell" id="agents">
        <div className="section-intro agents-intro"><p className="eyebrow">READY-MADE WORKFLOWS</p><div><h2>Two agents, intentionally constrained.</h2><p>Start with a dependable workflow, then build your own without giving it unrestricted shell access.</p></div></div>
        <div className="agent-grid">
          <article className="agent-card triage"><div className="agent-card-top"><span className="tag">BUILD TRIAGE</span><span>01</span></div><h3>Find the failure before fixing it.</h3><p>Runs the Maven test suite, groups failures, and turns noisy output into an actionable diagnosis.</p><div className="agent-command"><span>$</span> mvn test <i>↗</i></div><div className="agent-rule"><span>Allowed surface</span><strong>Test execution only</strong></div></article>
          <article className="agent-card reviewer"><div className="agent-card-top"><span className="tag">JAVA PR REVIEWER</span><span>02</span></div><h3>Review the change, not the entire world.</h3><p>Reads the Git diff and reports concrete concerns about Java correctness, tests, and maintainability.</p><div className="agent-command"><span>$</span> git diff <i>↗</i></div><div className="agent-rule"><span>Allowed surface</span><strong>Read-only repository access</strong></div></article>
        </div>
      </section>
      <section className="testing shell"><div className="testing-copy"><p className="eyebrow">TEST THE LOOP</p><h2>A harness should be as testable as the code it changes.</h2><p>Swap in fake providers and workspace operations to exercise behavior without a live model or a real filesystem.</p></div><div className="testing-diagram"><div className="test-node">Fake provider<small>scripted stream</small></div><div className="test-arrow">→</div><div className="test-node active">Agent loop<small>assert events</small></div><div className="test-arrow">→</div><div className="test-node">Fake workspace<small>controlled effects</small></div></div></section>
      <section className="start-section" id="start"><div className="shell start-card"><div><p className="eyebrow">MAKE THE LOOP YOURS</p><h2>Bring your provider. Keep your control.</h2><p>Configure an OpenAI-compatible endpoint, launch the CLI, and start an agent session from your terminal.</p></div><div className="quickstart"><div><span>01</span><code>export AGENT4J_API_KEY=…</code></div><div><span>02</span><code>export AGENT4J_MODEL=…</code></div><div><span>03</span><code>mvn -q -pl agent4j-cli exec:java</code></div></div></div></section>
      <footer className="shell"><a className="brand" href="#top"><span className="brand-mark">a4</span><span>agent4j</span></a><p>Java-native agent harness · PI-inspired behavioral compatibility</p><a href="#top">Back to top ↑</a></footer>
    </main>
  );
}
