/**
 * 平台通用错误类定义
 */

/** 基础错误类 */
export class DataforgeError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'DataforgeError';
  }
}

/** 网络错误（如断网、DNS 解析失败） */
export class NetworkError extends DataforgeError {
  constructor(message: string = '网络连接失败') {
    super(message);
    this.name = 'NetworkError';
  }
}

/** HTTP 状态码错误 (非 2xx) */
export class HttpError extends DataforgeError {
  status: number;
  statusText: string;
  response: Response;

  constructor(response: Response, message?: string) {
    super(message || `HTTP Error ${response.status}: ${response.statusText}`);
    this.name = 'HttpError';
    this.status = response.status;
    this.statusText = response.statusText;
    this.response = response;
  }
}

/** 业务逻辑错误 (code !== 0) */
export class BusinessError extends DataforgeError {
  code: number;
  data?: any;

  constructor(code: number, message: string, data?: any) {
    super(message);
    this.name = 'BusinessError';
    this.code = code;
    this.data = data;
  }
}

/** 认证错误 (401/403) */
export class AuthError extends HttpError {
  constructor(response: Response, message: string = '认证失败') {
    super(response, message);
    this.name = 'AuthError';
  }
}
