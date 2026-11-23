import type {ProjectFile} from './types'

const documentMime = new Set([
  'application/pdf',
  'application/msword',
  'application/vnd.ms-powerpoint',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  'application/vnd.openxmlformats-officedocument.presentationml.presentation',
])

export const fileExtension = (file: Pick<ProjectFile,'sourceExtension'|'name'>) =>
  file.sourceExtension ?? file.name.match(/\.([^.]+)$/)?.[1].toLowerCase() ?? ''

export const isDocument = (file: Pick<ProjectFile,'sourceExtension'|'name'|'type'>) =>
  documentMime.has(file.type.toLowerCase()) || /^(pdf|doc[xm]?|dot[xm]?|ppt[xm]?|pps[xm]?|pot[xm]?)$/.test(fileExtension(file))
