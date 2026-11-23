export function downloadMarkdown(name:string,content:string){
 const blob=new Blob([content],{type:'text/markdown;charset=utf-8'})
 const url=URL.createObjectURL(blob), link=document.createElement('a')
 link.href=url
 link.download=name+'.md'
 link.click()
 setTimeout(()=>URL.revokeObjectURL(url),1000)
}
