export type Capability = { id:string; name:string; description:string; active:boolean }
export type ContactInput = { name:string; email:string; role:string; notes:string }
export type CreateMatchInput = { accountName:string; industry:string; location:string; website:string|null; capabilityId:string; source:string|null; observedOn:string|null; observedFact:string|null; assumption:string|null; owner:string|null; hypothesis:string|null; nextAction:string|null; nextActionDate:string|null; contacts:ContactInput[] }
export type Match = { id:string; status:'DRAFT'|'ACTIVE'; accountName:string; capabilityName:string; owner:string|null; hypothesis:string|null; nextAction:string|null; nextActionDate:string|null; missingInformation:string[] }
