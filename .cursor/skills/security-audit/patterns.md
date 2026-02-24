# 安全检查模式参考

## 敏感信息关键词

- password, secret, apiKey, token, credential
- private_key, privateKey, rsa_private
- Bearer, Authorization

## 危险 API

- Runtime.getRuntime().exec
- ProcessBuilder
- Runtime.exec
- Statement.execute (非 PreparedStatement)
- ObjectInputStream.readObject
- MessageDigest.getInstance("MD5")
- Cipher.getInstance("DES")

## 配置/注解

- @RequestMapping 无鉴权
- debug=true, trace=true
- spring.profiles=dev 用于生产
