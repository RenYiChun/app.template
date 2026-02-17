<template>
  <LoginPage
    :redirect-path="(route.query.redirect as string) || '/'"
    :on-success="handleSuccess"
  />
</template>

<script setup lang="ts">
import { LoginPage } from '@lrenyi/template-platform-frontend/vue';
import { useRoute, useRouter } from 'vue-router';

const route = useRoute();
const router = useRouter();

function handleSuccess(_user: unknown, path: string) {
  router.push(path);
}
</script>
