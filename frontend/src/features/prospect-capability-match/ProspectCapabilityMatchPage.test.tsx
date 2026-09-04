import { fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { App } from '../../app/App'

const capabilityId='11111111-1111-1111-1111-111111111111'
afterEach(()=>vi.restoreAllMocks())

describe('Prospect capability match',()=>{
  it('shows SaaS context and saves incomplete evidence as a draft',async()=>{
    vi.spyOn(globalThis,'fetch').mockResolvedValueOnce(new Response(JSON.stringify([{id:capabilityId,name:'Reduce fixture changeover time',description:'',active:true}]))).mockResolvedValueOnce(new Response(JSON.stringify([]))).mockResolvedValueOnce(new Response(JSON.stringify({id:'match-1',status:'DRAFT',accountName:'Northstar Assembly Systems',capabilityName:'Reduce fixture changeover time',owner:'Asha Patel',hypothesis:null,nextAction:null,nextActionDate:null,missingInformation:['Add the signal source.']})))
    render(<App/>); expect(screen.getByText('Brand Empiricism')).toBeInTheDocument(); expect(screen.getByText('Signed in as')).toBeInTheDocument()
    fireEvent.change(await screen.findByLabelText(/Capability/),{target:{value:capabilityId}}); fireEvent.change(screen.getByLabelText(/^Name/),{target:{value:'Mina Shah'}}); fireEvent.change(screen.getByLabelText(/^Email/),{target:{value:'mina@example.com'}})
    fireEvent.click(screen.getByRole('button',{name:'Save capability match'})); expect(await screen.findByText('DRAFT')).toBeInTheDocument(); expect(screen.getByText('Add the signal source.')).toBeInTheDocument()
  })
})
