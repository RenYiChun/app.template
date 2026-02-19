import { computed } from 'vue';
import { createI18n, useI18n } from 'vue-i18n';
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

export const usePlatformUiLocale = () => {
  const { t } = useI18n();
  return computed(() => ({
    common: {
      manageSuffix: t('common.manageSuffix'),
      add: t('common.add'),
      search: t('common.search'),
      reset: t('common.reset'),
      export: t('common.export'),
      batchDelete: t('common.batchDelete'),
      selectedCount: t('common.selectedCount'),
      batchDeleteConfirm: t('common.batchDeleteConfirm'),
      tips: t('common.tips'),
      actions: t('common.actions'),
      view: t('common.view'),
      edit: t('common.edit'),
      delete: t('common.delete'),
      noData: t('common.noData'),
      submit: t('common.submit'),
    },
    search: {
      inputPlaceholder: t('platformUi.search.inputPlaceholder'),
      selectPlaceholder: t('platformUi.search.selectPlaceholder'),
      rangePlaceholder: t('platformUi.search.rangePlaceholder'),
      start: t('platformUi.search.start'),
      end: t('platformUi.search.end'),
      all: t('platformUi.search.all'),
      yes: t('platformUi.search.yes'),
      no: t('platformUi.search.no'),
    },
    form: {
      inputPlaceholder: t('platformUi.form.inputPlaceholder'),
      selectPlaceholder: t('platformUi.form.selectPlaceholder'),
      required: t('platformUi.form.required'),
      email: t('platformUi.form.email'),
    },
    errors: {
      exportFailed: t('error.exportFailed'),
    },
  }));
};
