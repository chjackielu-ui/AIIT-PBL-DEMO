import type {Message, ProjectFile} from './types'

export function attachmentIds(m: Message): string[] {
  return [...new Set([...(m.fileIds || []), ...(m.fileId ? [m.fileId] : [])])]
}

export function isCodeFile(f: ProjectFile) {
  return /\.(js|jsx|ts|tsx|vue|java|py|css|scss|html|xml|json|md|txt|sql|yml|yaml|c|cpp|h|go|rs|sh|ps1|csv)$/i.test(f.name)
    || f.type.startsWith('text/') || f.type === 'application/json'
}

export function codeLanguage(name: string) {
  const ext = name.split('.').at(-1)?.toLowerCase() || ''
  const map: Record<string,string> = {js:'javascript',jsx:'javascript',ts:'typescript',tsx:'typescript',vue:'html',java:'java',py:'python',css:'css',scss:'scss',html:'html',xml:'xml',json:'json',md:'markdown',sql:'sql',yml:'yaml',yaml:'yaml',c:'c',cpp:'cpp',h:'cpp',go:'go',rs:'rust',sh:'shell',ps1:'powershell'}
  return ext === 'c' ? 'cpp' : map[ext] || 'plaintext'
}
