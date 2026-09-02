import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { App } from './App'

describe('App',()=>{it('communicates the initial ETO workflow',()=>{render(<App/>);expect(screen.getByRole('heading',{name:'ETO CRM'})).toBeInTheDocument();expect(screen.getByText('Capability matches')).toBeInTheDocument()})})

