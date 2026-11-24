import type {LibraryMetadata,Project,ProjectFile,ProjectFolder} from './types'

export interface LibraryScope {
  id:string
  personal:boolean
  readonly:boolean
}
export interface LibraryDirectory extends LibraryMetadata {
  key:string
  name:string
  localId:string
  parent?:LibraryDirectory
  scope:LibraryScope
  projectRoot:boolean
  children:LibraryDirectory[]
  files:LibraryEntry[]
}
export interface LibraryEntry {
  key:string
  file:ProjectFile
  directory:LibraryDirectory
}
type Contents={files:ProjectFile[];folders?:ProjectFolder[]}
const keyFor=(scope:LibraryScope,id:string)=>JSON.stringify([scope.personal?'personal':'project',scope.id,id])
const sorter=new Intl.Collator('zh-CN',{numeric:true})

export function directoryIndex(source:Contents,scope:LibraryScope,projects:Project[]) {
  const directories=new Map<string,LibraryDirectory>()
  const files:LibraryEntry[]=[]
  function create(name:string,id:string,owner:LibraryScope,projectRoot=false):LibraryDirectory {
    const node:LibraryDirectory={key:keyFor(owner,id),name,localId:id,scope:owner,projectRoot,children:[],files:[]}
    directories.set(node.key,node)
    return node
  }
  function attach(node:LibraryDirectory,parent:LibraryDirectory){
    node.parent=parent
    parent.children.push(node)
  }
  function populate(base:LibraryDirectory,contents:Contents){
    const owner=base.scope
    const folders=(contents.folders || []).filter(f=>!f.projectId)
    for(const f of folders)Object.assign(create(f.name,f.id,owner),{
      time:f.time,userId:f.userId,modifiedAt:f.modifiedAt,modifiedBy:f.modifiedBy,
    })
    for(const f of folders){
      const parent=directories.get(keyFor(owner,f.parentId)) || base
      attach(directories.get(keyFor(owner,f.id))!,parent)
    }
    for(const file of contents.files){
      const directory=directories.get(keyFor(owner,file.folderId || '')) || base
      const entry={key:keyFor(owner,file.id),file,directory}
      directory.files.push(entry)
      files.push(entry)
    }
  }
  const root=create(scope.personal?'我的资料库':'项目资料库','',scope)
  populate(root,source)
  const byProject=new Map(projects.map(p=>[p.id,p]))
  for(const mount of source.folders || []){
    if(!scope.personal || !mount.projectId)continue
    const project=byProject.get(mount.projectId)
    if(!project)continue
    const owner={id:project.id,personal:false,readonly:['待审核','待修改','已归档','已关闭'].includes(project.status)}
    const dir=create(project.name,'',owner,true)
    Object.assign(dir,{time:project.created,userId:project.leaderId,modifiedAt:project.modifiedAt,modifiedBy:project.modifiedBy})
    attach(dir,root)
    populate(dir,project)
  }
  for(const dir of directories.values())dir.children.sort((a,b)=>Number(b.projectRoot)-Number(a.projectRoot)||sorter.compare(a.name,b.name))
  return {root,directories,files}
}

export function trail(folder:LibraryDirectory){
  const path:LibraryDirectory[]=[]
  for(let node:LibraryDirectory|undefined=folder;node;node=node.parent)path.unshift(node)
  return path
}
