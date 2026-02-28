<template>
  <EntityMetadataPage :locale="dataforgeUiLocale"/>
</template>

<script lang="ts" setup>
import {EntityMetadataPage} from '@lrenyi/dataforge-ui';
import {useDataforgeUiLocale} from '@/i18n';

const dataforgeUiLocale = useDataforgeUiLocale();
</script>
