import { definePlugin } from '@halo-dev/console-shared'
import MemosView from './views/MemosView.vue'
import { markRaw } from 'vue'
import RiStickyNoteLine from '~icons/ri/sticky-note-line'

export default definePlugin({
  components: {},
  routes: [
    {
      parentName: 'Root',
      route: {
        path: '/memos',
        name: 'Memos',
        component: MemosView,
        meta: {
          title: 'Memos',
          searchable: true,
          menu: {
            name: 'Memos',
            group: 'tool',
            icon: markRaw(RiStickyNoteLine),
            priority: 0,
          },
        },
      },
    },
  ],
  extensionPoints: {},
})
