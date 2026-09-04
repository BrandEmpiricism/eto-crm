import { ProspectCapabilityMatchPage } from '../features/prospect-capability-match/ProspectCapabilityMatchPage'

export function App() {
  return <div className="app-shell">
    <header className="app-header">
      <a className="product" href="/" aria-label="ETO CRM home"><span className="logo" aria-hidden="true">E</span><strong>ETO CRM</strong></a>
      <div className="session-context"><span><small>Company</small>Brand Empiricism</span><span><small>Signed in as</small>Asha Patel</span></div>
    </header>
    <ProspectCapabilityMatchPage />
  </div>
}
