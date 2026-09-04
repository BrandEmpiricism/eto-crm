import { useEffect, useState } from 'react'
import { createMatch, listCapabilities, loadOwnerQueue } from './prospectCapabilityMatchApi'
import { MatchOutcome } from './MatchOutcome'
import { OwnerWorkQueue } from './OwnerWorkQueue'
import { ProspectMatchForm } from './ProspectMatchForm'
import type { Capability, CreateMatchInput, Match } from './prospectCapabilityMatchTypes'
import './ProspectCapabilityMatchPage.css'

export function ProspectCapabilityMatchPage(){
  const [capabilities,setCapabilities]=useState<Capability[]>([]),[owner,setOwner]=useState('Asha Patel'),[result,setResult]=useState<Match|null>(null),[queue,setQueue]=useState<Match[]>([]),[error,setError]=useState(''),[saving,setSaving]=useState(false)
  useEffect(()=>{void listCapabilities().then(setCapabilities).catch(reason=>setError(message(reason)))},[])
  useEffect(()=>{if(owner.trim())void loadOwnerQueue(owner.trim()).then(setQueue).catch(reason=>setError(message(reason)));else setQueue([])},[owner])
  async function submit(input:CreateMatchInput){setSaving(true);setError('');setResult(null);try{const saved=await createMatch(input);setResult(saved);if(saved.status==='ACTIVE')setQueue(await loadOwnerQueue(owner.trim()))}catch(reason){setError(message(reason))}finally{setSaving(false)}}
  return <><div className="feature-hero"><p className="eyebrow">Prospecting</p><h1>Prospect capability match</h1><p>Capture what you observed, connect it to a proven capability, and make the next action unambiguous.</p></div><main className="match-layout"><ProspectMatchForm capabilities={capabilities} owner={owner} saving={saving} onOwnerChange={setOwner} onSubmit={input=>void submit(input)}/><aside aria-label="Match outcome"><MatchOutcome result={result} error={error}/><OwnerWorkQueue owner={owner} matches={queue}/></aside></main></>
}
function message(reason:unknown){return reason instanceof Error?reason.message:'The request could not be completed.'}
