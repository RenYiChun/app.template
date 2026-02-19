import { createI18n } from 'vue-i18n';
import zhCN from '../locales/zh-CN';
import en from '../locales/en';

const i18n = createI18n({
  legacy: false, // Use Composition API mode
  locale: 'zh-CN', // default locale
  fallbackLocale: 'en',
  messages: {
    'zh-CN': zhCN,
    en
  }
});

export default i18n;
