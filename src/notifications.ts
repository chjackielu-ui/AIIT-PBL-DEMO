import {useRouter} from 'vue-router'
import {useStore} from './store'
import {run} from './lib'
import type {Notice,NotificationType} from './types'

export const notificationTypes: {type:NotificationType;label:string}[] = [
  {type:'mentionMe',label:'@我'},
  {type:'mentionAll',label:'@全体'},
  {type:'reply',label:'回复我'},
  {type:'fileUpload',label:'文件上传'},
  {type:'codeUpdate',label:'代码更新'},
]

export function useNotificationActions() {
  const router = useRouter(), store = useStore()
  return {
    open: (notice: Notice) => run(async () => {
      if (!notice.read) await store.act('readNotices', {id:notice.id})
      await router.push(notice.path)
    }, ''),
    readAll: () => run(() => store.act('readNotices'), '全部通知已读'),
  }
}
