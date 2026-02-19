#!/usr/bin/env node
/**
 * 登录接口端到端验证脚本（OAuth2 Password Grant + JWT）。
 * 需先启动 template-dataforge-sample-backend（mvnw.cmd spring-boot:run -pl template-dataforge-sample-backend）。
 * 用法: node scripts/verify-auth-e2e.mjs [baseUrl]
 */

const BASE = process.argv[2] || 'http://localhost:8080';
const CLIENT_ID = 'dataforge-client';
const CLIENT_SECRET = 'dataforge-secret';

async function main() {
  console.log('=== 登录接口 E2E 验证 (OAuth2 + JWT) ===');
  console.log('Base URL:', BASE);

  // 1. 获取验证码
  console.log('\n1. GET /api/auth/0/captcha');
  const capRes = await fetch(`${BASE}/api/auth/0/captcha`);
  const capText = await capRes.text();
  const capData = capText ? JSON.parse(capText) : null;
  if (!capRes.ok || !capData?.data?.key) {
    console.error('   FAIL:', capRes.status, capData);
    process.exit(1);
  }
  console.log('   OK, key:', capData.data?.key?.slice(0, 8) + '...');

  // 2. OAuth2 登录
  console.log('\n2. POST /oauth2/token (Password Grant)');
  const params = new URLSearchParams();
  params.set('grant_type', 'authorization_password');
  params.set('username', 'admin');
  params.set('password', 'admin123');
  params.set('client_id', CLIENT_ID);
  params.set('client_secret', CLIENT_SECRET);

  const tokenRes = await fetch(`${BASE}/oauth2/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: params.toString(),
  });

  if (!tokenRes.ok) {
    const errText = await tokenRes.text();
    console.error('   FAIL:', tokenRes.status, errText);
    process.exit(1);
  }

  const tokenData = await tokenRes.json();
  const accessToken = tokenData.access_token;
  if (!accessToken) {
    console.error('   FAIL: 未返回 access_token', tokenData);
    process.exit(1);
  }
  console.log('   OK, token:', accessToken.slice(0, 16) + '...');

  // 3. 获取当前用户（Bearer Token）
  console.log('\n3. GET /api/auth/0/me (Bearer token)');
  const meRes = await fetch(`${BASE}/api/auth/0/me`, {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  const meText = await meRes.text();
  const meData = meText ? JSON.parse(meText) : null;
  if (!meRes.ok || !meData?.data) {
    console.error('   FAIL:', meRes.status, meData ?? '(empty body)');
    process.exit(1);
  }
  console.log('   OK, user:', meData.data?.username);

  // 4. 登出（清除服务端会话，本地 token 由客户端管理）
  console.log('\n4. POST /api/auth/0/logout');
  await fetch(`${BASE}/api/auth/0/logout`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  console.log('   OK');

  // 5. 登出后 me 不携带 token 应返回 401
  console.log('\n5. GET /api/auth/0/me (no token)');
  const meAfter = await fetch(`${BASE}/api/auth/0/me`);
  if (meAfter.status !== 401 && meAfter.ok) {
    const body = await meAfter.text();
    const parsed = body ? JSON.parse(body) : null;
    console.error('   FAIL: 预期 401，实际', meAfter.status, parsed);
    process.exit(1);
  }
  console.log('   OK (401 as expected)');

  console.log('\n=== 全部通过 ===');
}

main().catch((e) => {
  console.error('Error:', e.message);
  if (e.cause?.code === 'ECONNREFUSED') {
    console.error('请先启动后端: mvnw.cmd spring-boot:run -pl template-dataforge-sample-backend');
  }
  process.exit(1);
});
