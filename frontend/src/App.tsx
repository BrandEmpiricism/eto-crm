const workflow = ['Target accounts', 'Capability matches', 'Qualified needs', 'Engineering handoffs']

export function App() {
  return <div className="app-shell">
    <header><p className="eyebrow">Engineered-to-order</p><h1>ETO CRM</h1><p>Turn manufacturing signals into engineering-ready opportunities.</p></header>
    <main>
      <section aria-labelledby="focus-heading"><p className="section-label">Current product focus</p><h2 id="focus-heading">Find the right account. Match the right capability.</h2><p>Begin with an observed manufacturing need, connect it to a capability, and make the next action clear.</p><button type="button">Create prospective account</button></section>
      <nav aria-label="ETO workflow"><ol>{workflow.map((step,index)=><li key={step}><span>{String(index+1).padStart(2,'0')}</span>{step}</li>)}</ol></nav>
    </main>
  </div>
}

