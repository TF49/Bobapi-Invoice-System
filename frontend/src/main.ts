import { createApp } from 'vue'
import { createPinia } from 'pinia'
import {
  ElButton,
  ElCard,
  ElCheckbox,
  ElCol,
  ElDialog,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElIcon,
  ElInput,
  ElInputNumber,
  ElLink,
  ElLoading,
  ElOption,
  ElPagination,
  ElRadio,
  ElRow,
  ElSegmented,
  ElSelect,
  ElSwitch,
  ElTable,
  ElTableColumn,
  ElTag,
  ElTooltip,
  ElUpload
} from 'element-plus'
import 'element-plus/dist/index.css'
import './styles/theme.css'
import router from './router'
import App from './App.vue'

const app = createApp(App)
const pinia = createPinia()

const elementComponents = [
  ElButton,
  ElCard,
  ElCheckbox,
  ElCol,
  ElDialog,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElIcon,
  ElInput,
  ElInputNumber,
  ElLink,
  ElOption,
  ElPagination,
  ElRadio,
  ElRow,
  ElSegmented,
  ElSelect,
  ElSwitch,
  ElTable,
  ElTableColumn,
  ElTag,
  ElTooltip,
  ElUpload
]

for (const component of elementComponents) {
  app.use(component)
}

app.use(pinia)
app.use(router)
app.use(ElLoading)

app.mount('#app')
