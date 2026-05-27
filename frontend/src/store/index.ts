import {createPinia} from 'pinia'
import type {App} from 'vue'

const pinia = createPinia()

export function setupStore(app: App) {
  app.use(pinia)
}

export { pinia }
export * from './user'
export * from './chat'
export * from './diary'

