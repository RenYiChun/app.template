/**
 * 通用路径拼接工具
 */
export function joinPath(...parts: (string | number | undefined)[]): string {
    const result = parts
        .filter((p) => p !== undefined && p !== null && p !== '')
        .map((p) => String(p).replace(/\/+/g, '/').replace(/^\//, '').replace(/\/$/, ''))
        .join('/');

    const first = parts.find((p) => p);
    if (first && String(first).startsWith('/')) {
        return `/${result}`;
    }
    return result;
}

export function ensureSlash(s: string): string {
    if (!s || s === '/') return '';
    return s.startsWith('/') ? s : `/${s}`;
}
