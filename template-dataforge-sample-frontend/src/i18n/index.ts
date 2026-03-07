import {computed} from 'vue';
import {createI18n, useI18n} from 'vue-i18n';
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

export const useDataforgeUiLocale = () => {
    const {t} = useI18n();
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
            inputPlaceholder: t('dataforgeUi.search.inputPlaceholder'),
            selectPlaceholder: t('dataforgeUi.search.selectPlaceholder'),
            rangePlaceholder: t('dataforgeUi.search.rangePlaceholder'),
            start: t('dataforgeUi.search.start'),
            end: t('dataforgeUi.search.end'),
            all: t('dataforgeUi.search.all'),
            yes: t('dataforgeUi.search.yes'),
            no: t('dataforgeUi.search.no'),
        },
        form: {
            inputPlaceholder: t('dataforgeUi.form.inputPlaceholder'),
            selectPlaceholder: t('dataforgeUi.form.selectPlaceholder'),
            required: t('dataforgeUi.form.required'),
            email: t('dataforgeUi.form.email'),
        },
        errors: {
            exportFailed: t('error.exportFailed'),
        },
    }));
};
