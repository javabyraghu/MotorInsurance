import { useState } from 'react';
import {
  Activity, ArrowDownRight, ArrowRight, ArrowUpRight, Bell, CalendarDays,
  CarFront, Check, ChevronDown, CircleHelp, ClipboardCheck, Clock3, FileText,
  Gauge, LifeBuoy, Menu, Plus, Shield, ShieldCheck, Sparkles, Wrench, X
} from 'lucide-react';

const initialPolicies = [
  { id: 'MS-2849-7710', name: 'Honda City ZX', plate: 'MH 02 FH 4821', cover: 'Comprehensive', expiry: '24 Nov 2026', premium: '₹18,450', color: 'coral', icon: 'sedan' },
  { id: 'MS-1572-3094', name: 'Royal Enfield Classic 350', plate: 'MH 02 KV 1905', cover: 'Comprehensive', expiry: '08 Feb 2027', premium: '₹3,280', color: 'mint', icon: 'bike' },
];
const initialClaims = [{ id: 'CL-2408-0192', title: 'Side mirror damage', date: '18 Aug 2026', status: 'In review', color: 'orange' }];

function App() {
  const [active, setActive] = useState('Overview');
  const [policies, setPolicies] = useState(initialPolicies);
  const [claims, setClaims] = useState(initialClaims);
  const [modal, setModal] = useState('');
  const [toast, setToast] = useState('');
  const [menuOpen, setMenuOpen] = useState(false);

  const notify = (message) => { setToast(message); window.setTimeout(() => setToast(''), 3200); };

  async function submitQuote(e) {
    e.preventDefault();
    const data = Object.fromEntries(new FormData(e.currentTarget));
    const quote = { customerName: 'Aarav Mehta', vehicleMake: data.vehicleMake, vehicleRegistration: data.registration, coverType: data.coverType };
    try {
      const base = import.meta.env.VITE_QUOTE_API_URL || 'http://localhost:8081';
      const response = await fetch(`${base}/api/quotes`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(quote) });
      if (!response.ok) throw new Error('Quote service unavailable');
      const created = await response.json();
      notify(`Quote ready — ${created.id.slice(0, 8).toUpperCase()}`);
    } catch {
      notify('Quote request saved — connect the quote service to calculate your price.');
    }
    setModal('');
  }

  async function submitClaim(e) {
    e.preventDefault();
    const data = Object.fromEntries(new FormData(e.currentTarget));
    const claim = { policyNumber: data.policyNumber, vehicleRegistration: data.registration, incidentType: data.type, incidentDate: data.date, description: data.description };
    let submitted = false;
    try {
      const base = import.meta.env.VITE_CLAIMS_API_URL || 'http://localhost:8083';
      const response = await fetch(`${base}/api/claims`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(claim) });
      if (response.ok) { const result = await response.json(); claim.id = result.id; submitted = true; }
    } catch { /* Dashboard remains useful while backend services are being set up. */ }
    setClaims([{ id: claim.id ? `CL-${claim.id.slice(0, 8).toUpperCase()}` : `CL-${Date.now().toString().slice(-8)}`, title: data.type, date: new Date(data.date).toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' }), status: 'Submitted', color: 'blue' }, ...claims]);
    setModal('');
    notify(submitted ? 'Claim submitted successfully.' : 'Claim added to your dashboard. Start the claims service to sync it.');
  }

  const nav = [
    { label: 'Overview', icon: Gauge }, { label: 'My policies', icon: Shield },
    { label: 'Claims', icon: ClipboardCheck }, { label: 'Documents', icon: FileText },
  ];

  return <div className="app-shell">
    <aside className={`sidebar ${menuOpen ? 'sidebar-open' : ''}`}>
      <a className="brand" href="#overview" onClick={() => setActive('Overview')}><span className="brand-mark"><ShieldCheck size={22} strokeWidth={2.2} /></span><span>motor<span className="brand-light">sure</span><small>INSURANCE, MADE EASY</small></span></a>
      <div className="workspace-label">YOUR ACCOUNT</div>
      <nav className="main-nav">{nav.map(({ label, icon: Icon }) => <button key={label} className={`nav-link ${active === label ? 'selected' : ''}`} onClick={() => { setActive(label); setMenuOpen(false); if (label !== 'Overview') notify(`${label} view is coming in your next build increment.`); }}><Icon size={18} /><span>{label}</span>{label === 'Claims' && <span className="nav-count">1</span>}</button>)}</nav>
      <div className="sidebar-spacer" />
      <div className="help-card"><div className="help-spark"><Sparkles size={16} /></div><strong>Need a hand?</strong><p>Our team is here for you, every step of the way.</p><button onClick={() => notify('Support: hello@motorsure.in · 1800 123 4567')}>Talk to support <ArrowRight size={14} /></button></div>
      <div className="side-user"><div className="avatar">AM</div><div><strong>Aarav Mehta</strong><span>Personal account</span></div><button aria-label="Account menu"><ChevronDown size={16} /></button></div>
    </aside>

    <main className="main-content">
      <header className="topbar"><div className="mobile-brand"><button className="icon-button mobile-menu" onClick={() => setMenuOpen(!menuOpen)} aria-label="Open navigation">{menuOpen ? <X size={20} /> : <Menu size={20} />}</button><span className="brand-mark"><ShieldCheck size={20} /></span><strong>motorsure</strong></div><div className="breadcrumbs"><span>My account</span><span className="crumb-slash">/</span><strong>{active}</strong></div><div className="top-actions"><span className="top-date"><CalendarDays size={15} /> Saturday, 24 Oct 2026</span><button className="icon-button notification-button" aria-label="Notifications" onClick={() => notify('You’re all caught up.')}><Bell size={18} /><i /></button><div className="avatar top-avatar">AM</div></div></header>

      <div className="page-wrap">
        <section className="welcome-row"><div><div className="eyebrow"><span className="live-dot" /> YOUR MOTOR COVER, AT A GLANCE</div><h1>Good morning, Aarav <span className="wave">✳</span></h1><p className="welcome-sub">Everything you need to keep moving, all in one place.</p></div><button className="primary-button" onClick={() => setModal('quote')}><Plus size={17} /> Get a new quote</button></section>

        <section className="hero-card">
          <div className="hero-copy"><div className="hero-pill"><ShieldCheck size={13} /> YOU’RE PROTECTED</div><h2>Good things happen<br />when you’re <em>covered.</em></h2><p>Your vehicles are protected. We’ll make sure it stays that way.</p><button className="hero-link" onClick={() => setModal('claim')}>Make a claim <ArrowRight size={15} /></button></div>
          <div className="hero-art" aria-hidden="true"><div className="sun-orb"/><div className="hero-ring ring-one"/><div className="hero-ring ring-two"/><div className="road-line"/><div className="hero-car"><div className="car-window"/><div className="car-wheel wheel-a"/><div className="car-wheel wheel-b"/><div className="car-light"/></div><div className="hero-shield"><ShieldCheck size={36} /></div><span className="art-star star-a">✳</span><span className="art-star star-b">✦</span><span className="art-dot dot-a"/><span className="art-dot dot-b"/></div>
          <div className="hero-index"><span>01</span><i/><span>03</span></div>
        </section>

        <section className="metric-grid">
          <div className="metric-card"><div className="metric-top"><span>Active policies</span><span className="metric-icon green"><Shield size={17} /></span></div><div className="metric-value">{policies.length}<span className="metric-trend"><ArrowUpRight size={14} /> +1 this year</span></div><div className="metric-foot"><span className="mini-bars"><i/><i/><i/><i/><i/><i/><i/><i/><i/><i/></span><span>All vehicles covered</span></div></div>
          <div className="metric-card"><div className="metric-top"><span>Open claims</span><span className="metric-icon amber"><Activity size={17} /></span></div><div className="metric-value">{claims.filter(c => c.status !== 'Settled').length}<span className="metric-note">Needs your attention</span></div><div className="metric-foot"><span className="status-small"><i/> 1 claim in review</span><button onClick={() => setActive('Claims')}>View claims <ArrowRight size={13}/></button></div></div>
          <div className="metric-card premium-metric"><div className="metric-top"><span>Premium paid this year</span><span className="metric-icon lilac"><ArrowDownRight size={17} /></span></div><div className="metric-value">₹21,730</div><div className="metric-foot"><span>Across all your vehicles</span><span className="period-tag">2026 <ChevronDown size={12}/></span></div></div>
        </section>

        <section className="content-grid">
          <div className="panel policies-panel"><div className="panel-heading"><div><div className="section-kicker">YOUR COVER</div><h3>My policies <span className="count-badge">{policies.length}</span></h3></div><button className="text-button" onClick={() => { setActive('My policies'); notify('All policies are shown here.'); }}>View all <ArrowRight size={15} /></button></div>
            <div className="policy-list">{policies.map((policy) => <article className="policy-item" key={policy.id}><div className={`vehicle-icon ${policy.color}`}><CarFront size={21}/></div><div className="policy-main"><div className="policy-title-row"><strong>{policy.name}</strong><span className="cover-tag"><i/> {policy.cover}</span></div><div className="policy-meta"><span className="plate">{policy.plate}</span><span className="meta-dot"/>{policy.id}</div></div><div className="policy-expiry"><span>RENEWS ON</span><strong>{policy.expiry}</strong></div><button className="row-arrow" aria-label={`View ${policy.name} policy`} onClick={() => notify(`${policy.name}: ${policy.id}`)}><ArrowRight size={17}/></button></article>)}</div>
            <button className="add-policy" onClick={() => setModal('quote')}><span><Plus size={17}/></span><strong>Add another vehicle</strong><small>Get a quote in under 2 minutes</small><ArrowRight size={16}/></button>
          </div>

          <div className="panel claims-panel"><div className="panel-heading"><div><div className="section-kicker">WE’VE GOT YOUR BACK</div><h3>Recent claims</h3></div><button className="more-button" aria-label="Claim help" onClick={() => notify('For claim help, call 1800 123 4567')}><CircleHelp size={17}/></button></div><div className="claims-list">{claims.slice(0, 3).map(c => <div className="claim-item" key={c.id}><div className={`claim-indicator ${c.color}`}><Wrench size={16}/></div><div className="claim-info"><strong>{c.title}</strong><span>{c.id} · {c.date}</span></div><span className={`claim-status ${c.color}`}><i/>{c.status}</span></div>)}</div><button className="claim-help" onClick={() => setModal('claim')}><LifeBuoy size={16}/><span><strong>Something happened?</strong><small>We’ll help you get back on the road.</small></span><ArrowRight size={16}/></button></div>
        </section>

        <section className="renewal-banner"><div className="renewal-icon"><Clock3 size={20}/></div><div><strong>Your Honda City policy renews in 31 days</strong><span>Stay covered without missing a beat. Renew in just a few taps.</span></div><button onClick={() => notify('Renewal journey is ready for the next build increment.')}>Review renewal <ArrowRight size={15}/></button><button className="banner-close" aria-label="Dismiss renewal reminder" onClick={(e) => e.currentTarget.closest('.renewal-banner').remove()}><X size={16}/></button></section>
        <footer className="footer"><span>© 2026 MotorSure Insurance Services</span><div><button onClick={() => notify('Privacy information coming soon.')}>Privacy</button><button onClick={() => notify('Terms information coming soon.')}>Terms</button><button onClick={() => notify('Contact: hello@motorsure.in')}>Contact</button></div><span className="footer-made">Here for every mile <span>♥</span></span></footer>
      </div>
    </main>

    {modal && <div className="modal-backdrop" onMouseDown={(e) => e.target === e.currentTarget && setModal('')}><div className="modal-card"><div className="modal-head"><div><div className="section-kicker">{modal === 'quote' ? 'A FRESH START' : 'WE’RE HERE TO HELP'}</div><h2>{modal === 'quote' ? 'Get a quick quote' : 'Start a claim'}</h2></div><button className="icon-button" onClick={() => setModal('')} aria-label="Close"><X size={19}/></button></div>{modal === 'quote' ? <form onSubmit={submitQuote}><label>Vehicle make<input name="vehicleMake" required placeholder="e.g. Hyundai Creta" /></label><label>Registration number<input name="registration" required placeholder="MH 02 AB 1234" /></label><label>Cover type<select name="coverType"><option>Comprehensive</option><option>Third party</option></select></label><div className="modal-footnote"><ShieldCheck size={15}/> Your details stay safe and secure.</div><button className="primary-button modal-submit" type="submit">See my quote <ArrowRight size={16}/></button></form> : <form onSubmit={submitClaim}><label>Policy<select name="policyNumber">{policies.map(p => <option value={p.id} key={p.id}>{p.name} · {p.id}</option>)}</select></label><label>Vehicle registration<input name="registration" required placeholder="MH 02 AB 1234" /></label><div className="form-row"><label>Incident type<select name="type"><option>Accidental damage</option><option>Windshield damage</option><option>Theft</option><option>Third party damage</option></select></label><label>Incident date<input name="date" type="date" required defaultValue={new Date().toISOString().slice(0,10)} /></label></div><label>Tell us what happened<textarea name="description" required placeholder="A few details help us get started…" rows="3"/></label><div className="modal-footnote"><ShieldCheck size={15}/> A claims specialist will be in touch.</div><button className="primary-button modal-submit" type="submit">Submit claim <ArrowRight size={16}/></button></form>}</div></div>}
    {toast && <div className="toast"><span><Check size={15}/></span>{toast}</div>}
  </div>;
}

export default App;
