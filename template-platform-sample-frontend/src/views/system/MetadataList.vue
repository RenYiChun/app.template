<template>
  <EntityMetadataPage :locale="platformUiLocale" />
</template>

<script setup lang="ts">
import { EntityMetadataPage } from '@lrenyi/platform-ui';
import { usePlatformUiLocale } from '@/i18n';

const platformUiLocale = usePlatformUiLocale();
</script>
