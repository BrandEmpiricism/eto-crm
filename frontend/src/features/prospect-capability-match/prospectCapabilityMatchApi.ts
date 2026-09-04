import type { Capability, CreateMatchInput, Match } from './prospectCapabilityMatchTypes'

async function read<T>(response:Response):Promise<T>{
  const payload=await response.json() as T|{detail?:string}
  if(!response.ok) throw new Error(typeof payload==='object'&&payload!==null&&'detail' in payload&&payload.detail?payload.detail:'The request could not be completed.')
  return payload as T
}
export async function listCapabilities(){return read<Capability[]>(await fetch('/api/capabilities'))}
export async function loadOwnerQueue(owner:string){return read<Match[]>(await fetch(`/api/prospecting/work-queue?owner=${encodeURIComponent(owner)}`))}
export async function createMatch(input:CreateMatchInput){return read<Match>(await fetch('/api/prospecting/matches',{method:'POST',headers:{'Content-Type':'application/json','X-Actor':'demo-business-development-user'},body:JSON.stringify(input)}))}
