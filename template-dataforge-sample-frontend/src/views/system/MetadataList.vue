<template>
  <EntityMetadataPage :locale="dataforgeUiLocale" />
</template>

<script setup lang="ts">
import { EntityMetadataPage } from '@lrenyi/dataforge-ui';
import { useDataforgeUiLocale } from '@/i18n';

const dataforgeUiLocale = useDataforgeUiLocale();
</script>
